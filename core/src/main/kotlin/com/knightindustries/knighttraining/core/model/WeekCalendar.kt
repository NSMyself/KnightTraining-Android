package com.knightindustries.knighttraining.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The calendar every week boundary is computed against: the counterpart of iOS's
 * `Calendar.current`, passed in explicitly so tests can pin it.
 *
 * Weeks are stored as the `LocalDate` they start on and turned into instants against this zone.
 * iOS freezes `weekStart` as an instant instead, so a time-zone change mid-week moves the
 * boundaries here by the zone difference while iOS keeps them. Local wall-clock weeks are what
 * the user sees on their calendar, so that's the accepted difference.
 */
data class WeekCalendar(
    val zone: ZoneId,
    val firstDayOfWeek: DayOfWeek,
) {
    /** The first day of the week containing [instant], in this zone. */
    fun weekStart(instant: Instant): LocalDate = weekStart(localDate(instant))

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** Midnight at the start of [date], in this zone. */
    fun startOf(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    companion object {
        /** The device's zone and the locale's first day of the week. */
        fun system(): WeekCalendar =
            WeekCalendar(ZoneId.systemDefault(), WeekFields.of(Locale.getDefault()).firstDayOfWeek)
    }
}
