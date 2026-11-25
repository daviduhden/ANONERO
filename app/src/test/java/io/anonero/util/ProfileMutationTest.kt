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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMutationTest {
    @Test
    fun competingDecoyMutationIsRefusedUntilRollbackFinishes() {
        assertThrows(IllegalStateException::class.java) {
            WalletProfileStore.tryProfileMutation<Unit>(onBusy = { error("unexpected busy profile") }) {
                assertFalse(WalletProfileStore.tryProfileMutation(onBusy = { false }) { true })
                throw IllegalStateException("rollback")
            }
        }
        assertTrue(WalletProfileStore.tryProfileMutation(onBusy = { false }) { true })
    }
}
