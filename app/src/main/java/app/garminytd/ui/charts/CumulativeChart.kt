package app.garminytd.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.garminytd.R
import app.garminytd.stats.DashboardStats
import app.garminytd.ui.DistanceUnit
import app.garminytd.ui.currentLocale
import app.garminytd.ui.short
import app.garminytd.ui.theme.NumberStyle
import app.garminytd.ui.theme.RideTheme
import java.time.LocalDate
import java.time.Month
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Cumulative distance by day of year: last year (full), this year (to today), and the projected rest
 * of this year (dashed). Drag or tap to scrub; the readout above the chart follows your finger.
 */
@Composable
fun CumulativeChart(stats: DashboardStats, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val colors = RideTheme.colors
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    var scrub by remember(stats) { mutableStateOf<Int?>(null) }

    val todayIdx = stats.cumulativeThis.lastIndex
    val days = maxOf(stats.cumulativeRef.size, todayIdx + stats.projection.size)
    val others = stats.years.filter { it.year != stats.year && it.year != stats.refYear }
    val rawMax = maxOf(stats.refTotalKm, stats.projectedYearEndKm, stats.ytdKm, others.maxOfOrNull { it.totalKm } ?: 0.0, 1.0) * unit.perKm
    val locale = currentLocale()
    val monthLetters = remember(locale) {
        Month.entries.map { it.getDisplayName(java.time.format.TextStyle.NARROW_STANDALONE, locale) }
    }
    val projectedSuffix = stringResource(R.string.chart_projected_suffix)
    val step = niceStep(rawMax / 4)
    val yMax = ceil(rawMax / step) * step

    val idx = scrub ?: todayIdx
    val date = LocalDate.ofYearDay(stats.year, (idx + 1).coerceAtMost(LocalDate.of(stats.year, 12, 31).dayOfYear))
    val thisVal = when {
        idx <= todayIdx -> stats.cumulativeThis[idx]
        else -> stats.projection.getOrNull(idx - todayIdx)
    }
    val lastVal = stats.cumulativeRef.getOrNull(idx.coerceAtMost(stats.cumulativeRef.lastIndex))

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date.short(), style = MaterialTheme.typography.labelLarge, color = labelColor)
            Spacer(Modifier.width(12.dp))
            LegendDot(colors.thisYear)
            Text(
                " ${thisVal?.let { unit.withUnit(it) } ?: "–"}${if (idx > todayIdx) " $projectedSuffix" else ""}",
                style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
            )
            Spacer(Modifier.width(12.dp))
            LegendDot(colors.lastYear)
            Text(" ${lastVal?.let { unit.withUnit(it) } ?: "–"}", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(stats) {
                    detectTapGestures { pos -> scrub = indexAt(pos.x, size.width.toFloat(), LEFT_PAD.dp.toPx(), days) }
                }
                .pointerInput(stats) {
                    detectHorizontalDragGestures(
                        onDragStart = { pos -> scrub = indexAt(pos.x, size.width.toFloat(), LEFT_PAD.dp.toPx(), days) },
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                    ) { change, _ -> scrub = indexAt(change.position.x, size.width.toFloat(), LEFT_PAD.dp.toPx(), days) }
                },
        ) {
            val left = LEFT_PAD.dp.toPx()
            val bottom = size.height - BOTTOM_PAD.dp.toPx()
            val w = size.width - left
            fun x(i: Int) = left + w * i / (days - 1).toFloat()
            fun y(km: Double) = (bottom - bottom * (km * unit.perKm / yMax)).toFloat()

            val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp, fontFeatureSettings = "tnum")
            // Horizontal grid + y labels.
            var v = 0.0
            while (v <= yMax + 1e-6) {
                val yy = (bottom - bottom * (v / yMax)).toFloat()
                drawLine(colors.gridLine, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(compact(v), labelStyle)
                drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), yy - layout.size.height / 2f))
                v += step
            }
            // Month ticks.
            for (m in 1..12) {
                val d = LocalDate.of(stats.year, m, 1).dayOfYear - 1
                val layout = measurer.measure(monthLetters[m - 1], labelStyle)
                drawText(layout, topLeft = Offset(x(d) + 2.dp.toPx(), bottom + 4.dp.toPx()))
            }

            // Every other year with data, faint, behind everything else.
            others.forEach { row ->
                drawPath(linePath(row.cumulative, ::x, ::y), colors.lastYear.copy(alpha = 0.28f), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
            }

            // Reference year.
            drawPath(linePath(stats.cumulativeRef, ::x, ::y), colors.lastYear, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Projection (dashed).
            if (stats.projection.size > 1) {
                val proj = Path().apply {
                    stats.projection.forEachIndexed { i, km -> if (i == 0) moveTo(x(todayIdx + i), y(km)) else lineTo(x(todayIdx + i), y(km)) }
                }
                drawPath(
                    proj, colors.projection,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }

            // This year: gradient fill + bold line.
            val thisPath = linePath(stats.cumulativeThis, ::x, ::y)
            val fill = Path().apply {
                addPath(thisPath)
                lineTo(x(todayIdx), bottom)
                lineTo(x(0), bottom)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(colors.thisYear.copy(alpha = 0.35f), colors.thisYear.copy(alpha = 0f)), endY = bottom))
            drawPath(thisPath, colors.thisYear, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(colors.thisYear, 5.dp.toPx(), Offset(x(todayIdx), y(stats.ytdKm)))
            drawCircle(colors.thisYear.copy(alpha = 0.25f), 10.dp.toPx(), Offset(x(todayIdx), y(stats.ytdKm)))

            // Scrub cursor.
            scrub?.let { s ->
                drawLine(labelColor.copy(alpha = 0.6f), Offset(x(s), 0f), Offset(x(s), bottom), strokeWidth = 1.dp.toPx())
                thisVal?.let { drawCircle(colors.thisYear, 4.dp.toPx(), Offset(x(s), y(it))) }
                lastVal?.let { drawCircle(colors.lastYear, 4.dp.toPx(), Offset(x(s), y(it))) }
            }
        }
    }
}

private const val LEFT_PAD = 40
private const val BOTTOM_PAD = 18

/** Inverse of the Canvas x-mapping: pointer x -> day index. */
private fun indexAt(px: Float, width: Float, leftPx: Float, days: Int): Int {
    val frac = ((px - leftPx) / (width - leftPx)).coerceIn(0f, 1f)
    return (frac * (days - 1)).roundToInt()
}

private fun linePath(values: List<Double>, x: (Int) -> Float, y: (Double) -> Float) = Path().apply {
    values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
}

internal fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val mag = 10.0.pow(kotlin.math.floor(log10(raw)))
    val n = raw / mag
    val nice = when {
        n <= 1 -> 1.0
        n <= 2 -> 2.0
        n <= 2.5 -> 2.5
        n <= 5 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

internal fun compact(v: Double): String = when {
    v >= 10_000 -> "${(v / 1000).roundToInt()}k"
    v >= 1_000 -> "${"%.1f".format(v / 1000).removeSuffix(".0")}k"
    else -> v.roundToInt().toString()
}
