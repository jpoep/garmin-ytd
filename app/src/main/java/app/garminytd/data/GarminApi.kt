package app.garminytd.data

import app.garminytd.auth.GarminAuth
import app.garminytd.auth.GarminAuthException
import app.garminytd.auth.GarminConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate

class GarminApi(
    private val http: OkHttpClient,
    private val auth: GarminAuth,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Name to greet the user with; also doubles as a "does the token work" check right after login. */
    suspend fun displayName(): String? {
        val obj = json.parseToJsonElement(get(url("/userprofile-service/socialProfile"))).jsonObject
        return obj.string("fullName")?.takeIf { it.isNotBlank() } ?: obj.string("displayName")
    }

    /**
     * All cycling activities with a start date in [from]..[to].
     * Fetches every activity type and filters locally, because Garmin's server-side `activityType`
     * filter is not documented to include sub-types (gravel, MTB, virtual, e-bike...).
     */
    suspend fun cyclingRides(from: LocalDate, to: LocalDate, onProgress: (checked: Int) -> Unit = {}): List<Ride> {
        val rides = mutableListOf<Ride>()
        var start = 0
        while (true) {
            val page = url("/activitylist-service/activities/search/activities").newBuilder()
                .addQueryParameter("startDate", from.toString())
                .addQueryParameter("endDate", to.toString())
                .addQueryParameter("start", start.toString())
                .addQueryParameter("limit", PAGE_SIZE.toString())
                .build()
            val items = activityArray(json.parseToJsonElement(get(page)))
            items.mapNotNullTo(rides) { parseRide(it) }
            onProgress(start + items.size)
            if (items.size < PAGE_SIZE || start > MAX_ACTIVITIES) break
            start += PAGE_SIZE
        }
        return rides
    }

    private fun activityArray(el: JsonElement): JsonArray = when (el) {
        is JsonArray -> el
        is JsonObject -> (el["activityList"] as? JsonArray) ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }

    private fun parseRide(el: JsonElement): Ride? {
        val o = el as? JsonObject ?: return null
        val type = o["activityType"] as? JsonObject
        val typeKey = type?.string("typeKey") ?: return null
        val parentId = (type["parentTypeId"] as? JsonPrimitive)?.intOrNull
        if (!CyclingTypes.isCycling(typeKey, parentId)) return null
        val startLocal = o.string("startTimeLocal")?.replace(' ', 'T') ?: return null
        return Ride(
            id = (o["activityId"] as? JsonPrimitive)?.longOrNull ?: return null,
            name = o.string("activityName") ?: CyclingTypes.label(typeKey),
            startLocal = startLocal,
            distanceM = o.double("distance") ?: 0.0,
            movingS = o.double("movingDuration") ?: o.double("duration") ?: 0.0,
            elevationGainM = o.double("elevationGain") ?: 0.0,
            avgSpeedMps = o.double("averageSpeed"),
            maxSpeedMps = o.double("maxSpeed"),
            calories = o.double("calories"),
            typeKey = typeKey,
            indoor = CyclingTypes.isIndoor(typeKey),
        )
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        var token = auth.validAccessToken()
        repeat(2) { attempt ->
            http.newCall(request(url, token)).execute().use { resp ->
                when {
                    resp.isSuccessful -> return@withContext resp.body.string()
                    resp.code == 401 && attempt == 0 -> token = auth.forceRefresh()
                    resp.code == 427 || resp.code == 429 -> throw GarminAuthException.Blocked(resp.code)
                    resp.code == 401 -> throw GarminAuthException.SessionExpired()
                    resp.code == 403 -> throw GarminAuthException.Failed("HTTP 403")
                    else -> throw GarminAuthException.Failed("HTTP ${resp.code}")
                }
            }
        }
        throw GarminAuthException.SessionExpired()
    }

    private fun request(url: HttpUrl, token: String) = Request.Builder()
        .url(url)
        .apply { GarminConfig.NATIVE_HEADERS.forEach { (k, v) -> header(k, v) } }
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .build()

    private fun url(path: String) = (GarminConfig.CONNECT_API + path).toHttpUrl()

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.double(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull

    private companion object {
        const val PAGE_SIZE = 100
        /** All activity types count (runs, walks...), so a long Garmin history can be large. */
        const val MAX_ACTIVITIES = 50_000
    }
}
