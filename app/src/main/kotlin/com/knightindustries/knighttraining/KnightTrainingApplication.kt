package com.knightindustries.knighttraining

import android.app.Application
import androidx.room.Room
import com.knightindustries.knighttraining.core.persistence.AppDatabase
import com.knightindustries.knighttraining.core.persistence.AppDatabase.Companion.configure
import com.knightindustries.knighttraining.core.store.TrainingStore
import com.knightindustries.knighttraining.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app's object graph, built by hand: one maintainer, no DI framework (AGENTS.md). Screens reach
 * it through [AppContainer.from].
 */
class KnightTrainingApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.store.seedPresetsIfNeeded()
            container.preferences.firstLaunch()
        }
    }
}

class AppContainer(application: Application) {
    val appScope = CoroutineScope(SupervisorJob())

    /** The file-backed database, like iOS's `AppDatabase.shared`. */
    val database: AppDatabase = Room.databaseBuilder(application, AppDatabase::class.java, AppDatabase.FILE_NAME)
        .configure()
        .build()

    val store = TrainingStore(database)

    val preferences = AppPreferences(application)

    companion object {
        fun from(application: Application): AppContainer = (application as KnightTrainingApplication).container
    }
}
