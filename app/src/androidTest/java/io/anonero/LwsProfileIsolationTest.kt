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
import io.anonero.util.LwsConfig
import io.anonero.util.WalletProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LwsProfileIsolationTest {
    @Test
    fun legacyMainSettingsDoNotAuthorizeDecoyAndWritesStaySeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "lws-profile-isolation-fixture"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            assertTrue(prefs.edit().clear().commit())
            // Fixture representing the installed, pre-isolation MAIN configuration.
            assertTrue(
                prefs
                    .edit()
                    .putBoolean("lwsEnabled", true)
                    .putString("lwsHost", "main.example.onion")
                    .putInt("lwsPort", 8443)
                    .putBoolean("lwsTls", false)
                    .commit(),
            )
            val main = LwsConfig(true, "main.example.onion", 8443)
            assertEquals(main, LwsConfig.load(prefs, WalletProfile.MAIN))
            val decoyDefault = LwsConfig.load(prefs, WalletProfile.DECOY)
            assertFalse(decoyDefault.enabled)
            assertEquals("", decoyDefault.host)
            val decoy = LwsConfig(true, "decoy.example.onion", 9443)
            LwsConfig.save(prefs, decoy, WalletProfile.DECOY)
            assertEquals(decoy, LwsConfig.load(prefs, WalletProfile.DECOY))
            assertEquals(main, LwsConfig.load(prefs, WalletProfile.MAIN))
            LwsConfig.save(prefs, main.copy(enabled = false), WalletProfile.MAIN)
            assertEquals(decoy, LwsConfig.load(prefs, WalletProfile.DECOY))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
