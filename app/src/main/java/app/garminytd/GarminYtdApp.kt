package app.garminytd

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import app.garminytd.auth.GarminAuth
import app.garminytd.auth.TokenStore
import app.garminytd.data.GarminApi
import app.garminytd.data.RideRepository
import app.garminytd.stats.RideFilter
import app.garminytd.ui.DistanceUnit
import app.garminytd.widget.RideWidget
import androidx.glance.appwidget.updateAll
import java.time.LocalDate
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GarminYtdApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Hand-rolled DI: the graph is five objects, a framework would be overkill. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val auth = GarminAuth(http, TokenStore(context))
    val api = GarminApi(http, auth)
    val repository = RideRepository(context, api)
    val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Display settings shared by the dashboard and the widget.
    fun unit(): DistanceUnit = runCatching { DistanceUnit.valueOf(settings.getString(KEY_UNIT, null)!!) }.getOrDefault(DistanceUnit.KM)
    fun filter(): RideFilter = runCatching { RideFilter.valueOf(settings.getString(KEY_FILTER, null)!!) }.getOrDefault(RideFilter.ALL)
    /** The chosen comparison year if it is still in the past, else last year. */
    fun refYear(today: LocalDate): Int = settings.getInt(KEY_REF_YEAR, 0).takeIf { it in 1..<today.year } ?: (today.year - 1)

    fun saveUnit(unit: DistanceUnit) = settings.edit { putString(KEY_UNIT, unit.name) }
    fun saveFilter(filter: RideFilter) = settings.edit { putString(KEY_FILTER, filter.name) }
    fun saveRefYear(year: Int) = settings.edit { putInt(KEY_REF_YEAR, year) }

    /** Redraw every placed home-screen widget from the current cache and settings. */
    suspend fun refreshWidgets() {
        runCatching { RideWidget().updateAll(appContext) }
    }

    private companion object {
        const val KEY_UNIT = "unit"
        const val KEY_FILTER = "filter"
        const val KEY_REF_YEAR = "ref_year"
    }
}
