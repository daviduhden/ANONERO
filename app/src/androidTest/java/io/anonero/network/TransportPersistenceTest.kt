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
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportPersistenceTest {
    @Test
    fun commitFailureIsReportedEvenWhenPreferencesChangedInMemory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "transport_persistence_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val initial = TransportConfig()
        val updated = TransportConfig(mode = TransportMode.SYSTEM_VPN)
        try {
            assertTrue(initial.save(prefs))
            val failing =
                object : SharedPreferences by prefs {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = prefs.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun commit(): Boolean {
                                // Model Android's memory-before-disk semantics deterministically.
                                editor.commit()
                                return false
                            }
                        }
                    }
                }
            assertFalse(updated.save(failing))
            assertEquals(updated, TransportConfig.load(prefs))
            assertTrue(initial.save(prefs))
            assertEquals(initial, TransportConfig.load(prefs))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
