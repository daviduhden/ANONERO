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

package io.anonero

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import io.anonero.di.appModule
import io.anonero.util.LocaleController
import io.anonero.util.WalletKey
import io.anonero.model.WalletManager
import io.anonero.model.node.NodeFields
import io.anonero.services.TorService
import io.anonero.store.LogRepository
import io.anonero.store.NodesRepository
import io.anonero.ui.util.AnonLogTree
import io.anonero.util.WALLET_PREFERENCES
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.File


const val FOREGROUND_CHANNEL = "anon_foreground"
const val TX_CHANNEL = "anon_transactions"
private const val TAG = "AnonApplication"

class AnonApplication : Application(), Thread.UncaughtExceptionHandler {

    private lateinit var anonLogTree: AnonLogTree
    private val defaultHandler =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun onCreate() {
        super.onCreate()
        AnonConfig.context = this
        // Must run before Koin or any resource-bearing UI is created. On API 33+ this
        // reconciles the saved preference with the platform per-app locale.
        LocaleController.applySaved(this)
        AnonConfig.initWalletState()
        startKoin {
            androidContext(this@AnonApplication)
            modules(appModule)
        }
        initConfigs()
        plantLog()
        Thread.setDefaultUncaughtExceptionHandler(this)
        val torService: TorService = get()
        val prefs: SharedPreferences = get(named(WALLET_PREFERENCES))
        val host = prefs.getString(NodeFields.RPC_HOST.value, "")
        val lwsEnabled = prefs.getBoolean(io.anonero.util.PREFS_LWS_ENABLED, false)
        if (lwsEnabled || !host.isNullOrEmpty()) {
            torService.start()
        }
        AnonConfig.clearSpendCacheFiles(this)
        // A crash during backup creation/restore can leave a clear seed or an extracted
        // wallet cache in app cache. These are never needed across process starts.
        listOf("tmp_extract", "tmp_backup", "backup", "backup.zip", "backup.anon").forEach { name ->
            val removed = runCatching { File(cacheDir, name).deleteRecursively() }.getOrDefault(false)
            if (!removed) Timber.tag(TAG).w("Could not clear temporary backup data")
        }
        // recover a keystore migration interrupted by a crash, before any wallet open
        runCatching {
            val walletFile = AnonConfig.getDefaultWalletFile(this)
            WalletKey.recoverInterruptedMigration(this, walletFile.path, walletFile.name)
        }
    }

    private fun plantLog() {

        anonLogTree = AnonLogTree(AnonConfig.getLogFile(applicationContext), get<LogRepository>())

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
            Timber.plant(anonLogTree)
        } else {
            Timber.plant(anonLogTree)
        }
        Timber.tag(TAG).i("BUILD_TYPE: %s", BuildConfig.BUILD_TYPE)
        Timber.tag(TAG).i("FLAVOR: %s", BuildConfig.FLAVOR)
        Timber.tag(TAG).i("APPLICATION_ID: %s", BuildConfig.APPLICATION_ID)
        Timber.tag(TAG).i("MONERO_NETWORK: %s", AnonConfig.getNetworkType())
        Timber.tag(TAG)
            .i("VERSION: ${BuildConfig.VERSION_NAME} ${BuildConfig.VERSION_CODE}\n\n")
    }

    private fun initConfigs() {
        //initialize wallet manager
        WalletManager.instance?.init()
        initNotificationChannels()
    }

    fun initNotificationChannels() {
        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL,
            getString(R.string.notification_channel_wallet),
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(foregroundChannel)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                TX_CHANNEL,
                getString(R.string.notification_channel_wallet_activity),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    override fun onTerminate() {
        val torService: TorService = get()
        torService.dispose()
        anonLogTree.cleanup()
        super.onTerminate()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        Timber.tag(TAG).e(e)
        defaultHandler?.uncaughtException(t, e)
    }

}
