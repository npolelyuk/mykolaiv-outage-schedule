package com.example.lightmykolaiv

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import android.content.Intent
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.activity.viewModels
import androidx.glance.appwidget.updateAll


enum class AppTheme {LIGHT, DARK, AUTO}
// Цветовая палитра точно как на виджете
val AppGreen = Color(0xFF00B862)  // Зелений (Світло є) - фон карточки ON
val AppRed = Color(0xFFF0283C)    // Червоний (Відключено) - фон карточки OFF
val AppYellow = Color(0xFFE88A1A) // Бурштиновий (Можливо) - фон карточки MAYBE
val SegmentGreen = Color(0xFF00D26A)  // Зелений сегмент шкали (ON)
val SegmentRed = Color(0xFFF0283C)    // Червоний сегмент шкали (OFF)
val SegmentYellow = Color(0xFFFFC107) // Жовтий сегмент шкали (MAYBE)
val AppWhite = Color(0xFFFFFFFF)
val AppBackground = Color(0xFFF8F9FA)
val TextGray = Color(0xFF9E9E9E)



class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean ->
        if (isGranted){
            //permission granted
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        checkNotificationPermissions()
        handleIntent(intent)
        setContent {
            // Светлая тема принудительно для соответствия дизайну
            MaterialTheme(colorScheme = lightColorScheme(surface = Color(0xFFF8F9FA))) {
                PowerScheduleApp(viewModel)
            }
        }
    }
    override fun onStart() {
        super.onStart()
        viewModel.refreshSchedule()
    }

    // ВАЖНО: Исправленная функция (добавлен знак ?)
    // Вызывается, когда вы тапаете по виджету, а приложение уже открыто
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent // Используем свойство intent вместо setIntent
        handleIntent(intent)
        viewModel.refreshSchedule() // Обновляем данные при новом интенте (например, клик по виджету)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra("select_day") == "TOMORROW") {
            viewModel.setSelectedDay(SelectedDay.TOMORROW)
        }
    }

    // Метод для мгновенной команды виджету перерисоваться
    private fun updateWidgetNow() {
        MainScope().launch {
            try {
                PowerWidget().updateAll(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun checkNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Статус Світла"
            val descriptionText = "Сповіщення про відключення та ввімкнення"
            val importance = NotificationManager.IMPORTANCE_HIGH // ВАЖНО: HIGH для всплывающих уведомлений
            val channel = NotificationChannel("power_notifications", name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerScheduleApp(viewModel: MainViewModel = viewModel()) {
    // Получаем текущие состояния из ViewModel
    val theme: AppTheme by viewModel.currentTheme.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val selectedSubGroup by viewModel.selectedSubGroup.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val queueKey by viewModel.currentQueueKey.collectAsState(initial = "1.1")

    // Состояние видимости шторки настроек
    var showSettings by remember { mutableStateOf(false) }

    // Логика определения темного режима
    val isDark = when(theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.AUTO -> isSystemInDarkTheme()
    }

    // Палитра цветов на основе выбранной темы
    val bgColor = if (isDark) Color(0xFF0F111A) else Color(0xFFF8F9FA)
    val cardColor = if (isDark) Color(0xFF1B1E29) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color(0xFF9496A1) else Color.Gray

    val timeUntilChange = remember(currentTime, queueKey, schedule) {
        viewModel.getTimeUntilNextChange(queueKey)
    }

    val todayDate = schedule.today?.date ?: currentTime.toLocalDate()
    val tomorrowDate = schedule.tomorrow?.date ?: todayDate.plusDays(1)

    // Анимация цвета карточки статуса
    val currentSlotIndex = currentTime.hour * 2 + if (currentTime.minute >= 30) 1 else 0
    val todayGroupData = schedule.getScheduleForDate(currentTime.toLocalDate())?.groups?.get(queueKey)
        ?: schedule.today?.groups?.get(queueKey)
        ?: schedule.groups[queueKey]
    val currentStatus = todayGroupData?.hours?.getOrNull(currentSlotIndex)?.status
        ?: PowerStatus.MAYBE

    val targetColor = when (currentStatus) {
        PowerStatus.ON -> AppGreen
        PowerStatus.OFF -> AppRed
        PowerStatus.MAYBE -> AppYellow
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 600),
        label = "colorAnimation"
    )

    var showLinksSheet by remember { mutableStateOf(false) }

    val lastUpdatedTime = remember(schedule.lastUpdated) {
        schedule.lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Scaffold(
            containerColor = bgColor,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFD54F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Миколаївобленерго",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color.White else Color.Black
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Настройки",
                                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refreshSchedule() },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    StatusCard(
                        bgColor = animatedStatusColor,
                        status = currentStatus,
                        queue = queueKey,
                        countdown = timeUntilChange,
                        lastUpdatedTime = lastUpdatedTime
                    )

                    // В селекторы и графики тоже нужно передать параметры темы или использовать cardColor
                    GroupSelectors(
                        selectedGroup,
                        { viewModel.setGroup(it) },
                        selectedSubGroup,
                        { viewModel.setSubGroup(it) },
                        isDark = isDark
                    )

                    DaySelector(
                        selectedDay = selectedDay,
                        onDaySelected = { viewModel.setSelectedDay(it) },
                        hasTomorrow = schedule.hasTomorrow,
                        todayDate = todayDate,
                        tomorrowDate = tomorrowDate,
                        isDark = isDark
                    )

                    val displayedGroupData = when (selectedDay) {
                        SelectedDay.TODAY -> todayGroupData
                        SelectedDay.TOMORROW -> schedule.tomorrow?.groups?.get(queueKey)
                    }

                    val dayTitle = when (selectedDay) {
                        SelectedDay.TODAY -> "ГРАФІК НА СЬОГОДНІ"
                        SelectedDay.TOMORROW -> "ГРАФІК НА ЗАВТРА"
                    }

                    ScheduleTimeline(
                        groupData = displayedGroupData,
                        now = currentTime,
                        lastUpdated = schedule.lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        isDark = isDark,
                        isToday = (selectedDay == SelectedDay.TODAY),
                        dayTitle = dayTitle,
                        onRefresh = { viewModel.refreshSchedule() }
                    )

                    InfoCard(
                        schedule.lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }

        // Парящий остров (Навигация)
        Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)) {
            FloatingNavBar(
                onRefresh = { viewModel.refreshSchedule() },
                onOpenLinks = { showLinksSheet = true },
                isLoading = isLoading,
                isDark = isDark
            )
        }
    }

    // Отображение меню ссылок
    if (showLinksSheet) {
        LinksSheet(
            isDark = isDark,
            onDismiss = { showLinksSheet = false }
        )
    }

    // Диалог найденного обновления
    val updateInfo by viewModel.updateInfo.collectAsState()
    if (updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            isDark = isDark,
            onDismiss = { viewModel.dismissUpdate() }
        )
    }

    // Отображение меню настроек
    if (showSettings) {
        SettingsSheet(
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun FloatingNavBar(
    onRefresh: () -> Unit,
    onOpenLinks: () -> Unit,
    isLoading: Boolean,
    isDark: Boolean
) {
    val haptic = LocalHapticFeedback.current

    // Анимация вращения для иконки обновления
    val rotation by animateFloatAsState(
        targetValue = if (isLoading) 360f else 0f,
        animationSpec = if (isLoading) {
            infiniteRepeatable(animation = tween(1000, easing = LinearEasing))
        } else {
            tween(0)
        },
        label = "rotation"
    )

    // Цвета панели в зависимости от темы
    val containerColor = if (isDark) Color(0xFF1B1E29) else Color.White
    val iconColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.LightGray

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(72.dp)
            .shadow(if (isDark) 20.dp else 12.dp, CircleShape),
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // КНОПКА ГЛОБУС (открывает меню ссылок)
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenLinks()
            }) {
                Icon(Icons.Default.Language, contentDescription = "Корисні посилання", tint = iconColor)
            }

            // ЦЕНТРАЛЬНАЯ КНОПКА (Обновление)
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    },
                shape = CircleShape,
                color = Color(0xFFFFD54F) // Желтый всегда желтый
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            // КНОПКА ВИДЖЕТЫ
            val context = androidx.compose.ui.platform.LocalContext.current
            IconButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val appWidgetManager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                    val myProvider = android.content.ComponentName(context, PowerWidgetReceiver::class.java)
                    if (appWidgetManager.isRequestPinAppWidgetSupported) {
                        val previewViews = android.widget.RemoteViews(context.packageName, R.layout.widget_preview_layout)
                        val extras = android.os.Bundle().apply {
                            putParcelable(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, previewViews)
                        }
                        appWidgetManager.requestPinAppWidget(myProvider, extras, null)
                    } else {
                        android.widget.Toast.makeText(context, "Ваш лаунчер не підтримує додавання віджетів", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Додайте віджет вручну через меню", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Widgets, null, tint = iconColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSheet(
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = if (isDark) Color(0xFF1B1E29) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1F2937)
    val subtextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val itemBg = if (isDark) Color(0xFF242836) else Color(0xFFF3F4F6)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.15f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Корисні посилання",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 1. Офіційний графік
            LinkItemCard(
                title = "Офіційний графік відключень",
                subtitle = "off.energy.mk.ua",
                icon = Icons.Default.ElectricBolt,
                iconTint = Color(0xFFFFD54F),
                bgColor = itemBg,
                textColor = textColor,
                subtextColor = subtextColor,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    uriHandler.openUri("https://off.energy.mk.ua/")
                    onDismiss()
                }
            )

            // 2. Телеграм-канал
            LinkItemCard(
                title = "Телеграм-канал Миколаївобленерго",
                subtitle = "t.me/mk_energy_ua • оперативні новини",
                icon = Icons.AutoMirrored.Filled.Send,
                iconTint = Color(0xFF29B6F6),
                bgColor = itemBg,
                textColor = textColor,
                subtextColor = subtextColor,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    uriHandler.openUri("https://t.me/mk_energy_ua")
                    onDismiss()
                }
            )

            // 3. Інші відключення
            LinkItemCard(
                title = "Інші відключення",
                subtitle = "energy.mk.ua/vidklyuchennya • аварійні та планові",
                icon = Icons.Default.WarningAmber,
                iconTint = Color(0xFFFFA726),
                bgColor = itemBg,
                textColor = textColor,
                subtextColor = subtextColor,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    uriHandler.openUri("https://www.energy.mk.ua/vidklyuchennya/")
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun LinkItemCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    bgColor: Color,
    textColor: Color,
    subtextColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = subtextColor
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = subtextColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun StatusCard(
    bgColor: Color,
    status: PowerStatus,
    queue: String,
    countdown: TimeUntilChange?,
    lastUpdatedTime: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // Немного увеличили высоту для комфортного размещения
            .shadow(12.dp, RoundedCornerShape(40.dp)),
        shape = RoundedCornerShape(40.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ВЕРХНЯЯ ПАНЕЛЬ (Очередь и Время обновления)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        "ЧЕРГА $queue",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("ОНОВЛЕНО", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                    Text(
                        lastUpdatedTime,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            // УВЕЛИЧЕННЫЙ ОТСТУП, чтобы текст не налезал на верх
            Spacer(Modifier.height(16.dp))

            // ЦЕНТРАЛЬНЫЙ БЛОК
            Text(
                text = when (status) {
                    PowerStatus.ON -> "СВІТЛО Є"
                    PowerStatus.OFF -> "ВІДКЛЮЧЕНО"
                    PowerStatus.MAYBE -> "МОЖЛИВО"
                },
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = countdown?.let { "%d:%02d:%02d".format(it.hours, it.minutes, it.seconds) } ?: "--:--:--",
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp)) // Пружина, чтобы текст "ДО ЗМІНИ" был чуть ниже

            Text(
                "ДО ЗМІНИ СТАНУ",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ScheduleCard(groupData: GroupSchedule?, lastUpdate: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ГРАФІК НА ДОБУ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                Text("Оновлено: $lastUpdate", fontSize = 10.sp, color = TextGray)
            }

            Spacer(Modifier.height(16.dp))

            // Полоска графика с разделителями
            Row(modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))) {
                groupData?.hours?.forEach { h ->
                    Box(modifier = Modifier
                        .weight(1f).fillMaxHeight()
                        .background(when(h.status) {
                            PowerStatus.ON -> SegmentGreen
                            PowerStatus.OFF -> SegmentRed
                            PowerStatus.MAYBE -> SegmentYellow
                        })
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f)) // Тонкие разделители
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0:00", "4:00", "8:00", "12:00", "16:00", "20:00", "23:00").forEach { time ->
                    Text(time, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextGray)
                }
            }

            // Легенда
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                LegendItem(SegmentGreen, "Є СВІТЛО")
                Spacer(Modifier.width(16.dp))
                LegendItem(SegmentRed, "НЕМАЄ")
                Spacer(Modifier.width(16.dp))
                LegendItem(SegmentYellow, "МОЖЛИВО")
            }
        }
    }
}
@Composable
fun BottomNavBar(onRefresh: () -> Unit, isLoading: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.width(280.dp).height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            shadowElevation = 15.dp
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Default.Language, null, tint = Color.Gray) }
                Surface(
                    modifier = Modifier.size(48.dp).clickable { onRefresh() },
                    shape = CircleShape, color = Color(0xFFFFD54F)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.Black)
                        else Icon(Icons.Default.Refresh, null, tint = Color.Black)
                    }
                }
                IconButton(onClick = {}) { Icon(Icons.Default.Widgets, null, tint = Color.Gray) }
            }
        }
    }
}
@Composable
fun GroupSelectors(
    selectedGroup: Int,
    onGroupSelected: (Int) -> Unit,
    selectedSubGroup: Int,
    onSubGroupSelected: (Int) -> Unit,
    isDark: Boolean // Добавляем параметр
) {
    // Определяем цвета на основе темы
    val unselectedCardColor = if (isDark) Color(0xFF1B1E29) else Color.White
    val unselectedTextColor = if (isDark) Color(0xFF9E9E9E) else Color.LightGray

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ВИБЕРІТЬ ЧЕРГУ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..6).forEach { num ->
                val isSelected = selectedGroup == num
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(1f)
                        .shadow(if (isSelected) 10.dp else 0.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Color(0xFFFFD54F) else unselectedCardColor) // Используем переменную
                        .clickable { onGroupSelected(num) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$num", fontWeight = FontWeight.Black, color = if (isSelected) Color.Black else unselectedTextColor)
                }
            }
        }

        Text("ВИБЕРІТЬ ПІДЧЕРГУ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1, 2).forEach { sg ->
                val isSelected = selectedSubGroup == sg
                Box(
                    modifier = Modifier.weight(1f).height(56.dp)
                        .shadow(if (isSelected) 10.dp else 0.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFFFFD54F) else unselectedCardColor) // Используем переменную
                        .clickable { onSubGroupSelected(sg) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$sg ПІДЧЕРГА", fontWeight = FontWeight.Black, color = if (isSelected) Color.Black else unselectedTextColor)
                }
            }
        }
    }
}

@Composable
fun DaySelector(
    selectedDay: SelectedDay,
    onDaySelected: (SelectedDay) -> Unit,
    hasTomorrow: Boolean,
    todayDate: LocalDate,
    tomorrowDate: LocalDate,
    isDark: Boolean
) {
    val unselectedCardColor = if (isDark) Color(0xFF1B1E29) else Color.White
    val unselectedTextColor = if (isDark) Color(0xFF9E9E9E) else Color.LightGray
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ВИБЕРІТЬ ДЕНЬ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            if (hasTomorrow) {
                Surface(
                    color = Color(0xFF00B862).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00B862))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Є графік на завтра",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00B862)
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Сьогодні
            val isTodaySelected = selectedDay == SelectedDay.TODAY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .shadow(if (isTodaySelected) 10.dp else 0.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTodaySelected) Color(0xFFFFD54F) else unselectedCardColor)
                    .clickable { onDaySelected(SelectedDay.TODAY) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "СЬОГОДНІ",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (isTodaySelected) Color.Black else unselectedTextColor
                    )
                    Text(
                        todayDate.format(dateFormatter),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isTodaySelected) Color.Black.copy(alpha = 0.7f) else Color.Gray
                    )
                }
            }

            // Завтра
            val isTomorrowSelected = selectedDay == SelectedDay.TOMORROW
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .shadow(if (isTomorrowSelected) 10.dp else 0.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTomorrowSelected) Color(0xFFFFD54F) else unselectedCardColor)
                    .clickable { onDaySelected(SelectedDay.TOMORROW) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ЗАВТРА",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = if (isTomorrowSelected) Color.Black else unselectedTextColor
                        )
                        if (hasTomorrow) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isTomorrowSelected) Color(0xFF00964F) else Color(0xFF00B862))
                            )
                        }
                    }
                    Text(
                        if (hasTomorrow) tomorrowDate.format(dateFormatter) else "Очікується",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isTomorrowSelected) Color.Black.copy(alpha = 0.7f) else Color.Gray
                    )
                }
            }
        }
    }
}

enum class DetailViewMode {
    TABLE, PERIODS
}

data class SchedulePeriod(
    val startSlot: Int,
    val endSlot: Int,
    val status: PowerStatus
) {
    val startHour: Int get() = startSlot / 2
    val startMinute: Int get() = (startSlot % 2) * 30
    val endHour: Int get() = endSlot / 2
    val endMinute: Int get() = (endSlot % 2) * 30

    val timeRange: String get() = "%02d:%02d – %02d:%02d".format(startHour, startMinute, endHour, endMinute)

    val durationText: String get() {
        val totalMinutes = (endSlot - startSlot) * 30
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h год $m хв"
            h > 0 -> "$h год"
            else -> "$m хв"
        }
    }
}

fun calculatePeriods(hours: List<HourData>): List<SchedulePeriod> {
    if (hours.isEmpty()) return emptyList()
    val periods = mutableListOf<SchedulePeriod>()
    var startSlot = 0
    var currentStatus = hours[0].status

    for (i in 1 until hours.size) {
        if (hours[i].status != currentStatus) {
            periods.add(SchedulePeriod(startSlot, i, currentStatus))
            startSlot = i
            currentStatus = hours[i].status
        }
    }
    periods.add(SchedulePeriod(startSlot, hours.size, currentStatus))
    return periods
}

@Composable
fun ScheduleTimeline(
    groupData: GroupSchedule?,
    now: java.time.LocalDateTime,
    lastUpdated: String,
    isDark: Boolean,
    isToday: Boolean = true,
    dayTitle: String = "ГРАФІК НА ДОБУ",
    onRefresh: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var selectedSlotIndex by remember { mutableStateOf<Int?>(null) }
    var viewMode by remember { mutableStateOf(DetailViewMode.TABLE) }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B1E29) else Color.White)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dayTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("ОНОВЛЕНО: $lastUpdated", fontSize = 10.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(20.dp))

            if (groupData == null || groupData.hours.isEmpty()) {
                // Если данных на выбранный день еще нет
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = Color(0xFFFFD54F)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Графік на завтра ще не оприлюднено",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isDark) Color.White else Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Миколаївобленерго зазвичай публікує графік на наступний день після 18:00–20:00.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onRefresh?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD54F),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Перевірити оновлення", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            } else {
                val currentSlotIndex = now.hour * 2 + if (now.minute >= 30) 1 else 0

                val periods = remember(groupData.hours) { calculatePeriods(groupData.hours) }
                val selectedPeriod = selectedSlotIndex?.let { slotIdx ->
                    periods.find { slotIdx >= it.startSlot && slotIdx < it.endSlot }
                }

                // 1. Метки времени СВЕРХУ от шкалы (без полосок)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0:00", "4:00", "8:00", "12:00", "16:00", "20:00", "24:00").forEach { time ->
                        Text(time, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }

                // 2. Полоска со сгруппированными непрерывными сегментами по типу (зоне)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(groupData) {
                            detectTapGestures { offset ->
                                val w = size.width
                                if (w > 0) {
                                    val clickedSlot = ((offset.x / w) * 48).toInt().coerceIn(0, 47)
                                    val curPeriod = periods.find { clickedSlot >= it.startSlot && clickedSlot < it.endSlot }
                                    val targetSlot = curPeriod?.startSlot ?: clickedSlot
                                    selectedSlotIndex = if (selectedSlotIndex == targetSlot) null else targetSlot
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        .pointerInput(groupData) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val w = size.width
                                    if (w > 0) {
                                        val startSlot = ((offset.x / w) * 48).toInt().coerceIn(0, 47)
                                        val curPeriod = periods.find { startSlot >= it.startSlot && startSlot < it.endSlot }
                                        val targetSlot = curPeriod?.startSlot ?: startSlot
                                        if (selectedSlotIndex != targetSlot) {
                                            selectedSlotIndex = targetSlot
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width
                                    if (w > 0) {
                                        val curSlot = ((change.position.x / w) * 48).toInt().coerceIn(0, 47)
                                        val curPeriod = periods.find { curSlot >= it.startSlot && curSlot < it.endSlot }
                                        val targetSlot = curPeriod?.startSlot ?: curSlot
                                        if (selectedSlotIndex != targetSlot) {
                                            selectedSlotIndex = targetSlot
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // 1-й слой: Цветной фон сегментов
                    Row(modifier = Modifier.fillMaxSize()) {
                        periods.forEach { period ->
                            val slotCount = period.endSlot - period.startSlot
                            Box(
                                modifier = Modifier
                                    .weight(slotCount.toFloat())
                                    .fillMaxHeight()
                                    .background(
                                        when (period.status) {
                                            PowerStatus.ON -> SegmentGreen
                                            PowerStatus.OFF -> SegmentRed
                                            PowerStatus.MAYBE -> SegmentYellow
                                        }
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = Color.Black.copy(alpha = 0.18f)
                                    )
                            )
                        }
                    }

                    // 2-й слой: Синий мерцающий указатель текущего слота (под белой рамкой)
                    if (isToday) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            repeat(48) { index ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            if (index == currentSlotIndex)
                                                Color(0xFF2196F3).copy(alpha = animatedAlpha)
                                            else
                                                Color.Transparent
                                        )
                                )
                            }
                        }
                    }

                    // 3-й слой: Белая рамка выделения активного сегмента (со скруглением краев при необходимости)
                    selectedPeriod?.let { period ->
                        val startSlot = period.startSlot
                        val endSlot = period.endSlot

                        val shape = when {
                            startSlot == 0 && endSlot == 48 -> RoundedCornerShape(12.dp)
                            startSlot == 0 -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                            endSlot == 48 -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                            else -> RoundedCornerShape(0.dp)
                        }

                        Row(modifier = Modifier.fillMaxSize()) {
                            if (startSlot > 0) {
                                Spacer(modifier = Modifier.weight(startSlot.toFloat()))
                            }
                            Box(
                                modifier = Modifier
                                    .weight((endSlot - startSlot).toFloat())
                                    .fillMaxHeight()
                                    .border(2.5.dp, Color.White, shape)
                            )
                            if (endSlot < 48) {
                                Spacer(modifier = Modifier.weight((48 - endSlot).toFloat()))
                            }
                        }
                    }
                }

                // 3. Информационная плашка для выбранного сегмента
                AnimatedVisibility(
                    visible = selectedPeriod != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    selectedPeriod?.let { period ->
                        val statusText = when (period.status) {
                            PowerStatus.ON -> "СВІТЛО Є"
                            PowerStatus.OFF -> "ВІДКЛЮЧЕНО"
                            PowerStatus.MAYBE -> "МОЖЛИВО"
                        }
                        val statusColor = when (period.status) {
                            PowerStatus.ON -> SegmentGreen
                            PowerStatus.OFF -> SegmentRed
                            PowerStatus.MAYBE -> SegmentYellow
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF242836) else Color(0xFFEEF0F2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(9.dp).clip(CircleShape).background(statusColor)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        period.timeRange,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color.Black
                                    )
                                    Text(
                                        " • ${period.durationText}",
                                        fontSize = 11.5.sp,
                                        color = Color.Gray
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        statusText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = statusColor
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Закрити",
                                        tint = Color.Gray,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { selectedSlotIndex = null }
                                    )
                                }
                            }
                        }
                    }
                }

                // Легенда
                Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                    LegendItem(SegmentGreen, "Є СВІТЛО")
                    Spacer(Modifier.width(16.dp))
                    LegendItem(SegmentRed, "НЕМАЄ")
                    Spacer(Modifier.width(16.dp))
                    LegendItem(SegmentYellow, "МОЖЛИВО")
                }

                Spacer(Modifier.height(14.dp))

                // Кнопка "Детальніше" / "Згорнути"
                Surface(
                    onClick = { isExpanded = !isExpanded },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isExpanded) "Згорнути деталі" else "Детальніше (по півгодинах)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color.Black
                            )
                        }

                        Surface(
                            color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Згорнути" else "48 слотів",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFF8A6D00)
                            )
                        }
                    }
                }

                // Розгорнута детальна інформація
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        // Перемикач режимів: Таблиця | Періоди
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color.Black.copy(alpha = 0.3f) else Color(0xFFE9ECEF))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                DetailViewMode.TABLE to "Таблиця",
                                DetailViewMode.PERIODS to "Періоди"
                            ).forEach { (mode, title) ->
                                val isSelected = viewMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) (if (isDark) Color(0xFF1B1E29) else Color.White) else Color.Transparent)
                                        .clickable { viewMode = mode },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isSelected) (if (isDark) Color.White else Color.Black) else Color.Gray
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        when (viewMode) {
                            DetailViewMode.TABLE -> {
                                DetailedTableView(
                                    groupData = groupData,
                                    isToday = isToday,
                                    currentSlotIndex = currentSlotIndex,
                                    isDark = isDark
                                )
                            }
                            DetailViewMode.PERIODS -> {
                                DetailedPeriodsView(
                                    hours = groupData.hours,
                                    isToday = isToday,
                                    currentSlotIndex = currentSlotIndex,
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedTableView(
    groupData: GroupSchedule,
    isToday: Boolean,
    currentSlotIndex: Int,
    isDark: Boolean
) {
    val quarters = listOf("Всі 24г", "00–06", "06–12", "12–18", "18–24")
    var selectedQuarter by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Фільтр по чвертях доби для максимальної компактності
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quarters.forEachIndexed { idx, label ->
                val isSel = selectedQuarter == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSel) Color(0xFFFFD54F)
                            else if (isDark) Color.White.copy(alpha = 0.06f)
                            else Color.Black.copy(alpha = 0.04f)
                        )
                        .clickable { selectedQuarter = idx },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) Color.Black else Color.Gray
                    )
                }
            }
        }

        // Відображення в 2 колонки (як на сайті, але повністю без внутрішнього скролу)
        val slotsToShow: List<HourData> = when (selectedQuarter) {
            1 -> groupData.hours.subList(0, 12)
            2 -> groupData.hours.subList(12, 24)
            3 -> groupData.hours.subList(24, 36)
            4 -> groupData.hours.subList(36, 48)
            else -> groupData.hours
        }

        val half = (slotsToShow.size + 1) / 2
        val leftSlots = slotsToShow.take(half)
        val rightSlots = slotsToShow.drop(half)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ліва колонка
            Column(modifier = Modifier.weight(1f)) {
                ScheduleTableHeader(groupData.id, isDark)
                leftSlots.forEach { slot ->
                    ScheduleTableCell(
                        slot = slot,
                        queueId = groupData.id,
                        isCurrentSlot = isToday && slot.hour == currentSlotIndex,
                        isDark = isDark
                    )
                }
            }

            // Права колонка
            Column(modifier = Modifier.weight(1f)) {
                ScheduleTableHeader(groupData.id, isDark)
                rightSlots.forEach { slot ->
                    ScheduleTableCell(
                        slot = slot,
                        queueId = groupData.id,
                        isCurrentSlot = isToday && slot.hour == currentSlotIndex,
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleTableHeader(queueId: String, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
            .border(0.5.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Час",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.3f),
            textAlign = TextAlign.Center,
            color = if (isDark) Color.White else Color.Black
        )
        Text(
            text = queueId,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = if (isDark) Color.White else Color.Black
        )
    }
}

@Composable
fun ScheduleTableCell(
    slot: HourData,
    queueId: String,
    isCurrentSlot: Boolean,
    isDark: Boolean
) {
    val startH = slot.hour / 2
    val startM = (slot.hour % 2) * 30
    val endH = (slot.hour + 1) / 2
    val endM = ((slot.hour + 1) % 2) * 30

    val cellBg = when (slot.status) {
        PowerStatus.ON -> SegmentGreen.copy(alpha = if (isDark) 0.45f else 0.4f)
        PowerStatus.OFF -> SegmentRed.copy(alpha = if (isDark) 0.45f else 0.4f)
        PowerStatus.MAYBE -> SegmentYellow.copy(alpha = if (isDark) 0.5f else 0.45f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .border(0.5.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            .then(if (isCurrentSlot) Modifier.border(1.5.dp, Color(0xFF2196F3)) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%02d:%02d-%02d:%02d".format(startH, startM, endH, endM),
            fontSize = 10.5.sp,
            fontWeight = if (isCurrentSlot) FontWeight.Bold else FontWeight.Normal,
            color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
            modifier = Modifier
                .weight(1.3f)
                .padding(horizontal = 2.dp),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(cellBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = queueId,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun DetailedPeriodsView(
    hours: List<HourData>,
    isToday: Boolean,
    currentSlotIndex: Int,
    isDark: Boolean
) {
    val periods = remember(hours) { calculatePeriods(hours) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        periods.forEach { period ->
            val isCurrentPeriod = isToday && (currentSlotIndex >= period.startSlot && currentSlotIndex < period.endSlot)
            val periodColor = when (period.status) {
                PowerStatus.ON -> SegmentGreen
                PowerStatus.OFF -> SegmentRed
                PowerStatus.MAYBE -> SegmentYellow
            }
            val statusTitle = when (period.status) {
                PowerStatus.ON -> "Світло є"
                PowerStatus.OFF -> "Відключено"
                PowerStatus.MAYBE -> "Можливо"
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) Color(0xFF242836) else Color(0xFFF3F4F6),
                border = if (isCurrentPeriod) BorderStroke(1.5.dp, Color(0xFF2196F3)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(periodColor)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = period.timeRange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDark) Color.White else Color.Black
                            )
                            Text(
                                text = statusTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrentPeriod) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color(0xFF2196F3).copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    "Зараз",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2196F3)
                                )
                            }
                        }
                        Text(
                            text = period.durationText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(lastUpdated: String, isDark: Boolean) {
    val cardBg = if (isDark) Color(0xFF1B1E29) else Color.White
    // Цвет линии: очень тонкий белый или черный с низкой прозрачностью
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val innerNoteBg = if (isDark) Color.Black.copy(alpha = 0.2f) else Color(0xFFF1F3F5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "ІНФОРМАЦІЯ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(12.dp))

            // Первая строка + подчеркивание
            InfoRow("ДЖЕРЕЛО", "off.energy.mk.ua", isDark)
            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            // Вторая строка + подчеркивание
            InfoRow("ДАНІ ОНОВЛЕНО", lastUpdated, isDark)
            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            Spacer(Modifier.height(20.dp))

            // Внутреннее поле с текстом
            Surface(
                color = innerNoteBg,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Система автоматично синхронізується з даними Миколаївобленерго. Якщо ви помітили невідповідність або хочете отримати офіційне підтвердження, будь ласка, перейдіть на сайт джерела.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isDark: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        // Текст значения должен быть белым в темной теме
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (isDark) Color.White else Color.Black)
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: MainViewModel, isDark: Boolean, onDismiss: () -> Unit) {
    // Подписываемся на тему из ViewModel
    val currentTheme by viewModel.currentTheme.collectAsState()

    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val minsOff by viewModel.minutesBeforeOff.collectAsState()
    val minsOn by viewModel.minutesBeforeOn.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1B1E29) else Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.LightGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            // Заголовок
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Surface(
                    color = Color(0xFFFFD54F),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ElectricBolt, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Налаштування",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDark) Color.White else Color.Black
                )
            }

            // Блок ТЕМА
            Text("ТЕМА", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(Modifier.height(12.dp))

            // Переключатель тем
            Surface(
                color = if (isDark) Color.White.copy(0.1f) else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    AppTheme.entries.forEach { themeOption: AppTheme ->
                        val isSelected = currentTheme == themeOption
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { viewModel.setTheme(themeOption) },
                            color = if (isSelected) Color(0xFFFFD54F) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when(themeOption) {
                                        AppTheme.LIGHT -> "СВІТЛА"
                                        AppTheme.DARK -> "ТЕМНА"
                                        AppTheme.AUTO -> "АВТО"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Блок СПОВІЩЕННЯ
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("СПОВІЩЕННЯ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Switch(
                    checked = notificationsEnabled, // Берем значение из ViewModel
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }, // Отправляем во ViewModel
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFFFFD54F)
                    )
                )
            }

            // Слайдеры уведомлений
            NotificationSlider(
                label = "ЗА СКІЛЬКИ ДО ВІДКЛЮЧЕННЯ?",
                value = "$minsOff ХВ",
                currentValue = minsOff.toFloat(),
                onValueChange = { viewModel.setMinutesBeforeOff(it.toInt()) },
                enabled = notificationsEnabled,
                isDark = isDark
            )

            NotificationSlider(
                label = "ЗА СКІЛЬКИ ДО ВВІМКНЕННЯ?",
                value = "$minsOn ХВ",
                currentValue = minsOn.toFloat(),
                onValueChange = { viewModel.setMinutesBeforeOn(it.toInt()) },
                enabled = notificationsEnabled,
                isDark = isDark
            )

            // Настройка: Считать до зеленой или до желтой
            val notifyOnYellow by viewModel.notifyOnYellow.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .alpha(if (notificationsEnabled) 1f else 0.3f) // Визуальное отключение
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = notificationsEnabled) { viewModel.setNotifyOnYellow(!notifyOnYellow) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = notifyOnYellow,
                    onCheckedChange = { viewModel.setNotifyOnYellow(it) },
                    enabled = notificationsEnabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFFFD54F),
                        checkmarkColor = Color.Black,
                        uncheckedColor = Color.Gray
                    )
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        "Сповіщати також про 'Можливо'",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isDark) Color.White else Color.Black
                    )
                    Text(
                        "Якщо увімкнено, таймер буде рахувати і до жовтої зони",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            // Настройка: Сповіщення про графік на завтра
            val notifyTomorrowSchedule by viewModel.notifyTomorrowSchedule.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .alpha(if (notificationsEnabled) 1f else 0.3f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = notificationsEnabled) { viewModel.setNotifyTomorrowSchedule(!notifyTomorrowSchedule) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = notifyTomorrowSchedule,
                    onCheckedChange = { viewModel.setNotifyTomorrowSchedule(it) },
                    enabled = notificationsEnabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFFFD54F),
                        checkmarkColor = Color.Black,
                        uncheckedColor = Color.Gray
                    )
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        "Графік на завтра",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isDark) Color.White else Color.Black
                    )
                    Text(
                        "Надіслати сповіщення, коли з'явиться графік на наступний день",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Блок ОНОВЛЕННЯ ТА ВЕРСІЯ
            Text("ОНОВЛЕННЯ ТА ВЕРСІЯ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(Modifier.height(10.dp))

            val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
            val updateCheckResult by viewModel.updateCheckResult.collectAsState()
            val settingsUpdateInfo by viewModel.updateInfo.collectAsState()

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Версія додатку",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color.Black
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Button(
                            onClick = { viewModel.checkForUpdates(isManual = true) },
                            enabled = !isCheckingUpdate,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD54F),
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Перевірити", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (updateCheckResult != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = updateCheckResult!!,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color.White.copy(alpha = 0.85f) else Color.DarkGray
                        )
                    }

                    if (settingsUpdateInfo != null) {
                        Spacer(Modifier.height(12.dp))
                        val uriHandler = LocalUriHandler.current
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF00B862).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Доступна версія ${settingsUpdateInfo!!.latestVersion}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00B862)
                                )
                                TextButton(
                                    onClick = { uriHandler.openUri(settingsUpdateInfo!!.downloadUrl) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Оновити", color = Color(0xFF00B862), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Кнопка ЗБЕРЕГТИ
            Button(
                onClick = onDismiss, // Просто закрывает шторку
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("ГОТОВО", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun NotificationSlider(
    label: String,
    value: String,
    currentValue: Float, // Текущее значение из ViewModel
    onValueChange: (Float) -> Unit, // Коллбек для изменения
    enabled: Boolean,
    isDark: Boolean
) {
    val alpha = if (enabled) 1f else 0.3f

    Column(modifier = Modifier.padding(vertical = 8.dp).alpha(alpha)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
        }
        Slider(
            value = currentValue, // ВАЖНО: это значение должно приходить извне
            onValueChange = onValueChange,
            valueRange = 5f..60f, // Диапазон в минутах
            steps = 10, // Дискретные шаги (необязательно)
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (isDark) Color.White else Color(0xFF424242),
                activeTrackColor = Color(0xFFFFD54F)
            )
        )
    }
}

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1B1E29) else Color.White,
        icon = {
            Surface(
                color = Color(0xFFFFD54F),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color.Black)
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Доступне оновлення!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isDark) Color.White else Color.Black
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF00B862).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        updateInfo.latestVersion,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B862)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        "Що нового:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        updateInfo.releaseNotes,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "Бажаєте завантажити та встановити нову версію?",
                    fontSize = 12.5.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    uriHandler.openUri(updateInfo.downloadUrl)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Завантажити", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Пізніше", color = Color.Gray)
            }
        }
    )
}


