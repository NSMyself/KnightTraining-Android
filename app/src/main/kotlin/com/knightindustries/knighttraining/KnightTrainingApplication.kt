package com.knightindustries.knighttraining

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.knightindustries.knighttraining.core.persistence.AppDatabase
import com.knightindustries.knighttraining.core.persistence.AppDatabase.Companion.configure
import com.knightindustries.knighttraining.core.store.TrainingStore
import com.knightindustries.knighttraining.core.store.weeklySurface
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

    val store: TrainingStore by lazy { TrainingStore(database) }

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            store.seedPresetsIfNeeded()
            // Until Home exists (Phase 2): proves the store's queries run on the device, not just the JVM.
            if (BuildConfig.DEBUG) Log.d(TAG, "Weekly surface at launch: ${store.weeklySurface()}")
        }
    }

    private companion object {
        const val TAG = "KnightTraining"
    }
}
