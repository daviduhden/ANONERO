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

import com.sparrowwallet.hummingbird.UR

/**
 * UR registry types used by ANONERO's airgapped transaction exchange.
 *
 * The strings and tags are part of the interchange format and must match the UR
 * implementation on the other side. See `AnonUrRegistryTypes` consumers in `Send.kt`,
 * `SpendQRExchange.kt`, and `QRScanner.kt`.
 */
enum class AnonUrRegistryTypes(
    val type: String,
    val tag: Int,
) {
    XMR_OUTPUT("xmr-output", 610),
    XMR_KEY_IMAGE("xmr-keyimage", 611),
    XMR_TX_UNSIGNED("xmr-txunsigned", 612),
    XMR_TX_SIGNED("xmr-txsigned", 613),
    ;

    override fun toString(): String = type

    companion object {
        fun fromString(type: String): AnonUrRegistryTypes? = AnonUrRegistryTypes.values().find { it.type == type }

        fun fromUrTag(ur: UR): AnonUrRegistryTypes? = AnonUrRegistryTypes.values().find { it.type == ur.type }
    }
}
