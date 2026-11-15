package com.example.lightmykolaiv

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    // 1. ЛОГИКА ТЕМЫ
    private val _currentTheme = MutableStateFlow<AppTheme>(AppTheme.DARK)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        saveToPrefs("theme", theme.name)
    }

    // 2. ЛОГИКА УВЕДОМЛЕНИЙ
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    private val _minutesBeforeOff = MutableStateFlow(15)
    val minutesBeforeOff = _minutesBeforeOff.asStateFlow()

    private val _minutesBeforeOn = MutableStateFlow(5)
    val minutesBeforeOn = _minutesBeforeOn.asStateFlow()

    // Методы обновления с мгновенным сохранением
    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        saveToPrefs("notifications_enabled", enabled)
    }

    fun setMinutesBeforeOff(minutes: Int) {
        _minutesBeforeOff.value = minutes
        saveToPrefs("mins_off", minutes)
    }

    fun setMinutesBeforeOn(minutes: Int) {
        _minutesBeforeOn.value = minutes
        saveToPrefs("mins_on", minutes)
    }

    private val _notifyOnYellow = MutableStateFlow(false)
    val notifyOnYellow = _notifyOnYellow.asStateFlow()

    fun setNotifyOnYellow(enabled: Boolean) {
        _notifyOnYellow.value = enabled
        saveToPrefs("notify_on_yellow", enabled)
    }

    private val _notifyTomorrowSchedule = MutableStateFlow(true)
    val notifyTomorrowSchedule = _notifyTomorrowSchedule.asStateFlow()

    fun setNotifyTomorrowSchedule(enabled: Boolean) {
        _notifyTomorrowSchedule.value = enabled
        saveToPrefs("notify_tomorrow_schedule", enabled)
    }

    // 3. ОСНОВНАЯ ЛОГИКА ГРАФИКА
    private val repository = ScraperRepository(application.applicationContext)
    private val _selectedGroup = MutableStateFlow(1)
    val selectedGroup = _selectedGroup.asStateFlow()
    private val _selectedSubGroup = MutableStateFlow(1)
    val selectedSubGroup = _selectedSubGroup.asStateFlow()
    private val _selectedDay = MutableStateFlow(SelectedDay.TODAY)
    val selectedDay = _selectedDay.asStateFlow()
    private val _schedule = MutableStateFlow(FullSchedule())
    val schedule = _schedule.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    val currentQueueKey = combine(selectedGroup, selectedSubGroup) { g, sg -> "$g.$sg" }

    init {
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Восстановление всех настроек
        _currentTheme.value = AppTheme.valueOf(prefs.getString("theme", AppTheme.AUTO.name) ?: AppTheme.AUTO.name)
        _selectedGroup.value = prefs.getInt("selected_group", 1)
        _selectedSubGroup.value = prefs.getInt("selected_subgroup", 1)
        _notificationsEnabled.value = prefs.getBoolean("notifications_enabled", true)
        _minutesBeforeOff.value = prefs.getInt("mins_off", 15)
        _minutesBeforeOn.value = prefs.getInt("mins_on", 5)
        _notifyOnYellow.value = prefs.getBoolean("notify_on_yellow", false)
        _notifyTomorrowSchedule.value = prefs.getBoolean("notify_tomorrow_schedule", true)

        refreshSchedule()

        // Запуск фонового процесса уведомлений
        setupNotificationWork()

        // Таймер обновления текущего времени в UI
        viewModelScope.launch {
            while (true) {
                _currentTime.value = LocalDateTime.now()
                delay(1000)
            }
        }
        
        // Принудительно обновляем виджет при старте, чтобы он синхронизировался с текущим состоянием
        updateWidget()
    }

    // Вспомогательная функция для чистоты кода
    private fun saveToPrefs(key: String, value: Any) {
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
            }
            // Замените apply() на commit()!
            commit()
        }
    }
    private fun updateWidget() {
        viewModelScope.launch {
            try {
                PowerWidget().updateAll(getApplication()) //
            } catch (e: Exception) {
                // Игнорируем ошибки инициализации виджета
            }
        }
    }

    fun setGroup(group: Int) {
        _selectedGroup.value = group
        saveToPrefs("selected_group", group)
        updateWidget()
    }

    fun setSubGroup(subGroup: Int) {
        _selectedSubGroup.value = subGroup
        saveToPrefs("selected_subgroup", subGroup)
        updateWidget()
    }

    fun setSelectedDay(day: SelectedDay) {
        _selectedDay.value = day
    }

    fun refreshSchedule() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.fetchSchedule()?.let {
                _schedule.value = it
                updateWidget() // ДОБАВИТЬ: чтобы виджет узнал о новых данных
                val queueKey = "${_selectedGroup.value}.${_selectedSubGroup.value}"
                NotificationWorker.checkAndNotifyTomorrow(getApplication(), it, queueKey)
            }
            _isLoading.value = false
        }
    }

    fun getTimeUntilNextChange(queueKey: String): TimeUntilChange? {
        return calculateTimeUntilChange(_schedule.value, queueKey, _currentTime.value)
    }

    companion object {
        fun calculateTimeUntilChange(
            scheduleValue: FullSchedule,
            queueKey: String,
            now: LocalDateTime
        ): TimeUntilChange? {
            val todayDate = now.toLocalDate()
            val tomorrowDate = todayDate.plusDays(1)

            val todayGroup = scheduleValue.getScheduleForDate(todayDate)?.groups?.get(queueKey)
                ?: scheduleValue.today?.groups?.get(queueKey)
                ?: scheduleValue.groups[queueKey]
                ?: return null

            if (todayGroup.hours.isEmpty()) return null

            val tomorrowGroup = scheduleValue.getScheduleForDate(tomorrowDate)?.groups?.get(queueKey)
                ?: if (scheduleValue.today?.date == todayDate) scheduleValue.tomorrow?.groups?.get(queueKey) else null

            // Индекс текущего 30-минутного слота (0-47)
            val currentSlotIndex = now.hour * 2 + if (now.minute >= 30) 1 else 0
            val currentStatus = todayGroup.hours.getOrNull(currentSlotIndex)?.status ?: PowerStatus.MAYBE

            val todayHours = todayGroup.hours
            val tomorrowHours = tomorrowGroup?.hours

            var slotsOffset = 1
            var nextStatus = currentStatus

            val maxOffset = if (tomorrowHours != null && tomorrowHours.isNotEmpty()) {
                (48 - currentSlotIndex) + 48
            } else {
                48 - currentSlotIndex
            }

            while (slotsOffset < maxOffset) {
                val checkIndex = currentSlotIndex + slotsOffset
                val statusAtSlot = if (checkIndex < 48) {
                    todayHours[checkIndex].status
                } else {
                    tomorrowHours!![checkIndex - 48].status
                }

                if (statusAtSlot != currentStatus) {
                    nextStatus = statusAtSlot
                    break
                }
                slotsOffset++
            }

            if (nextStatus == currentStatus) {
                nextStatus = when (currentStatus) {
                    PowerStatus.ON -> PowerStatus.OFF
                    PowerStatus.OFF -> PowerStatus.ON
                    PowerStatus.MAYBE -> PowerStatus.ON
                }
            }

            val todayStart = todayDate.atStartOfDay()
            val targetTime = todayStart.plusMinutes((currentSlotIndex + slotsOffset) * 30L)
            val duration = Duration.between(now, targetTime)
            val safeDuration = if (duration.isNegative) Duration.ZERO else duration

            val totalSeconds = safeDuration.seconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return TimeUntilChange(
                hours,
                minutes,
                seconds,
                nextStatus
            )
        }
    }
    // В MainViewModel.kt
    fun setupNotificationWork() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniquePeriodicWork(
                "power_check",
                ExistingPeriodicWorkPolicy.KEEP, // Не перезапускать, если уже работает
                workRequest
            )
    }
}
