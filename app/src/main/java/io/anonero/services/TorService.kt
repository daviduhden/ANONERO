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

package io.anonero.services

import android.content.SharedPreferences
import io.anonero.AnonConfig
import io.matthewnelson.kmp.tor.runtime.Action.Companion.restartDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber


private const val TAG = "TorService"

/*
 * Owns the embedded Tor daemon and publishes its SOCKS endpoint to the wallet layer.
 *
 * The service is stateful: [start]/[restart] set `wanted` and [stop]/[dispose] clear it.
 * If the daemon dies while wanted, the state observer clears the cached endpoint and
 * retries after ten seconds. The wallet must not connect until a SOCKS endpoint has been
 * observed through [socksFlow]; `AnonWalletHandler.applyProxy` enforces that wait and
 * fails closed rather than using a direct connection.
 *
 * Tor actions run in [scope] with a SupervisorJob and are logged rather than crashing
 * the app when Android or an update stops the daemon mid-action.
 */
class TorService(prefs: SharedPreferences) {

    // A Tor action throws when the daemon dies or is stopped mid-action (the app being
    // killed for an update, Android reclaiming the process). Log it instead of crashing:
    // the UI already shows Tor as not connected.
    val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, e ->
        Timber.tag(TAG).w(e, "Tor action failed")
    })

    private val _socksSharedFlow =
        MutableSharedFlow<IPSocketAddress>(replay = 1)

    private val _torLogs =
        MutableSharedFlow<String>(
            replay = 20, onBufferOverflow = BufferOverflow.DROP_OLDEST,
            extraBufferCapacity = 50
        )
    private var _socks: IPSocketAddress? = null

    // set by start()/restart(), cleared by stop()/dispose(): Tor is supposed to be running
    @Volatile
    private var wanted = false
    val socks get() = _socks
    val socksFlow get() = _socksSharedFlow
    val torLogs: Flow<String> get() = _torLogs.asSharedFlow()

    val runtime: TorRuntime = TorRuntime.Builder(environment = AnonConfig.getTorConfig(scope)) {

        TorEvent.entries().forEach { event ->
            if (event == TorEvent.ERR)
                observerStatic(event, OnEvent.Executor.Immediate) { data ->
                    _torLogs.tryEmit(data)
                }
        }
        RuntimeEvent.entries().forEach { event ->
            observerStatic(event, OnEvent.Executor.Immediate) { data ->
                _torLogs.tryEmit(data.toString())
            }
        }
        observerStatic(RuntimeEvent.STATE) { state ->
            // the daemon went down without stop() -- killed, or a start that failed: drop the
            // dead port and start again; the new port reaches the wallet through socksFlow
            if (state.daemon.isOff && wanted) {
                _socks = null
                scope.launch {
                    delay(10_000)
                    if (wanted) runtime.startDaemonAsync()
                }
            }
        }
        observerStatic(RuntimeEvent.LISTENERS) {
            val socketAddr = it.socks.firstOrNull()
            if (socketAddr != null) {
                _socks = socketAddr
                scope.launch {
                    _socksSharedFlow.emit(socketAddr) // Ensure the value is emitted
                }
            }
        }
        config { _ ->
            TorOption.SocksPort.configure { auto() }
        }
        required(TorEvent.ERR)
        required(TorEvent.WARN)
        required(TorEvent.INFO)
    }

    fun start() {
        wanted = true
        scope.launch {
            runtime.startDaemonAsync()
        }
    }

    fun stop() {
        wanted = false
        _socks = null
        scope.launch {
            runtime.stopDaemonAsync()
        }
    }


    fun restart() {
        wanted = true
        scope.launch {
            runtime.restartDaemonAsync()
        }
    }

    fun dispose() {
        wanted = false
        scope.launch {
            runtime.stopDaemonAsync()
        }.invokeOnCompletion {
            it?.let {
                Timber.tag(TAG).i("Tor dispose exception: ${it.message}")
            }
            scope.cancel()
        }
    }
}
