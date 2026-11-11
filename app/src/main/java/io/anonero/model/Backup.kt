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

import io.anonero.model.node.Node
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

object VersionAsIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Version", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val jsonPrimitive =
            decoder as? kotlinx.serialization.json.JsonDecoder
                ?: error("This serializer only works with JSON")

        val element = jsonPrimitive.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    element.content.substringBefore(".").toIntOrNull() ?: 0
                } else {
                    element.int
                }
            }

            else -> {
                0
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: Int,
    ) {
        encoder.encodeInt(value) // always write as int
    }
}

/**
 * Versioned backup envelope. The version selects how the wallet files inside the archive
 * are keyed: v2 is PIN-keyed, v3 is passphrase-keyed, and v1 is the legacy format detected
 * by its `Salted__` header before this JSON is read.
 */
@Serializable
data class BackupPayload(
    val backup: Backup,
    @Serializable(with = VersionAsIntSerializer::class)
    val version: Int,
)

@Serializable
data class NodeBackup(
    val host: String,
    val isOnion: Boolean,
    val networkType: String,
    val password: String,
    val rpcPort: Int,
    val username: String,
)

@Serializable
data class Backup(
    val meta: BackupMeta,
    val node: NodeBackup,
    val nodes: List<Node> = emptyList(),
    val wallet: WalletBackup,
)

@Serializable
data class BackupMeta(
    val network: String,
    val timestamp: Long,
)

/**
 * Wallet data and secret material inside an encrypted backup.
 *
 * [seed] is the recovery mnemonic and is plaintext inside the encrypted envelope. Never log
 * this object or write it outside the encrypted archive. The `neroPayload` is present only
 * when the backup came from a view-only wallet.
 */
@Serializable
data class WalletBackup(
    val address: String,
    val balanceAll: Long,
    val isSynchronized: Boolean,
    val isWatchOnly: Boolean,
    val numAccounts: Int,
    val numSubaddresses: Int,
    val restoreHeight: Long?,
    val primaryAddress: String = "",
    val seed: String?,
    val neroPayload: NeroKeyPayload? = null,
)
