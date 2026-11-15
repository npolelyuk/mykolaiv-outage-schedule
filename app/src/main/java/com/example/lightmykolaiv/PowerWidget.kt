@file:Suppress("RestrictedApi")

package com.example.lightmykolaiv

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PowerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = try {
            manager.getAppWidgetId(id)
        } catch (e: Exception) {
            -1
        }

        if (ScraperRepository.lastLoadedSchedule == null) {
            try {
                ScraperRepository(context).fetchSchedule()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        provideContent {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val customQueue = if (appWidgetId != -1) prefs.getString("widget_queue_$appWidgetId", null) else null
            val queueKey = customQueue ?: "${prefs.getInt("selected_group", 1)}.${prefs.getInt("selected_subgroup", 1)}"

            val schedule = ScraperRepository.lastLoadedSchedule
            val todayGroup = schedule?.today?.groups?.get(queueKey) ?: schedule?.groups?.get(queueKey)
            val hours = todayGroup?.hours ?: emptyList()

            val now = LocalTime.now()
            val currentSlotIndex = now.hour * 2 + if (now.minute >= 30) 1 else 0
            val currentStatus = hours.find { it.hour == currentSlotIndex }?.status ?: PowerStatus.MAYBE

            // Расчет времени до смены зоны
            val timeLeft = calculateTimeLeft(schedule, queueKey, now)

            // Текущее время (например 20:29)
            val currentTimeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))

            // Расчет периодов и текущего сегмента
            val periods = calculateWidgetPeriods(hours)
            val currentPeriod = periods.find { currentSlotIndex >= it.startSlot && currentSlotIndex < it.endSlot }
                ?: periods.firstOrNull()

            // Создаем битмап шкалы графика с белым индикатором
            val timelineBitmap = createTimelineBitmap(periods, now)

            WidgetCard(
                context = context,
                appWidgetId = appWidgetId,
                queueKey = queueKey,
                currentStatus = currentStatus,
                currentTimeText = currentTimeText,
                timeLeft = timeLeft,
                timelineBitmap = timelineBitmap,
                currentPeriod = currentPeriod
            )
        }
    }

    @Composable
    private fun WidgetCard(
        context: Context,
        appWidgetId: Int,
        queueKey: String,
        currentStatus: PowerStatus,
        currentTimeText: String,
        timeLeft: String,
        timelineBitmap: Bitmap,
        currentPeriod: WidgetPeriod?
    ) {
        val size = LocalSize.current
        val isVerySmall = size.height < 210.dp || size.width < 185.dp
        val isCompact = size.height < 265.dp || size.width < 235.dp
        val showSubtext = size.height >= 240.dp && size.width >= 200.dp

        val paddingH = if (isVerySmall) 12.dp else if (isCompact) 14.dp else 16.dp
        val paddingV = if (isVerySmall) 7.dp else if (isCompact) 9.dp else 13.dp

        val badgeFont = if (isVerySmall) 11.sp else if (isCompact) 12.sp else 13.5.sp
        val timeFont = if (isVerySmall) 13.sp else if (isCompact) 15.sp else 18.sp
        val statusFont = if (isVerySmall) 19.sp else if (isCompact) 23.sp else 29.sp
        val timerFont = if (isVerySmall) 17.sp else if (isCompact) 20.sp else 26.sp
        val timerSubFont = if (isVerySmall) 8.5.sp else if (isCompact) 9.5.sp else 11.sp
        val timelineHeight = if (isVerySmall) 15.dp else if (isCompact) 18.dp else 22.dp

        val spaceStatusTimer = 1.dp
        val spaceCardButton = if (isVerySmall) 3.dp else if (isCompact) 5.dp else 8.dp

        val cardPadH = if (isVerySmall) 8.dp else if (isCompact) 10.dp else 12.dp
        val cardPadV = if (!showSubtext) (if (isVerySmall) 3.dp else 4.dp) else (if (isVerySmall) 4.dp else 6.dp)

        val cardTimeFont = if (isVerySmall) 11.sp else if (isCompact) 12.sp else 13.5.sp
        val cardStatusFont = if (isVerySmall) 10.sp else if (isCompact) 11.sp else 12.sp
        val cardClockSize = if (isVerySmall) 11.dp else if (isCompact) 12.5.dp else 14.dp
        val cardDurationFont = if (isVerySmall) 11.sp else if (isCompact) 12.sp else 13.5.sp
        val cardDurationSubFont = if (isVerySmall) 8.5.sp else if (isCompact) 9.5.sp else 10.5.sp

        val buttonHeight = if (isVerySmall) 28.dp else if (isCompact) 33.dp else 39.dp
        val buttonIconSize = if (isVerySmall) 13.dp else if (isCompact) 15.dp else 17.dp
        val buttonFont = if (isVerySmall) 11.5.sp else if (isCompact) 13.sp else 14.5.sp

        // Цвета виджета: точный красный с картинки, стильные зеленый и желтый
        val cardBgColor = when (currentStatus) {
            PowerStatus.ON -> Color(0xFF00B862)    // Насыщенный гармоничный зеленый
            PowerStatus.OFF -> Color(0xFFF0283C)   // Красный 1 в 1 с картинки
            PowerStatus.MAYBE -> Color(0xFFE88A1A) // Стильный янтарный
        }

        val statusText = when (currentStatus) {
            PowerStatus.ON -> "СВІТЛО Є"
            PowerStatus.OFF -> "ВІДКЛЮЧЕНО"
            PowerStatus.MAYBE -> "МОЖЛИВО"
        }

        val segmentDotColor = when (currentPeriod?.status) {
            PowerStatus.ON -> Color(0xFF00D26A)
            PowerStatus.OFF -> Color(0xFFF0283C)
            PowerStatus.MAYBE -> Color(0xFFFFC107)
            null -> Color.White
        }

        val segmentStatusTitle = when (currentPeriod?.status) {
            PowerStatus.ON -> "Світло є"
            PowerStatus.OFF -> "Відключено"
            PowerStatus.MAYBE -> "Можливо"
            null -> ""
        }

        val segmentTimeRange = currentPeriod?.timeRange ?: "--:-- - --:--"
        val segmentDurationText = currentPeriod?.durationText ?: "--"

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(cardBgColor)
                .padding(horizontal = paddingH, vertical = paddingV),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Top
        ) {
            // Верхняя кликабельная часть (открывает приложение)
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Top
            ) {
                // 1. Верхний ряд: ЧЕРГА X.X (нажатие открывает выбор очереди) и текущее время
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        putExtra("appWidgetId", appWidgetId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        data = android.net.Uri.parse("lightmykolaiv://widget/config/$appWidgetId")
                    }

                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(100.dp)
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = if (isVerySmall) 8.dp else 10.dp, vertical = if (isVerySmall) 2.dp else 3.dp)
                            .clickable(actionStartActivity(configIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ЧЕРГА $queueKey",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = badgeFont
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = currentTimeText,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = timeFont
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 2. Статус (ВІДКЛЮЧЕНО / СВІТЛО Є)
                Text(
                    text = statusText,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = statusFont,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(spaceStatusTimer))

                // 3. Таймер обратного отсчета
                if (timeLeft.isNotEmpty()) {
                    Text(
                        text = timeLeft,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = timerFont,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "ДО ЗМІНИ СТАНУ",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                            fontSize = timerSubFont,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 4. Горизонтальная шкала графика с белым индикатором
                Image(
                    provider = ImageProvider(timelineBitmap),
                    contentDescription = "Графік",
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(timelineHeight),
                    contentScale = ContentScale.FillBounds
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 5. Карточка информации о текущем сегменте
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(if (showSubtext) 16.dp else 13.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = cardPadH, vertical = cardPadV)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Левая часть: точка и блок (время + статус) вертикально отцентрированы
                        Row(
                            modifier = GlanceModifier.defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(if (isVerySmall) 7.dp else 8.dp)
                                    .cornerRadius(10.dp)
                                    .background(segmentDotColor)
                            ) {}
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Column(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = segmentTimeRange,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = cardTimeFont
                                    ),
                                    maxLines = 1
                                )
                                if (showSubtext) {
                                    Spacer(modifier = GlanceModifier.height(1.dp))
                                    Text(
                                        text = segmentStatusTitle,
                                        style = TextStyle(
                                            color = ColorProvider(Color.White.copy(alpha = 0.9f)),
                                            fontWeight = FontWeight.Normal,
                                            fontSize = cardStatusFont
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Разделитель
                        Box(
                            modifier = GlanceModifier
                                .width(1.dp)
                                .height(if (showSubtext) (if (isVerySmall) 20.dp else 24.dp) else (if (isVerySmall) 14.dp else 16.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {}

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Правая часть: часы и длительность (плюс "тривалість" если showSubtext)
                        Column(
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_clock_white),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(cardClockSize)
                                )
                                Spacer(modifier = GlanceModifier.width(4.dp))
                                Text(
                                    text = segmentDurationText,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = cardDurationFont
                                    ),
                                    maxLines = 1
                                )
                            }
                            if (showSubtext) {
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = "тривалість",
                                    style = TextStyle(
                                        color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                                        fontSize = cardDurationSubFont
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())
            }

            Spacer(modifier = GlanceModifier.height(spaceCardButton))

            // 6. Кнопка "Оновити" (гарантированно прижата к низу карточки)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .cornerRadius(100.dp)
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh_white),
                        contentDescription = "Оновити",
                        modifier = GlanceModifier.size(buttonIconSize)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "Оновити",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = buttonFont
                        )
                    )
                }
            }
        }
    }

    private fun createTimelineBitmap(periods: List<WidgetPeriod>, now: LocalTime): Bitmap {
        val width = 720
        val height = 56
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val barTop = 14f
        val barBottom = 42f
        val barHeight = barBottom - barTop
        val cornerRadius = barHeight / 2f

        val barRect = RectF(0f, barTop, width.toFloat(), barBottom)

        // 1. Светлая полупрозрачная подложка-трек шкалы
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(35, 255, 255, 255)
        }
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, trackPaint)

        // 2. Отрисовка цветных сегментов с обрезкой по скруглению
        if (periods.isNotEmpty()) {
            val clipPath = Path().apply {
                addRoundRect(barRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)

            val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            periods.forEach { period ->
                val x1 = width * (period.startSlot / 48f)
                val x2 = width * (period.endSlot / 48f)
                segmentPaint.color = when (period.status) {
                    PowerStatus.ON -> android.graphics.Color.parseColor("#00D26A")
                    PowerStatus.OFF -> android.graphics.Color.parseColor("#EE243A")
                    PowerStatus.MAYBE -> android.graphics.Color.parseColor("#FFC107")
                }
                canvas.drawRect(x1, barTop, x2, barBottom, segmentPaint)
            }
            canvas.restore()
        }

        // 3. Полупрозрачная белая окантовка шкалы (в стиле референса)
        val strokeW = 3f
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = android.graphics.Color.argb(85, 255, 255, 255)
        }
        val borderRect = RectF(
            barRect.left + strokeW / 2f,
            barRect.top + strokeW / 2f,
            barRect.right - strokeW / 2f,
            barRect.bottom - strokeW / 2f
        )
        canvas.drawRoundRect(borderRect, cornerRadius - strokeW / 2f, cornerRadius - strokeW / 2f, borderPaint)

        // 4. Белый индикатор текущего времени
        val currentMinutes = (now.hour * 60 + now.minute).toFloat().coerceIn(0f, 1440f)
        val pinX = (width * (currentMinutes / 1440f)).coerceIn(10f, width - 10f)

        // Верхний треугольник маркера
        val triangleWidth = 18f
        val triangleHeight = 13f
        val triangleTop = 2f

        val trianglePath = Path().apply {
            moveTo(pinX - triangleWidth / 2f, triangleTop)
            lineTo(pinX + triangleWidth / 2f, triangleTop)
            lineTo(pinX, triangleTop + triangleHeight)
            close()
        }
        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(trianglePath, whiteFill)

        // Белая вертикальная черта через шкалу
        val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(pinX, triangleTop + triangleHeight - 2f, pinX, barBottom + 6f, shaftPaint)

        return bitmap
    }

    private fun calculateWidgetPeriods(hours: List<HourData>): List<WidgetPeriod> {
        if (hours.isEmpty()) return emptyList()
        val periods = mutableListOf<WidgetPeriod>()
        var startSlot = 0
        var currentStatus = hours[0].status

        for (i in 1 until hours.size) {
            if (hours[i].status != currentStatus) {
                periods.add(WidgetPeriod(startSlot, i, currentStatus))
                startSlot = i
                currentStatus = hours[i].status
            }
        }
        periods.add(WidgetPeriod(startSlot, hours.size, currentStatus))
        return periods
    }

    private fun calculateTimeLeft(schedule: FullSchedule?, queueKey: String, now: LocalTime): String {
        val todayGroup = schedule?.today?.groups?.get(queueKey) ?: schedule?.groups?.get(queueKey) ?: return ""
        val currentSlotIndex = now.hour * 2 + if (now.minute >= 30) 1 else 0
        val currentStatus = todayGroup.hours.find { it.hour == currentSlotIndex }?.status ?: return ""

        val tomorrowGroup = schedule?.tomorrow?.groups?.get(queueKey)
        val todayHours = todayGroup.hours
        val tomorrowHours = tomorrowGroup?.hours

        var slotsOffset = 1
        val maxOffset = if (!tomorrowHours.isNullOrEmpty()) {
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
            if (statusAtSlot != currentStatus) break
            slotsOffset++
        }

        val targetMinutesFromStart = (currentSlotIndex + slotsOffset) * 30L
        val currentMinutesFromStart = (now.hour * 60 + now.minute).toLong()

        val diffMinutes = (targetMinutesFromStart - currentMinutesFromStart).coerceAtLeast(0)

        val h = diffMinutes / 60
        val m = diffMinutes % 60

        return if (h > 0) "${h}г ${m}хв" else "${m}хв"
    }
}

data class WidgetPeriod(
    val startSlot: Int,
    val endSlot: Int,
    val status: PowerStatus
) {
    val startHour: Int get() = startSlot / 2
    val startMinute: Int get() = (startSlot % 2) * 30
    val endHour: Int get() = endSlot / 2
    val endMinute: Int get() = (endSlot % 2) * 30

    val timeRange: String get() = "%02d:%02d - %02d:%02d".format(startHour, startMinute, endHour, endMinute)

    val durationText: String get() {
        val totalMinutes = (endSlot - startSlot) * 30
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}г ${m}хв"
            h > 0 -> "$h год"
            else -> "$m хв"
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val schedule = ScraperRepository(context).fetchSchedule()
            if (schedule != null) {
                ScraperRepository.lastLoadedSchedule = schedule
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            PowerWidget().update(context, glanceId)
        } catch (e: Exception) {
            // ignore
        }
        PowerWidget().updateAll(context)
    }
}

class PowerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PowerWidget()
}
