package io.anonero.ui.theme

import android.content.SharedPreferences
import androidx.core.content.edit
import io.anonero.util.PREFS_THEME_MODE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM;

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: DARK
    }
}

/**
 * The settings screen and the activity observe the same flow, so changing the theme is
 * immediate rather than requiring a restart.
 */
object ThemeStore {
    private val _mode = MutableStateFlow(ThemeMode.DARK)
    val mode: StateFlow<ThemeMode> = _mode

    fun load(prefs: SharedPreferences) {
        _mode.value = ThemeMode.fromStored(prefs.getString(PREFS_THEME_MODE, null))
    }

    fun set(prefs: SharedPreferences, mode: ThemeMode) {
        _mode.value = mode
        prefs.edit { putString(PREFS_THEME_MODE, mode.name) }
    }
}
