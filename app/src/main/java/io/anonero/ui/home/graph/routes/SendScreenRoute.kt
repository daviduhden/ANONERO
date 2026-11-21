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

import io.anonero.AnonConfig
import io.anonero.model.Wallet
import io.anonero.util.MoneroAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

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

        fun parse(uri: String): SendScreenRoute? =
            parse(uri) { address -> Wallet.isAddressValid(address, AnonConfig.getNetworkType().value) }

        internal fun parse(
            uri: String,
            validateAddress: (String) -> Boolean,
        ): SendScreenRoute? =
            try {
                if (uri.startsWith("monero:", ignoreCase = true)) {
                    parsePaymentUri(uri.substringAfter(':'), validateAddress)
                } else {
                    uri.takeIf(validateAddress)?.let { SendScreenRoute(address = it) }
                }
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: URISyntaxException) {
                null
            }

        private fun parsePaymentUri(
            body: String,
            validateAddress: (String) -> Boolean,
        ): SendScreenRoute? {
            val parsed = URI("monero://${body.removePrefix("//")}")
            val address = parsed.rawAuthority ?: return null
            if (!address.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' } ||
                !parsed.rawPath.isNullOrEmpty() ||
                parsed.rawFragment != null ||
                !validateAddress(address)
            ) {
                return null
            }

            val recognized = setOf("tx_amount", "payment_id", "recipient_name", "tx_description")
            val parameters = mutableMapOf<String, String>()
            parsed.rawQuery?.split('&')?.forEach { pair ->
                val name = decodeQueryComponent(pair.substringBefore('='))
                if (name in recognized) {
                    val encodedValue = pair.substringAfter('=', "")
                    val value = decodeQueryComponent(if (name == "tx_amount") encodedValue.replace("+", "%2B") else encodedValue)
                    if (parameters.put(name, value) != null) return null
                }
            }
            return SendScreenRoute(
                address = address,
                amount = parseAmountParameter(parameters["tx_amount"]) ?: return null,
                paymentId = parameters["payment_id"],
                recipientName = parameters["recipient_name"].orEmpty(),
                txDescription = parameters["tx_description"].orEmpty(),
            )
        }

        private fun decodeQueryComponent(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
