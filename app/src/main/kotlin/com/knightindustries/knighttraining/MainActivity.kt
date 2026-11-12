package com.knightindustries.knighttraining

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.knightindustries.knighttraining.designsystem.KnightTrainingTheme
import com.knightindustries.knighttraining.designsystem.Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark-only, so the system bars always get light icons, whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            KnightTrainingTheme {
                PlaceholderScreen()
            }
        }
    }
}

/** Phase 0 only: proves the theme and the build. Replaced by onboarding/Home in Phase 2. */
@Composable
private fun PlaceholderScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Theme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = Theme.accent,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    KnightTrainingTheme { PlaceholderScreen() }
}
