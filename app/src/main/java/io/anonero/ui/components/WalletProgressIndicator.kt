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

package io.anonero.ui.components

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.asLiveData
import io.anonero.R
import io.anonero.model.Wallet
import io.anonero.services.WalletState
import io.anonero.util.Formats
import org.koin.java.KoinJavaComponent.inject
import java.util.Locale

@Composable
fun networkConnected(): State<Boolean> {
    val context = LocalContext.current
    return produceState(
        initialValue = run {
            val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
    ) {
        val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            ?: return@produceState
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { value = true }
            override fun onLost(network: Network) { value = false }
        }
        cm.registerNetworkCallback(request, callback)
        awaitDispose { cm.unregisterNetworkCallback(callback) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletProgressIndicator(modifier: Modifier = Modifier, refreshIndicatorProgress: Float = 0.0f) {
    val walletState: WalletState by inject(WalletState::class.java)

    val showIndefiniteLoading by walletState.isLoading.asLiveData().observeAsState(false)
    val syncProgress by walletState.syncProgress.asLiveData().observeAsState(null)
    val connectionStatus by walletState.connectionStatus.asLiveData().observeAsState(null)
    val isConnected = connectionStatus == Wallet.ConnectionStatus.ConnectionStatus_Connected
    val isNetworkConnected by networkConnected()
    val isSyncing = syncProgress != null && syncProgress!!.left > 0L
    val offline by walletState.offlineFlow.asLiveData().observeAsState(walletState.offline)
    AnimatedVisibility(
        !offline && (showIndefiniteLoading || syncProgress != null || !isConnected || !isNetworkConnected || refreshIndicatorProgress != 0.0f),
        modifier = modifier
            .animateContentSize()
    ) {
        if (isSyncing) {
            val progress = syncProgress ?: return@AnimatedVisibility
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    vertical = 12.dp
                )
            ) {
                LinearProgressIndicator(
                    progress = {
                        progress.progress
                    },
                    trackColor = MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.2f
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                if (progress.left > 50) {
                    Text(
                        pluralStringResource(
                            R.plurals.blocks_left_count,
                            progress.left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            Formats.convertNumber(progress.left, Locale.getDefault())
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (refreshIndicatorProgress != 0.0f && !showIndefiniteLoading && isConnected && isNetworkConnected) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                progress = {
                    refreshIndicatorProgress
                },
                trackColor = MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.2f
                ),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                trackColor = MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.2f
                ),
            )
        }

    }

}
