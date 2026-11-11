package app.garminytd.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.garminytd.GarminYtdApp
import app.garminytd.MainActivity
import app.garminytd.R
import app.garminytd.stats.DashboardStats
import app.garminytd.stats.YtdStats
import app.garminytd.ui.DistanceUnit
import app.garminytd.ui.signed
import app.garminytd.ui.theme.rideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Home-screen mirror of the dashboard's cumulative-distance panel: YTD, delta vs the reference year,
 * and the chart. Reads the on-disk ride cache; [WidgetSyncWorker] keeps that cache fresh.
 */
class RideWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as GarminYtdApp).container
        val cache = if (c.auth.isLoggedIn) c.repository.cached() else null
        val unit = c.unit()
        val stats = cache?.let {
            val today = LocalDate.now()
            withContext(Dispatchers.Default) { YtdStats.compute(YtdStats.filter(it.rides, c.filter()), today, c.refYear(today)) }
        }
        provideContent { WidgetContent(stats, unit) }
    }
}

class RideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RideWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSyncWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSyncWorker.cancel(context)
    }
}

// Colours chosen to read on both the light and the dark widget background, since the chart is a
// single bitmap while the background follows the system theme.
private val Bg = ColorProvider(day = Color(0xFFF6F7F9), night = Color(0xFF171C22))
private val Fg = ColorProvider(day = Color(0xFF11161C), night = Color(0xFFE8ECF1))
private val Muted = ColorProvider(day = Color(0xFF5B6675), night = Color(0xFF9AA6B5))
private val ChartLabel = Color(0xFF8A94A3)
private val ChartColors = rideColors(dark = true).copy(
    gridLine = Color(0x338A94A3),
    projection = Color(0xFFF59E5B),
)

@Composable
private fun WidgetContent(s: DashboardStats?, unit: DistanceUnit) {
    val context = LocalContext.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(Bg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (s == null) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(context.getString(R.string.widget_connect), style = TextStyle(color = Muted, fontSize = 14.sp))
            }
        } else {
            Chart(s, unit, context)
        }
    }
}

@Composable
private fun Chart(s: DashboardStats, unit: DistanceUnit, context: Context) {
    val size = LocalSize.current
    val orange = ChartColors.thisYear
    val deltaColor = if (s.deltaKm >= 0) Color(0xFF22C55E) else Color(0xFFEF4444)
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.title_years, s.year, s.refYear),
                style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                signed(s.deltaKm * unit.perKm) + " ${unit.label}" + (s.deltaPct?.let { " · ${signed(it)} %" } ?: ""),
                style = TextStyle(color = ColorProvider(deltaColor), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(unit.distance(s.ytdKm), style = TextStyle(color = ColorProvider(orange), fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Text(" ${unit.label}", style = TextStyle(color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                context.getString(R.string.widget_ref, unit.withUnit(s.refSameDateKm)),
                style = TextStyle(color = Muted, fontSize = 11.sp),
            )
        }
        // Whatever height is left goes to the chart; on a small widget the numbers alone remain.
        val chartHeight = size.height - 96.dp
        if (chartHeight >= 48.dp) {
            Spacer(GlanceModifier.height(4.dp))
            val density = context.resources.displayMetrics.density
            val widthPx = ((size.width - 28.dp).value * density).toInt()
            val heightPx = (chartHeight.value * density).toInt()
            val bmp = ChartBitmap.render(s, unit, ChartColors, ChartLabel, widthPx, heightPx, density)
            Image(
                provider = ImageProvider(bmp),
                contentDescription = context.getString(R.string.cumulative_title),
                modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
            )
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend("${s.year}", orange)
                Legend("${s.refYear}", ChartColors.lastYear)
                Legend(context.getString(R.string.projection_title), ChartColors.projection)
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Text("● ", style = TextStyle(color = ColorProvider(color), fontSize = 10.sp))
    Text("$label   ", style = TextStyle(color = Muted, fontSize = 10.sp))
}
