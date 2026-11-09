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