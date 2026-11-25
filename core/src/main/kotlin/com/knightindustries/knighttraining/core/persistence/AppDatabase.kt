package com.knightindustries.knighttraining.core.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.knightindustries.knighttraining.core.persistence.entities.AccountEntity
import com.knightindustries.knighttraining.core.persistence.entities.ActivityEntity
import com.knightindustries.knighttraining.core.persistence.entities.ActivityReminderEntity
import com.knightindustries.knighttraining.core.persistence.entities.ActivityTypeEntity
import com.knightindustries.knighttraining.core.persistence.entities.CatalogCourseEntity
import com.knightindustries.knighttraining.core.persistence.entities.EloSnapshotEntity
import com.knightindustries.knighttraining.core.persistence.entities.GoalTemplateEntity
import com.knightindustries.knighttraining.core.persistence.entities.LichessActivityBatchEntity
import com.knightindustries.knighttraining.core.persistence.entities.MilestoneEntity
import com.knightindustries.knighttraining.core.persistence.entities.PlanTemplateEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceCompletionEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceKindEntity
import com.knightindustries.knighttraining.core.persistence.entities.SharedCourseEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyGoalEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyPlanEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyReviewEntity
import kotlinx.coroutines.Dispatchers

/**
 * Version 1 is the iOS schema's end state after its migration `v24`; version 2 is iOS's `v25`. No migrations are replayed:
 * data never moves between platforms. From here on, every schema change is a new version with a
 * real Room migration, since release data must survive.
 */
@Database(
    version = 2,
    exportSchema = true,
    entities = [
        ActivityTypeEntity::class,
        ResourceKindEntity::class,
        CatalogCourseEntity::class,
        ResourceEntity::class,
        ResourceCompletionEntity::class,
        SharedCourseEntity::class,
        ActivityEntity::class,
        AccountEntity::class,
        EloSnapshotEntity::class,
        MilestoneEntity::class,
        LichessActivityBatchEntity::class,
        PlanTemplateEntity::class,
        GoalTemplateEntity::class,
        WeeklyPlanEntity::class,
        WeeklyGoalEntity::class,
        WeeklyReviewEntity::class,
        ActivityReminderEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lookupDao(): LookupDao

    abstract fun activityDao(): ActivityDao

    abstract fun planDao(): PlanDao

    abstract fun weekDao(): WeekDao

    abstract fun resourceDao(): ResourceDao

    abstract fun accountDao(): AccountDao

    abstract fun reviewDao(): ReviewDao

    abstract fun libraryDao(): LibraryDao

    abstract fun lichessDao(): LichessDao

    companion object {
        const val FILE_NAME = "knighttraining.db"

        /**
         * The one configuration every builder (the app's file-backed database, tests' in-memory
         * ones) goes through. The bundled SQLite is the same version on every device; Android 9's
         * system SQLite (3.22) predates upserts and window functions.
         */
        fun RoomDatabase.Builder<AppDatabase>.configure(): RoomDatabase.Builder<AppDatabase> =
            setDriver(BundledSQLiteDriver())
                .addMigrations(*Migrations.all)
                .setQueryCoroutineContext(Dispatchers.IO)
    }
}
