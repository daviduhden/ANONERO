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

package io.anonero.util

/** Input bounds for the app's multi-recipient form; wallet2 still validates the payment. */
object MultiPaymentPolicy {
    const val MAX_RECIPIENTS = 16

    fun totalOrNull(recipients: List<Pair<String, Long>>): Long? {
        if (recipients.size !in 1..MAX_RECIPIENTS || recipients.any { it.first.isBlank() || it.second <= 0L }) return null
        return try {
            recipients.fold(0L) { total, recipient -> Math.addExact(total, recipient.second) }
        } catch (_: ArithmeticException) {
            null
        }
    }
}

/** A completed address check is usable only for exactly the inputs that were checked. */
data class AddressValidationSnapshot(
    val addresses: List<String>,
    val valid: List<Boolean>,
) {
    fun matches(current: List<String>): Boolean = addresses == current && valid.size == current.size

    fun allValidFor(current: List<String>): Boolean = current.isNotEmpty() && matches(current) && valid.all { it }

    fun isValidAt(
        current: List<String>,
        index: Int,
    ): Boolean = matches(current) && valid.getOrNull(index) == true
}
