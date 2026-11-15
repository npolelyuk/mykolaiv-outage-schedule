package com.example.lightmykolaiv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class ScraperRepository(context: Context) {
    // Контекст оставляем для совместимости
    companion object {
        // Хранилище последнего загруженного графика
        var lastLoadedSchedule: FullSchedule? = null
        val KYIV_ZONE: ZoneId = ZoneId.of("Europe/Kyiv")

        val queueMap = mapOf(
            "1.1" to 14, "1.2" to 15, "2.1" to 16, "2.2" to 17,
            "3.1" to 19, "3.2" to 20, "4.1" to 21, "4.2" to 22,
            "5.1" to 24, "5.2" to 25, "6.1" to 26, "6.2" to 27
        )

        fun parseDateFromApi(fromStr: String?): LocalDate? {
            if (fromStr.isNullOrBlank()) return null
            return try {
                Instant.parse(fromStr).atZone(KYIV_ZONE).toLocalDate()
            } catch (e: Exception) {
                try {
                    OffsetDateTime.parse(fromStr).atZoneSameInstant(KYIV_ZONE).toLocalDate()
                } catch (e2: Exception) {
                    try {
                        LocalDate.parse(fromStr.substring(0, 10))
                    } catch (e3: Exception) {
                        null
                    }
                }
            }
        }

        fun buildGroupsFromSeries(seriesList: List<ScheduleSeries>): Map<String, GroupSchedule> {
            val groupsMap = mutableMapOf<String, GroupSchedule>()
            queueMap.forEach { (queueName, targetId) ->
                val mySeries = seriesList.filter { it.outage_queue_id == targetId }
                val hours = (0..47).map { i ->
                    val seriesItem = mySeries.findLast { it.time_series_id == i + 1 }
                    val status = when (seriesItem?.type?.uppercase()?.trim()) {
                        "OFF", "SURE_OFF", "FORCE_OFF" -> PowerStatus.OFF
                        "PROBABLY_OFF", "POSSIBLE_OFF" -> PowerStatus.MAYBE
                        "ON", "ENABLE", "NOT_PLAN", "OK" -> PowerStatus.ON
                        else -> PowerStatus.ON
                    }
                    HourData(i, status)
                }
                groupsMap[queueName] = GroupSchedule(queueName, hours)
            }
            return groupsMap
        }
    }

    private val api = Retrofit.Builder()
        .baseUrl("https://off.energy.mk.ua/api/v2/schedule/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(EnergyApiService::class.java)

    suspend fun fetchSchedule(): FullSchedule? = withContext(Dispatchers.IO) {
        try {
            val response = api.getActiveSchedule()
            if (response.isEmpty()) return@withContext null

            val todayDate = LocalDate.now(KYIV_ZONE)
            val tomorrowDate = todayDate.plusDays(1)

            // Группируем серии по дате
            val seriesByDate = mutableMapOf<LocalDate, MutableList<ScheduleSeries>>()

            response.forEachIndexed { index, item ->
                val date = parseDateFromApi(item.from) ?: todayDate.plusDays(index.toLong())
                val list = seriesByDate.getOrPut(date) { mutableListOf() }
                list.addAll(item.series)
            }

            // Создаем DaySchedule для каждой даты
            val daySchedules = seriesByDate.map { (date, series) ->
                DaySchedule(
                    date = date,
                    groups = buildGroupsFromSeries(series)
                )
            }.sortedBy { it.date }

            val todaySchedule = daySchedules.find { it.date == todayDate }
                ?: daySchedules.firstOrNull()

            val tomorrowSchedule = daySchedules.find { it.date == tomorrowDate }
                ?: if (todaySchedule != null) daySchedules.find { it.date > todaySchedule.date } else null

            val finalSchedule = FullSchedule(
                today = todaySchedule,
                tomorrow = tomorrowSchedule,
                days = daySchedules,
                lastUpdated = LocalDateTime.now()
            )

            lastLoadedSchedule = finalSchedule
            finalSchedule
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}