/*
 * Copyright (c) 2026, David Uhden Collado
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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Serializes endpoint revocation with listener callbacks arriving on runtime threads. */
internal class TorListenerState<T : Any> {
    private val endpoint = MutableStateFlow<T?>(null)
    val flow: StateFlow<T?> = endpoint.asStateFlow()
    private var generation = 0L

    @Volatile
    var wanted = false
        private set

    @Synchronized
    fun start(): Boolean {
        if (wanted) return false
        wanted = true
        return true
    }

    @Synchronized
    fun reset(wantDaemon: Boolean) {
        wanted = wantDaemon
        update(null)
    }

    @Synchronized
    fun update(listener: T?) {
        val accepted = listener.takeIf { wanted }
        if (endpoint.value != accepted) {
            generation++
            endpoint.value = accepted
        }
    }

    @Synchronized
    fun snapshot(): Pair<T?, Long> = endpoint.value to generation
}
