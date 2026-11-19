package com.knightindustries.knighttraining.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import androidx.glance.material3.ColorProviders
import com.knightindustries.knighttraining.core.model.DailyTrainingIntensity
import com.knightindustries.knighttraining.designsystem.Theme
import java.time.LocalDate

/** Shared look for the widgets: dark-only like the app, colors from iOS's widget extension. */
internal object WidgetStyle {
    private val scheme = androidx.compose.material3.darkColorScheme(
        primary = Theme.accent,
        background = Theme.background,
        onBackground = Color(0xFFEDE6F7),
        onSurfaceVariant = Color(0xFFCBC2DB),
        surfaceVariant = Color(0xFF2A2236),
    )
    val colors = ColorProviders(light = scheme, dark = scheme)

    /** iOS's widget ring: a #3D3770 track, filled #F3F2F8 → #A282FF. */
    private val ringTrack = Color(0xFF3D3770)
    private val ringStart = Color(0xFFF3F2F8)
    private val ringEnd = Color(0xFFA282FF)

    /** Behind a goal row on the medium widget. */
    val rowBackground = Color.White.copy(alpha = 0.08f)

    /** The stat widget's label lavender and its piece's faint silhouette. */
    val statLabel = Color(0xFFCBBDF2)
    val silhouette = Color.White.copy(alpha = 0.08f)

    /**
     * The goals ring as a bitmap: Glance's own circular indicator is indeterminate only, and a
     * widget can't draw. [sizePx] square, track and arc [strokePx] wide.
     */
    fun ring(sizePx: Int, strokePx: Float, progress: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = ringTrack.toArgb()
        }
        canvas.drawOval(rect, paint)
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) {
            val center = sizePx / 2f
            paint.strokeCap = Paint.Cap.ROUND
            paint.shader = SweepGradient(center, center, intArrayOf(ringStart.toArgb(), ringEnd.toArgb()), floatArrayOf(0f, maxOf(fraction, 0.01f)))
            canvas.save()
            canvas.rotate(-90f, center, center)
            canvas.drawArc(rect, 0f, 360f * fraction, false, paint)
            canvas.restore()
        }
        return bitmap
    }

    /** iOS's grid blue, at each intensity's opacity; an empty or future day at 0.18 on a widget. */
    fun intensityColor(minutes: Int): Color {
        val blue = Color(0.34f, 0.44f, 0.89f)
        return blue.copy(
            alpha = when (DailyTrainingIntensity.of(minutes)) {
                null -> 0.18f
                DailyTrainingIntensity.LIGHT -> 0.3f
                DailyTrainingIntensity.MODERATE -> 0.5f
                DailyTrainingIntensity.INTENSE -> 0.8f
            },
        )
    }

    /**
     * The daily grid as a bitmap (iOS `DailyActivityWidget`): weeks as columns, oldest left, days as
     * rows from the first weekday, as many weeks as fit the width (up to 16). A RemoteViews layout
     * of 100-odd boxes would be far heavier than one image.
     */
    fun grid(widthPx: Int, heightPx: Int, spacingPx: Float, currentWeekStart: LocalDate, today: LocalDate, dailySeconds: Map<LocalDate, Int>): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cell = maxOf(2f, (heightPx - spacingPx * 6) / 7)
        val weeks = ((widthPx + spacingPx) / (cell + spacingPx)).toInt().coerceIn(1, 16)
        val columnGap = if (weeks > 1) (widthPx - weeks * cell) / (weeks - 1) else 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = maxOf(1f, cell * 0.15f)
        for (week in 0 until weeks) {
            val weekStart = currentWeekStart.minusWeeks((weeks - 1 - week).toLong())
            for (day in 0 until 7) {
                val date = weekStart.plusDays(day.toLong())
                val minutes = if (date.isAfter(today)) 0 else (dailySeconds[date] ?: 0) / 60
                paint.color = intensityColor(minutes).toArgb()
                val left = week * (cell + columnGap)
                val top = day * (cell + spacingPx)
                canvas.drawRoundRect(left, top, left + cell, top + cell, radius, radius, paint)
            }
        }
        return bitmap
    }

    /** Redraws every widget: the app calls this whenever the weekly surface changes. */
    suspend fun updateAll(context: Context) {
        WeekWidget().updateAll(context)
        DailyActivityWidget().updateAll(context)
        StatWidget().updateAll(context)
    }
}
