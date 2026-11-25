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

import io.anonero.model.Wallet
import io.anonero.model.node.DaemonInfo
import io.anonero.ui.home.LockScreenShortCut
import io.anonero.util.WalletProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises session and lock flow transitions without opening a native wallet. */
class WalletStateSessionTest {
    @Test
    fun `lock fault rejects unlock profile changes and online state until session reset`() =
        runBlocking {
            val state = WalletState()
            state.resetSession(WalletProfile.MAIN)
            state.recordBackgroundSyncFault()

            state.setOffline(false)
            state.setProfile(WalletProfile.DECOY)

            assertTrue(state.backgroundSync)
            assertTrue(state.backgroundSyncFaulted)
            assertTrue(state.offline)
            assertFalse(state.backgroundSyncRunning)
            assertEquals(WalletProfile.MAIN, state.profile.first())
            assertFalse(state.exitBackgroundSync("", null))

            state.resetSession(WalletProfile.MAIN)

            assertFalse(state.backgroundSync)
            assertFalse(state.backgroundSyncFaulted)
        }

    @Test
    fun `connection changes update only the cached daemon and reset discards it`() =
        runBlocking {
            val state = WalletState()
            val connected = Wallet.ConnectionStatus.ConnectionStatus_Connected
            val disconnected = Wallet.ConnectionStatus.ConnectionStatus_Disconnected
            state.updateDaemon(DaemonInfo("node.example", connected, 500L))

            state.setConnectionStatus(disconnected)

            assertEquals(disconnected, state.walletConnectionStatus.first())
            assertEquals(DaemonInfo("node.example", disconnected, 500L), state.daemonInfo.first())
            state.resetSession(WalletProfile.DECOY)
            assertNull(state.daemonInfo.first())
        }

    @Test
    fun `reopening the same profile clears residual sync and queued actions`() =
        runBlocking {
            val state = WalletState()
            state.resetSession(WalletProfile.MAIN)
            state.setOffline(true)
            state.setLoading(true)
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
            // A shortcut queued by the previous session must not reach a new collector.
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
