package com.knightindustries.knighttraining

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
        // The app is dark-only, so the system bars always get light icons, whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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
        AppContainer.from(application).apply {
            syncLichessSoon()
            prefetchCoachModelOnce()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAction.value = intent.action
    }
}
