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

package io.anonero.model

/**
 * Wallet2 coin-control view for the active wallet.
 *
 * [all] is the latest native output snapshot. Consumers filter spent outputs and scope
 * the list to the active account. Transaction construction uses the key image
 * (`CoinsInfo.key`). [freeze] and [thaw] use the output public key and check the refreshed
 * flag because the JNI mutation returns no result.
 *
 * The [handle] is borrowed from wallet2 and is valid only while the owning wallet is open.
 */
class Coins(
    private val handle: Long,
) {
    var all: List<CoinsInfo> = ArrayList()
        private set

    fun refresh() {
        val transactionInfos = refreshJ()
        all = transactionInfos
    }

    private external fun refreshJ(): List<CoinsInfo>

    external fun getCount(): Int

    // Freeze by output public key (not key image), then re-read wallet2's state.
    fun freeze(publicKey: String) {
        setFrozenJ(publicKey)
        refresh()
        check(all.any { it.pub_key == publicKey && it.frozen }) { "Output freeze was not applied" }
    }

    // Thaw by output public key, then re-read wallet2's state.
    fun thaw(publicKey: String) {
        thawJ(publicKey)
        refresh()
        check(all.any { it.pub_key == publicKey && !it.frozen }) { "Output thaw was not applied" }
    }

    private external fun setFrozenJ(publicKey: String)

    private external fun thawJ(publicKey: String)

    companion object {
        init {
            System.loadLibrary("anonero")
        }
    }
}
