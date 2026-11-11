package app.garminytd.stats

import app.garminytd.data.Ride
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class YtdStatsTest {

    private var nextId = 1L
    private fun ride(date: String, km: Double, indoor: Boolean = false, hours: Double = km / 25) = Ride(
        id = nextId++, name = "r", startLocal = "${date}T10:00:00", distanceM = km * 1000, movingS = hours * 3600,
        elevationGainM = km * 10, avgSpeedMps = null, maxSpeedMps = null, calories = km * 30,
        typeKey = if (indoor) "indoor_cycling" else "road_biking", indoor = indoor,
    )

    @Test
    fun `ytd compares against the same calendar date last year`() {
        val rides = listOf(
            ride("2025-03-01", 50.0), ride("2025-06-01", 100.0), ride("2025-12-01", 70.0),
            ride("2026-02-01", 40.0), ride("2026-05-30", 80.0),
        )
        val s = YtdStats.compute(rides, LocalDate.parse("2026-06-01"))
        assertEquals(120.0, s.ytdKm, 1e-9)
        assertEquals(150.0, s.refSameDateKm, 1e-9) // includes the ride ON 2025-06-01
        assertEquals(220.0, s.refTotalKm, 1e-9)
        assertEquals(-30.0, s.deltaKm, 1e-9)
        assertEquals(-20.0, s.deltaPct!!, 1e-9)
    }

    @Test
    fun `rides after today are ignored`() {
        val s = YtdStats.compute(listOf(ride("2026-06-01", 10.0), ride("2026-06-02", 99.0)), LocalDate.parse("2026-06-01"))
        assertEquals(10.0, s.ytdKm, 1e-9)
    }

    @Test
    fun `leap day maps to Feb 28 of the previous year`() {
        assertEquals(LocalDate.parse("2027-02-28"), YtdStats.sameDayLastYear(LocalDate.parse("2028-02-29")))
        val s = YtdStats.compute(listOf(ride("2027-02-28", 10.0), ride("2028-02-29", 5.0)), LocalDate.parse("2028-02-29"))
        assertEquals(10.0, s.refSameDateKm, 1e-9)
        assertEquals(5.0, s.ytdKm, 1e-9)
    }

    @Test
    fun `days ahead is measured on last year's curve`() {
        // Last year: 100 km on Jan 10 and 100 km on Jan 20. This year: 200 km by Jan 15 -> last year got there Jan 20.
        val rides = listOf(ride("2025-01-10", 100.0), ride("2025-01-20", 100.0), ride("2025-06-01", 100.0), ride("2026-01-05", 200.0))
        val s = YtdStats.compute(rides, LocalDate.parse("2026-01-15"))
        assertEquals(5, s.daysAhead)
    }

    @Test
    fun `seasonal projection scales last year's remainder by this year's ratio`() {
        val rides = listOf(
            ride("2025-02-01", 100.0), ride("2025-08-01", 400.0),
            ride("2026-02-01", 110.0),
        )
        val s = YtdStats.compute(rides, LocalDate.parse("2026-03-01"))
        assertTrue(s.projectionIsSeasonal)
        assertEquals(110.0 + 400.0 * 1.1, s.projectedYearEndKm, 1e-6)
        // 1.1x of last year's pace beats last year's 500 km: find the date (Aug 1 is when the big ride lands).
        assertEquals(LocalDate.parse("2026-08-01"), s.beatRefOn)
        assertNull(s.alreadyBeatRefOn)
    }

    @Test
    fun `behind last year means a weekly target instead of a beat date`() {
        val rides = listOf(ride("2025-02-01", 100.0), ride("2025-08-01", 400.0), ride("2026-02-01", 50.0))
        val s = YtdStats.compute(rides, LocalDate.parse("2026-03-01"))
        assertNull(s.beatRefOn)
        assertNotNull(s.kmPerWeekToBeatRef)
        val weeksLeft = (365 - 60) / 7.0
        assertEquals(450.0 / weeksLeft, s.kmPerWeekToBeatRef!!, 1e-6)
    }

    @Test
    fun `already beating last year reports the date it happened`() {
        val rides = listOf(ride("2025-05-01", 100.0), ride("2026-03-01", 60.0), ride("2026-04-01", 60.0))
        val s = YtdStats.compute(rides, LocalDate.parse("2026-06-01"))
        assertEquals(LocalDate.parse("2026-04-01"), s.alreadyBeatRefOn)
        assertNull(s.daysAhead)
    }

    @Test
    fun `week streak ignores a running week without a ride yet`() {
        // Wed 2026-06-10 today; rides in the 3 previous ISO weeks but not this one.
        val rides = listOf(ride("2026-05-19", 10.0), ride("2026-05-26", 10.0), ride("2026-06-03", 10.0))
        val s = YtdStats.compute(rides, LocalDate.parse("2026-06-10"))
        assertEquals(3, s.currentWeekStreak)
        assertEquals(3, s.longestWeekStreak)
    }

    @Test
    fun `filter separates indoor and outdoor`() {
        val rides = listOf(ride("2026-01-02", 30.0, indoor = true), ride("2026-01-03", 50.0))
        assertEquals(30.0, YtdStats.compute(YtdStats.filter(rides, RideFilter.INDOOR), LocalDate.parse("2026-01-05")).ytdKm, 1e-9)
        assertEquals(50.0, YtdStats.compute(YtdStats.filter(rides, RideFilter.OUTDOOR), LocalDate.parse("2026-01-05")).ytdKm, 1e-9)
    }

    @Test
    fun `score is bounded and full marks need 125 percent`() {
        assertEquals(100, YtdStats.score(125.0, 100.0, 10, 10, 125.0, 100.0).total)
        assertEquals(0, YtdStats.score(0.0, 100.0, 0, 10, 0.0, 100.0).total)
        val half = YtdStats.score(62.5, 100.0, 5, 10, 62.5, 100.0)
        assertEquals(20, half.volume)
        assertEquals(15, half.consistency)
        assertEquals(15, half.momentum)
    }

    @Test
    fun `empty history does not crash`() {
        val s = YtdStats.compute(emptyList(), LocalDate.parse("2026-01-01"))
        assertEquals(0.0, s.ytdKm)
        assertNull(s.deltaPct)
        assertEquals(0, s.score.total)
    }

    @Test
    fun `every day of a leap and a normal year computes with realistic data`() {
        for (year in listOf(2027, 2028, 2029)) {
            val rides = DemoData.rides(LocalDate.of(year, 12, 31))
            var d = LocalDate.of(year, 1, 1)
            while (d.year == year) {
                val s = YtdStats.compute(rides, d)
                assertEquals(d.dayOfYear, s.cumulativeThis.size)
                assertEquals(d.lengthOfYear() - d.dayOfYear + 1, s.projection.size)
                assertTrue(s.score.total in 0..100)
                d = d.plusDays(1)
            }
        }
    }

    @Test
    fun `any earlier year can be the reference`() {
        val rides = listOf(
            ride("2022-03-01", 300.0), ride("2022-09-01", 700.0), // 2022: 300 by Jun 1, 1000 total
            ride("2025-05-01", 100.0),
            ride("2026-05-01", 200.0),
        )
        val s = YtdStats.compute(rides, LocalDate.parse("2026-06-01"), refYear = 2022)
        assertEquals(2022, s.refYear)
        assertEquals(300.0, s.refSameDateKm, 1e-9)
        assertEquals(1000.0, s.refTotalKm, 1e-9)
        assertEquals(-100.0, s.deltaKm, 1e-9)
        assertEquals(listOf(2025, 2022), s.pastYears)
    }

    @Test
    fun `year rows cover every year with rides plus the current one`() {
        val rides = listOf(ride("2019-02-01", 50.0), ride("2019-12-01", 50.0), ride("2024-01-10", 10.0))
        val s = YtdStats.compute(rides, LocalDate.parse("2026-03-01"))
        assertEquals(listOf(2019, 2024, 2026), s.years.map { it.year })
        val y2019 = s.years.first()
        assertEquals(50.0, y2019.sameDateKm, 1e-9)
        assertEquals(100.0, y2019.totalKm, 1e-9)
        assertEquals(365, y2019.cumulative.size)
        assertEquals(0.0, s.years.last().totalKm, 1e-9)
        assertEquals(2019, s.firstYear)
    }

    @Test
    fun `momentum window reaches into the previous december in early january`() {
        // Today Jan 10 2026: window Dec 14 2025..Jan 10 2026; the reference window is Dec 14 2024..Jan 10 2025.
        val rides = listOf(ride("2024-12-20", 40.0), ride("2025-01-05", 10.0), ride("2025-12-20", 30.0))
        val s = YtdStats.compute(rides, LocalDate.parse("2026-01-10"))
        assertEquals(30.0, s.last28Km, 1e-9)
        assertEquals(50.0, s.last28KmRef, 1e-9)
    }
}
