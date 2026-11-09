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
        val jsonPrimitive = decoder as? kotlinx.serialization.json.JsonDecoder
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

            else -> 0
        }
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value) // always write as int
    }
}

@Serializable
data class BackupPayload(
    val backup: Backup,
    @Serializable(with = VersionAsIntSerializer::class)
    val version: Int
)

@Serializable
data class NodeBackup(
    val host: String,
    val isOnion: Boolean,
    val networkType: String,
    val password: String,
    val rpcPort: Int,
    val username: String
)

@Serializable
data class Backup(
    val meta: BackupMeta,
    val node: NodeBackup,
    val nodes: List<Node> = emptyList(),
    val wallet: WalletBackup
)


@Serializable
data class BackupMeta(
    val network: String,
    val timestamp: Long
)

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
    val neroPayload: NeroKeyPayload? = null
)