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
 * Monero network selector passed across JNI.
 *
 * [value] is the integer expected by wallet2 (`0` mainnet, `1` testnet, `2` stagenet).
 * [toString] is the persisted/display name used by node settings; [toStringForBackUp]
 * preserves the historical `NetworkType_*` spelling used inside encrypted backups.
 */
enum class NetworkType(
    val value: Int,
) {
    NetworkType_Mainnet(0),
    NetworkType_Testnet(1),
    NetworkType_Stagenet(2),
    ;

    companion object {
        fun fromInteger(n: Int): NetworkType? {
            when (n) {
                0 -> return NetworkType_Mainnet
                1 -> return NetworkType_Testnet
                2 -> return NetworkType_Stagenet
            }
            return null
        }
    }

    override fun toString(): String =
        when (this) {
            NetworkType_Mainnet -> "mainnet"
            NetworkType_Testnet -> "testnet"
            NetworkType_Stagenet -> "stagenet"
        }

    fun toStringForBackUp(): String =
        when (this) {
            NetworkType_Mainnet -> "NetworkType_Mainnet"
            NetworkType_Testnet -> "NetworkType_Testnet"
            NetworkType_Stagenet -> "NetworkType_Stagenet"
        }
}
