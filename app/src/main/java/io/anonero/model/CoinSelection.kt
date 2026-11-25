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
 * Validates an explicit coin selection against the current wallet snapshot.
 * Wallet2 still determines transaction inputs and fees.
 */
internal fun validateCoinSelection(
    keyImages: List<String>,
    outputs: List<CoinsInfo>,
    account: Int,
    amount: Long?,
) {
    require(amount == null || amount > 0L) { "invalid transaction amount" }
    require(keyImages.isNotEmpty() && keyImages.none(String::isBlank)) { "invalid selected key image" }
    val requested = keyImages.toSet()
    require(requested.size == keyImages.size) { "duplicate selected key image" }

    val found = mutableSetOf<String>()
    var total = 0L
    for (output in outputs) {
        if (output.key !in requested) continue
        require(found.add(output.key)) { "duplicate output in wallet snapshot" }
        require(
            output.subaddrAccount == account && output.keyImageKnown && output.unlocked &&
                !output.spent && !output.frozen && output.amount > 0L,
        ) { "selected output is unavailable" }
        total = Math.addExact(total, output.amount)
    }
    require(found == requested) { "selected output is missing" }
    require(amount == null || total > amount) { "insufficient selected balance" }
}
