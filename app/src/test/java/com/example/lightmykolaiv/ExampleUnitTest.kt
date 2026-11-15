package com.example.lightmykolaiv

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalDateTime

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDateParsingFromApi() {
        val date = ScraperRepository.parseDateFromApi("2026-11-04T22:00:00.000000Z")
        assertEquals(LocalDate.of(2026, 11, 5), date)
    }

    @Test
    fun testTimerLooksAheadIntoTomorrow() {
        val todayDate = LocalDate.of(2026, 11, 5)
        val tomorrowDate = LocalDate.of(2026, 11, 6)

        // Today: slots 40..47 are ON (20:00 to 24:00, 4 hours)
        val todayHours = (0..47).map { i ->
            HourData(i, if (i >= 40) PowerStatus.ON else PowerStatus.OFF)
        }
        val todayGroup = GroupSchedule("1.1", todayHours)

        // Tomorrow: slots 0..5 are ON (00:00 to 03:00, 3 hours), slot 6 is MAYBE (03:00)
        val tomorrowHours = (0..47).map { i ->
            val status = when {
                i < 6 -> PowerStatus.ON
                i == 6 -> PowerStatus.MAYBE
                else -> PowerStatus.OFF
            }
            HourData(i, status)
        }
        val tomorrowGroup = GroupSchedule("1.1", tomorrowHours)

        val schedule = FullSchedule(
            today = DaySchedule(todayDate, mapOf("1.1" to todayGroup)),
            tomorrow = DaySchedule(tomorrowDate, mapOf("1.1" to tomorrowGroup)),
            days = listOf(
                DaySchedule(todayDate, mapOf("1.1" to todayGroup)),
                DaySchedule(tomorrowDate, mapOf("1.1" to tomorrowGroup))
            ),
            lastUpdated = LocalDateTime.now()
        )

        // Now is 20:00:00
        val now = todayDate.atTime(20, 0, 0)
        val timeUntilChange = MainViewModel.calculateTimeUntilChange(schedule, "1.1", now)

        assertNotNull(timeUntilChange)
        assertEquals(7L, timeUntilChange!!.hours)
        assertEquals(0L, timeUntilChange.minutes)
        assertEquals(0L, timeUntilChange.seconds)
        assertEquals(PowerStatus.MAYBE, timeUntilChange.nextStatus)
    }

    @Test
    fun testTimerWithoutTomorrowStopsAtMidnight() {
        val todayDate = LocalDate.of(2026, 11, 5)
        val todayHours = (0..47).map { i ->
            HourData(i, if (i >= 40) PowerStatus.ON else PowerStatus.OFF)
        }
        val todayGroup = GroupSchedule("1.1", todayHours)

        val schedule = FullSchedule(
            today = DaySchedule(todayDate, mapOf("1.1" to todayGroup)),
            tomorrow = null,
            days = listOf(DaySchedule(todayDate, mapOf("1.1" to todayGroup))),
            lastUpdated = LocalDateTime.now()
        )

        val now = todayDate.atTime(20, 0, 0)
        val timeUntilChange = MainViewModel.calculateTimeUntilChange(schedule, "1.1", now)

        assertNotNull(timeUntilChange)
        assertEquals(4L, timeUntilChange!!.hours)
        assertEquals(0L, timeUntilChange.minutes)
        assertEquals(0L, timeUntilChange.seconds)
    }

    @Test
    fun testTomorrowScheduleDetection() {
        val today = LocalDate.of(2026, 11, 5)
        val tomorrow = LocalDate.of(2026, 11, 6)

        val scheduleWithoutTomorrow = FullSchedule(
            today = DaySchedule(today, emptyMap()),
            tomorrow = null
        )
        assertFalse(scheduleWithoutTomorrow.hasTomorrow)

        val scheduleWithTomorrow = FullSchedule(
            today = DaySchedule(today, emptyMap()),
            tomorrow = DaySchedule(tomorrow, mapOf("3.1" to GroupSchedule("3.1", listOf(HourData(0, PowerStatus.ON)))))
        )
        assertTrue(scheduleWithTomorrow.hasTomorrow)
        assertEquals(tomorrow, scheduleWithTomorrow.tomorrow?.date)
    }
}