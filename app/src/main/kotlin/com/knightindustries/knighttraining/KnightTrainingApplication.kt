package com.knightindustries.knighttraining

import android.app.Application
import androidx.room.Room
import com.knightindustries.knighttraining.core.persistence.AppDatabase
import com.knightindustries.knighttraining.core.persistence.AppDatabase.Companion.configure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KnightTrainingApplication : Application() {
    /** The file-backed database, like iOS's `AppDatabase.shared`. */
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.FILE_NAME)
            .configure()
            .build()
    }

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        appScope.launch { database.lookupDao().seedPresetsIfNeeded() }
    }
}
