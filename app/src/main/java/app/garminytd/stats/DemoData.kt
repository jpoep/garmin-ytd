package app.garminytd.stats

import app.garminytd.data.Ride
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/**
 * Deterministic, plausible-looking rides for the last [years] years up to [today], with good and bad years:
 * seasonal (summer-heavy), weekend long rides, indoor sessions in winter. Used for the debug-only
 * demo mode and as realistic input in unit tests – never mixed with real Garmin data.
 */
object DemoData {
    fun rides(today: LocalDate, seed: Int = 42, years: Int = 6): List<Ride> {
        val rnd = Random(seed)
        val out = mutableListOf<Ride>()
        var id = 1L
        var d = LocalDate.of(today.year - years + 1, 1, 1)
        while (!d.isAfter(today)) {
            // 0 in mid-January, 1 in mid-July.
            val season = (1 - cos(2 * PI * (d.dayOfYear - 15) / 365.0)) / 2
            val weekend = d.dayOfWeek.value >= 6
            val boost = YEAR_FORM[(today.year - d.year).coerceAtMost(YEAR_FORM.lastIndex)]
            val p = (if (weekend) 0.55 else 0.22) + 0.25 * season
            if (rnd.nextDouble() < p) {
                val indoor = season < 0.3 && rnd.nextDouble() < 0.6
                val km = when {
                    indoor -> 20 + rnd.nextDouble() * 25
                    weekend -> 45 + rnd.nextDouble() * (40 + 80 * season)
                    else -> 18 + rnd.nextDouble() * (20 + 25 * season)
                } * boost
                val speed = if (indoor) 29.0 else 23 + rnd.nextDouble() * 6 // km/h
                val type = when {
                    indoor -> if (rnd.nextBoolean()) "virtual_ride" else "indoor_cycling"
                    rnd.nextDouble() < 0.15 -> "gravel_cycling"
                    rnd.nextDouble() < 0.1 -> "mountain_biking"
                    else -> "road_biking"
                }
                val start = d.atTime(LocalTime.of(if (weekend) 9 else 18, rnd.nextInt(0, 59)))
                out += Ride(
                    id = id++,
                    name = when (type) {
                        "virtual_ride" -> "Zwift session"
                        "indoor_cycling" -> "Trainer intervals"
                        "gravel_cycling" -> "Gravel loop"
                        "mountain_biking" -> "Trail ride"
                        else -> if (weekend) "Weekend long ride" else "After-work spin"
                    },
                    startLocal = start.toString(),
                    distanceM = km * 1000,
                    movingS = km / speed * 3600,
                    elevationGainM = if (indoor) 0.0 else km * (6 + rnd.nextDouble() * 10),
                    avgSpeedMps = speed / 3.6,
                    maxSpeedMps = (speed + 20) / 3.6,
                    calories = km * 28,
                    typeKey = type,
                    indoor = indoor,
                )
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** Relative volume by age: this year, last year, 2 years ago, ... */
    private val YEAR_FORM = listOf(1.12, 1.0, 0.82, 1.18, 0.9, 0.7)
}
