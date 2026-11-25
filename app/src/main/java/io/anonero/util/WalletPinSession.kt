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

import android.content.SharedPreferences
import io.anonero.AnonConfig
import io.anonero.model.Wallet
import io.anonero.model.WalletManager

/** Transient proof of verification for a particular wallet and stored gate. Never persist it. */
internal class WalletPinSession private constructor(
    val wallet: Wallet,
    val profile: WalletProfile,
    private val prefs: SharedPreferences,
    private val credential: String,
) {
    fun current(): Boolean =
        WalletSession.profile == profile && WalletManager.instance?.wallet === wallet &&
            prefs.getString(WalletProfileStore.pinHashKey(profile), null) == credential

    companion object {
        fun verify(
            wallet: Wallet,
            profile: WalletProfile,
            prefs: SharedPreferences,
            pin: String,
        ): WalletPinSession? =
            WalletProfileStore.tryProfileMutation(onBusy = { null }) {
                val context = AnonConfig.context ?: return@tryProfileMutation null
                val file = WalletProfileStore.walletFile(context, profile)
                val key = WalletProfileStore.pinHashKey(profile)
                if (WalletSession.profile != profile || WalletManager.instance?.managedWalletAt(file) !== wallet ||
                    !PinHash.matches(pin, prefs.getString(key, null)) ||
                    !WalletProfileStore.upgradeVerifiedPin(prefs, profile, pin)
                ) {
                    return@tryProfileMutation null
                }
                val credential = prefs.getString(key, null) ?: return@tryProfileMutation null
                WalletPinSession(wallet, profile, prefs, credential).takeIf { it.current() }
            }
    }
}
