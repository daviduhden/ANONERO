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

package io.anonero.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asLiveData
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.node.NodeFields
import io.anonero.network.TransportConfig
import io.anonero.services.TorService
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.WalletProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@Composable
fun TorSplash(
    modifier: Modifier = Modifier,
    enableTor: Boolean,
    content: @Composable () -> Unit = {},
) {
    var anonPrefs: SharedPreferences = koinInject<SharedPreferences>(named(WALLET_PREFERENCES))
    val context = LocalContext.current
    var walletExist by remember {
        mutableStateOf(WalletProfileStore.anyWalletExists(context, anonPrefs))
    }
    val torService: TorService = koinInject<TorService>()
    val socks by torService.socksFlow.asLiveData().observeAsState(null)
    val scope = rememberCoroutineScope()
    var isAppReady by remember { mutableStateOf(false) }
    var hasNetWork by remember { mutableStateOf(false) }

    val progressAnim by animateFloatAsState(
        targetValue = if (socks != null) .98f else 0.4f,
        label = "progressAnim",
    )

    if (walletExist) {
        LaunchedEffect(key1 = walletExist, socks) {
            // if user disabled tor and uses manual proxy, skip tor splash
            hasNetWork = isNetworkConnected(context)
            if (enableTor.not()) {
                return@LaunchedEffect
            }
            scope.launch(Dispatchers.IO) {
                walletExist = WalletProfileStore.anyWalletExists(context, anonPrefs)
                // if user selected an external transport, skip tor splash
                if (!TransportConfig.load(anonPrefs).mode.usesEmbeddedTor) {
                    isAppReady = true
                } else {
                    if ((socks != null)) {
                        scope.launch(Dispatchers.IO) {
                            isAppReady = true
                        }
                    }
                }
            }
        }
        // Timeout: proceed after 20s even if Tor hasn't connected
        LaunchedEffect(Unit) {
            delay(20000)
            if (!isAppReady) {
                isAppReady = true
            }
        }
    }

    // no need to show tor splash if user disabled tor
    // if there is no wallet file, skip tor splash
    val lwsEnabled = anonPrefs.getBoolean(io.anonero.util.PREFS_LWS_ENABLED, false)
    if (!enableTor || !walletExist || !hasNetWork ||
        !TransportConfig.load(anonPrefs).mode.usesEmbeddedTor ||
        (!lwsEnabled && anonPrefs.getString(NodeFields.RPC_HOST.value, "").isNullOrEmpty())
    ) {
        return content()
    }
    AnimatedContent(targetState = isAppReady) {
        if (it) {
            content()
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_anon),
                    contentDescription = stringResource(R.string.app_logo),
                    modifier = Modifier.size(120.dp),
                )
                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 16.dp,
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LinearProgressIndicator(
                        progress = { progressAnim },
                        modifier =
                            Modifier
                                .fillMaxWidth(0.6f)
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 16.dp,
                                ),
                        strokeCap = StrokeCap.Round,
                        color = Color.White,
                        drawStopIndicator = {},
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    Text(
                        text = stringResource(R.string.initializing_tor),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

fun isNetworkConnected(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
