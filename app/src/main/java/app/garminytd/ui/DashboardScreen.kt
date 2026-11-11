package app.garminytd.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.garminytd.R
import app.garminytd.data.Ride
import app.garminytd.stats.DashboardStats
import app.garminytd.stats.PeriodSummary
import app.garminytd.stats.RideFilter
import app.garminytd.stats.YearRow
import app.garminytd.stats.YtdStats
import app.garminytd.ui.charts.CumulativeChart
import app.garminytd.ui.charts.LegendDot
import app.garminytd.ui.charts.MonthlyBars
import app.garminytd.ui.charts.ScoreRing
import app.garminytd.ui.charts.WeekdayBars
import app.garminytd.ui.charts.YearHeatmap
import app.garminytd.ui.theme.NumberStyle
import app.garminytd.ui.theme.RideTheme
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onFullResync: () -> Unit,
    onFilter: (RideFilter) -> Unit,
    onRefYear: (Int) -> Unit,
    onToggleUnit: () -> Unit,
    onLogout: () -> Unit,
    onExitDemo: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.text()
    LaunchedEffect(state.message) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(
                            state.stats?.let { stringResource(R.string.title_years, it.year, it.refYear) }
                                ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            subtitle(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onToggleUnit) { Text(state.unit.label.uppercase()) }
                    IconButton(onClick = onRefresh, enabled = !state.syncing && !state.demo) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.action_refresh))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (state.demo) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.menu_exit_demo)) }, onClick = { menu = false; onExitDemo() })
                            } else {
                                DropdownMenuItem(text = { Text(stringResource(R.string.menu_resync)) }, onClick = { menu = false; onFullResync() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.menu_disconnect)) }, onClick = { menu = false; onLogout() })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val stats = state.stats
            if (stats == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.loading_rides), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.syncProgress?.let {
                            Text(
                                pluralStringResource(R.plurals.loading_progress, it, it),
                                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                DashboardContent(stats, state.unit, state.filter, onFilter, onRefYear)
            }
        }
    }
}

@Composable
private fun subtitle(state: UiState): String {
    val who = state.displayName ?: if (state.demo) stringResource(R.string.demo_rider) else "Garmin Connect"
    val synced = when {
        state.demo -> stringResource(R.string.sub_sample_data)
        state.syncing -> stringResource(R.string.sub_syncing)
        state.lastSyncMillis != null -> {
            val locale = currentLocale()
            // Short form ("5 min. ago"), lower-cased because it sits mid-sentence ("Vor" -> "vor" in German).
            val ago = DateUtils.getRelativeTimeSpanString(
                state.lastSyncMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString().replaceFirstChar { it.lowercase(locale) }
            stringResource(R.string.sub_synced, ago)
        }
        else -> null
    }
    // Sync state first: the name (sometimes Garmin only has the e-mail address) is what gets truncated.
    return listOfNotNull(synced, who).joinToString(" · ")
}

@Composable
private fun DashboardContent(
    s: DashboardStats,
    unit: DistanceUnit,
    filter: RideFilter,
    onFilter: (RideFilter) -> Unit,
    onRefYear: (Int) -> Unit,
) {
    // Phones use the full width; foldables/tablets get a readable centered column.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 720.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Selectors(s, filter, onFilter, onRefYear) }
        item { HeroCard(s, unit) }
        item {
            SectionCard(stringResource(R.string.cumulative_title), stringResource(R.string.cumulative_sub)) {
                CumulativeChart(s, unit)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(RideTheme.colors.thisYear); Text(" ${s.year}  ", style = MaterialTheme.typography.labelSmall)
                    LegendDot(RideTheme.colors.lastYear); Text(" ${s.refYear}  ", style = MaterialTheme.typography.labelSmall)
                    LegendDot(RideTheme.colors.projection)
                    Text(
                        " " + stringResource(if (s.projectionIsSeasonal) R.string.legend_projection_seasonal else R.string.legend_projection_linear),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (s.years.size > 2) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(RideTheme.colors.lastYear.copy(alpha = 0.35f))
                        Text(" " + stringResource(R.string.legend_other_years), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { EveryYearCard(s, unit, onRefYear) }
        item { ScoreAndProjection(s, unit) }
        item {
            SectionCard(stringResource(R.string.monthly_title), stringResource(R.string.monthly_sub)) {
                MonthlyBars(s.monthly, s.year, s.refYear, unit)
            }
        }
        item { StatGrid(s.thisYear, s.refSamePeriod, s, unit) }
        item { StreakCard(s, unit) }
        item {
            SectionCard(stringResource(R.string.heatmap_title, s.year), stringResource(R.string.heatmap_sub)) {
                YearHeatmap(s, unit)
            }
        }
        item {
            SectionCard(stringResource(R.string.weekday_title), stringResource(R.string.weekday_sub, s.year)) {
                WeekdayBars(s.weekdayKm, unit)
            }
        }
        item { FunFacts(s, unit) }
        if (s.recent.isNotEmpty()) item { RecentRides(s.recent, unit) }
    }
    }
}

/** Ride-type filter, and which past year everything is compared against. */
@Composable
private fun Selectors(s: DashboardStats, filter: RideFilter, onFilter: (RideFilter) -> Unit, onRefYear: (Int) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RideFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { onFilter(f) },
                    label = {
                        Text(
                            stringResource(
                                when (f) {
                                    RideFilter.ALL -> R.string.filter_all
                                    RideFilter.OUTDOOR -> R.string.filter_outdoor
                                    RideFilter.INDOOR -> R.string.filter_indoor
                                },
                            ),
                        )
                    },
                )
            }
        }
        // Always offer last year, plus every earlier year that has rides.
        val choices = (s.pastYears + (s.year - 1) + s.refYear).distinct().sortedDescending()
        if (choices.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.compare_with),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(choices, key = { it }) { y ->
                        FilterChip(selected = y == s.refYear, onClick = { onRefYear(y) }, label = { Text(y.toString()) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(s: DashboardStats, unit: DistanceUnit) {
    val colors = RideTheme.colors
    val up = s.deltaKm >= 0
    val deltaColor = if (up) colors.positive else colors.negative
    SectionCard(null, null) {
        Text(stringResource(R.string.hero_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(unit.distance(s.ytdKm), style = MaterialTheme.typography.displayLarge.merge(NumberStyle), color = colors.thisYear)
            Text(" ${unit.label}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
        }
        Text(
            stringResource(R.string.hero_vs, unit.withUnit(s.refSameDateKm), YtdStats.sameDayIn(s.refYear, s.today).long()),
            style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(deltaColor) {
                Icon(
                    painterResource(R.drawable.ic_trending_up), null, tint = deltaColor,
                    modifier = Modifier.size(16.dp).rotate(if (up) 0f else 90f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    signed(s.deltaKm * unit.perKm) + " ${unit.label}" + (s.deltaPct?.let { " · ${signed(it, 1)} %" } ?: ""),
                    style = MaterialTheme.typography.labelLarge.merge(NumberStyle), color = deltaColor, fontWeight = FontWeight.SemiBold,
                )
            }
            s.daysAhead?.let { d ->
                Text(
                    when {
                        d > 0 -> pluralStringResource(R.plurals.days_ahead, d, d)
                        d < 0 -> pluralStringResource(R.plurals.days_behind, abs(d), abs(d))
                        else -> stringResource(R.string.dead_even)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        s.alreadyBeatRefOn?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.hero_beaten, s.refYear, unit.withUnit(s.refTotalKm), it.short()),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Every year with data at today's calendar date, plus how this year (and its projection) ranks. */
@Composable
private fun EveryYearCard(s: DashboardStats, unit: DistanceUnit, onRefYear: (Int) -> Unit) {
    val colors = RideTheme.colors
    SectionCard(stringResource(R.string.years_title), stringResource(R.string.years_sub, s.today.short())) {
        val past = s.years.filter { it.year != s.year }
        if (past.isEmpty()) {
            Text(stringResource(R.string.years_none), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val count = s.years.size
        val rank = past.count { it.sameDateKm > s.ytdKm } + 1
        val projectedRank = past.count { it.totalKm > s.projectedYearEndKm } + 1
        val best = past.maxBy { it.totalKm }
        Text(stringResource(R.string.years_rank, rank, count), style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
        Text(
            stringResource(R.string.years_projected_rank, projectedRank, count),
            style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.years_best, best.year, unit.withUnit(best.totalKm)),
            style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val maxKm = s.years.maxOf { it.sameDateKm }.coerceAtLeast(1.0)
        s.years.sortedByDescending { it.year }.forEach { row ->
            val current = row.year == s.year
            val selected = row.year == s.refYear
            YearBar(
                row = row,
                fraction = (row.sameDateKm / maxKm).toFloat(),
                barColor = when {
                    current -> colors.thisYear
                    selected -> colors.lastYear
                    else -> colors.lastYear.copy(alpha = 0.4f)
                },
                highlight = current || selected,
                detail = if (current) {
                    stringResource(R.string.years_projected, unit.withUnit(s.projectedYearEndKm))
                } else {
                    stringResource(R.string.years_full, unit.withUnit(row.totalKm))
                },
                unit = unit,
                onClick = if (current) null else ({ onRefYear(row.year) }),
            )
        }
    }
}

@Composable
private fun YearBar(
    row: YearRow,
    fraction: Float,
    barColor: Color,
    highlight: Boolean,
    detail: String,
    unit: DistanceUnit,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.year.toString(),
            style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(44.dp),
        )
        Box(Modifier.weight(1f).height(14.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .background(barColor, RoundedCornerShape(4.dp)),
            )
        }
        Column(Modifier.width(120.dp), horizontalAlignment = Alignment.End) {
            Text(
                unit.withUnit(row.sameDateKm),
                style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ScoreAndProjection(s: DashboardStats, unit: DistanceUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.score_title), null, Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ScoreRing(s.score) }
            Spacer(Modifier.height(8.dp))
            ScoreLine(stringResource(R.string.score_volume), s.score.volume, 40)
            ScoreLine(stringResource(R.string.score_consistency), s.score.consistency, 30)
            ScoreLine(stringResource(R.string.score_momentum), s.score.momentum, 30)
        }
        SectionCard(stringResource(R.string.projection_title), null, Modifier.weight(1f)) {
            Text(stringResource(R.string.projection_finish, s.year), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(unit.withUnit(s.projectedYearEndKm), style = MaterialTheme.typography.headlineSmall.merge(NumberStyle), color = RideTheme.colors.projection)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.projection_last_total, s.refYear), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(unit.withUnit(s.refTotalKm), style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
            Spacer(Modifier.height(10.dp))
            val verdict = when {
                s.alreadyBeatRefOn != null -> stringResource(R.string.verdict_beaten)
                s.beatRefOn != null -> stringResource(R.string.verdict_pass_on, s.refYear, s.beatRefOn.short())
                s.kmPerWeekToBeatRef != null -> stringResource(R.string.verdict_per_week, unit.withUnit(s.kmPerWeekToBeatRef), s.refYear)
                else -> stringResource(R.string.verdict_no_data, s.refYear)
            }
            Text(verdict, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScoreLine(label: String, pts: Int, max: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text("$pts/$max", style = MaterialTheme.typography.labelMedium.merge(NumberStyle))
    }
}

@Composable
private fun StatGrid(now: PeriodSummary, before: PeriodSummary, s: DashboardStats, unit: DistanceUnit) {
    val weeks = maxOf(1.0, s.today.dayOfYear / 7.0)
    val tiles = listOf(
        Tile(R.string.stat_rides, now.rides.toString(), before.rides.toString(), now.rides.toDouble(), before.rides.toDouble()),
        Tile(R.string.stat_avg_ride, unit.withUnit(now.avgKmPerRide, 1), unit.withUnit(before.avgKmPerRide, 1), now.avgKmPerRide, before.avgKmPerRide),
        Tile(R.string.stat_avg_speed, unit.speed(now.avgSpeedKmh), unit.speed(before.avgSpeedKmh), now.avgSpeedKmh, before.avgSpeedKmh),
        Tile(R.string.stat_climbing, unit.elevation(now.elevationM), unit.elevation(before.elevationM), now.elevationM, before.elevationM),
        Tile(R.string.stat_saddle_time, hours(now.movingHours), hours(before.movingHours), now.movingHours, before.movingHours),
        Tile(R.string.stat_longest, unit.withUnit(now.longestKm, 1), unit.withUnit(before.longestKm, 1), now.longestKm, before.longestKm),
        Tile(R.string.stat_per_week, unit.withUnit(s.kmPerWeek, 1), unit.withUnit(before.km / weeks, 1), s.kmPerWeek, before.km / weeks),
        Tile(R.string.stat_active_days, now.activeDays.toString(), before.activeDays.toString(), now.activeDays.toDouble(), before.activeDays.toDouble()),
    )
    SectionCard(stringResource(R.string.numbers_title), stringResource(R.string.numbers_sub, s.refYear)) {
        tiles.chunked(2).forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { StatTile(it, s.refYear, Modifier.weight(1f)) }
            }
        }
    }
}

private data class Tile(val label: Int, val now: String, val before: String, val nowValue: Double, val beforeValue: Double)

@Composable
private fun StatTile(t: Tile, refYear: Int, modifier: Modifier) {
    val colors = RideTheme.colors
    Column(modifier) {
        Text(stringResource(t.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(t.now, style = MaterialTheme.typography.titleLarge.merge(NumberStyle), fontWeight = FontWeight.Bold)
        val pct = if (t.beforeValue > 0) (t.nowValue - t.beforeValue) / t.beforeValue * 100 else null
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stat_ref, refYear, t.before),
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pct?.let {
                Text(
                    "  ${signed(it)} %",
                    style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                    color = when {
                        it >= 0.5 -> colors.positive
                        it <= -0.5 -> colors.negative
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun StreakCard(s: DashboardStats, unit: DistanceUnit) {
    SectionCard(stringResource(R.string.streaks_title), null) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigNumber(
                "${s.currentWeekStreak}", stringResource(R.string.streak_current),
                stringResource(R.string.streak_current_sub), Modifier.weight(1f),
            )
            BigNumber(
                "${s.longestWeekStreak}", stringResource(R.string.streak_longest),
                stringResource(R.string.streak_longest_sub, s.firstYear), Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigNumber(
                unit.distance(s.bestWeekKm), stringResource(R.string.best_week, unit.label),
                s.bestWeekStart?.let { stringResource(R.string.best_week_sub, it.short()) } ?: "–", Modifier.weight(1f),
            )
            BigNumber(
                "${s.activeWeeks}/${s.elapsedWeeks}", stringResource(R.string.active_weeks),
                stringResource(R.string.active_weeks_sub, unit.withUnit(s.last28Km), s.refYear, unit.withUnit(s.last28KmRef)),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BigNumber(value: String, label: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium.merge(NumberStyle), fontWeight = FontWeight.Black, color = RideTheme.colors.thisYear)
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FunFacts(s: DashboardStats, unit: DistanceUnit) {
    val km = s.ytdKm
    val t = s.thisYear
    val facts = listOf(
        "🌍" to stringResource(R.string.fact_earth, number(km / EARTH_KM * 100, 1)),
        "🇫🇷" to stringResource(R.string.fact_tdf, number(km / TDF_KM, 2)),
        "🏔️" to stringResource(R.string.fact_everest, number(t.elevationM / EVEREST_M, 1), unit.elevation(t.elevationM)),
        "🔥" to stringResource(R.string.fact_pizza, number(t.calories, 0), number(t.calories / PIZZA_KCAL, 0)),
        "⏱️" to stringResource(R.string.fact_saddle, hours(t.movingHours), number(t.movingHours / 24, 1)),
    )
    SectionCard(stringResource(R.string.facts_title), null) {
        facts.forEach { (emoji, text) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, modifier = Modifier.width(32.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium.merge(NumberStyle))
            }
        }
    }
}

private const val EARTH_KM = 40_075.0
private const val TDF_KM = 3_338.0 // 2025 edition
private const val EVEREST_M = 8_849.0
private const val PIZZA_KCAL = 2_000.0

@Composable
private fun RecentRides(rides: List<Ride>, unit: DistanceUnit) {
    SectionCard(stringResource(R.string.recent_title), null) {
        rides.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${r.date.withWeekday()} · ${rideTypeLabel(r.typeKey)}" +
                            (r.avgSpeedMps?.let { " · ${unit.speed(it * 3.6)}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(unit.withUnit(r.km, 1), style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
            }
        }
    }
}

@Composable
private fun Pill(color: Color, content: @Composable () -> Unit) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun SectionCard(title: String?, subtitle: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}
