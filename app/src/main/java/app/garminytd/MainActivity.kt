package app.garminytd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.garminytd.ui.DashboardScreen
import app.garminytd.ui.ExchangingScreen
import app.garminytd.ui.LoginScreen
import app.garminytd.ui.MainViewModel
import app.garminytd.ui.Screen
import app.garminytd.ui.WelcomeScreen
import app.garminytd.ui.theme.GarminYtdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GarminYtdApp).container
        setContent {
            GarminYtdTheme {
                val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
                val state by vm.state.collectAsStateWithLifecycle()
                when (state.screen) {
                    Screen.Welcome -> WelcomeScreen(state.message, onConnect = vm::startLogin, onDemo = vm::startDemo)
                    Screen.Login -> {
                        BackHandler(onBack = vm::cancelLogin)
                        LoginScreen(onTicket = vm::onTicket, onBlocked = vm::onLoginBlocked, onCrashed = vm::onLoginCrashed, onCancel = vm::cancelLogin)
                    }
                    Screen.Exchanging -> ExchangingScreen()
                    Screen.Dashboard -> DashboardScreen(
                        state = state,
                        onRefresh = vm::refresh,
                        onFullResync = vm::fullResync,
                        onFilter = vm::setFilter,
                        onRefYear = vm::setRefYear,
                        onToggleUnit = vm::toggleUnit,
                        onLogout = { vm.logout() },
                        onExitDemo = vm::exitDemo,
                        onDismissMessage = vm::dismissMessage,
                    )
                }
            }
        }
    }
}
