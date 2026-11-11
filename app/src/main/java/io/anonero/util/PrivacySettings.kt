/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
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

package io.anonero.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Reduces casual visual and notification exposure. It is not an anonymity guarantee:
 * the Android system, screen-capture-privileged apps, a rooted device, or a forensic
 * image can still observe much of what the app does. FLAG_SECURE (set unconditionally in
 * MainActivity) is the actual protection for the recents thumbnail and screenshots.
 */
object StealthMode {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREFS_STEALTH_MODE, false)

    fun load(prefs: SharedPreferences) {
        _enabled.value = isEnabled(prefs)
    }

    fun setEnabled(
        prefs: SharedPreferences,
        enabled: Boolean,
    ) {
        prefs.edit { putBoolean(PREFS_STEALTH_MODE, enabled) }
        _enabled.value = enabled
    }

    fun autoLock(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREFS_STEALTH_AUTO_LOCK, true)

    fun setAutoLock(
        prefs: SharedPreferences,
        enabled: Boolean,
    ) = prefs.edit { putBoolean(PREFS_STEALTH_AUTO_LOCK, enabled) }

    // The app name is distinctive; a stealth notification should not announce the product.
    fun notificationTitle(
        prefs: SharedPreferences,
        normal: String,
    ): String = if (isEnabled(prefs)) "Wallet" else normal

    // Never include balances, heights, addresses, or transaction counts in stealth mode.
    fun notificationBody(
        prefs: SharedPreferences,
        normal: String,
    ): String = if (isEnabled(prefs)) "Background sync active" else normal

    fun incomingTitle(
        prefs: SharedPreferences,
        normal: String,
    ): String = if (isEnabled(prefs)) "Wallet" else normal

    fun incomingBody(
        prefs: SharedPreferences,
        normal: String,
    ): String = if (isEnabled(prefs)) "New activity" else normal
}

/**
 * Copies to the system clipboard and removes the entry again after a short delay, unless
 * the user has copied something else in the meantime. A clipboard manager (including the
 * OS on newer versions) may still retain history; this reduces, not eliminates, exposure.
 */
object SecureClipboard {
    /** Removes the current clipboard entry, if this app can. */
    fun clear(context: Context) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.clearPrimaryClip()
    }

    fun copy(
        context: Context,
        text: String,
        clearAfterMs: Long = 60_000L,
    ) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("address", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.setExtras(
                android.os.PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                },
            )
        }
        manager.setPrimaryClip(clip)
        CoroutineScope(Dispatchers.Main).launch {
            delay(clearAfterMs)
            val current = manager.primaryClip
            if (current != null && current.itemCount > 0 &&
                current.getItemAt(0).text?.toString() == text
            ) {
                manager.clearPrimaryClip()
            }
        }
    }
}

/**
 * A Monero light-wallet server compatible with the MyMonero/LWS REST API that wallet2
 * speaks in light-wallet mode. Only the wallet's view key and public address are sent;
 * the spend key never leaves the device. The server can still see every incoming
 * transaction, amount, and the approximate timing, and can lie about chain state.
 * Communications are forced through Tor by AnonWalletHandler.
 */
enum class LwsValidationError(
    val technicalMessage: String,
) {
    HOST_EMPTY("Host cannot be empty"),
    HOST_TOO_LONG("Host is too long"),
    HOST_HAS_SCHEME_OR_PORT("Enter only the host name, without scheme, path or port"),
    HOST_INVALID("Invalid host name"),
    PORT_RANGE("Port must be between 1 and 65535"),
}

data class LwsConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
) {
    val address: String get() = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 8443

        fun load(prefs: SharedPreferences): LwsConfig =
            LwsConfig(
                enabled = prefs.getBoolean(PREFS_LWS_ENABLED, false),
                host = prefs.getString(PREFS_LWS_HOST, "").orEmpty().trim(),
                port = prefs.getInt(PREFS_LWS_PORT, DEFAULT_PORT),
            )

        fun save(
            prefs: SharedPreferences,
            config: LwsConfig,
        ) {
            prefs.edit(commit = true) {
                putBoolean(PREFS_LWS_ENABLED, config.enabled)
                putString(PREFS_LWS_HOST, config.host)
                putInt(PREFS_LWS_PORT, config.port)
            }
        }

        /*
         * Host is a DNS name, .onion address, or IPv4 literal. No scheme, credentials,
         * path, whitespace or embedded port: the port is a separate, validated field.
         *
         * Returns stable codes; the UI maps them to translated resources and the
         * technical text is only for logs and internal exceptions.
         */
        fun validateHost(host: String): LwsValidationError? {
            val h = host.trim()
            if (h.isEmpty()) return LwsValidationError.HOST_EMPTY
            if (h.length > 253) return LwsValidationError.HOST_TOO_LONG
            if (h.contains("://") || h.contains('/') || h.contains('@') || h.contains(' ') ||
                h.contains(':')
            ) {
                return LwsValidationError.HOST_HAS_SCHEME_OR_PORT
            }
            val allowed = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$")
            if (!allowed.matches(h)) return LwsValidationError.HOST_INVALID
            return null
        }

        fun validatePort(port: Int): LwsValidationError? = if (port !in 1..65535) LwsValidationError.PORT_RANGE else null

        fun validate(
            host: String,
            port: Int,
        ): LwsValidationError? = validateHost(host) ?: validatePort(port)
    }
}
