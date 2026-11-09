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

package io.anonero.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.anonero.AnonConfig
import io.anonero.services.AnonWalletHandler
import io.anonero.services.InvalidPin
import io.anonero.services.NOTIFICATION_ID
import io.anonero.services.TorService
import io.anonero.services.WalletState
import io.anonero.store.LogRepository
import io.anonero.util.SecureClipboard
import io.anonero.util.WalletProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

private const val TAG = "AppViewModel"

/**
 * Activity-scoped coordinator for opening the wallet and starting its service.
 *
 * It does not implement wallet behavior: [openWallet] delegates profile/PIN resolution and
 * Keystore handling to [AnonWalletHandler], then resets session-scoped state (logs and
 * notifications). [startService] starts the wallet service asynchronously; callers must
 * wait for [WalletState] before assuming the wallet is synchronized.
 */
class AppViewModel(
    private val walletState: WalletState,
    private val torService: TorService,
    private val logRepository: LogRepository,
) :
    ViewModel() {

    private var splashInit = false
    private var walletExist: MutableSharedFlow<Boolean> = MutableSharedFlow(replay = 2)

    val isReady get() = splashInit
    val existWallet get() = walletExist as SharedFlow<Boolean>

    private val walletHandler: AnonWalletHandler by inject(AnonWalletHandler::class.java)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            walletExist.emit(
                WalletProfileStore.anyWalletExists(
                    AnonConfig.context!!,
                    AnonConfig.context!!.getSharedPreferences(
                        AnonConfig.PREFS,
                        android.content.Context.MODE_PRIVATE
                    )
                )
            )
            splashInit = true
        }
    }

    @Synchronized
    fun openWallet(pin: String): Boolean {
        return try {
            val opened = walletHandler.openWallet(pin)
            if (opened) {
                walletState.setProfile(io.anonero.util.WalletSession.profile)
                // Logs are shared app state. Clear them for every successful unlock so a
                // decoy session cannot read the legitimate wallet's history and a later
                // legitimate session cannot read the decoy's. This deliberately discards
                // previous-session diagnostics.
                viewModelScope.launch(Dispatchers.IO) { logRepository.clear() }
                // Do not leave a previous profile's notification (or an old activity
                // count) sitting in the system shade.
                AnonConfig.context?.let { context ->
                    (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as? android.app.NotificationManager)?.cancel(NOTIFICATION_ID + 1)
                    // A copied address or signature must not cross into the other profile.
                    SecureClipboard.clear(context)
                }
            }
            opened
        } catch (e: InvalidPin) {
            false
        }
    }

    @Synchronized
    fun startService() {
        walletHandler.scope.launch {
            walletHandler.startService()
        }.invokeOnCompletion {
            if (it != null)
                Timber.tag(TAG).e(it)
        }
    }


}
