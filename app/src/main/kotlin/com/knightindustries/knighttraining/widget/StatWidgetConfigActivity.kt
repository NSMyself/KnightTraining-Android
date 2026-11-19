package com.knightindustries.knighttraining.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.designsystem.KnightTrainingTheme
import kotlinx.coroutines.launch

/**
 * Picks the stat a Training Stat widget shows (iOS's `SelectStatIntent`): shown when the widget
 * is added and from the launcher's widget settings. Choosing one saves it and closes; backing
 * out leaves a new widget unadded.
 */
class StatWidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@StatWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            val current = WidgetStat.of(
                getAppWidgetState(this@StatWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)[StatWidget.STAT_KEY],
            )
            setContent {
                KnightTrainingTheme {
                    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stat_choose_title)) }) }) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding).selectableGroup()) {
                            WidgetStat.entries.forEach { stat ->
                                ListItem(
                                    headlineContent = { Text(stringResource(stat.title)) },
                                    leadingContent = { RadioButton(selected = stat == current, onClick = null) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.selectable(selected = stat == current, role = Role.RadioButton) { choose(appWidgetId, stat) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, stat: WidgetStat) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@StatWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@StatWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[StatWidget.STAT_KEY] = stat.key }
            }
            StatWidget().update(this@StatWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
