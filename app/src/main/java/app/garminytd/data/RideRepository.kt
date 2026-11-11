package app.garminytd.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
data class RideCache(
    val rides: List<Ride>,
    /** Last date (inclusive) the cache is known to be complete through. */
    val syncedThrough: String,
    val syncedAtMillis: Long,
    val displayName: String? = null,
    /** First date the cache covers; caches from before full-history sync have none and get re-downloaded. */
    val historyFrom: String? = null,
)

/**
 * The whole cycling history since [HISTORY_START], cached on disk so the dashboard opens instantly
 * and refreshes only re-download what can still change (the current year).
 */
class RideRepository(context: Context, private val api: GarminApi) {
    private val file = File(context.filesDir, "rides.json")
    private val json = Json { ignoreUnknownKeys = true }
    /** The app and the widget's background sync can overlap; only one writes the cache at a time. */
    private val lock = Mutex()

    suspend fun cached(): RideCache? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(RideCache.serializer(), file.readText()) }.getOrNull()
    }

    suspend fun sync(today: LocalDate, full: Boolean, onProgress: (checked: Int) -> Unit = {}): RideCache = lock.withLock {
        val previous = if (full) null else cached()?.takeIf { it.historyFrom == HISTORY_START.toString() }
        val coverageStart = HISTORY_START
        // Re-fetch from Jan 1 of whichever year the last sync ended in (handles the New Year rollover).
        val fetchFrom = previous?.let {
            val through = LocalDate.parse(it.syncedThrough)
            maxOf(coverageStart, LocalDate.of(minOf(through.year, today.year), 1, 1))
        } ?: coverageStart

        val fresh = api.cyclingRides(fetchFrom, today, onProgress)
        val kept = previous?.rides.orEmpty().filter { it.date >= coverageStart && it.date < fetchFrom }
        val name = previous?.displayName ?: runCatching { api.displayName() }.getOrNull()

        val cache = RideCache(
            rides = (kept + fresh).distinctBy { it.id }.sortedBy { it.startLocal },
            syncedThrough = today.toString(),
            syncedAtMillis = System.currentTimeMillis(),
            displayName = name,
            historyFrom = HISTORY_START.toString(),
        )
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "rides.json.tmp")
            tmp.writeText(json.encodeToString(RideCache.serializer(), cache))
            tmp.renameTo(file)
        }
        cache
    }

    fun clear() {
        file.delete()
    }

    private companion object {
        /** Garmin Connect launched in 2008; this also catches older imported activities. */
        val HISTORY_START: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
