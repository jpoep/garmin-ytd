package app.garminytd.stats

import app.garminytd.data.Ride
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class RideFilter { ALL, OUTDOOR, INDOOR }

data class PeriodSummary(
    val rides: Int,
    val km: Double,
    val movingHours: Double,
    val elevationM: Double,
    val activeDays: Int,
    val longestKm: Double,
    val calories: Double,
) {
    val avgKmPerRide: Double get() = if (rides == 0) 0.0 else km / rides
    /** Distance-weighted: total distance over total moving time. */
    val avgSpeedKmh: Double get() = if (movingHours <= 0.0) 0.0 else km / movingHours
}

data class MonthBar(val month: Int, val thisKm: Double, val refKm: Double, val isFuture: Boolean)

enum class ScoreTier { WARMING_UP, BUILDING, SOLID, STRONG, UNSTOPPABLE }

data class Score(val total: Int, val volume: Int, val consistency: Int, val momentum: Int) {
    val tier: ScoreTier get() = when {
        total >= 90 -> ScoreTier.UNSTOPPABLE
        total >= 75 -> ScoreTier.STRONG
        total >= 60 -> ScoreTier.SOLID
        total >= 40 -> ScoreTier.BUILDING
        else -> ScoreTier.WARMING_UP
    }
}

/** One calendar year of history, compared at the same calendar date as today. */
data class YearRow(
    val year: Int,
    /** Distance from Jan 1 through the same calendar date as today (for the current year: YTD). */
    val sameDateKm: Double,
    /** Full-year distance; for the current year, distance so far. */
    val totalKm: Double,
    /** Index = day-of-year - 1; full year, or through today for the current year. */
    val cumulative: List<Double>,
)

data class DashboardStats(
    val today: LocalDate,
    val year: Int,
    /** The year everything is compared against (last year by default, any year with data on request). */
    val refYear: Int,
    val ytdKm: Double,
    val refSameDateKm: Double,
    val refTotalKm: Double,
    /** Days ahead (+) / behind (-) of the reference year's cumulative curve; null when not meaningful. */
    val daysAhead: Int?,
    /** Index = day-of-year - 1, through today. */
    val cumulativeThis: List<Double>,
    /** Index = day-of-year - 1, full reference year. */
    val cumulativeRef: List<Double>,
    /** Projected cumulative km from today to Dec 31 (index continues after [cumulativeThis]). */
    val projection: List<Double>,
    val projectedYearEndKm: Double,
    val projectionIsSeasonal: Boolean,
    val beatRefOn: LocalDate?,
    val alreadyBeatRefOn: LocalDate?,
    val kmPerWeekToBeatRef: Double?,
    val thisYear: PeriodSummary,
    val refSamePeriod: PeriodSummary,
    val monthly: List<MonthBar>,
    /** Monday..Sunday, current year. */
    val weekdayKm: List<Double>,
    /** Index = day-of-year - 1, current year. */
    val dailyKm: List<Double>,
    val last28Km: Double,
    val last28KmRef: Double,
    val bestWeekKm: Double,
    val bestWeekStart: LocalDate?,
    val currentWeekStreak: Int,
    val longestWeekStreak: Int,
    val activeWeeks: Int,
    val elapsedWeeks: Int,
    val score: Score,
    val recent: List<Ride>,
    /** Every year with at least one ride, plus the current year; ascending. */
    val years: List<YearRow>,
) {
    val deltaKm: Double get() = ytdKm - refSameDateKm
    val deltaPct: Double? get() = if (refSameDateKm > 0) deltaKm / refSameDateKm * 100 else null
    val firstYear: Int get() = years.firstOrNull()?.year ?: year
    /** Years other than the current one, newest first: the choices for [refYear]. */
    val pastYears: List<Int> get() = years.map { it.year }.filter { it != year }.sortedDescending()
    val kmPerWeek: Double get() = ytdKm / max(1.0, (today.dayOfYear) / 7.0)
}

object YtdStats {

    fun filter(rides: List<Ride>, filter: RideFilter): List<Ride> = when (filter) {
        RideFilter.ALL -> rides
        RideFilter.OUTDOOR -> rides.filter { !it.indoor }
        RideFilter.INDOOR -> rides.filter { it.indoor }
    }

    fun compute(allRides: List<Ride>, today: LocalDate, refYear: Int = today.year - 1): DashboardStats {
        val year = today.year
        require(refYear < year) { "reference year must be in the past" }
        val rides = allRides.filter { it.date <= today }
        val thisYearRides = rides.filter { it.date.year == year }
        val refRides = rides.filter { it.date.year == refYear }

        // Same calendar date in the reference year (Feb 29 -> Feb 28).
        val refSameDate = sameDayIn(refYear, today)

        val dailyThis = dailyKm(thisYearRides, year)
        val dailyRef = dailyKm(refRides, refYear)
        val cumThisFull = dailyThis.runningSum()
        val cumRef = dailyRef.runningSum()
        val todayIdx = today.dayOfYear - 1
        val cumThis = cumThisFull.subList(0, todayIdx + 1)

        val ytd = cumThis.last()
        val refSame = cumRef[refSameDate.dayOfYear - 1]
        val refTotal = cumRef.last()

        // --- Projection: scale the reference year's remaining season by this year's ratio (falls back to linear pace).
        val yearLen = today.lengthOfYear()
        val seasonal = refSame > 0 && refTotal > refSame
        val ratio = if (refSame > 0) ytd / refSame else 0.0
        val projection = (todayIdx until yearLen).map { d ->
            if (seasonal) {
                val lastIdxNow = refSameDate.dayOfYear - 1
                val lastIdx = min(d + (lastIdxNow - todayIdx), cumRef.size - 1)
                ytd + (cumRef[lastIdx] - cumRef[lastIdxNow]) * ratio
            } else {
                ytd / (todayIdx + 1) * (d + 1)
            }
        }
        val projectedEnd = projection.last()

        // --- When do we pass the reference year's total?
        val alreadyBeat = if (refTotal > 0) {
            cumThis.indexOfFirst { it > refTotal }.takeIf { it >= 0 }?.let { LocalDate.ofYearDay(year, it + 1) }
        } else null
        val beatOn = if (alreadyBeat == null && refTotal > 0) {
            projection.indexOfFirst { it > refTotal }.takeIf { it >= 0 }
                ?.let { LocalDate.ofYearDay(year, todayIdx + it + 1) }
        } else null
        val weeksLeft = (yearLen - today.dayOfYear) / 7.0
        val perWeekToBeat = if (alreadyBeat == null && refTotal > ytd && weeksLeft > 0) (refTotal - ytd) / weeksLeft else null

        // --- Days ahead/behind: on which day of the reference year had we ridden what we have ridden now?
        val daysAhead = when {
            ytd <= 0 || refTotal <= 0 || ytd > refTotal -> null
            else -> {
                val reachedIdx = cumRef.indexOfFirst { it >= ytd }
                reachedIdx - (refSameDate.dayOfYear - 1)
            }
        }

        // --- Monthly comparison.
        val monthly = (1..12).map { m ->
            MonthBar(
                month = m,
                thisKm = thisYearRides.filter { it.date.monthValue == m }.sumOf { it.km },
                refKm = refRides.filter { it.date.monthValue == m }.sumOf { it.km },
                isFuture = m > today.monthValue,
            )
        }

        val weekday = DayOfWeek.entries.map { dow -> thisYearRides.filter { it.date.dayOfWeek == dow }.sumOf { it.km } }

        val last28Start = today.minusDays(27)
        val last28 = rides.filter { it.date in last28Start..today }.sumOf { it.km }
        // Shift the whole window by the year gap, so early January also reaches back into the December before.
        val gap = year - refYear
        val last28Ref = rides.filter { it.date in sameDayIn(last28Start.year - gap, last28Start)..refSameDate }.sumOf { it.km }

        // --- Weeks (ISO, Monday start).
        val weekKm = rides.groupBy { weekStart(it.date) }.mapValues { (_, r) -> r.sumOf { it.km } }
        val thisYearWeeks = weekKm.filterKeys { it.year == year || weekStart(LocalDate.of(year, 1, 1)) == it }
        val best = thisYearWeeks.maxByOrNull { it.value }
        val currentWeek = weekStart(today)
        val firstWeek = weekStart(LocalDate.of(year, 1, 1))
        // The running week only counts once it has a ride, so a quiet Monday doesn't punish you.
        val lastCompleteOrActive = if (weekKm.containsKey(currentWeek)) currentWeek else currentWeek.minusWeeks(1)
        val elapsedWeeks = (ChronoUnit.WEEKS.between(firstWeek, lastCompleteOrActive) + 1).toInt().coerceAtLeast(1)
        val activeWeeks = generateSequence(firstWeek) { it.plusWeeks(1) }
            .takeWhile { it <= lastCompleteOrActive }
            .count { weekKm.containsKey(it) }
        val currentStreak = generateSequence(lastCompleteOrActive) { it.minusWeeks(1) }
            .takeWhile { weekKm.containsKey(it) }
            .count()
        val longestStreak = longestWeekStreak(weekKm.keys)

        val score = score(ytd, refSame, activeWeeks, elapsedWeeks, last28, last28Ref)

        return DashboardStats(
            today = today,
            year = year,
            refYear = refYear,
            ytdKm = ytd,
            refSameDateKm = refSame,
            refTotalKm = refTotal,
            daysAhead = daysAhead,
            cumulativeThis = cumThis,
            cumulativeRef = cumRef,
            projection = projection,
            projectedYearEndKm = projectedEnd,
            projectionIsSeasonal = seasonal,
            beatRefOn = beatOn,
            alreadyBeatRefOn = alreadyBeat,
            kmPerWeekToBeatRef = perWeekToBeat,
            thisYear = summarize(thisYearRides),
            refSamePeriod = summarize(refRides.filter { it.date <= refSameDate }),
            monthly = monthly,
            weekdayKm = weekday,
            dailyKm = dailyThis,
            last28Km = last28,
            last28KmRef = last28Ref,
            bestWeekKm = best?.value ?: 0.0,
            bestWeekStart = best?.key,
            currentWeekStreak = currentStreak,
            longestWeekStreak = longestStreak,
            activeWeeks = activeWeeks,
            elapsedWeeks = elapsedWeeks,
            score = score,
            recent = rides.sortedByDescending { it.startLocal }.take(5),
            years = yearRows(rides, today, cumThis),
        )
    }

    private fun yearRows(rides: List<Ride>, today: LocalDate, cumThis: List<Double>): List<YearRow> {
        val byYear = rides.groupBy { it.date.year }
        return (byYear.keys + today.year).sorted().map { y ->
            if (y == today.year) {
                YearRow(y, cumThis.last(), cumThis.last(), cumThis)
            } else {
                val cum = dailyKm(byYear[y].orEmpty(), y).runningSum()
                YearRow(y, cum[sameDayIn(y, today).dayOfYear - 1], cum.last(), cum)
            }
        }
    }

    /**
     * 0–100 "form score":
     *  - Volume (40): YTD vs the reference year to the same date; 125 % of it earns full marks.
     *  - Consistency (30): share of weeks this year with at least one ride.
     *  - Momentum (30): last 28 days vs the same 28 days of the reference year; 125 % earns full marks.
     */
    internal fun score(ytd: Double, refSame: Double, activeWeeks: Int, elapsedWeeks: Int, last28: Double, last28Ref: Double): Score {
        fun ratioPoints(now: Double, before: Double, maxPts: Int): Int {
            val ratio = when {
                before > 0 -> now / before
                now > 0 -> FULL_MARKS_RATIO
                else -> 0.0
            }
            return (maxPts * min(ratio / FULL_MARKS_RATIO, 1.0)).roundToInt()
        }
        val volume = ratioPoints(ytd, refSame, 40)
        val consistency = (30.0 * activeWeeks / max(1, elapsedWeeks)).roundToInt().coerceIn(0, 30)
        val momentum = ratioPoints(last28, last28Ref, 30)
        return Score(volume + consistency + momentum, volume, consistency, momentum)
    }

    private const val FULL_MARKS_RATIO = 1.25

    fun summarize(rides: List<Ride>) = PeriodSummary(
        rides = rides.size,
        km = rides.sumOf { it.km },
        movingHours = rides.sumOf { it.movingS } / 3600.0,
        elevationM = rides.sumOf { it.elevationGainM },
        activeDays = rides.map { it.date }.distinct().size,
        longestKm = rides.maxOfOrNull { it.km } ?: 0.0,
        calories = rides.sumOf { it.calories ?: 0.0 },
    )

    fun sameDayLastYear(date: LocalDate): LocalDate = sameDayIn(date.year - 1, date)

    /** The same calendar date in [year]; Feb 29 maps to Feb 28 in non-leap years. */
    fun sameDayIn(year: Int, date: LocalDate): LocalDate {
        val month = date.month
        val len = month.length(java.time.Year.isLeap(year.toLong()))
        return LocalDate.of(year, month, min(date.dayOfMonth, len))
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun dailyKm(rides: List<Ride>, year: Int): List<Double> {
        val arr = DoubleArray(java.time.Year.of(year).length())
        rides.forEach { arr[it.date.dayOfYear - 1] += it.km }
        return arr.toList()
    }

    private fun List<Double>.runningSum(): List<Double> {
        var acc = 0.0
        return map { acc += it; acc }
    }

    private fun longestWeekStreak(weeks: Set<LocalDate>): Int {
        var best = 0
        for (w in weeks) {
            if (w.minusWeeks(1) in weeks) continue // not the start of a run
            var len = 1
            var next = w.plusWeeks(1)
            while (next in weeks) { len++; next = next.plusWeeks(1) }
            best = max(best, len)
        }
        return best
    }
}
