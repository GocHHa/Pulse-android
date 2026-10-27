package dev.pulse.monitor.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pulseDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.pulseDataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.themeMode,
            tempUnit = p[TEMP_UNIT]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() } ?: defaults.tempUnit,
            chargeAlertEnabled = p[CHARGE_ENABLED] ?: defaults.chargeAlertEnabled,
            chargeTargetPercent = p[CHARGE_TARGET] ?: defaults.chargeTargetPercent,
            heatAlertEnabled = p[HEAT_ENABLED] ?: defaults.heatAlertEnabled,
            heatThresholdC = p[HEAT_THRESHOLD] ?: defaults.heatThresholdC,
            storageAlertEnabled = p[STORAGE_ENABLED] ?: defaults.storageAlertEnabled,
            storageThresholdPercent = p[STORAGE_THRESHOLD] ?: defaults.storageThresholdPercent,
            liveMonitoring = p[LIVE] ?: defaults.liveMonitoring,
            askedNotificationPermission = p[ASKED_NOTIF] ?: defaults.askedNotificationPermission,
        )
    }.distinctUntilChanged()

    val latches: Flow<AlertLatches> = store.data.map { p ->
        AlertLatches(
            charge = p[LATCH_CHARGE] ?: false,
            heat = p[LATCH_HEAT] ?: false,
            storage = p[LATCH_STORAGE] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun currentLatches(): AlertLatches = latches.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setTempUnit(unit: TempUnit) = store.edit { it[TEMP_UNIT] = unit.name }
    suspend fun setChargeAlertEnabled(enabled: Boolean) = store.edit { it[CHARGE_ENABLED] = enabled; it[LATCH_CHARGE] = false }
    suspend fun setChargeTarget(percent: Int) = store.edit { it[CHARGE_TARGET] = percent.coerceIn(50, 100); it[LATCH_CHARGE] = false }
    suspend fun setHeatAlertEnabled(enabled: Boolean) = store.edit { it[HEAT_ENABLED] = enabled; it[LATCH_HEAT] = false }
    suspend fun setHeatThreshold(celsius: Float) = store.edit { it[HEAT_THRESHOLD] = celsius.coerceIn(30f, 55f); it[LATCH_HEAT] = false }
    suspend fun setStorageAlertEnabled(enabled: Boolean) = store.edit { it[STORAGE_ENABLED] = enabled; it[LATCH_STORAGE] = false }
    suspend fun setStorageThreshold(percent: Int) = store.edit { it[STORAGE_THRESHOLD] = percent.coerceIn(1, 50); it[LATCH_STORAGE] = false }
    suspend fun setLiveMonitoring(enabled: Boolean) = store.edit { it[LIVE] = enabled }
    suspend fun setAskedNotificationPermission() = store.edit { it[ASKED_NOTIF] = true }

    suspend fun setLatches(latches: AlertLatches) = store.edit {
        it[LATCH_CHARGE] = latches.charge
        it[LATCH_HEAT] = latches.heat
        it[LATCH_STORAGE] = latches.storage
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val CHARGE_ENABLED = booleanPreferencesKey("charge_alert_enabled")
        val CHARGE_TARGET = intPreferencesKey("charge_target")
        val HEAT_ENABLED = booleanPreferencesKey("heat_alert_enabled")
        val HEAT_THRESHOLD = floatPreferencesKey("heat_threshold_c")
        val STORAGE_ENABLED = booleanPreferencesKey("storage_alert_enabled")
        val STORAGE_THRESHOLD = intPreferencesKey("storage_threshold")
        val LIVE = booleanPreferencesKey("live_monitoring")
        val ASKED_NOTIF = booleanPreferencesKey("asked_notification_permission")
        val LATCH_CHARGE = booleanPreferencesKey("latch_charge")
        val LATCH_HEAT = booleanPreferencesKey("latch_heat")
        val LATCH_STORAGE = booleanPreferencesKey("latch_storage")
    }
}
