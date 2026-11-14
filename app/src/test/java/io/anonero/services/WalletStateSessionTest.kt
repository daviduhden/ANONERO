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

package io.anonero.services

import io.anonero.ui.home.LockScreenShortCut
import io.anonero.util.WalletProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WalletStateSessionTest {
    @Test
    fun `reopening the same profile clears residual sync and queued actions`() =
        runBlocking {
            val state = WalletState()
            state.resetSession(WalletProfile.MAIN)
            state.setOffline(true)
            state.setLoading(true)
            state.setBackGroundSync(true)
            state.setConnectionStatus(io.anonero.model.Wallet.ConnectionStatus.ConnectionStatus_Connected)
            state.syncUpdate(SyncProgress(0.5f, 100L))
            state.emitUnlockShortcut(LockScreenShortCut.HOME)

            state.resetSession(WalletProfile.MAIN)

            assertFalse(state.offline)
            assertFalse(state.isLoading.first())
            assertFalse(state.isSyncing)
            assertFalse(state.backgroundSync)
            assertFalse(state.backgroundSyncRunning)
            assertNull(state.connectionStatus.first())
            assertNull(state.connectionStatusValue)
            assertNull(state.syncProgress.first())
            assertNull(withTimeoutOrNull(50L) { state.unlockShortcut.first() })
        }

    @Test
    fun `switching profiles starts with empty wallet data`() =
        runBlocking {
            val state = WalletState()
            state.resetSession(WalletProfile.MAIN)
            state.setProfile(WalletProfile.DECOY)

            assertEquals(WalletProfile.DECOY, state.profile.first())
            assertEquals(0, state.activeAccount.first())
            assertEquals(emptyList<Any>(), state.transactions.first())
            assertEquals(emptyList<Any>(), state.accounts.first())
            assertEquals(emptyList<Any>(), state.coins.first())
            assertNull(state.balanceInfo.first())
            assertNull(state.unLockedBalance.first())
            assertNull(state.nextAddress.first())
            assertNull(state.daemonInfo.first())
        }
}
