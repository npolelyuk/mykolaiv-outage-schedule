package com.example.lightmykolaiv

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetId = intent.getIntExtra("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultQueue = "${prefs.getInt("selected_group", 1)}.${prefs.getInt("selected_subgroup", 1)}"
        val currentQueue = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            prefs.getString("widget_queue_$appWidgetId", defaultQueue) ?: defaultQueue
        } else {
            defaultQueue
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            val bg = if (isDark) Color(0xFF13151B) else Color(0xFFF6F7F9)
            val cardBg = if (isDark) Color(0xFF1E212B) else Color.White
            val textColor = if (isDark) Color.White else Color(0xFF1F2937)
            val subtextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

            var selectedQueue by remember { mutableStateOf(currentQueue) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Черга для віджета",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрити",
                                    tint = subtextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Оберіть чергу відключень, яка відображатиметься в цьому віджеті:",
                            fontSize = 13.5.sp,
                            color = subtextColor,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Сетка 6 очередей (по 2 подгруппы)
                        val groups = listOf("1", "2", "3", "4", "5", "6")
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            groups.chunked(3).forEach { rowGroups ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowGroups.forEach { grp ->
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf("$grp.1", "$grp.2").forEach { qKey ->
                                                val isSelected = selectedQueue == qKey
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary
                                                            else if (isDark) Color(0xFF282C38)
                                                            else Color(0xFFEEF1F6)
                                                        )
                                                        .clickable {
                                                            selectedQueue = qKey
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = qKey,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Color.White else textColor,
                                                        fontSize = 14.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                saveAndFinish(selectedQueue)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "Зберегти (Черга $selectedQueue)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveAndFinish(queueKey: String) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            prefs.edit().putString("widget_queue_$appWidgetId", queueKey).commit()
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                    PowerWidget().update(this@WidgetConfigActivity, glanceId)
                }
            } catch (e: Exception) {
                // ignore
            }
            try {
                PowerWidget().updateAll(this@WidgetConfigActivity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}
