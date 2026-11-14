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

package io.anonero.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.anonero.FOREGROUND_CHANNEL
import io.anonero.R
import io.anonero.TX_CHANNEL
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.model.node.NodeFields
import io.anonero.network.TransportConfig
import io.anonero.store.NodesRepository
import io.anonero.ui.MainActivity
import io.anonero.util.Formats
import io.anonero.util.StealthMode
import io.anonero.util.WALLET_PREFERENCES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.Locale

const val NOTIFICATION_ID = 2

fun startAnonService(context: Context) {
    val intent =
        Intent(context, AnonNeroService::class.java).apply {
            action = "start"
        }
    context.startForegroundService(intent)
}

/**
 * Foreground service that keeps wallet synchronization alive while the UI is backgrounded.
 *
 * The service owns no wallet. It observes [WalletState] and publishes status/progress and
 * incoming-transaction notifications; `AnonApplication` and [AnonWalletHandler] own the
 * wallet lifecycle. Android may restart a sticky service, so the service must be safe to
 * start without a live wallet; all reads are nullable and notification updates are guarded.
 *
 * Notification text is routed through [StealthMode] so a locked device does not disclose
 * balances, heights, addresses, or the product name.
 */
class AnonNeroService : Service() {
    private val tag: String = AnonNeroService::class.java.simpleName
    private val job = SupervisorJob()
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private val walletState: WalletState by inject(WalletState::class.java)
    private val torService: TorService by inject(TorService::class.java)
    private val nodesRepository: NodesRepository by inject(NodesRepository::class.java)
    private val prefs: SharedPreferences by inject(named(WALLET_PREFERENCES))

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent == null || intent.action == "start") {
            start()
        }
        return START_STICKY
    }

    private fun start() {
        if (updateJob?.isActive == true) return
        startForeground(NOTIFICATION_ID, foregroundNotification())
        updateJob =
            scope.launch {
                // One consumer serializes daemon probes; bursts need only the latest state.
                val updates = Channel<Unit>(Channel.CONFLATED)
                launch {
                    walletState.walletConnectionStatus.collect {
                        updates.trySend(Unit)
                    }
                }
                launch {
                    walletState.walletStatus.collect {
                        updates.trySend(Unit)
                    }
                }
                launch {
                    while (isActive) {
                        updates.trySend(Unit)
                        delay(1000)
                    }
                }
                launch {
                    walletState.incomingTx.collect {
                        updateNotificationSafely { postIncomingTxNotification() }
                    }
                }
                launch {
                    walletState.syncProgress.collect {
                        val socks = torService.socks
                        val torSate =
                            if (socks != null) {
                                getString(R.string.tor_daemon_suffix, socks.port.toString())
                            } else {
                                ""
                            }
                        if (it != null) {
                            updateNotificationSafely {
                                withContext(Dispatchers.Main) {
                                    showProgress(it, torSate)
                                }
                            }
                        }
                    }
                }
                for (ignored in updates) {
                    updateNotificationSafely { updateNotificationState() }
                }
            }
    }

    private suspend fun updateNotificationSafely(update: suspend () -> Unit) {
        try {
            update()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A transient wallet/notification error must not stop the other collectors.
            Timber.tag(tag).w("Notification update failed")
        }
    }

    private suspend fun updateNotificationState() {
        val mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val wallet = WalletManager.instance?.wallet
        val lwsEnabled = prefs.getBoolean(io.anonero.util.PREFS_LWS_ENABLED, false)
        val daemon =
            if (lwsEnabled) {
                prefs.getString(io.anonero.util.PREFS_LWS_HOST, "") ?: ""
            } else {
                prefs.getString(NodeFields.RPC_HOST.value, "") ?: ""
            }
        val socks = torService.socks
        var torSate =
            if (TransportConfig.load(prefs).mode.usesEmbeddedTor &&
                socks != null && !StealthMode.isEnabled(prefs)
            ) {
                getString(R.string.tor_daemon_suffix, socks.port.toString())
            } else {
                ""
            }
        if (wallet != null) {
            val isSyncing = walletState.isSyncing
            if (!isSyncing) {
                val notificationMessage =
                    if (walletState.offline) {
                        if (walletState.backgroundSync) {
                            getString(R.string.wallet_locked_offline)
                        } else {
                            getString(R.string.offline)
                        }
                    } else if (walletState.backgroundSync) {
                        getString(R.string.wallet_locked_synced, wallet.getBlockChainHeight())
                    } else if (!wallet.isInitialized) {
                        getString(R.string.loading_wallet)
                    } else if (!isNetworkAvailable()) {
                        // Network is offline — skip the blocking RPC call and mark
                        // disconnected immediately so the bar shows without waiting
                        // for a 30-second timeout
                        walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
                        getString(R.string.disconnected)
                    } else {
                        // Network is available — probe the daemon with a live RPC call
                        val daemonHeight =
                            withContext(Dispatchers.IO) {
                                wallet.getDaemonBlockChainHeight()
                            }
                        val liveStatus =
                            if (daemonHeight > 0) {
                                Wallet.ConnectionStatus.ConnectionStatus_Connected
                            } else {
                                Wallet.ConnectionStatus.ConnectionStatus_Disconnected
                            }
                        walletState.setConnectionStatus(liveStatus)
                        when (liveStatus) {
                            Wallet.ConnectionStatus.ConnectionStatus_Disconnected -> {
                                getString(R.string.daemon_disconnected)
                            }

                            Wallet.ConnectionStatus.ConnectionStatus_WrongVersion -> {
                                getString(R.string.wrong_version)
                            }

                            Wallet.ConnectionStatus.ConnectionStatus_Connected -> {
                                if (wallet.getBlockChainHeight() > 1) {
                                    getString(R.string.synced_height, wallet.getBlockChainHeight())
                                } else {
                                    getString(R.string.syncing)
                                }
                            }
                        }
                    }
                if (daemon.isEmpty()) {
                    torSate = ""
                }
                withContext(Dispatchers.Main) {
                    mNotificationManager.notify(
                        NOTIFICATION_ID,
                        foregroundNotification(
                            StealthMode.notificationBody(prefs, "$notificationMessage$torSate"),
                            title = StealthMode.notificationTitle(prefs, "[ΛИ0ИΞR0]"),
                        ),
                    )
                }
            }
        }
    }

    private fun showProgress(
        it: SyncProgress,
        torSate: String,
    ) {
        val normal =
            if (it.left != 0L) {
                getString(
                    R.string.syncing_blocks_left,
                    Formats.convertNumber(it.left, Locale.getDefault())
                        ?: it.left.toString(),
                ) + torSate
            } else {
                getString(R.string.syncing_completed) + torSate
            }
        val notification =
            foregroundNotification(
                content = StealthMode.notificationBody(prefs, normal),
                title = StealthMode.notificationTitle(prefs, "[ΛИ0ИΞR0]"),
                progress = it,
            )
        val mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun foregroundNotification(
        content: String = getString(R.string.loading_wallet),
        title: String = StealthMode.notificationTitle(prefs, "[ΛИ0ИΞR0]"),
        progress: SyncProgress? = null,
    ): Notification {
        val mainActivityIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                mainActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this.applicationContext, FOREGROUND_CHANNEL)
            .setSmallIcon(R.drawable.anon_notification)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setContentTitle(title)
            .setContentText(content)
            .apply {
                if (progress != null && progress.progress < 1) {
                    this.setProgress(100, (progress.progress * 100).toInt(), false)
                }
            }.setGroup("BackgroundService")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun postIncomingTxNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val mainActivityIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                1,
                mainActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(applicationContext, TX_CHANNEL)
                .setSmallIcon(R.drawable.anon_notification)
                .setContentTitle(StealthMode.incomingTitle(prefs, "[ΛИ0ИΞR0]"))
                .setContentText(
                    StealthMode.incomingBody(prefs, getString(R.string.transaction_received)),
                ).setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        updateJob?.cancel()
        Timber.tag(tag).i("onDestroy: ")
        super.onDestroy()
        job.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.tag(tag).i("onTaskRemoved: ")
        torService.dispose()
        updateJob?.cancel()
        job.cancel()
        super.onTaskRemoved(rootIntent)
    }
}
