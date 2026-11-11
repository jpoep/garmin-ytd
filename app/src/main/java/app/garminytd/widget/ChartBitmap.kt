package app.garminytd.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import app.garminytd.stats.DashboardStats
import app.garminytd.ui.DistanceUnit
import app.garminytd.ui.charts.compact
import app.garminytd.ui.charts.niceStep
import app.garminytd.ui.theme.RideColors
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * The dashboard's cumulative chart, drawn into a bitmap: widgets can only show views and images,
 * so the same picture (this year, reference year, projection, other years) is rasterised at the
 * widget's exact pixel size.
 */
object ChartBitmap {

    fun render(s: DashboardStats, unit: DistanceUnit, colors: RideColors, labelColor: Color, widthPx: Int, heightPx: Int, density: Float): Bitmap {
        val bmp = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
        val c = Canvas(bmp)
        fun dp(v: Float) = v * density

        val leftPad = dp(30f)
        val topPad = dp(6f) // room for the top y label, which is centred on its grid line
        val bottomPad = dp(14f)
        val bottom = heightPx - bottomPad
        val w = widthPx - leftPad
        val todayIdx = s.cumulativeThis.lastIndex
        val days = maxOf(s.cumulativeRef.size, todayIdx + s.projection.size)
        val others = s.years.filter { it.year != s.year && it.year != s.refYear }
        val rawMax = maxOf(s.refTotalKm, s.projectedYearEndKm, s.ytdKm, others.maxOfOrNull { it.totalKm } ?: 0.0, 1.0) * unit.perKm
        val step = niceStep(rawMax / 3)
        val yMax = ceil(rawMax / step) * step

        fun x(i: Int) = leftPad + w * i / (days - 1).toFloat()
        fun yv(value: Double) = (bottom - (bottom - topPad) * (value / yMax)).toFloat()
        fun y(km: Double) = yv(km * unit.perKm)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = dp(9f)
            fontFeatureSettings = "tnum"
        }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.gridLine.toArgb(); strokeWidth = dp(1f) }

        // Grid + y labels.
        var v = 0.0
        while (v <= yMax + 1e-6) {
            val yy = yv(v)
            c.drawLine(leftPad, yy, widthPx.toFloat(), yy, grid)
            val label = compact(v)
            c.drawText(label, leftPad - text.measureText(label) - dp(4f), yy + text.textSize / 3, text)
            v += step
        }
        // Month letters.
        for (m in 1..12) {
            val d = LocalDate.of(s.year, m, 1).dayOfYear - 1
            val letter = Month.of(m).getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault())
            c.drawText(letter, x(d) + dp(2f), heightPx - dp(2f), text)
        }

        fun line(color: Color, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color.toArgb()
            strokeWidth = dp(width)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        fun path(values: List<Double>, offset: Int = 0) = Path().apply {
            values.forEachIndexed { i, km -> if (i == 0) moveTo(x(i + offset), y(km)) else lineTo(x(i + offset), y(km)) }
        }

        others.forEach { c.drawPath(path(it.cumulative), line(colors.lastYear.copy(alpha = 0.28f), 1f)) }
        c.drawPath(path(s.cumulativeRef), line(colors.lastYear, 1.75f))
        if (s.projection.size > 1) {
            c.drawPath(path(s.projection, todayIdx), line(colors.projection, 1.75f).apply {
                pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
            })
        }

        val thisPath = path(s.cumulativeThis)
        val fill = Path(thisPath).apply {
            lineTo(x(todayIdx), bottom)
            lineTo(x(0), bottom)
            close()
        }
        c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, bottom,
                colors.thisYear.copy(alpha = 0.35f).toArgb(), colors.thisYear.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP,
            )
        })
        c.drawPath(thisPath, line(colors.thisYear, 2.5f))
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.thisYear.toArgb() }
        c.drawCircle(x(todayIdx), y(s.ytdKm), dp(3.5f), dot)
        return bmp
    }
}
