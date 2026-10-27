package dev.pulse.monitor.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.pulse.monitor.PulseApp
import dev.pulse.monitor.R
import dev.pulse.monitor.alerts.Notifications
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.model.ChargeStatus
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.util.Format
import dev.pulse.monitor.widget.PulseWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional "Live monitoring": a foreground service with a persistent notification that
 * refreshes every few seconds and checks alerts continuously (so a charge-limit alert
 * arrives within seconds, not up to 15 minutes later).
 *
 * Android 14+ requires every foreground service to declare a type. Hardware monitoring
 * doesn't match any predefined type, so it uses `specialUse` with an explanation in the
 * manifest, plus the FOREGROUND_SERVICE_SPECIAL_USE permission.
 */
class LiveMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val container = (application as PulseApp).container
            scope.launch {
                container.settingsRepository.setLiveMonitoring(false)
            }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        val started = runCatching {
            ServiceCompat.startForeground(this, Notifications.ID_LIVE, buildNotification(null, false), type)
        }
        if (started.isFailure) {
            // E.g. started from the background on a newer Android version: give up quietly.
            stopSelf()
            return START_NOT_STICKY
        }

        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_NOT_STICKY
    }

    private suspend fun runLoop() {
        val container = (application as PulseApp).container
        var lastWidgetPush = 0L
        while (scope.isActive) {
            val settings = container.settingsRepository.current()
            val fahrenheit = settings.tempUnit == TempUnit.FAHRENHEIT
            val snapshot = container.batteryReader.read()

            postLive(buildNotification(snapshot, fahrenheit))

            container.alertEngine.check(
                battery = snapshot,
                thermal = container.cpuReader.thermalLevel(),
                storageFreePercent = container.memoryReader.storageFreePercent(),
            )

            val now = System.currentTimeMillis()
            if (now - lastWidgetPush > 60_000) {
                lastWidgetPush = now
                runCatching { PulseWidget.push(this, snapshot, fahrenheit, settings.heatThresholdC) }
            }
            delay(UPDATE_MS)
        }
    }

    @SuppressLint("MissingPermission") // guarded by Notifications.canPost()
    private fun postLive(notification: Notification) {
        if (Notifications.canPost(this)) {
            NotificationManagerCompat.from(this).notify(Notifications.ID_LIVE, notification)
        }
    }

    private fun buildNotification(s: BatterySnapshot?, fahrenheit: Boolean): Notification {
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LiveMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title: String
        val text: String
        if (s == null) {
            title = "Live monitoring"
            text = "Starting…"
        } else {
            val level = s.levelPercent?.let { "$it%" } ?: "Battery n/a"
            val state = when (s.status) {
                ChargeStatus.CHARGING -> "Charging"
                ChargeStatus.FULL -> "Full"
                ChargeStatus.NOT_CHARGING -> "Plugged in, not charging"
                else -> "On battery"
            }
            title = "$level · $state"
            text = listOfNotNull(
                s.powerW?.let { Format.signedWatts(it) },
                s.temperatureC?.let { Format.temp(it, fahrenheit) },
                s.chargeTimeRemainingMs?.let { "full in ${Format.duration(it)}" },
            ).joinToString("  ·  ").ifEmpty { "Readings not available on this device" }
        }
        return NotificationCompat.Builder(this, Notifications.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Notifications.openAppIntent(this))
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "dev.pulse.monitor.action.STOP_LIVE"
        private const val UPDATE_MS = 5_000L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, LiveMonitorService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveMonitorService::class.java))
        }
    }
}
