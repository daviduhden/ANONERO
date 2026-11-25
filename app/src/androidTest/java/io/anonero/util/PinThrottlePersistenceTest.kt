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

package io.anonero.util

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinThrottlePersistenceTest {
    @Test
    fun failureToPersistAttemptRefusesVerificationEvenWhenMemoryChanged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "pin_throttle_persistence_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val failing =
            object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun commit(): Boolean {
                            // SharedPreferences can update its in-memory values even on a failed disk write.
                            editor.commit()
                            return false
                        }
                    }
                }
            }
        try {
            val result = runCatching { PinThrottle.tryAttempt(failing, now = 60_000L) }
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            // A reboot now requires a new durable timestamp even below the normal lockout threshold.
            assertEquals(30_000L, PinThrottle.waitLeft(failing, now = 1L))
            assertEquals(0L, PinThrottle.tryAttempt(prefs, now = 60_001L))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
