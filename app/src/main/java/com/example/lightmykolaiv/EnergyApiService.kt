package com.example.lightmykolaiv

import retrofit2.http.GET

interface EnergyApiService {
    @GET("active")
    suspend fun getActiveSchedule(): List<ScheduleResponse>
}

// Модели данных
data class ScheduleResponse(
    val id: Long? = null,
    val from: String? = null,
    val to: String? = null,
    val series: List<ScheduleSeries> = emptyList()
)

data class ScheduleSeries(
    val outage_queue_id: Int,
    val time_series_id: Int, // 1-48 (каждые 30 мин)
    val type: String? = null // "OFF", "SURE_OFF", "PROBABLY_OFF", etc.
)