package dev.pulse.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.pulse.monitor.data.settings.AppSettings
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.data.settings.ThemeMode
import dev.pulse.monitor.service.LiveMonitorService
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.PulseRoot
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.ui.theme.resolveDark
import dev.pulse.monitor.widget.PulseWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as PulseApp).container

        // If the user left Live monitoring on, bring the service back while we're in front.
        lifecycleScope.launch {
            if (container.settingsRepository.settings.first().liveMonitoring) {
                LiveMonitorService.start(applicationContext)
            }
        }

        // While the app is visible: check alerts every 15 s (so a charge-limit alert doesn't
        // wait for the 15-minute background job) and refresh the home-screen widget every 60 s.
        // repeatOnLifecycle stops collecting in the background, so polling pauses too.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastAlertCheck = 0L
                var lastWidgetPush = 0L
                container.monitorRepository.battery.collect { snapshot ->
                    val now = System.currentTimeMillis()
                    if (now - lastAlertCheck > 15_000) {
                        lastAlertCheck = now
                        runCatching {
                            container.alertEngine.check(
                                battery = snapshot,
                                thermal = container.cpuReader.thermalLevel(),
                                storageFreePercent = container.memoryReader.storageFreePercent(),
                            )
                        }
                    }
                    if (now - lastWidgetPush > 60_000) {
                        lastWidgetPush = now
                        val s = container.settingsRepository.current()
                        runCatching {
                            PulseWidget.push(applicationContext, snapshot, s.tempUnit == TempUnit.FAHRENHEIT, s.heatThresholdC)
                        }
                    }
                }
            }
        }

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            val current = settings
            val dark = resolveDark(current?.themeMode ?: ThemeMode.DARK)

            // Match status/navigation bar icon colours to the chosen theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }

            // Ask for notification permission once, on first launch (Android 13+).
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* The Settings screen shows the current status either way. */ }
            LaunchedEffect(current?.askedNotificationPermission) {
                if (current != null && !current.askedNotificationPermission &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    scope.launch { container.settingsRepository.setAskedNotificationPermission() }
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            CompositionLocalProvider(LocalAppContainer provides container) {
                PulseTheme(dark = dark) {
                    PulseRoot()
                }
            }
        }
    }
}
