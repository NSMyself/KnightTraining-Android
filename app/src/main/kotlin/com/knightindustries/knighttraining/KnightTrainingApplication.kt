package com.knightindustries.knighttraining

import android.app.Application
import androidx.room.Room
import com.knightindustries.knighttraining.core.persistence.AppDatabase
import com.knightindustries.knighttraining.core.persistence.AppDatabase.Companion.configure
import com.knightindustries.knighttraining.core.store.TrainingStore
import com.knightindustries.knighttraining.core.net.CatalogSync
import com.knightindustries.knighttraining.core.net.ResourceEnricher
import com.knightindustries.knighttraining.data.AppPreferences
import com.knightindustries.knighttraining.session.SessionNotifier
import com.knightindustries.knighttraining.session.SessionRepository
import androidx.glance.appwidget.updateAll
import com.knightindustries.knighttraining.core.store.observeWeeklySurface
import com.knightindustries.knighttraining.widget.WeekWidget
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
            container.catalogSync.syncIfNeeded()
        }
        // The widget redraws whenever the week changes, like iOS's widget writer off the same surface.
        container.appScope.launch {
            val firstLaunch = container.preferences.firstLaunch()
            container.store.observeWeeklySurface(firstLaunch = firstLaunch).collect {
                WeekWidget().updateAll(this@KnightTrainingApplication)
            }
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

    /** The practice session in progress, if any, and its notification. */
    val sessions = SessionRepository(application, SessionNotifier(application))

    /** What a pasted or shared link can tell the add-resource form. */
    val enricher = ResourceEnricher()

    /** The course catalog, re-fetched with its ETag (a cheap 304 when unchanged). */
    val catalogSync = CatalogSync(store, preferences.catalogEtags)

    companion object {
        fun from(application: Application): AppContainer = (application as KnightTrainingApplication).container
    }
}
