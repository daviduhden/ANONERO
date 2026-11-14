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

/*
 * Keys stored in the `AnonConfig.PREFS` file (actual name `anonPref`).
 *
 * `WALLET_PREFERENCES` is the Koin qualifier used to inject that same file; it is not a
 * second preference file. Main and decoy profiles deliberately use different key names for
 * credentials and restore height. Anything that can differ per profile must go through
 * `WalletProfileStore` rather than reading a raw constant. Changing a key name requires a
 * migration; these names are part of the on-device format.
 */
const val WALLET_PREFERENCES = "walletPref" // Koin qualifier; the file is AnonConfig.PREFS

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
const val PREFS_LWS_TLS = "lwsTls"
