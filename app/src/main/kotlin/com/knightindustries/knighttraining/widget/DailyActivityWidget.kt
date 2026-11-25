package com.knightindustries.knighttraining.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.KnightTrainingApplication
import com.knightindustries.knighttraining.MainActivity
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.core.store.weeklySurface
import java.time.Instant

/**
 * The last weeks, day by day (iOS `DailyActivityWidget`): a column per week, as many as fit (up
 * to 16, the weekly surface's `WIDGET_GRID_DAYS`), each day tinted by its tracked time.
 */
class DailyActivityWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppContainer.from(context.applicationContext as KnightTrainingApplication)
        val surface = container.store.weeklySurface(firstLaunch = container.preferences.firstLaunch())
        provideContent {
            GlanceTheme(colors = WidgetStyle.colors) { DailyActivityContent(context, surface, LocalSize.current) }
        }
    }
}

@Composable
internal fun DailyActivityContent(context: Context, surface: WeeklySurface, size: DpSize) {
    val padding = 16.dp
    val density = context.resources.displayMetrics.density
    val width = ((size.width - padding * 2).value * density).toInt()
    val height = ((size.height - padding * 2).value * density).toInt()
    val calendar = WeekCalendar.system()
    val today = calendar.localDate(Instant.now())
    val bitmap = WidgetStyle.grid(width, height, 3 * density, calendar.weekStart(today), today, surface.recentDailyDurationSeconds, WidgetStyle.palette(context))
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp).padding(padding)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Image(ImageProvider(bitmap), contentDescription = context.getString(R.string.daily_activity), contentScale = ContentScale.Fit, modifier = GlanceModifier.fillMaxSize())
    }
}

class DailyActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyActivityWidget()
}
