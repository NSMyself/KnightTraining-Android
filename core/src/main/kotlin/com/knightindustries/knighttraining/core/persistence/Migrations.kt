package com.knightindustries.knighttraining.core.persistence

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Every schema change after version 1, in order. Each is pinned by `MigrationTest`. */
object Migrations {
    /**
     * iOS `v25`: the weekly coach's tip, stored on the reviewed week's review and shown while the
     * following week is planned (`WeeklyReview.CoachTip`). A `NULL` status means the coach hasn't
     * been asked yet.
     */
    val V1_TO_V2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE weekly_review ADD COLUMN coachTipStatus TEXT")
            connection.execSQL("ALTER TABLE weekly_review ADD COLUMN coachTipText TEXT")
            connection.execSQL("ALTER TABLE weekly_review ADD COLUMN coachTipFocus TEXT")
            connection.execSQL("ALTER TABLE weekly_review ADD COLUMN coachTipTargetCode TEXT")
            connection.execSQL("ALTER TABLE weekly_review ADD COLUMN coachTipTargetValue INTEGER")
            connection.execSQL("ALTER TABLE weekly_review ADD COLUMN coachTipSuggestsCoach INTEGER NOT NULL DEFAULT 0")
        }
    }

    val all = arrayOf(V1_TO_V2)
}
