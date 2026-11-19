package com.knightindustries.knighttraining.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity as startIntent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.KnightTrainingApplication
import com.knightindustries.knighttraining.MainActivity
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.core.store.weeklySurface
import com.knightindustries.knighttraining.ui.ratingTypeNameRes

/** The stats a Training Stat widget can show (iOS `StatKind`). Stored by [key], so never rename one. */
enum class WidgetStat(val key: String, val title: Int) {
    RATING("elo", R.string.stat_rating),
    SESSIONS_THIS_WEEK("sessionsThisWeek", R.string.stat_sessions_week),
    TIME_THIS_WEEK("timeTrainedThisWeek", R.string.stat_time_week),
    STREAK("streak", R.string.stat_training_streak),
    TOTAL_HOURS("totalHours", R.string.total_hours),
    ;

    companion object {
        fun of(key: String?): WidgetStat? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One stat, chosen per widget (iOS `TrainingStatWidget`): rating, sessions or time this week, the
 * streak, or lifetime hours. Streak and hours sit over a chess piece that climbs with them, pawn
 * to king. The choice is made in [StatWidgetConfigActivity] when the widget is added, and again
 * from the launcher's widget settings (Android 12+; before that, an unset widget's tap opens it).
 */
class StatWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppContainer.from(context.applicationContext as KnightTrainingApplication)
        val surface = container.store.weeklySurface(firstLaunch = container.preferences.firstLaunch())
        // Before Android 12 the launcher can't reopen a widget's settings, so an unset widget's
        // tap opens the chooser itself.
        val chooser = Intent(context, StatWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, GlanceAppWidgetManager(context).getAppWidgetId(id))
        provideContent {
            val stat = WidgetStat.of(currentState<Preferences>()[STAT_KEY])
            GlanceTheme(colors = WidgetStyle.colors) { StatWidgetContent(context, surface, stat, chooser) }
        }
    }

    companion object {
        val STAT_KEY = stringPreferencesKey("stat")
    }
}

@Composable
internal fun StatWidgetContent(
    context: Context,
    surface: WeeklySurface,
    stat: WidgetStat?,
    chooser: Intent? = null,
    sdk: Int = Build.VERSION.SDK_INT,
) {
    // Android 12 reconfigures from a long-press; earlier launchers can't, so the tap does it.
    val reconfigurable = sdk >= Build.VERSION_CODES.S
    val tap = if (stat == null && !reconfigurable && chooser != null) startIntent(chooser) else actionStartActivity<MainActivity>()
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp).padding(12.dp)
            .clickable(tap),
        contentAlignment = Alignment.Center,
    ) {
        when (stat) {
            null -> Empty(context.getString(if (reconfigurable) R.string.stat_choose_hint else R.string.stat_choose_hint_tap))
            WidgetStat.RATING -> when {
                !surface.hasLichessAccount -> Empty(context.getString(R.string.stat_connect_lichess))
                surface.currentRating == null -> Empty(context.getString(R.string.stat_not_enough_data))
                else -> Hero(
                    surface.currentRating.toString(),
                    surface.currentRatingType?.let { context.getString(ratingTypeNameRes(it)) } ?: context.getString(R.string.stat_rating),
                )
            }
            WidgetStat.SESSIONS_THIS_WEEK -> Hero(surface.sessionsThisWeekCount.toString(), context.getString(R.string.stat_sessions_week))
            WidgetStat.TIME_THIS_WEEK -> Hero(durationText(context, surface.weeklyTrackedSeconds), context.getString(R.string.stat_time_week))
            // 0 is the empty state: an unstarted streak isn't a streak.
            WidgetStat.STREAK -> if (surface.currentStreak >= 1) {
                Ranked(surface.currentStreak.toString(), context.getString(R.string.stat_week_streak), streakPiece(surface.currentStreak))
            } else {
                Empty(context.getString(R.string.stat_streak_empty))
            }
            WidgetStat.TOTAL_HOURS -> (surface.allTimeStudySeconds / 3600).let { hours ->
                Ranked(hours.toString(), context.getString(R.string.stat_hours_studied), hoursPiece(hours))
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center))
}

@Composable
private fun Hero(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 40.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(WidgetStyle.statLabel), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** The value over its piece's faint silhouette, bottom-right, as on iOS. */
@Composable
private fun Ranked(value: String, label: String, piece: Int) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Image(ImageProvider(piece), contentDescription = null, colorFilter = ColorFilter.tint(ColorProvider(WidgetStyle.silhouette)), modifier = GlanceModifier.size(96.dp))
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Hero(value, label) }
    }
}

/** iOS's tiers: a streak climbs every three weeks, hours widen as they grow. */
internal fun streakPiece(weeks: Int): Int = when {
    weeks < 2 -> R.drawable.chess_piece_pawn
    weeks <= 4 -> R.drawable.chess_piece_knight
    weeks <= 7 -> R.drawable.chess_piece_bishop
    weeks <= 10 -> R.drawable.chess_piece_rook
    weeks <= 13 -> R.drawable.chess_piece_queen
    else -> R.drawable.chess_piece_king
}

internal fun hoursPiece(hours: Int): Int = when {
    hours < 25 -> R.drawable.chess_piece_pawn
    hours < 75 -> R.drawable.chess_piece_knight
    hours < 150 -> R.drawable.chess_piece_bishop
    hours < 300 -> R.drawable.chess_piece_rook
    hours < 600 -> R.drawable.chess_piece_queen
    else -> R.drawable.chess_piece_king
}

class StatWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatWidget()
}
