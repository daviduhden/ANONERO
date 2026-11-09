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
import io.anonero.BuildConfig
import io.anonero.store.AnonLog
import io.anonero.store.LogRepository
import io.anonero.util.StealthMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Date


class AnonLogTree(
    private val logFile: File,
    private val logRepository: LogRepository
) : Timber.DebugTree() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs by lazy {
        AnonConfig.context?.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)
    }

    // Every persisted line rewrites the whole log store, so release builds keep info and
    // above; debug-level lines (per-block sync, QR payloads) stay in debug builds only.
    // Stealth mode suppresses persistence entirely: a log file is an at-rest record of
    // amounts (historically), node addresses and wallet lifecycle.
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        val stealth = prefs?.let { StealthMode.isEnabled(it) } == true
        return !stealth && (BuildConfig.DEBUG || priority >= Log.INFO)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Timber has already appended t's stack trace to message
        val item = AnonLog(date = Date().time, message = message, priority = priority, tag = tag ?: "Anon")
        ioScope.launch {
            try {
                logRepository.addItem(item)
            } catch (e: Exception) {
                // a failed write must not crash the app, and must not go through Timber:
                // that re-enters this tree and loops
                Log.w(TAG, "log write failed", e)
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
