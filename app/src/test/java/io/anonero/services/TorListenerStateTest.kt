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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorListenerStateTest {
    @Test
    fun stopRejectsLateListenerAndRevokesPreviousGeneration() {
        val listeners = TorListenerState<Int>()
        assertTrue(listeners.start())
        assertFalse(listeners.start())
        listeners.update(9050)
        val first = listeners.snapshot()
        assertEquals(9050, first.first)

        listeners.reset(wantDaemon = false)
        listeners.update(9050)
        assertNull(listeners.flow.value)
        assertTrue(listeners.snapshot().second > first.second)

        assertTrue(listeners.start())
        assertNull(listeners.flow.value)
        listeners.update(9050)
        assertTrue(listeners.snapshot().second > first.second)
    }
}
