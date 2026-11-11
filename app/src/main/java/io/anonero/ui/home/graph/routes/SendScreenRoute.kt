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

package io.anonero.ui.home.graph.routes

import android.net.Uri
import androidx.core.net.toUri
import io.anonero.AnonConfig
import io.anonero.model.Wallet
import io.anonero.util.MoneroAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

// PaymentUri is a data class that represents a payment URI.
// example: monero:84jaiKcu3....d9a49f5&tx_amount=0.000000000010&recipient_name=el00ruobuob%20Stagenet%20wallet&tx_description=Testing%20out%20the%20make_uri%20function.
// PaymentUri can also handle non-URI addresses, which will be validated and wrapped in a PaymentUri object.

@Serializable
data class SendScreenRoute(
    val address: String,
    // Atomic units. Kept as Long so no payment amount ever passes through Double.
    val amount: Long = 0L,
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
        private val ZERO_AMOUNT = Regex("^0(?:\\.0{1,12})?$")

        /**
         * Parses an optional Monero URI `tx_amount` in XMR. `null` means malformed or
         * over-precise; an omitted or explicit zero amount means "no amount prefilled".
         * The conversion is exact and never uses Double.
         */
        internal fun parseAmountParameter(raw: String?): Long? {
            if (raw == null) return 0L
            val text = raw.trim()
            if (text.isEmpty()) return 0L
            MoneroAmount.parseExact(text)?.let { return it }
            return if (ZERO_AMOUNT.matches(text)) 0L else null
        }

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
                    // Extract query parameters safely. Reject a malformed or over-precise
                    // amount instead of silently changing what the user will send.
                    val amount =
                        parseAmountParameter(
                            parsedUri.getQueryParameter("tx_amount"),
                        ) ?: return null
                    val paymentId = parsedUri.getQueryParameter("payment_id")
                    val recipientName = parsedUri.getQueryParameter("recipient_name") ?: ""
                    val txDescription = parsedUri.getQueryParameter("tx_description") ?: ""

                    return SendScreenRoute(
                        address = address,
                        amount = amount,
                        paymentId = paymentId,
                        recipientName = recipientName,
                        txDescription = txDescription,
                    )
                } else {
                    Wallet
                        .isAddressValid(
                            uri,
                            AnonConfig.getNetworkType().value,
                        ).let {
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
