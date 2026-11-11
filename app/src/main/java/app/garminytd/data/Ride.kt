package app.garminytd.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/** One cycling activity, reduced to what the dashboard needs. Pure Kotlin so stats stay JVM-testable. */
@Serializable
data class Ride(
    val id: Long,
    val name: String,
    /** Local start time, ISO-8601 without zone (Garmin's startTimeLocal). */
    val startLocal: String,
    val distanceM: Double,
    val movingS: Double,
    val elevationGainM: Double,
    val avgSpeedMps: Double?,
    val maxSpeedMps: Double?,
    val calories: Double?,
    val typeKey: String,
    val indoor: Boolean,
) {
    val start: LocalDateTime get() = LocalDateTime.parse(startLocal)
    val date: LocalDate get() = start.toLocalDate()
    val km: Double get() = distanceM / 1000.0
}

object CyclingTypes {
    private val indoorKeys = setOf("indoor_cycling", "virtual_ride", "indoor_hand_cycling")
    private const val CYCLING_PARENT_TYPE_ID = 2

    fun isCycling(typeKey: String, parentTypeId: Int?): Boolean =
        parentTypeId == CYCLING_PARENT_TYPE_ID ||
            typeKey == "cycling" ||
            "cycling" in typeKey || "biking" in typeKey || "bike" in typeKey ||
            typeKey == "virtual_ride" || typeKey == "cyclocross" || typeKey == "bmx"

    fun isIndoor(typeKey: String): Boolean =
        typeKey in indoorKeys || "indoor" in typeKey || "virtual" in typeKey

    fun label(typeKey: String): String = when (typeKey) {
        "road_biking" -> "Road"
        "mountain_biking" -> "MTB"
        "gravel_cycling" -> "Gravel"
        "indoor_cycling" -> "Indoor"
        "virtual_ride" -> "Virtual"
        "cyclocross" -> "Cyclocross"
        "track_cycling" -> "Track"
        "e_bike_fitness", "e_bike_mountain" -> "E-bike"
        "cycling" -> "Ride"
        else -> typeKey.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}
