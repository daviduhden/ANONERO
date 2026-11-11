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

package io.anonero.ui.onboard

// Structural checks only; wallet2 validates mnemonic language and checksum.
internal object SeedRestoreInput {
    private val separators = Regex("[\\s\\p{Z}]+")

    fun words(input: String): List<String> = input.splitToSequence(separators).filter { it.isNotEmpty() }.toList()

    fun hasSupportedCount(words: List<String>): Boolean = words.size == 16 || words.size == 25

    fun isValidHeightInput(input: String): Boolean {
        val value = input.trim()
        return value.isEmpty() ||
            (
                value.all { it in '0'..'9' } &&
                    value.toLongOrNull()?.let { it in 0..io.anonero.util.MAX_RESTORE_HEIGHT } == true
            )
    }

    fun requireValid(
        seed: List<String>,
        restoreHeight: Long?,
    ): List<String> {
        val normalized = words(seed.joinToString(" "))
        require(hasSupportedCount(normalized)) { "Unsupported mnemonic length" }
        require(
            restoreHeight == null ||
                restoreHeight in 0..io.anonero.util.MAX_RESTORE_HEIGHT,
        ) {
            "Invalid restore height"
        }
        require(normalized.size != 25 || restoreHeight != null) { "Restore height required" }
        return normalized
    }
}
