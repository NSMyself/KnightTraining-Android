package com.knightindustries.knighttraining.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Shared fixtures for the pure-model tests. */
object Fixtures {
    /** UTC, Monday-first: the fixed calendar the iOS policy tests use. */
    val utcMonday = WeekCalendar(ZoneOffset.UTC, DayOfWeek.MONDAY)

    /** A real zone with DST and a Sunday-first week, so week math isn't only checked in UTC. */
    val newYorkSunday = WeekCalendar(ZoneId.of("America/New_York"), DayOfWeek.SUNDAY)

    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** `"2026-08-12 09:00"` read in [calendar]'s zone. */
    fun at(text: String, calendar: WeekCalendar = utcMonday): Instant =
        LocalDateTime.parse(text, format).atZone(calendar.zone).toInstant()

    fun type(code: String) = ActivityType(UUID.randomUUID(), code)

    val puzzles = type(ActivityType.PUZZLES)
    val playing = type(ActivityType.PLAYING)
    val openings = type(ActivityType.OPENINGS)
    val endgames = type(ActivityType.ENDGAMES)
    val coaching = type(ActivityType.COACHING)
    val general = type(ActivityType.GENERAL)

    val book = ResourceKind(UUID.randomUUID(), ResourceKind.BOOK)
}
