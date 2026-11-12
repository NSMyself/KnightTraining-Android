package com.knightindustries.knighttraining.core.persistence

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Column encodings. Unlike iOS (where GRDB stores UUIDs as 16-byte BLOBs, see AGENTS.md §5),
 * UUIDs here are plain TEXT, so a hand-written `sqlite3` query matches them. Data never moves
 * between the two platforms, so the encodings don't have to agree.
 *
 * - `UUID` → lowercase canonical text (`"00000000-0000-0000-0000-000000000001"`).
 * - `Instant` → epoch milliseconds.
 * - `LocalDate` → ISO text (`"2026-11-09"`), for day-precision fields like `weekStart`.
 */
class Converters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
