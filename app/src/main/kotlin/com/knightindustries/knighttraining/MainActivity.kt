package com.knightindustries.knighttraining

import com.knightindustries.knighttraining.review.StoreReview
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.knightindustries.knighttraining.designsystem.KnightTrainingTheme
import com.knightindustries.knighttraining.navigation.KnightTrainingApp

class MainActivity : ComponentActivity() {
    /** A notification's action (open or stop the session), consumed once by the UI. */
    private val pendingAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app follows the system's light/dark setting, and the bars' icons with it.
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingAction.value = intent?.action
        setContent {
            KnightTrainingTheme {
                KnightTrainingApp(
                    pendingAction = pendingAction.value,
                    onActionHandled = { pendingAction.value = null },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        StoreReview.onForeground()
        AppContainer.from(application).apply {
            syncLichessSoon()
            prefetchCoachModelOnce()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAction.value = intent.action
    }

    override fun onStop() {
        super.onStop()
        StoreReview.onBackground()
    }
}

