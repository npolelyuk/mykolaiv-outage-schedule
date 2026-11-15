package com.example.lightmykolaiv

import java.time.LocalDate
import java.time.LocalDateTime

enum class PowerStatus {
    ON, OFF, MAYBE
}

enum class SelectedDay {
    TODAY, TOMORROW
}

data class HourData(
    val hour: Int,
    val status: PowerStatus
)

data class GroupSchedule(
    val id: String,
    val hours: List<HourData>
)

data class DaySchedule(
    val date: LocalDate,
    val groups: Map<String, GroupSchedule> = emptyMap()
)

data class FullSchedule(
    val today: DaySchedule? = null,
    val tomorrow: DaySchedule? = null,
    val days: List<DaySchedule> = emptyList(),
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    private val _fallbackGroups: Map<String, GroupSchedule> = emptyMap()
) {
    constructor(groups: Map<String, GroupSchedule>, lastUpdated: LocalDateTime = LocalDateTime.now()) : this(
        today = DaySchedule(LocalDate.now(), groups),
        tomorrow = null,
        days = listOf(DaySchedule(LocalDate.now(), groups)),
        lastUpdated = lastUpdated,
        _fallbackGroups = groups
    )

    val groups: Map<String, GroupSchedule>
        get() = today?.groups ?: days.firstOrNull()?.groups ?: _fallbackGroups

    val hasTomorrow: Boolean
        get() = tomorrow != null && tomorrow.groups.isNotEmpty()

    fun getScheduleForDate(date: LocalDate): DaySchedule? {
        if (today?.date == date) return today
        if (tomorrow?.date == date) return tomorrow
        return days.find { it.date == date }
    }
}

data class NotificationSettings(
    val enabled: Boolean = false,
    val outageLeadTime: Int = 15,
    val restoreLeadTime: Int = 5,
    val notifyTomorrow: Boolean = true
)

data class TimeUntilChange(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val nextStatus: PowerStatus
)

