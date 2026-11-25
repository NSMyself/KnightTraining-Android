package com.knightindustries.knighttraining.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import androidx.glance.material3.ColorProviders
import com.knightindustries.knighttraining.core.model.DailyTrainingIntensity
import com.knightindustries.knighttraining.designsystem.DarkPalette
import com.knightindustries.knighttraining.designsystem.LightPalette
import com.knightindustries.knighttraining.designsystem.PaletteColors
import com.knightindustries.knighttraining.designsystem.colorScheme
import java.time.LocalDate

/**
 * Shared look for the widgets. They follow the system's light/dark setting through the app's
 * palette, like iOS's since 2026-11-23: Glance colors resolve per mode on their own, and the two
 * bitmaps (ring, grid) are drawn for the mode they're drawn in ([palette]); the app redraws every
 * widget when the mode changes.
 */
internal object WidgetStyle {
    val colors = ColorProviders(light = colorScheme(LightPalette), dark = colorScheme(DarkPalette))

    /** The palette for [context]'s current light/dark mode, for what's drawn into a bitmap. */
    fun palette(context: Context): PaletteColors =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) DarkPalette else LightPalette

    /** Behind a goal row on the medium widget: a white overlay iOS left out of the palette move. */
    val rowBackground = Color.White.copy(alpha = 0.08f)

    /** The stat widget's piece, a faint silhouette (a white overlay, like iOS's). */
    val silhouette = Color.White.copy(alpha = 0.08f)

    /**
     * The goals ring as a bitmap: Glance's own circular indicator is indeterminate only, and a
     * widget can't draw. [sizePx] square, track and arc [strokePx] wide.
     */
    fun ring(sizePx: Int, strokePx: Float, progress: Float, palette: PaletteColors): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            // iOS: a border-colored track, filled with the solid accent.
            color = palette.border.toArgb()
        }
        canvas.drawOval(rect, paint)
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) {
            val center = sizePx / 2f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = palette.accent.toArgb()
            canvas.save()
            canvas.rotate(-90f, center, center)
            canvas.drawArc(rect, 0f, 360f * fraction, false, paint)
            canvas.restore()
        }
        return bitmap
    }

    /** The heatmap step for a day (iOS `DailyTrainingIntensity.color`), the same as the app's grid. */
    fun intensityColor(minutes: Int, palette: PaletteColors): Color = palette.heatmap[
        when (DailyTrainingIntensity.of(minutes)) {
            null -> 0
            DailyTrainingIntensity.LIGHT -> 2
            DailyTrainingIntensity.MODERATE -> 4
            DailyTrainingIntensity.INTENSE -> 5
        },
    ]

    /**
     * The daily grid as a bitmap (iOS `DailyActivityWidget`): weeks as columns, oldest left, days as
     * rows from the first weekday, as many weeks as fit the width (up to 16). A RemoteViews layout
     * of 100-odd boxes would be far heavier than one image.
     */
    fun grid(widthPx: Int, heightPx: Int, spacingPx: Float, currentWeekStart: LocalDate, today: LocalDate, dailySeconds: Map<LocalDate, Int>, palette: PaletteColors): Bitmap {
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
                paint.color = intensityColor(minutes, palette).toArgb()
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
