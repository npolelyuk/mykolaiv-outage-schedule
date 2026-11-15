package com.example.lightmykolaiv

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

        if (!prefs.getBoolean("notifications_enabled", true)) return Result.success()

        val selectedGroup = prefs.getInt("selected_group", 1)
        val selectedSubGroup = prefs.getInt("selected_subgroup", 1)
        val queueKey = "$selectedGroup.$selectedSubGroup"
        // Минимально возможный интервал WorkManager ~15 мин.
        // Чтобы не пропустить уведомление, мы должны захватывать окно шире, чем порог.
        // Добавляем буфер 15 минут к пользовательской настройке.
        val workManagerIntervalBuffer = 15
        val minsOffThreshold = prefs.getInt("mins_off", 15).toLong()
        val minsOnThreshold = prefs.getInt("mins_on", 5).toLong()
        val notifyOnYellow = prefs.getBoolean("notify_on_yellow", false)

        val schedule = try {
            ScraperRepository(applicationContext).fetchSchedule()
        } catch (e: Exception) {
            ScraperRepository.lastLoadedSchedule ?: return Result.retry()
        } ?: ScraperRepository.lastLoadedSchedule ?: return Result.success()

        try {
            PowerWidget().updateAll(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. Проверяем появление графика на завтра
        checkAndNotifyTomorrow(applicationContext, schedule, queueKey)

        val now = LocalDateTime.now()
        val todayDate = now.toLocalDate()
        val tomorrowDate = todayDate.plusDays(1)
        val todayGroup = schedule.getScheduleForDate(todayDate)?.groups?.get(queueKey)
            ?: schedule.today?.groups?.get(queueKey)
            ?: schedule.groups[queueKey]
            ?: return Result.success()

        if (todayGroup.hours.isEmpty()) return Result.success()

        val tomorrowGroup = schedule.getScheduleForDate(tomorrowDate)?.groups?.get(queueKey)
            ?: if (schedule.today?.date == todayDate) schedule.tomorrow?.groups?.get(queueKey) else null

        // Определяем точное начало текущего 30-минутного слота
        val currentSlotIndex = now.hour * 2 + if (now.minute >= 30) 1 else 0
        val currentSlotStart = todayDate.atStartOfDay().plusMinutes(currentSlotIndex * 30L)

        val currentStatus = todayGroup.hours.getOrNull(currentSlotIndex)?.status ?: PowerStatus.MAYBE

        val todayHours = todayGroup.hours
        val tomorrowHours = tomorrowGroup?.hours

        // Ищем изменение
        var targetOffset = -1
        var targetStatus = PowerStatus.MAYBE

        val maxLookahead = if (tomorrowHours != null && tomorrowHours.isNotEmpty()) {
            (48 - currentSlotIndex) + 48
        } else {
            48 - currentSlotIndex
        }

        for (offset in 1..maxLookahead) {
            val checkIndex = currentSlotIndex + offset
            val checkStatus = if (checkIndex < 48) {
                todayHours[checkIndex].status
            } else {
                tomorrowHours!![checkIndex - 48].status
            }

            val isTarget = when (currentStatus) {
                PowerStatus.ON, PowerStatus.MAYBE -> checkStatus == PowerStatus.OFF
                PowerStatus.OFF -> {
                    if (notifyOnYellow) checkStatus == PowerStatus.ON || checkStatus == PowerStatus.MAYBE
                    else checkStatus == PowerStatus.ON
                }
            }

            if (isTarget) {
                targetOffset = offset
                targetStatus = checkStatus
                break
            }
        }

        if (targetOffset == -1) return Result.success()

        // Рассчитываем время цели, прибавляя смещение к началу текущего слота
        // Это автоматически обрабатывает переход через полночь
        val targetTime = currentSlotStart.plusMinutes(targetOffset * 30L)
        val minutesDistance = Duration.between(now, targetTime).toMinutes()

        // ID события для дедупликации (Epoch seconds)
        val targetEpoch = targetTime.atZone(ZoneId.systemDefault()).toEpochSecond()
        val lastNotifiedTimestamp = prefs.getLong("last_notified_event_time", 0L)

        if (targetEpoch == lastNotifiedTimestamp) return Result.success()

        // Проверка порогов с учетом буфера (чтобы поймать событие между запусками воркера)
        // Разрешаем уведомлять, даже если опоздали на 5 минут (minutesDistance >= -5)
        var shouldNotify = false
        var title = ""
        var message = ""

        if (currentStatus == PowerStatus.OFF) {
            // Ждем включения
            // Если осталось времени меньше (порог + интервал воркера), уведомляем
            if (minutesDistance <= (minsOnThreshold + workManagerIntervalBuffer) && minutesDistance >= -5) {
                shouldNotify = true
                val statusText = if (targetStatus == PowerStatus.MAYBE) "жовту зону (Можливо)" else "зелену зону"
                title = "Скоро буде світло!"
                message = if (minutesDistance > 0) "Через $minutesDistance хв перехід у $statusText" else "Вмикають світло! ($statusText)"
            }
        } else {
            // Ждем выключения
            if (minutesDistance <= (minsOffThreshold + workManagerIntervalBuffer) && minutesDistance >= -5) {
                shouldNotify = true
                title = "Увага! Відключення"
                message = if (minutesDistance > 0) "Через $minutesDistance хв світло вимкнуть (Черга $queueKey)" else "Світло вимикають зараз!"
            }
        }

        if (shouldNotify) {
            showNotification(applicationContext, title, message, notificationId = 1001)
            prefs.edit().putLong("last_notified_event_time", targetEpoch).apply()
        }

        return Result.success()
    }

    companion object {
        fun checkAndNotifyTomorrow(context: Context, schedule: FullSchedule, queueKey: String) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notifications_enabled", true)) return
            if (!prefs.getBoolean("notify_tomorrow_schedule", true)) return
            if (!schedule.hasTomorrow) return

            val tomorrowSchedule = schedule.tomorrow ?: return
            val tomorrowDate = tomorrowSchedule.date
            val tomorrowDateStr = tomorrowDate.toString()
            val lastNotifiedDate = prefs.getString("last_notified_tomorrow_date", null)

            if (lastNotifiedDate == tomorrowDateStr) return

            val tomorrowGroup = schedule.getScheduleForDate(tomorrowDate)?.groups?.get(queueKey)
                ?: tomorrowSchedule.groups[queueKey]

            if (tomorrowGroup != null && tomorrowGroup.hours.isNotEmpty()) {
                val formatter = DateTimeFormatter.ofPattern("dd.MM")
                val dateFormatted = tomorrowDate.format(formatter)
                showNotification(
                    context = context,
                    title = "Оприлюднено графік на завтра!",
                    message = "З'явився графік відключень на $dateFormatted (Черга $queueKey)",
                    notificationId = 2001,
                    openTomorrow = true
                )
                prefs.edit().putString("last_notified_tomorrow_date", tomorrowDateStr).apply()
            }
        }

        fun showNotification(
            context: Context,
            title: String,
            message: String,
            notificationId: Int = System.currentTimeMillis().toInt(),
            openTomorrow: Boolean = false
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                return
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (openTomorrow) {
                    putExtra("select_day", "TOMORROW")
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, "power_notifications")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            try {
                NotificationManagerCompat.from(context).notify(
                    notificationId,
                    builder.build()
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}
