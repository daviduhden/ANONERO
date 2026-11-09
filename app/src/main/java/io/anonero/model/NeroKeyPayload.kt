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

package io.anonero.model

import io.anonero.AnonConfig
import kotlinx.serialization.Serializable
import org.json.JSONObject


/**
 * View-only key material used by the NERO flavor and by backups.
 *
 * It contains the public address and private view key, never the spend key. The payload is
 * sensitive: the view key reveals all incoming transactions and balances. Do not log it or
 * place it in an unencrypted file.
 */
@Serializable
class NeroKeyPayload(
    val primaryAddress: String,
    val privateViewKey: String,
    val restoreHeight: Long,
    val version: Int
) {

    fun toJSONObject(): JSONObject {
        return JSONObject()
            .apply {
                put("primaryAddress", primaryAddress)
                put("privateViewKey", privateViewKey)
                put("restoreHeight", restoreHeight)
                put("version", version)
            }
    }


    companion object {
        fun fromWallet(wallet: Wallet): NeroKeyPayload {
            val primaryAddress = wallet.getAddress(0)
            val privateViewKey = wallet.getSecretViewKey()
            val restoreHeight = wallet.getRestoreHeight()
            val version = AnonConfig.NERO_KEY_PAYLOAD_VERSION
            return NeroKeyPayload(
                primaryAddress,
                privateViewKey,
                restoreHeight,
                version
            )
        }
    }

}
