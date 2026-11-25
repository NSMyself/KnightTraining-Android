package com.knightindustries.knighttraining.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.UUID

/** Every migration runs against the exported schema it starts from, and keeps the rows it finds. */
class MigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val helper by lazy {
        MigrationTestHelper(
            schemaDirectoryPath = Path.of("schemas"),
            databasePath = folder.root.toPath().resolve("migration.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
    }

    @Test
    fun version2AddsTheCoachTipAndKeepsEveryReview() {
        val planId = UUID.randomUUID().toString()
        val templateId = UUID.randomUUID().toString()
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO plan_template (id, name, isActive, isArchived, createdAt, updatedAt) VALUES ('$templateId', 'Plan', 1, 0, 0, 0)")
            execSQL("INSERT INTO weekly_plan (id, planTemplateId, weekStart, createdAt) VALUES ('$planId', '$templateId', '2026-11-08', 0)")
            execSQL(
                "INSERT INTO weekly_review (id, weeklyPlanId, wentWell, createdAt, updatedAt) " +
                    "VALUES ('${UUID.randomUUID()}', '$planId', 'Tactics', 0, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(2, listOf(Migrations.V1_TO_V2))

        val statement = migrated.prepare("SELECT wentWell, coachTipStatus, coachTipSuggestsCoach FROM weekly_review")
        statement.step()
        assertEquals("Tactics", statement.getText(0))
        assertEquals(true, statement.isNull(1))
        assertEquals(0L, statement.getLong(2))
        statement.close()
        migrated.close()
    }
}
