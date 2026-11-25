package com.knightindustries.knighttraining.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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
import com.knightindustries.knighttraining.ui.activityNameRes
import java.time.format.DateTimeFormatter

/**
 * The week widget: this week at a glance, at three sizes (iOS's Weekly Progress and Weekly
 * Progress Report, plus the full list Android had). Widgets run in the app's process on Android,
 * so it reads the weekly surface straight from the database (iOS needs an App Group snapshot),
 * and the app pushes an update whenever the surface changes (see `KnightTrainingApplication`).
 */
class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppContainer.from(context.applicationContext as KnightTrainingApplication)
        val surface = container.store.weeklySurface(firstLaunch = container.preferences.firstLaunch())
        provideContent {
            GlanceTheme(colors = WidgetStyle.colors) { WeekWidgetContent(context, surface, LocalSize.current) }
        }
    }

    companion object {
        /** A ring and the count (iOS Weekly Progress, small). */
        val SMALL = DpSize(110.dp, 110.dp)
        /** The ring beside what's left to do (iOS Weekly Progress Report, medium). */
        val MEDIUM = DpSize(250.dp, 110.dp)
        /** Every goal with its bar, and what to continue. */
        val LARGE = DpSize(250.dp, 220.dp)
    }
}

/** The widget's layout, apart from its data load so a test can render it. */
@Composable
internal fun WeekWidgetContent(context: Context, surface: WeeklySurface, size: DpSize = WeekWidget.LARGE) {
    val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp)
        .clickable(actionStartActivity<MainActivity>())
    when {
        size.height >= WeekWidget.LARGE.height -> Column(base.padding(14.dp)) { FullList(context, surface) }
        surface.plan == null || surface.goals.isEmpty() -> Column(base.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { NoPlan(context, surface) }
        size.width >= WeekWidget.MEDIUM.width -> Row(base.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Report(context, surface) }
        else -> Column(base.padding(14.dp)) { Summary(context, surface, ringDp = 56, countSp = 22) }
    }
}

private val muted @Composable get() = GlanceTheme.colors.onSurfaceVariant
private val strong @Composable get() = GlanceTheme.colors.onBackground

/** iOS's "no plan" states: this week's time once there is some, else a quiet line. */
@Composable
private fun NoPlan(context: Context, surface: WeeklySurface) {
    if (surface.weeklyTrackedSeconds > 0) {
        Text(context.getString(R.string.widget_this_week), style = TextStyle(color = muted, fontSize = 12.sp))
        Text(durationText(context, surface.weeklyTrackedSeconds), style = TextStyle(color = strong, fontSize = 22.sp, fontWeight = FontWeight.Bold))
    } else {
        Text(context.getString(R.string.widget_no_training), style = TextStyle(color = muted, fontSize = 12.sp))
    }
}

/** The ring, the plan's name, "2/5 goals" and the week's dates. */
@Composable
private fun Summary(context: Context, surface: WeeklySurface, ringDp: Int, countSp: Int) {
    val plan = surface.plan ?: return
    Ring(context, ringDp, plan.completedGoalCount, plan.goalCount)
    Spacer(GlanceModifier.height(8.dp))
    Text(planTitle(context, surface), style = TextStyle(color = strong, fontSize = 11.sp), maxLines = 1)
    Text(
        context.getString(R.string.goals_fraction, plan.completedGoalCount, plan.goalCount),
        style = TextStyle(color = strong, fontSize = countSp.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
    Text(weekRange(context, surface), style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
}

/** The summary beside the first three goals still open. */
@Composable
private fun RowScope.Report(context: Context, surface: WeeklySurface) {
    Column(GlanceModifier.width(110.dp)) { Summary(context, surface, ringDp = 48, countSp = 18) }
    Spacer(GlanceModifier.width(12.dp))
    Column(GlanceModifier.defaultWeight()) {
        surface.goals.filterNot { it.isCompleted }.take(3).forEach { goal ->
            Row(
                GlanceModifier.fillMaxWidth().background(WidgetStyle.rowBackground).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(goalName(context, goal), style = TextStyle(color = strong, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text(progressText(context, goal), style = TextStyle(color = strong, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
        }
    }
}

@Composable
private fun FullList(context: Context, surface: WeeklySurface) {
    val plan = surface.plan
    val title = if (plan == null) context.getString(R.string.app_name) else planTitle(context, surface)
    Text(title, style = TextStyle(color = strong, fontWeight = FontWeight.Medium, fontSize = 15.sp), maxLines = 1)
    val summary = buildList {
        plan?.let { add(context.resources.getQuantityString(R.plurals.goals_done, it.goalCount, it.completedGoalCount, it.goalCount)) }
        add(context.getString(R.string.duration_minutes, surface.weeklyTrackedMinutes))
    }.joinToString(" · ")
    Text(summary, style = TextStyle(color = muted, fontSize = 12.sp))
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

@Composable
private fun GoalLine(context: Context, goal: WeeklyGoal) {
    Text("${goalName(context, goal)}  ${progressText(context, goal)}", style = TextStyle(color = strong, fontSize = 12.sp), maxLines = 1)
    LinearProgressIndicator(
        progress = (goal.progressPercentage / 100).toFloat(),
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
    Spacer(GlanceModifier.height(6.dp))
}

@Composable
private fun Ring(context: Context, sizeDp: Int, done: Int, total: Int) {
    val density = context.resources.displayMetrics.density
    val bitmap = WidgetStyle.ring((sizeDp * density).toInt(), 8 * density * sizeDp / 66f, if (total > 0) done.toFloat() / total else 0f, WidgetStyle.palette(context))
    Image(ImageProvider(bitmap), contentDescription = context.getString(R.string.goals_fraction, done, total), modifier = GlanceModifier.size(sizeDp.dp))
}

private fun planTitle(context: Context, surface: WeeklySurface) =
    PlanTemplate.displayName(surface.planName, context.getString(R.string.my_training_plan))

private fun goalName(context: Context, goal: WeeklyGoal) = goal.name ?: context.getString(activityNameRes(goal.activityType.code))

private fun progressText(context: Context, goal: WeeklyGoal) = when (goal.targetType) {
    TargetType.DURATION -> context.getString(R.string.progress_minutes, WeeklyGoal.displayMinutes(goal.currentValue, goal.targetValue), goal.targetValue / 60)
    TargetType.PROGRESS -> context.getString(R.string.progress_percent, goal.currentValue)
    TargetType.COUNT -> context.getString(R.string.progress_count, goal.currentValue, goal.targetValue)
}

private fun weekRange(context: Context, surface: WeeklySurface): String {
    val locale = context.resources.configuration.locales[0]
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    return context.getString(R.string.week_range, surface.weekStart.format(formatter), surface.weekStart.plusDays(6).format(formatter))
}

/** "2 h 15 min", like the app's `durationText`, from a Context. */
internal fun durationText(context: Context, seconds: Int): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> context.getString(R.string.duration_hours_minutes, hours, rest)
        hours > 0 -> context.getString(R.string.duration_hours, hours)
        else -> context.getString(R.string.duration_minutes, minutes)
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
