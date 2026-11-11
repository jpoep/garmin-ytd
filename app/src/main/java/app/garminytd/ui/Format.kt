package app.garminytd.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.garminytd.R
import app.garminytd.data.CyclingTypes
import app.garminytd.stats.ScoreTier
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Distance unit; stats are computed in km and converted only for display. */
enum class DistanceUnit(val label: String, val perKm: Double) {
    KM("km", 1.0),
    MI("mi", 0.621371);

    fun distance(km: Double, decimals: Int = 0): String = number(km * perKm, decimals)
    fun withUnit(km: Double, decimals: Int = 0): String = "${distance(km, decimals)} $label"
    fun speed(kmh: Double): String = "${number(kmh * perKm, 1)} ${if (this == KM) "km/h" else "mph"}"
    fun elevation(m: Double): String =
        if (this == KM) "${number(m, 0)} m" else "${number(m * 3.28084, 0)} ft"
}

fun number(value: Double, decimals: Int = 0): String {
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.minimumFractionDigits = decimals
    nf.maximumFractionDigits = decimals
    return nf.format(value)
}

/** "+12", "−3.4"; values that round to zero print as "±0" instead of a misleading "−0". */
fun signed(value: Double, decimals: Int = 0): String {
    val scale = 10.0.pow(decimals)
    val rounded = kotlin.math.round(value * scale) / scale
    return when {
        rounded == 0.0 -> "±" + number(0.0, decimals)
        rounded > 0 -> "+" + number(rounded, decimals)
        else -> "−" + number(abs(rounded), decimals)
    }
}

fun hours(h: Double): String {
    val total = (h * 60).roundToInt()
    return "${total / 60}h ${"%02d".format(total % 60)}m"
}

/** Locale-appropriate pattern for the given fields, e.g. "MMMd" -> "Nov 11" (en) / "11. Nov." (de). */
private fun localized(skeleton: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

fun LocalDate.short(): String = format(localized("MMMd"))
fun LocalDate.long(): String = format(localized("yMMMd"))
fun LocalDate.withWeekday(): String = format(localized("EEEMMMd"))

/** The UI locale, read so that Compose recomposes when it changes (per-app language switch). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun UiMessage.text(): String = stringResource(id, *args.toTypedArray())

/** Localized short name for a Garmin activity type key. */
@Composable
fun rideTypeLabel(typeKey: String): String = when (typeKey) {
    "road_biking" -> stringResource(R.string.type_road)
    "mountain_biking" -> stringResource(R.string.type_mtb)
    "gravel_cycling" -> stringResource(R.string.type_gravel)
    "indoor_cycling" -> stringResource(R.string.type_indoor)
    "virtual_ride" -> stringResource(R.string.type_virtual)
    "cyclocross" -> stringResource(R.string.type_cyclocross)
    "track_cycling" -> stringResource(R.string.type_track)
    "e_bike_fitness", "e_bike_mountain" -> stringResource(R.string.type_ebike)
    "cycling" -> stringResource(R.string.type_ride)
    else -> CyclingTypes.label(typeKey)
}

@Composable
fun scoreTierLabel(tier: ScoreTier): String = stringResource(
    when (tier) {
        ScoreTier.UNSTOPPABLE -> R.string.tier_unstoppable
        ScoreTier.STRONG -> R.string.tier_strong
        ScoreTier.SOLID -> R.string.tier_solid
        ScoreTier.BUILDING -> R.string.tier_building
        ScoreTier.WARMING_UP -> R.string.tier_warming_up
    },
)
