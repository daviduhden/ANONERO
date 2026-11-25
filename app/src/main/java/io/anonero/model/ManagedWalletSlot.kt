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

package io.anonero.model

import java.io.File

/** Reserves the managed path until native close succeeds. */
internal class ManagedWalletSlot<T : Any> {
    private var path: String? = null

    @Volatile
    var visible: T? = null
        private set

    @Synchronized
    fun open(
        file: File,
        initialize: (T) -> Unit = {},
        acquire: () -> T,
    ): T {
        check(path == null) { "Another wallet is already managed" }
        path = file.canonicalPath
        val wallet =
            try {
                acquire()
            } catch (failure: Throwable) {
                path = null
                throw failure
            }
        try {
            initialize(wallet)
        } finally {
            // Initialization can fail after acquisition. Keep the handle owned for cleanup.
            visible = wallet
        }
        return wallet
    }

    fun close(
        wallet: T,
        release: () -> Boolean,
    ): Boolean {
        synchronized(this) {
            if (visible !== wallet) return false
            visible = null
        }
        var closed = false
        try {
            // Native close can join a callback thread. Do not hold the slot monitor.
            closed = release()
            return closed
        } finally {
            synchronized(this) {
                if (closed) path = null else visible = wallet
            }
        }
    }

    @Synchronized
    fun isIdle(): Boolean = path == null

    @Synchronized
    fun visibleAt(file: File): T? = visible.takeIf { path == file.canonicalPath }

    @Synchronized
    fun withPathClosed(
        file: File,
        action: () -> Boolean,
    ): Boolean = path != file.canonicalPath && action()
}
