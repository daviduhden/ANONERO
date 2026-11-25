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

package io.anonero

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.anonero.store.AnonLog
import io.anonero.store.LogRepository
import io.anonero.util.PREFS_STEALTH_MODE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticIsolationTest {
    @Test
    fun staleSessionWriteAndStealthWriteAreRejected() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)
            val originalStealth = prefs.getBoolean(PREFS_STEALTH_MODE, false)
            val repository = LogRepository(context)
            val event = AnonLog(123456L, "synthetic-wallet", "Node connection failed", 5)
            try {
                prefs.edit().putBoolean(PREFS_STEALTH_MODE, false).commit()
                repository.clear()
                // Simulate a write that obtained its session token before the clear.
                val staleGeneration = repository.generation
                repository.clear()
                repository.addItem(event, staleGeneration)
                assertEquals(emptyList<AnonLog>(), repository.logFlow.first())
                repository.addItem(event)
                assertEquals(1, repository.logFlow.first().size)
                repository.clear()
                prefs.edit().putBoolean(PREFS_STEALTH_MODE, true).commit()
                repository.addItem(event)
                assertEquals(emptyList<AnonLog>(), repository.logFlow.first())
            } finally {
                repository.clear()
                prefs.edit().putBoolean(PREFS_STEALTH_MODE, originalStealth).commit()
            }
        }
}
