package app.garminytd.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.garminytd.AppContainer
import app.garminytd.R
import app.garminytd.auth.GarminAuthException
import app.garminytd.auth.clearGarminWebSession
import app.garminytd.data.Ride
import app.garminytd.stats.DashboardStats
import app.garminytd.stats.DemoData
import app.garminytd.stats.RideFilter
import app.garminytd.stats.YtdStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

enum class Screen { Welcome, Login, Exchanging, Dashboard }

/** A user-facing message, resolved against the current locale only when shown. */
data class UiMessage(@StringRes val id: Int, val args: List<Any> = emptyList())

data class UiState(
    val screen: Screen = Screen.Welcome,
    val rides: List<Ride> = emptyList(),
    val stats: DashboardStats? = null,
    val filter: RideFilter = RideFilter.ALL,
    /** Year to compare against; null = last year. */
    val refYear: Int? = null,
    val unit: DistanceUnit = DistanceUnit.KM,
    val displayName: String? = null,
    val lastSyncMillis: Long? = null,
    val syncing: Boolean = false,
    /** Activities checked so far during a sync (the first one walks the whole Garmin history). */
    val syncProgress: Int? = null,
    val message: UiMessage? = null,
    val demo: Boolean = false,
)

class MainViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        UiState(
            screen = if (c.auth.isLoggedIn) Screen.Dashboard else Screen.Welcome,
            unit = c.unit(),
            filter = c.filter(),
            refYear = c.refYear(LocalDate.now()),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (c.auth.isLoggedIn) {
            viewModelScope.launch {
                c.repository.cached()?.let { cache ->
                    _state.update { it.copy(rides = cache.rides, displayName = cache.displayName, lastSyncMillis = cache.syncedAtMillis) }
                    recompute()
                }
                sync(full = false)
            }
        }
    }

    fun startLogin() = _state.update { it.copy(screen = Screen.Login, message = null) }
    fun cancelLogin() = _state.update { it.copy(screen = Screen.Welcome) }

    fun onLoginBlocked(code: Int) = _state.update {
        it.copy(screen = Screen.Welcome, message = UiMessage(R.string.err_blocked, listOf(code)))
    }

    fun onLoginCrashed() = _state.update { it.copy(screen = Screen.Welcome, message = UiMessage(R.string.err_login_crashed)) }

    fun onTicket(ticket: String) {
        _state.update { it.copy(screen = Screen.Exchanging, message = null) }
        viewModelScope.launch {
            try {
                c.auth.loginWithTicket(ticket)
                _state.update { it.copy(screen = Screen.Dashboard) }
                sync(full = true)
            } catch (e: Exception) {
                _state.update { it.copy(screen = Screen.Welcome, message = friendly(e)) }
            }
        }
    }

    fun refresh() = viewModelScope.launch { sync(full = false) }
    fun fullResync() = viewModelScope.launch { sync(full = true) }

    private suspend fun sync(full: Boolean) {
        if (_state.value.demo || _state.value.syncing) return
        _state.update { it.copy(syncing = true, syncProgress = null, message = null) }
        try {
            val cache = c.repository.sync(LocalDate.now(), full) { n -> _state.update { it.copy(syncProgress = n) } }
            _state.update { it.copy(rides = cache.rides, displayName = cache.displayName, lastSyncMillis = cache.syncedAtMillis) }
            recompute()
            c.refreshWidgets()
        } catch (_: GarminAuthException.SessionExpired) {
            logout(message = UiMessage(R.string.err_session_expired))
        } catch (e: Exception) {
            _state.update { it.copy(message = friendly(e)) }
        } finally {
            _state.update { it.copy(syncing = false, syncProgress = null) }
        }
    }

    fun setFilter(filter: RideFilter) {
        c.saveFilter(filter)
        _state.update { it.copy(filter = filter) }
        viewModelScope.launch { recompute(); refreshWidgetsUnlessDemo() }
    }

    fun setRefYear(year: Int) {
        if (!_state.value.demo) c.saveRefYear(year)
        _state.update { it.copy(refYear = year) }
        viewModelScope.launch { recompute(); refreshWidgetsUnlessDemo() }
    }

    fun toggleUnit() {
        val next = if (_state.value.unit == DistanceUnit.KM) DistanceUnit.MI else DistanceUnit.KM
        c.saveUnit(next)
        _state.update { it.copy(unit = next) }
        viewModelScope.launch { refreshWidgetsUnlessDemo() }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun logout(message: UiMessage? = null) {
        c.auth.logout()
        c.repository.clear()
        clearGarminWebSession()
        _state.update { UiState(screen = Screen.Welcome, unit = it.unit, filter = it.filter, message = message) }
        viewModelScope.launch { c.refreshWidgets() }
    }

    /** Debug builds only: explore the dashboard with generated rides, without a Garmin account. */
    fun startDemo() {
        _state.update { it.copy(screen = Screen.Dashboard, demo = true, rides = DemoData.rides(LocalDate.now()), displayName = null) }
        viewModelScope.launch { recompute() }
    }

    fun exitDemo() = _state.update { UiState(screen = Screen.Welcome, unit = it.unit, filter = it.filter) }

    private suspend fun recompute() {
        val s = _state.value
        val today = LocalDate.now()
        val ref = s.refYear?.takeIf { it < today.year } ?: (today.year - 1)
        val stats = withContext(Dispatchers.Default) {
            YtdStats.compute(YtdStats.filter(s.rides, s.filter), today, ref)
        }
        _state.update { it.copy(stats = stats) }
    }

    private fun friendly(e: Exception): UiMessage = when (e) {
        is GarminAuthException.Blocked -> UiMessage(R.string.err_blocked, listOf(e.code))
        is GarminAuthException.SessionExpired -> UiMessage(R.string.err_session_expired)
        is GarminAuthException.Failed -> UiMessage(R.string.err_garmin, listOf(e.message.orEmpty()))
        is IOException -> UiMessage(R.string.err_network)
        else -> UiMessage(R.string.err_unknown, listOf(e.message ?: e::class.simpleName.orEmpty()))
    }

    private suspend fun refreshWidgetsUnlessDemo() {
        if (!_state.value.demo) c.refreshWidgets()
    }
}
