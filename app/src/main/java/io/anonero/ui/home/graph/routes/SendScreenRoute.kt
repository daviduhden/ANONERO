package io.anonero.ui.home.graph.routes

import android.net.Uri
import io.anonero.AnonConfig
import io.anonero.model.Wallet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import androidx.core.net.toUri


// PaymentUri is a data class that represents a payment URI.
// example: monero:84jaiKcu3....d9a49f5&tx_amount=0.000000000010&recipient_name=el00ruobuob%20Stagenet%20wallet&tx_description=Testing%20out%20the%20make_uri%20function.
// PaymentUri can also handle non-URI addresses, which will be validated and wrapped in a PaymentUri object.

@Serializable
data class SendScreenRoute(
    val address: String,
    val amount: Double = 0.0,
    @SerialName("payment_id")
    val paymentId: String? = null,
    @SerialName("recipient_name")
    val recipientName: String = "",
    @SerialName("tx_description")
    val txDescription: String = "",
    @SerialName("coins")
    val coins: List<String> = emptyList(),
) {
    companion object {
        fun parse(uri: String): SendScreenRoute? {
            try {
                if (uri.startsWith("monero")) {

                    var parsedUri = uri.toUri()

                    if (parsedUri.schemeSpecificPart != "monero://") {
                        parsedUri = uri.replace("monero:", "monero://").toUri()
                    }

                    // Extract address from scheme-specific part
                    val address = parsedUri.host ?: return null

                    if (!Wallet.isAddressValid(address, AnonConfig.getNetworkType().value)) {
                        return null
                    }
                    // Extract query parameters safely
                    val amount = parsedUri.getQueryParameter("tx_amount")?.toDoubleOrNull() ?: 0.0
                    val paymentId = parsedUri.getQueryParameter("payment_id")
                    val recipientName = parsedUri.getQueryParameter("recipient_name") ?: ""
                    val txDescription = parsedUri.getQueryParameter("tx_description") ?: ""

                    return SendScreenRoute(
                        address = address,
                        amount = amount,
                        paymentId = paymentId,
                        recipientName = recipientName,
                        txDescription = txDescription
                    )
                } else {
                    Wallet.isAddressValid(
                        uri,
                        AnonConfig.getNetworkType().value
                    )
                        .let {
                            if (it) {
                                return SendScreenRoute(address = uri)
                            }
                        }
                }
            } catch (e: Exception) {
                Timber.tag("PaymentUri:Parse ").e(e)
                return null
            }
            return null
        }
    }
}
