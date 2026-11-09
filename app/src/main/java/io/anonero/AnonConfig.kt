/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
 * Copyright (c) 2017-2023 m2049r
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
 * Derived from Monerujo (https://github.com/m2049r/xmrwallet) and modified
 * for ANONERO. See NOTICE.
 */

package io.anonero

import android.content.Context
import io.anonero.model.NetworkType
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.pow

val jsonDecoder = Json { ignoreUnknownKeys = true }

/**
 * Process-wide paths and build constants.
 *
 * This object is deliberately small and static: it holds the application context, the
 * network type derived from the build's application id, wallet/Tor/log directories and
 * the fixed file names used by the airgapped export/import flows. It is not a state
 * owner; wallet balances, accounts and sync state belong to [io.anonero.services.WalletState].
 *
 * `context` is assigned once in [io.anonero.AnonApplication] before Koin or any wallet
 * code runs. Helpers here assume it is non-null; do not call them from a process that
 * has not completed Application.onCreate().
 */
object AnonConfig {

    const val NERO_KEY_PAYLOAD_VERSION = 1
    const val XMR_DECIMALS = 12
    val ONE_XMR = Math.round(10.0.pow(XMR_DECIMALS.toDouble()))
    const val MAX_LOG_SIZE = 250
    const val PREFS = "anonPref" // actual SharedPreferences file; WALLET_PREFERENCES is the Koin qualifier
    var context: AnonApplication? = null
    private var walletFound: Boolean = false

    const val EXPORT_OUTPUT_FILE = "export_wallet_outputs.out"
    const val IMPORT_OUTPUT_FILE = "import_wallet_outputs"

    const val EXPORT_KEY_IMAGE_FILE = "export_key_images.imgs"
    const val IMPORT_KEY_IMAGE_FILE = "import_key_images"

    const val EXPORT_UNSIGNED_TX_FILE = "export_unsigned_tx.utx"
    const val IMPORT_UNSIGNED_TX_FILE = "import_unsigned_tx"

    const val EXPORT_SIGNED_TX_FILE = "export_signed_tx.stx"
    const val IMPORT_SIGNED_TX_FILE = "import_signed_tx"


    // The stageNet build type adds ".stagenet" to the application ID; all other builds use
    // mainnet. The network is a build property, not a user setting.
    fun getNetworkType(): NetworkType {
        if (BuildConfig.APPLICATION_ID.lowercase().contains("stagenet")) {
            return NetworkType.NetworkType_Stagenet
        }
        return NetworkType.NetworkType_Mainnet
    }

    val viewOnly: Boolean get() = BuildConfig.VIEW_ONLY

    fun getDefaultWalletFile(context: Context): File {
        val walletDir = getDefaultWalletDir(context)
        val anonWallet = File(walletDir, "anon")
        return anonWallet
    }


    fun getTorConfig(scope: CoroutineScope): TorRuntime.Environment {
        val appContext = requireNotNull(context)
        val torDir = File(appContext.filesDir, "tor")
        check(torDir.isDirectory || torDir.mkdirs()) { "Could not create Tor working directory" }
        val cacheDir = File(appContext.cacheDir, "tor")
        check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Could not create Tor cache directory" }
        return TorRuntime.Environment.Builder(
            workDirectory = torDir,
            cacheDirectory = cacheDir,
            loader = ResourceLoaderTorExec::getOrCreate,
        )
    }


    /* Legacy, asynchronous flag. Do not use as the source of truth for wallet presence. */
    fun isWalletFileExist(): Boolean {
        return walletFound
    }


    fun getLogFile(context: Context): File {
        val logDir = File(context.applicationContext.cacheDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs() // Create the directory if it doesn't exist
        }
        val logFile = File(logDir, "anon_log")
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
        return logFile
    }

    fun getDefaultWalletDir(context: Context): File {
        val walletDir = File(context.applicationContext.filesDir, "wallets")
        if (!walletDir.exists()) {
            walletDir.mkdirs()
        }
        return walletDir
    }

    /*
     * Legacy asynchronous presence flag. UI wallet existence is decided by
     * WalletProfileStore.anyWalletExists; this helper is not a process-start barrier and
     * must not be used to gate onboarding or wallet opening.
     */
    fun initWalletState() {
        MainScope().launch(Dispatchers.IO) {
            walletFound = getDefaultWalletFile(context!!).exists()
        }
    }

    /*
     * Fixed cache names used by airgapped export/import flows. Add any new name here and to
     * the wipe/startup cleanup so plaintext transaction material does not survive a process
     * restart.
     */
    fun clearSpendCacheFiles(context: Context) {
        val files = arrayListOf(
            EXPORT_OUTPUT_FILE,
            IMPORT_OUTPUT_FILE,

            EXPORT_KEY_IMAGE_FILE,
            IMPORT_KEY_IMAGE_FILE,

            EXPORT_UNSIGNED_TX_FILE,
            IMPORT_UNSIGNED_TX_FILE,

            EXPORT_SIGNED_TX_FILE,
            IMPORT_SIGNED_TX_FILE
        );
        context.cacheDir.listFiles()?.forEach { file ->
            if (files.any { file.name.contains(it) }) {
                file.delete()
            }
        }
    }

    fun disposeState() {
        walletFound = false
    }
}
