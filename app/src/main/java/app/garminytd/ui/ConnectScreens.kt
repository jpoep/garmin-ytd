package app.garminytd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.garminytd.BuildConfig
import app.garminytd.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.garminytd.auth.GarminLoginWebView
import app.garminytd.ui.theme.GarminYtdTheme
import app.garminytd.ui.theme.RideTheme

@Composable
fun WelcomeScreen(message: UiMessage?, onConnect: () -> Unit, onDemo: () -> Unit) {
    val colors = RideTheme.colors
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.systemBarsPadding().wrapContentWidth().widthIn(max = 520.dp).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width
                val h = size.height
                fun curve(k: Float) = Path().apply {
                    moveTo(0f, h)
                    cubicTo(w * 0.35f, h * (1 - 0.15f * k), w * 0.55f, h * (1 - 0.7f * k), w, h * (1 - 0.95f * k))
                }
                drawPath(curve(0.8f), colors.lastYear, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawPath(curve(1f), colors.thisYear, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(colors.thisYear, 8.dp.toPx(), androidx.compose.ui.geometry.Offset(w, h * 0.05f))
            }
            Spacer(Modifier.height(36.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Spacer(Modifier.height(20.dp))
                Text(message.text(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.welcome_connect))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.welcome_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (BuildConfig.DEBUG) {
                TextButton(onClick = onDemo) { Text(stringResource(R.string.welcome_demo)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onTicket: (String) -> Unit, onBlocked: (Int) -> Unit, onCrashed: () -> Unit, onCancel: () -> Unit) = GarminYtdTheme(dark = true) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.login_cancel)) } },
            )
        },
    ) { padding ->
        GarminLoginWebView(onTicket = onTicket, onBlocked = onBlocked, onCrashed = onCrashed, modifier = Modifier.padding(padding).fillMaxSize())
    }
}

@Composable
fun ExchangingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.connecting), style = MaterialTheme.typography.titleMedium)
        }
    }
}
