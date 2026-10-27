package dev.pulse.monitor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.pulse.monitor.MainActivity
import dev.pulse.monitor.PulseApp
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.util.Format
import java.text.DateFormat
import java.util.Date

/**
 * Home-screen widget (Jetpack Glance). Widgets can't poll every second, so the app pushes
 * fresh numbers into the widget's own small state store whenever it has them: every
 * 15 minutes from the history job, while the app is open, and while Live monitoring runs.
 */
class PulseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // First placement (or after a reboot): fill the state with a fresh reading.
        val container = (context.applicationContext as PulseApp).container
        val settings = container.settingsRepository.current()
        val fahrenheit = settings.tempUnit == TempUnit.FAHRENHEIT
        updateAppWidgetState(context, id) { prefs ->
            if (prefs[KEY_UPDATED] == null) {
                writeSnapshot(prefs, container.batteryReader.read(), fahrenheit, settings.heatThresholdC)
            }
        }
        provideContent {
            WidgetContent(currentState())
        }
    }

    companion object {
        private val KEY_LEVEL = intPreferencesKey("level")
        private val KEY_TEMP_C = floatPreferencesKey("temp_c")
        private val KEY_WATTS = floatPreferencesKey("watts")
        private val KEY_PLUGGED = booleanPreferencesKey("plugged")
        private val KEY_CHARGING = booleanPreferencesKey("charging")
        private val KEY_FAHRENHEIT = booleanPreferencesKey("fahrenheit")
        private val KEY_HOT = booleanPreferencesKey("hot")
        private val KEY_UPDATED = longPreferencesKey("updated")

        internal fun writeSnapshot(
            prefs: MutablePreferences,
            s: BatterySnapshot,
            fahrenheit: Boolean,
            hotThresholdC: Float,
        ) {
            val level = s.levelPercent
            if (level != null) prefs[KEY_LEVEL] = level else prefs.remove(KEY_LEVEL)
            val temp = s.temperatureC
            if (temp != null) prefs[KEY_TEMP_C] = temp else prefs.remove(KEY_TEMP_C)
            val watts = s.powerW
            if (watts != null) prefs[KEY_WATTS] = watts else prefs.remove(KEY_WATTS)
            prefs[KEY_HOT] = temp != null && temp >= hotThresholdC
            prefs[KEY_PLUGGED] = s.isPlugged
            prefs[KEY_CHARGING] = s.isCharging
            prefs[KEY_FAHRENHEIT] = fahrenheit
            prefs[KEY_UPDATED] = s.timestampMs
        }

        /** Pushes a snapshot to every placed Pulse widget. Safe to call often. */
        suspend fun push(context: Context, snapshot: BatterySnapshot, fahrenheit: Boolean, hotThresholdC: Float) {
            val manager = GlanceAppWidgetManager(context)
            val ids = runCatching { manager.getGlanceIds(PulseWidget::class.java) }.getOrDefault(emptyList())
            if (ids.isEmpty()) return
            val widget = PulseWidget()
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs -> writeSnapshot(prefs, snapshot, fahrenheit, hotThresholdC) }
                widget.update(context, id)
            }
        }

        @Composable
        private fun WidgetContent(prefs: Preferences) {
            val level = prefs[KEY_LEVEL]
            val tempC = prefs[KEY_TEMP_C]
            val watts = prefs[KEY_WATTS]
            val plugged = prefs[KEY_PLUGGED] ?: false
            val charging = prefs[KEY_CHARGING] ?: false
            val fahrenheit = prefs[KEY_FAHRENHEIT] ?: false
            val updated = prefs[KEY_UPDATED]

            val green = Color(0xFF3DF5A0)
            val hot = prefs[KEY_HOT] ?: false
            val heatColor = if (hot) Color(0xFFFF4D5E) else Color(0xFFE8EEF4)
            val dim = Color(0xFF8A97A6)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1217))
                    .cornerRadius(24.dp)
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        text = "PULSE",
                        style = TextStyle(color = ColorProvider(dim), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = when {
                            charging -> "⚡ CHARGING"
                            plugged -> "PLUGGED IN"
                            else -> "ON BATTERY"
                        },
                        style = TextStyle(color = ColorProvider(if (charging) green else dim), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = level?.let { "$it%" } ?: "Not available",
                    style = TextStyle(color = ColorProvider(green), fontSize = 40.sp, fontWeight = FontWeight.Bold),
                )
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        text = Format.temp(tempC, fahrenheit) + if (hot) " HOT" else "",
                        style = TextStyle(color = ColorProvider(heatColor), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    Text(
                        text = if (watts != null) Format.signedWatts(watts) else "W n/a",
                        style = TextStyle(color = ColorProvider(Color(0xFFE8EEF4)), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    )
                }
                if (updated != null) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "Updated " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updated)),
                        style = TextStyle(color = ColorProvider(dim), fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

class PulseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PulseWidget()
}
