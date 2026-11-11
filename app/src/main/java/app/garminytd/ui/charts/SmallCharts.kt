package app.garminytd.ui.charts

import app.garminytd.ui.currentLocale
import app.garminytd.ui.scoreTierLabel
import app.garminytd.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.garminytd.stats.DashboardStats
import app.garminytd.stats.MonthBar
import app.garminytd.stats.Score
import app.garminytd.stats.YtdStats
import app.garminytd.ui.DistanceUnit
import app.garminytd.ui.short
import app.garminytd.ui.theme.NumberStyle
import app.garminytd.ui.theme.RideTheme
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle as JTextStyle
import kotlin.math.max
import kotlin.math.min

@Composable
fun LegendDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** Grouped monthly bars, this year vs last year. Tap a month for exact values. */
@Composable
fun MonthlyBars(months: List<MonthBar>, year: Int, refYear: Int, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val colors = RideTheme.colors
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val current = months.indexOfLast { !it.isFuture }.coerceAtLeast(0)
    var selected by remember(months) { mutableIntStateOf(current) }
    val sel = months[selected]
    val maxKm = max(1.0, months.maxOf { max(it.thisKm, it.refKm) })

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Month.of(sel.month).getDisplayName(JTextStyle.FULL, locale),
                style = MaterialTheme.typography.labelLarge, color = labelColor,
            )
            Spacer(Modifier.width(12.dp))
            LegendDot(colors.thisYear)
            Text(" $year ${if (sel.isFuture) "–" else unit.withUnit(sel.thisKm)}", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
            Spacer(Modifier.width(12.dp))
            LegendDot(colors.lastYear)
            Text(" $refYear ${unit.withUnit(sel.refKm)}", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(months) {
                    detectTapGestures { pos -> selected = (pos.x / (size.width / 12f)).toInt().coerceIn(0, 11) }
                },
        ) {
            val bottom = size.height - 16.dp.toPx()
            val slot = size.width / 12f
            val barW = min(slot * 0.32f, 12.dp.toPx())
            val gap = 2.dp.toPx()
            val radius = CornerRadius(3.dp.toPx())
            val style = TextStyle(color = labelColor, fontSize = 10.sp)
            months.forEachIndexed { i, m ->
                val cx = slot * i + slot / 2
                if (i == selected) {
                    drawRoundRect(colors.gridLine, Offset(slot * i + 1.dp.toPx(), 0f), Size(slot - 2.dp.toPx(), bottom), CornerRadius(6.dp.toPx()))
                }
                val hLast = (bottom * (m.refKm / maxKm)).toFloat()
                drawRoundRect(colors.lastYear.copy(alpha = 0.75f), Offset(cx - barW - gap / 2, bottom - hLast), Size(barW, hLast), radius)
                if (!m.isFuture) {
                    val hThis = (bottom * (m.thisKm / maxKm)).toFloat()
                    drawRoundRect(colors.thisYear, Offset(cx + gap / 2, bottom - hThis), Size(barW, hThis), radius)
                }
                val l = measurer.measure(Month.of(m.month).getDisplayName(JTextStyle.NARROW, locale), style)
                drawText(l, topLeft = Offset(cx - l.size.width / 2f, bottom + 3.dp.toPx()))
            }
        }
    }
}

/** 270° gauge for the 0–100 form score. */
@Composable
fun ScoreRing(score: Score, modifier: Modifier = Modifier, diameter: Dp = 120.dp) {
    val colors = RideTheme.colors
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                colors.thisYear,
                135f, 270f * score.total / 100f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${score.total}", style = MaterialTheme.typography.displayMedium.merge(NumberStyle))
            Text(scoreTierLabel(score.tier), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * GitHub-style calendar of the current year: one column per week, Monday on top.
 * Intensity buckets are relative to your own non-zero days (quartiles), so it works for any volume.
 */
@Composable
fun YearHeatmap(stats: DashboardStats, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val colors = RideTheme.colors
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val jan1 = LocalDate.of(stats.year, 1, 1)
    val firstMonday = YtdStats.weekStart(jan1)
    val dec31 = LocalDate.of(stats.year, 12, 31)
    val weeks = ((YtdStats.weekStart(dec31).toEpochDay() - firstMonday.toEpochDay()) / 7 + 1).toInt()
    val nonZero = stats.dailyKm.filter { it > 0 }.sorted()
    val thresholds = if (nonZero.isEmpty()) emptyList() else listOf(0.25, 0.5, 0.75).map { nonZero[(it * (nonZero.size - 1)).toInt()] }
    var picked by remember(stats) { mutableStateOf<LocalDate?>(null) }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(weeks / 8.6f)
                .pointerInput(stats) {
                    detectTapGestures { pos ->
                        val top = 14.dp.toPx()
                        val cell = min(size.width / weeks.toFloat(), (size.height - top) / 7f)
                        val col = (pos.x / cell).toInt()
                        val row = ((pos.y - top) / cell).toInt()
                        val d = firstMonday.plusDays(col * 7L + row)
                        picked = d.takeIf { row in 0..6 && d.year == stats.year && !d.isAfter(stats.today) }
                    }
                },
        ) {
            val top = 14.dp.toPx()
            val cell = min(size.width / weeks.toFloat(), (size.height - top) / 7f)
            val pad = max(1f, cell * 0.14f)
            val style = TextStyle(color = labelColor, fontSize = 9.sp)
            var lastMonth = 0
            for (w in 0 until weeks) {
                for (r in 0 until 7) {
                    val d = firstMonday.plusDays(w * 7L + r)
                    if (d.year != stats.year) continue
                    if (r == 0 || d.dayOfMonth == 1) {
                        if (d.monthValue != lastMonth && d.dayOfMonth <= 7) {
                            lastMonth = d.monthValue
                            val l = measurer.measure(Month.of(d.monthValue).getDisplayName(JTextStyle.SHORT, locale), style)
                            if (w * cell + l.size.width < size.width) drawText(l, topLeft = Offset(w * cell, 0f))
                        }
                    }
                    val km = stats.dailyKm[d.dayOfYear - 1]
                    val color = when {
                        d.isAfter(stats.today) -> colors.heatEmpty.copy(alpha = 0.4f)
                        km <= 0 -> colors.heatEmpty
                        else -> colors.heatScale[thresholds.count { km > it }.coerceAtMost(colors.heatScale.lastIndex)]
                    }
                    drawRoundRect(
                        color,
                        Offset(w * cell + pad / 2, top + r * cell + pad / 2),
                        Size(cell - pad, cell - pad),
                        CornerRadius(cell * 0.2f),
                    )
                    if (d == picked) {
                        drawRoundRect(
                            labelColor, Offset(w * cell + pad / 2, top + r * cell + pad / 2), Size(cell - pad, cell - pad),
                            CornerRadius(cell * 0.2f), style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val text = picked?.let { "${it.short()}: ${unit.withUnit(stats.dailyKm[it.dayOfYear - 1], 1)}" }
                ?: stringResource(R.string.heatmap_hint)
            Text(text, style = MaterialTheme.typography.labelMedium.merge(NumberStyle), color = labelColor, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.heatmap_less) + " ", style = MaterialTheme.typography.labelSmall, color = labelColor)
            (listOf(colors.heatEmpty) + colors.heatScale).forEach {
                Box(Modifier.padding(horizontal = 1.dp).size(10.dp).background(it, RoundedCornerShape(2.dp)))
            }
            Text(" " + stringResource(R.string.heatmap_more), style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

/** Distance per weekday this year: when do you actually ride? */
@Composable
fun WeekdayBars(km: List<Double>, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val colors = RideTheme.colors
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val maxKm = max(1.0, km.max())
    val top = km.indexOf(km.max())
    val locale = currentLocale()
    val names = java.time.DayOfWeek.entries.map { it.getDisplayName(JTextStyle.SHORT, locale) }
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val bottom = size.height - 16.dp.toPx()
        val slot = size.width / 7f
        val barW = slot * 0.55f
        val style = TextStyle(color = labelColor, fontSize = 10.sp)
        val valueStyle = TextStyle(color = labelColor, fontSize = 9.sp, fontFeatureSettings = "tnum")
        km.forEachIndexed { i, v ->
            val h = ((bottom - 14.dp.toPx()) * (v / maxKm)).toFloat()
            val x = slot * i + (slot - barW) / 2
            drawRoundRect(
                if (i == top) colors.thisYear else colors.thisYear.copy(alpha = 0.45f),
                Offset(x, bottom - h), Size(barW, h), CornerRadius(4.dp.toPx()),
            )
            val vl = measurer.measure(compact(v * unit.perKm), valueStyle)
            drawText(vl, topLeft = Offset(slot * i + slot / 2 - vl.size.width / 2f, bottom - h - vl.size.height - 1.dp.toPx()))
            val l = measurer.measure(names[i], style)
            drawText(l, topLeft = Offset(slot * i + slot / 2 - l.size.width / 2f, bottom + 3.dp.toPx()))
        }
    }
}
