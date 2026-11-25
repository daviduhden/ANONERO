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

package io.anonero.model

import java.io.File

/**
 * Retains auxiliary wallets by canonical file path until native close succeeds.
 *
 * A failed or throwing close leaves ownership intact for a retry. Path aliases share the
 * same entry and a caller must close the instance actually returned by [open].
 */
internal class RetainedWallets<T : Any>(
    private val closeNative: (T) -> Boolean,
) {
    private val wallets = mutableMapOf<String, T>()

    @Synchronized
    fun open(
        path: String,
        create: () -> T,
    ): T {
        val key = File(path).canonicalPath
        check(key !in wallets) { "Auxiliary wallet is still open" }
        return create().also { wallets[key] = it }
    }

    @Synchronized
    fun close(wallet: T): Boolean {
        val entry = wallets.entries.firstOrNull { it.value === wallet }
        checkNotNull(entry) { "Auxiliary wallet is not owned" }
        if (!closeNative(wallet)) return false
        wallets.remove(entry.key)
        return true
    }

    @Synchronized
    fun closeAt(path: String): Boolean {
        val wallet = wallets[File(path).canonicalPath] ?: return true
        return close(wallet)
    }

    @Synchronized
    fun contains(path: String): Boolean = File(path).canonicalPath in wallets

    /** Holds the same monitor as [open] throughout a guarded file operation. */
    @Synchronized
    fun withPathClosed(
        path: String,
        action: () -> Boolean,
    ): Boolean {
        if (File(path).canonicalPath in wallets) return false
        return action()
    }

    @Synchronized
    fun containsIn(directory: File): Boolean {
        val root = directory.canonicalPath
        return wallets.keys.any { it == root || it.startsWith(root + File.separator) }
    }

    @Synchronized
    fun isEmpty(): Boolean = wallets.isEmpty()
}
