package io.anonero.model

import io.anonero.AnonConfig
import kotlinx.serialization.Serializable
import org.json.JSONObject


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