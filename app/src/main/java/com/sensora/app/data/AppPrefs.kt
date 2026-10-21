package com.sensora.app.data

import android.content.Context
import com.sensora.app.core.astro.Observer
import com.sensora.app.core.steps.StepDayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small key-value settings. Everything stays on the device. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("sensora_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        sp.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private val _favorites = MutableStateFlow(sp.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun toggleFavorite(id: String) {
        val next = _favorites.value.toMutableSet().apply { if (!add(id)) remove(id) }
        sp.edit().putStringSet(KEY_FAVORITES, next).apply()
        _favorites.value = next
    }

    private val _recents = MutableStateFlow(
        sp.getString(KEY_RECENTS, "").orEmpty().split(',').filter { it.isNotBlank() }
    )
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    fun addRecent(id: String) {
        val next = (listOf(id) + _recents.value.filter { it != id }).take(6)
        sp.edit().putString(KEY_RECENTS, next.joinToString(",")).apply()
        _recents.value = next
    }

    var soundCalibrationOffset: Double?
        get() = if (sp.contains(KEY_SOUND_CAL)) sp.getFloat(KEY_SOUND_CAL, 0f).toDouble() else null
        set(value) {
            val e = sp.edit()
            if (value == null) e.remove(KEY_SOUND_CAL) else e.putFloat(KEY_SOUND_CAL, value.toFloat())
            e.apply()
        }

    var levelOffsetX: Double
        get() = sp.getFloat(KEY_LEVEL_X, 0f).toDouble()
        set(v) = sp.edit().putFloat(KEY_LEVEL_X, v.toFloat()).apply()

    var levelOffsetY: Double
        get() = sp.getFloat(KEY_LEVEL_Y, 0f).toDouble()
        set(v) = sp.edit().putFloat(KEY_LEVEL_Y, v.toFloat()).apply()

    var levelOffsetV: Double
        get() = sp.getFloat(KEY_LEVEL_V, 0f).toDouble()
        set(v) = sp.edit().putFloat(KEY_LEVEL_V, v.toFloat()).apply()

    var heightCm: Int
        get() = sp.getInt(KEY_HEIGHT, 170)
        set(v) = sp.edit().putInt(KEY_HEIGHT, v).apply()

    var stepGoal: Int
        get() = sp.getInt(KEY_STEP_GOAL, 8000)
        set(v) = sp.edit().putInt(KEY_STEP_GOAL, v).apply()

    var stepState: StepDayState
        get() {
            val p = sp.getString(KEY_STEP_STATE, null)?.split('|')
            if (p == null || p.size != 4) return StepDayState()
            return StepDayState(
                day = p[0].ifBlank { null },
                baseline = p[1].toLongOrNull() ?: 0,
                lastCounter = p[2].toLongOrNull() ?: -1,
                carry = p[3].toLongOrNull() ?: 0,
            )
        }
        set(s) = sp.edit().putString(KEY_STEP_STATE, "${s.day ?: ""}|${s.baseline}|${s.lastCounter}|${s.carry}").apply()

    fun dailySteps(): Map<String, Long> =
        sp.getString(KEY_DAILY, "").orEmpty().split(';').mapNotNull { e ->
            val kv = e.split('=')
            if (kv.size == 2) kv[1].toLongOrNull()?.let { kv[0] to it } else null
        }.toMap()

    fun putDailySteps(day: String, steps: Long) {
        val m = dailySteps().toMutableMap()
        m[day] = steps
        val kept = m.toSortedMap().entries.toList().takeLast(400)
        sp.edit().putString(KEY_DAILY, kept.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    private fun readObserver(): Observer? =
        if (!sp.contains(KEY_OBS_LAT)) null
        else Observer(sp.getFloat(KEY_OBS_LAT, 0f).toDouble(), sp.getFloat(KEY_OBS_LON, 0f).toDouble(), sp.getFloat(KEY_OBS_ELEV, 0f).toDouble())

    private val _observer = MutableStateFlow(readObserver())
    /** Observing location for astronomy tools. Stored only because the user chose it; can be deleted in Settings. */
    val observer: StateFlow<Observer?> = _observer.asStateFlow()
    val observerFromGps: Boolean get() = sp.getBoolean(KEY_OBS_GPS, false)

    fun setObserver(o: Observer, fromGps: Boolean) {
        sp.edit().putFloat(KEY_OBS_LAT, o.latDeg.toFloat()).putFloat(KEY_OBS_LON, o.lonDeg.toFloat())
            .putFloat(KEY_OBS_ELEV, o.elevationM.toFloat()).putBoolean(KEY_OBS_GPS, fromGps).apply()
        _observer.value = o
    }

    fun clearObserver() {
        sp.edit().remove(KEY_OBS_LAT).remove(KEY_OBS_LON).remove(KEY_OBS_ELEV).remove(KEY_OBS_GPS).apply()
        _observer.value = null
    }

    /** Screen pixels per millimetre, from the bank-card calibration in the vision tests. */
    var pxPerMm: Float?
        get() = if (sp.contains(KEY_PX_MM)) sp.getFloat(KEY_PX_MM, 0f) else null
        set(v) { val e = sp.edit(); if (v == null) e.remove(KEY_PX_MM) else e.putFloat(KEY_PX_MM, v); e.apply() }

    fun resetCalibrations() {
        sp.edit().remove(KEY_SOUND_CAL).remove(KEY_PX_MM).remove(KEY_LEVEL_X).remove(KEY_LEVEL_Y).remove(KEY_LEVEL_V).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val KEY_SOUND_CAL = "sound_cal"
        const val KEY_LEVEL_X = "level_x"
        const val KEY_LEVEL_Y = "level_y"
        const val KEY_LEVEL_V = "level_v"
        const val KEY_HEIGHT = "height_cm"
        const val KEY_STEP_GOAL = "step_goal"
        const val KEY_STEP_STATE = "step_state"
        const val KEY_DAILY = "daily_steps"
        const val KEY_PX_MM = "px_per_mm"
        const val KEY_OBS_LAT = "obs_lat"
        const val KEY_OBS_LON = "obs_lon"
        const val KEY_OBS_ELEV = "obs_elev"
        const val KEY_OBS_GPS = "obs_gps"
    }
}
