/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2023-2026 r4v3r23
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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

/*
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
