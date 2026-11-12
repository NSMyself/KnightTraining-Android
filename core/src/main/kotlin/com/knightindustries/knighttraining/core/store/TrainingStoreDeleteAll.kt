package com.knightindustries.knighttraining.core.store

import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Tables holding nothing the user made: the seeded lookups every row points at, and the course
 * catalog (a cache of a public listing). Everything else is the user's.
 */
internal val tablesKeptByDeleteAllUserData = setOf("activity_type", "resource_kind", "catalog_course")

/**
 * Settings → "Delete all data", in one transaction. Reads the table list from `sqlite_master`, so
 * a table a future migration adds is wiped by default rather than silently surviving.
 */
suspend fun TrainingStore.deleteAllUserData() {
    db.useWriterConnection { connection ->
        connection.immediateTransaction {
            val tables = usePrepared(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'",
            ) { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
            // Checked at commit, so the order tables are emptied in doesn't matter.
            execSQL("PRAGMA defer_foreign_keys = ON")
            for (table in tables - tablesKeptByDeleteAllUserData) {
                execSQL("DELETE FROM `$table`")
            }
        }
    }
}
