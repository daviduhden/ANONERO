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

package io.anonero.util


const val WALLET_PREFERENCES = "walletPref"


const val PREFS_PASSPHRASE_HASH = "passPhraseHash"

const val PREFS_PIN_HASH = "pinHash"
const val RESTORE_HEIGHT = "restoreHeight"
const val WALLET_PROXY = "proxy"
const val WALLET_USE_TOR = "useTor"
const val WALLET_PROXY_PORT = "proxyPort"

// Decoy wallet / duress PIN. Separate keys so a wrong PIN can never overwrite the
// legitimate wallet's gate and a secure wipe of one profile cannot lock the other.
const val PREFS_DURESS_PIN_HASH = "duressPinHash"
const val PREFS_DECOY_PASSPHRASE_HASH = "decoyPassphraseHash"
const val PREFS_DECOY_CONFIGURED = "decoyConfigured"

// Appearance and stealth mode
const val PREFS_THEME_MODE = "themeMode"
const val PREFS_STEALTH_MODE = "stealthMode"
const val PREFS_STEALTH_AUTO_LOCK = "stealthAutoLock"

// Monero Light Wallet Server (LWS / MyMonero-compatible REST server)
const val PREFS_LWS_ENABLED = "lwsEnabled"
const val PREFS_LWS_HOST = "lwsHost"
const val PREFS_LWS_PORT = "lwsPort"