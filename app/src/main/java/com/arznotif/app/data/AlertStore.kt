package com.arznotif.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "arznotif_alerts")

class AlertStore(private val context: Context) {

    private val gson = Gson()
    private val key = stringPreferencesKey("alerts_json")

    val alertsFlow: Flow<List<AlertSetting>> = context.dataStore.data.map { prefs ->
        val json = prefs[key] ?: "[]"
        try {
            val type = object : TypeToken<List<AlertSetting>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveAlerts(alerts: List<AlertSetting>) {
        context.dataStore.edit { prefs ->
            prefs[key] = gson.toJson(alerts)
        }
    }

    suspend fun updateAlert(alert: AlertSetting) {
        context.dataStore.edit { prefs ->
            val current = try {
                val type = object : TypeToken<List<AlertSetting>>() {}.type
                gson.fromJson(prefs[key] ?: "[]", type) as? List<AlertSetting> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val updated = current.filter { it.assetId != alert.assetId } + alert
            prefs[key] = gson.toJson(updated)
        }
    }
}
