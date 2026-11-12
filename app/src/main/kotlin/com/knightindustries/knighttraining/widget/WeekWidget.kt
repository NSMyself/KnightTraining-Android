package com.knightindustries.knighttraining.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.KnightTrainingApplication
import com.knightindustries.knighttraining.MainActivity
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.core.store.weeklySurface
import com.knightindustries.knighttraining.designsystem.Theme
import com.knightindustries.knighttraining.ui.activityNameRes

/**
 * The Home Screen widget: this week at a glance. Widgets run in the app's process on Android, so it
 * reads the weekly surface straight from the database (iOS needs an App Group snapshot), and the
 * app pushes an update whenever the surface changes (see `KnightTrainingApplication`).
 */
class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppContainer.from(context.applicationContext as KnightTrainingApplication)
        val surface = container.store.weeklySurface(firstLaunch = container.preferences.firstLaunch())
        provideContent {
            GlanceTheme(colors = colors) { WeekWidgetContent(context, surface) }
        }
    }

    private companion object {
        /** Dark-only, like the app: the same scheme whatever the system theme. */
        private val scheme = androidx.compose.material3.darkColorScheme(
            primary = Theme.accent,
            background = Theme.background,
            onBackground = Color(0xFFEDE6F7),
            onSurfaceVariant = Color(0xFFCBC2DB),
            surfaceVariant = Color(0xFF2A2236),
        )
        val colors = ColorProviders(light = scheme, dark = scheme)
    }
}

/** The widget's layout, apart from its data load so a test can render it. */
@Composable
internal fun WeekWidgetContent(context: Context, surface: WeeklySurface) {
    val plan = surface.plan
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp).padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        val title = if (plan == null) {
            context.getString(R.string.app_name)
        } else {
            PlanTemplate.displayName(surface.planName, context.getString(R.string.my_training_plan))
        }
        Text(title, style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Medium, fontSize = 15.sp), maxLines = 1)
        val summary = buildList {
            plan?.let { add(context.getString(R.string.goals_done, it.completedGoalCount, it.goalCount)) }
            add(context.getString(R.string.duration_minutes, surface.weeklyTrackedMinutes))
        }.joinToString(" · ")
        Text(summary, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        Spacer(GlanceModifier.height(8.dp))

        if (plan == null) {
            Text(context.getString(R.string.plan_week_body), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp))
        } else {
            plan.weeklyGoals.take(3).forEach { goal -> GoalLine(context, goal) }
        }
        // A this-week surface only claims a resource touched this week (iOS `isWorthSurfacing`).
        surface.continueResource?.takeIf { it.isWorthSurfacing }?.let {
            Spacer(GlanceModifier.height(6.dp))
            Text("▶ ${it.title}", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp), maxLines = 1)
        }
    }
}

@Composable
private fun GoalLine(context: Context, goal: WeeklyGoal) {
    val name = goal.name ?: context.getString(activityNameRes(goal.activityType.code))
    val progress = when (goal.targetType) {
        TargetType.DURATION -> context.getString(R.string.progress_minutes, WeeklyGoal.displayMinutes(goal.currentValue, goal.targetValue), goal.targetValue / 60)
        TargetType.PROGRESS -> context.getString(R.string.progress_percent, goal.currentValue)
        TargetType.COUNT -> context.getString(R.string.progress_count, goal.currentValue, goal.targetValue)
    }
    Text("$name  $progress", style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp), maxLines = 1)
    LinearProgressIndicator(
        progress = (goal.progressPercentage / 100).toFloat(),
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
    Spacer(GlanceModifier.height(6.dp))
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
