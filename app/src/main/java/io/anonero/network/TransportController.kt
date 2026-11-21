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

package io.anonero.network

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.anonero.services.TorService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val SOCKS_PROBE_TIMEOUT_MS = 3_000

private val I2P_JAVA_PACKAGES =
    listOf(
        "net.i2p.android",
        "net.i2p.android.donate",
        "net.i2p.android.router",
    )

/**
 * One transport provider. Providers only acquire an endpoint; wallet2 is proxied by
 * [io.anonero.services.AnonWalletHandler], so transport code never talks to the wallet.
 */
interface TransportProvider {
    val mode: TransportMode

    suspend fun acquire(): TransportEndpoint

    fun release()
}

private class EmbeddedTorProvider(
    private val torService: TorService,
) : TransportProvider {
    override val mode: TransportMode = TransportMode.EMBEDDED_TOR
    private var released = false

    override suspend fun acquire(): TransportEndpoint {
        synchronized(this) {
            if (released) throw TransportUnavailableException(TransportFailure.TRANSPORT_CHANGED)
            torService.start()
        }
        val deadline = 60_000L
        var waited = 0L
        while (torService.socks == null) {
            synchronized(this) {
                if (released) throw TransportUnavailableException(TransportFailure.TRANSPORT_CHANGED)
            }
            if (waited >= deadline) {
                throw TransportUnavailableException(TransportFailure.EMBEDDED_TOR_TIMEOUT)
            }
            delay(200)
            waited += 200
        }
        val socket =
            torService.socks
                ?: throw TransportUnavailableException(TransportFailure.TRANSPORT_CHANGED)
        return TransportEndpoint.Socks5(
            ProxyEndpoint(socket.address.value, socket.port.value),
        )
    }

    @Synchronized
    override fun release() {
        released = true
    }
}

private class ExternalSocksProvider(
    override val mode: TransportMode,
    private val config: TransportConfig,
) : TransportProvider {
    override suspend fun acquire(): TransportEndpoint {
        val endpoint =
            config.proxyEndpoint
                ?: throw TransportUnavailableException(TransportFailure.INVALID_CONFIGURATION)
        if (!canConnect(endpoint)) {
            throw TransportUnavailableException(TransportFailure.EXTERNAL_PROXY_UNREACHABLE)
        }
        return TransportEndpoint.Socks5(endpoint)
    }

    override fun release() = Unit

    private suspend fun canConnect(endpoint: ProxyEndpoint): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    // The endpoint is validated as numeric/loopback, so this opens no DNS
                    // path and never contacts a Monero node directly.
                    socket.connect(InetSocketAddress(endpoint.host, endpoint.port), SOCKS_PROBE_TIMEOUT_MS)
                    socket.soTimeout = SOCKS_PROBE_TIMEOUT_MS
                    acceptsSocks5WithoutAuthentication(socket.getInputStream(), socket.getOutputStream())
                }
            } catch (_: IOException) {
                false
            }
        }
}

/**
 * The probe sends only the SOCKS5 method greeting. The caller closes the socket without
 * sending CONNECT, BIND, a destination, or wallet metadata.
 */
internal fun acceptsSocks5WithoutAuthentication(
    input: InputStream,
    output: OutputStream,
): Boolean {
    // RFC 1928: version 5, one offered method, no authentication.
    output.write(byteArrayOf(0x05, 0x01, 0x00))
    output.flush()
    return input.read() == 0x05 && input.read() == 0x00
}

/**
 * Owns the user's connectivity choice and produces the proxy endpoint consumed by the
 * wallet. It deliberately does not expose kmp-tor types.
 *
 * Switching transport releases the previous provider before persisting the new mode.
 * System VPN mode never returns a direct endpoint unless a VPN network is active, and
 * never changes to a direct connection on loss.
 */
class TransportController(
    context: Context,
    private val prefs: SharedPreferences,
    private val torService: TorService,
) {
    private val appContext = context.applicationContext
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _config = MutableStateFlow(TransportConfig.load(prefs))
    val config: StateFlow<TransportConfig> = _config.asStateFlow()

    private val _state =
        MutableStateFlow(
            TransportState(
                mode = _config.value.mode,
                status = TransportStatus.STOPPED,
                vpnActive = hasVpnTransport(),
            ),
        )
    val state: StateFlow<TransportState> = _state.asStateFlow()

    private var provider: TransportProvider? = null
    private var generation = 0L
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshVpnState()

            override fun onLost(network: Network) = refreshVpnState()

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = refreshVpnState()
        }

    init {
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
        refreshVpnState()
    }

    @Synchronized
    fun saveConfig(newConfig: TransportConfig): Result<Unit> {
        val error = newConfig.validationError()
        if (error != null) return Result.failure(IllegalArgumentException(error.name))
        val previous = _config.value
        val transportChanged = previous.requiresProviderRelease(newConfig)
        if (transportChanged) {
            release()
            if (previous.mode.usesEmbeddedTor && !newConfig.mode.usesEmbeddedTor) {
                torService.stop()
            }
        }
        val persisted = runCatching { newConfig.save(prefs) }.getOrDefault(false)
        if (!persisted) {
            // commit can update memory even when its disk write fails. Restore only
            // network configuration; never snapshot the preference map containing secrets.
            val restored = runCatching { previous.save(prefs) }.getOrDefault(false)
            release()
            _state.value =
                TransportState(
                    mode = previous.mode,
                    status = TransportStatus.ERROR,
                    failure = TransportFailure.INVALID_CONFIGURATION,
                    vpnActive = hasVpnTransport(),
                )
            val message =
                if (restored) {
                    "Transport configuration could not be persisted"
                } else {
                    "Transport configuration persistence and restoration failed"
                }
            return Result.failure(IOException(message))
        }
        _config.value = newConfig
        if (transportChanged) {
            _state.value =
                TransportState(
                    mode = newConfig.mode,
                    status = TransportStatus.STOPPED,
                    vpnActive = hasVpnTransport(),
                )
        }
        return Result.success(Unit)
    }

    suspend fun authorize(): TransportEndpoint {
        val (current, authorizationGeneration) = synchronized(this) { _config.value to generation }

        fun publish(state: TransportState) {
            synchronized(this) {
                if (generation != authorizationGeneration || _config.value != current) {
                    throw TransportUnavailableException(TransportFailure.TRANSPORT_CHANGED)
                }
                if (state.endpoint == TransportEndpoint.Direct && !hasVpnTransport()) {
                    generation++
                    _state.value =
                        state.copy(
                            status = TransportStatus.UNAVAILABLE,
                            endpoint = null,
                            failure = TransportFailure.VPN_INACTIVE,
                            vpnActive = false,
                        )
                    throw TransportUnavailableException(TransportFailure.VPN_INACTIVE)
                }
                _state.value = state
            }
        }
        val error = current.validationError()
        if (error != null) {
            publish(
                TransportState(
                    mode = current.mode,
                    status = TransportStatus.ERROR,
                    failure = TransportFailure.INVALID_CONFIGURATION,
                    vpnActive = hasVpnTransport(),
                ),
            )
            throw TransportUnavailableException(TransportFailure.INVALID_CONFIGURATION)
        }

        if (current.mode == TransportMode.I2P_JAVA && !isI2pJavaInstalled()) {
            publish(
                TransportState(
                    mode = current.mode,
                    status = TransportStatus.UNAVAILABLE,
                    failure = TransportFailure.I2P_APP_MISSING,
                    vpnActive = hasVpnTransport(),
                ),
            )
            throw TransportUnavailableException(TransportFailure.I2P_APP_MISSING)
        }
        if (current.mode == TransportMode.SYSTEM_VPN) {
            if (!hasVpnTransport()) {
                publish(
                    TransportState(
                        mode = current.mode,
                        status = TransportStatus.UNAVAILABLE,
                        failure = TransportFailure.VPN_INACTIVE,
                        vpnActive = false,
                    ),
                )
                throw TransportUnavailableException(TransportFailure.VPN_INACTIVE)
            }
            publish(
                TransportState(
                    mode = current.mode,
                    status = TransportStatus.READY,
                    endpoint = TransportEndpoint.Direct,
                    vpnActive = true,
                ),
            )
            return TransportEndpoint.Direct
        }

        val active =
            synchronized(this) {
                if (generation != authorizationGeneration || _config.value != current) {
                    throw TransportUnavailableException(TransportFailure.TRANSPORT_CHANGED)
                }
                if (current.mode != TransportMode.EMBEDDED_TOR) {
                    torService.stop()
                }
                provider
                    ?.takeIf { it.mode == current.mode }
                    ?: factory(current).also { provider = it }
            }
        return try {
            val endpoint = active.acquire()
            publish(
                TransportState(
                    mode = current.mode,
                    status = TransportStatus.READY,
                    endpoint = endpoint,
                    vpnActive = hasVpnTransport(),
                ),
            )
            endpoint
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransportUnavailableException) {
            if (synchronized(this) { generation != authorizationGeneration || _config.value != current }) throw e
            publish(
                TransportState(
                    mode = current.mode,
                    status = TransportStatus.UNAVAILABLE,
                    failure = e.failure,
                    vpnActive = hasVpnTransport(),
                ),
            )
            throw e
        } catch (e: Exception) {
            if (synchronized(this) { generation != authorizationGeneration || _config.value != current }) {
                throw TransportUnavailableException(TransportFailure.TRANSPORT_CHANGED)
            }
            publish(
                TransportState(
                    mode = current.mode,
                    status = TransportStatus.ERROR,
                    failure = TransportFailure.INTERNAL_ERROR,
                    vpnActive = hasVpnTransport(),
                ),
            )
            throw TransportUnavailableException(TransportFailure.INTERNAL_ERROR)
        }
    }

    /** Drops the active endpoint. Callers must re-authorize before the next wallet call. */
    @Synchronized
    fun release() {
        generation++
        provider?.release()
        provider = null
        _state.value =
            TransportState(
                mode = _config.value.mode,
                status = TransportStatus.STOPPED,
                vpnActive = hasVpnTransport(),
            )
    }

    fun isVpnActive(): Boolean = hasVpnTransport()

    /**
     * Detects the official I2P for Android package variants. Package visibility is
     * declared in AndroidManifest.xml; this never starts or configures the app.
     */
    @Suppress("DEPRECATION")
    fun isI2pJavaInstalled(): Boolean =
        I2P_JAVA_PACKAGES.any { packageName ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.packageManager.getPackageInfo(
                        packageName,
                        android.content.pm.PackageManager.PackageInfoFlags
                            .of(0),
                    )
                } else {
                    appContext.packageManager.getPackageInfo(packageName, 0)
                }
            }.isSuccess
        }

    fun dispose() {
        release()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
    }

    private fun factory(config: TransportConfig): TransportProvider =
        when (config.mode) {
            TransportMode.EMBEDDED_TOR -> {
                EmbeddedTorProvider(torService)
            }

            TransportMode.ORBOT,
            TransportMode.INVIZIBLE_TOR,
            TransportMode.I2PD,
            TransportMode.INVIZIBLE_I2P,
            TransportMode.I2P_JAVA,
            TransportMode.CUSTOM_PROXY,
            -> {
                ExternalSocksProvider(config.mode, config)
            }

            TransportMode.SYSTEM_VPN -> {
                throw TransportUnavailableException(TransportFailure.VPN_INACTIVE)
            }
        }

    private fun hasVpnTransport(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    @Synchronized
    private fun refreshVpnState() {
        val vpn = hasVpnTransport()
        val current = _state.value
        if (current.mode == TransportMode.SYSTEM_VPN && !vpn && (current.vpnActive || current.endpoint != null)) {
            generation++
        }
        _state.value = current.observeVpn(vpn)
    }
}
