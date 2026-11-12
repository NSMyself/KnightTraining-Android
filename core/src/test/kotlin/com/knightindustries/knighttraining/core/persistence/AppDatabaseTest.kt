package com.knightindustries.knighttraining.core.persistence

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.knightindustries.knighttraining.core.persistence.AppDatabase.Companion.configure
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().configure().build()
        db.lookupDao().seedPresetsIfNeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedingIsIdempotentAndMatchesIos() = runTest {
        db.lookupDao().seedPresetsIfNeeded()

        assertEquals(
            setOf("puzzles", "playing", "analyzing", "openings", "endgames", "coaching", "general"),
            db.lookupDao().allActivityTypes().map { it.code }.toSet(),
        )
        assertEquals(7, db.lookupDao().allActivityTypes().size)
        assertEquals(
            setOf("book", "course", "video", "article", "lichess_study"),
            db.lookupDao().allResourceKinds().map { it.code }.toSet(),
        )
    }

    @Test
    fun deletingAResourceCascadesItsCompletionsAndDetachesItsActivities() = runTest {
        db.useWriterConnection { connection ->
            connection.execSQL(
                """
                INSERT INTO resource (id, kindId, title, isOwned, progressPercentage, completionCount,
                    createdAt, updatedAt, isAutoImported)
                VALUES ('r1', '00000000-0000-0000-0001-000000000001', 'My System', 0, 0, 0, 0, 0, 0)
                """,
            )
            connection.execSQL(
                "INSERT INTO resource_completion (id, resourceId, completedAt) VALUES ('c1', 'r1', 0)",
            )
            connection.execSQL(
                """
                INSERT INTO activity (id, activityTypeId, resourceId, startedAt, duration, source, createdAt)
                VALUES ('a1', '00000000-0000-0000-0000-000000000001', 'r1', 0, 60, 'manual', 0)
                """,
            )
            connection.execSQL("DELETE FROM resource WHERE id = 'r1'")
        }

        db.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM resource_completion") { stmt ->
                stmt.step()
                assertEquals(0L, stmt.getLong(0))
            }
            connection.usePrepared("SELECT resourceId FROM activity WHERE id = 'a1'") { stmt ->
                stmt.step()
                assertTrue(stmt.isNull(0))
            }
        }
    }
}
