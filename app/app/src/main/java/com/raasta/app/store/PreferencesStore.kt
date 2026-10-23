package com.raasta.app.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raasta.app.planner.Goal
import com.raasta.app.planner.TravelPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore by preferencesDataStore(name = "travel_preferences")

/** Remembers the rider's last travel preferences so the next journey can reuse them. */
class PreferencesStore(private val context: Context) {
    private val goalsKey = stringSetPreferencesKey("goals")
    private val safetyKey = booleanPreferencesKey("safety_aware")
    private val walksKey = booleanPreferencesKey("avoid_long_walks")

    /** Null until the rider has chosen preferences once. */
    val saved: Flow<TravelPreferences?> = context.preferencesDataStore.data.map { prefs ->
        val goals = prefs[goalsKey] ?: return@map null
        TravelPreferences(
            goals = goals.mapNotNull { name -> Goal.entries.firstOrNull { it.name == name } }.toSet(),
            safetyAware = prefs[safetyKey] ?: false,
            avoidLongWalks = prefs[walksKey] ?: false,
        )
    }

    suspend fun save(preferences: TravelPreferences) {
        context.preferencesDataStore.edit {
            it[goalsKey] = preferences.goals.map(Goal::name).toSet()
            it[safetyKey] = preferences.safetyAware
            it[walksKey] = preferences.avoidLongWalks
        }
    }
}
