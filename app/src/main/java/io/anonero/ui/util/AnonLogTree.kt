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

package io.anonero.ui.util

import android.content.Context
import android.util.Log
import io.anonero.AnonConfig
import io.anonero.store.AnonLog
import io.anonero.store.DiagnosticPolicy
import io.anonero.store.LogRepository
import io.anonero.util.StealthMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date

class AnonLogTree(
    private val logRepository: LogRepository,
) : Timber.DebugTree() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs by lazy {
        AnonConfig.context?.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)
    }

    // Debug/info messages include activity patterns and untrusted payloads. Only
    // warnings/errors enter the reviewed persistent diagnostic policy in all builds.
    override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean {
        val stealth = prefs?.let { StealthMode.isEnabled(it) } == true
        return !stealth && priority >= Log.WARN
    }

    // This is the Timber tree itself; using Timber here would recurse into this method.
    @Suppress("LogNotTimber")
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        // Timber has already appended throwable text: sanitize before persistence.
        val item = DiagnosticPolicy.sanitize(AnonLog(date = Date().time, message = message, priority = priority, tag = tag ?: "Anon"))
        val generation = logRepository.generation
        ioScope.launch {
            try {
                logRepository.addItem(item, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // a failed write must not crash the app, and must not go through Timber:
                // that re-enters this tree and loops
                Log.w(TAG, "log write failed")
            }
        }
    }

    fun cleanup() {
        ioScope.cancel()
    }

    companion object {
        private const val TAG = "LogTree"
    }
}
