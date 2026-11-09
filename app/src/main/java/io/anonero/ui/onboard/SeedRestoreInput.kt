package io.anonero.ui.onboard

/** Structural checks only; wallet2 validates mnemonic language and checksum. */
internal object SeedRestoreInput {
    private val separators = Regex("[\\s\\p{Z}]+")

    fun words(input: String): List<String> =
        input.splitToSequence(separators).filter { it.isNotEmpty() }.toList()

    fun hasSupportedCount(words: List<String>): Boolean = words.size == 16 || words.size == 25

    fun isValidHeightInput(input: String): Boolean {
        val value = input.trim()
        return value.isEmpty() ||
            (value.all { it in '0'..'9' } && value.toLongOrNull() != null)
    }

    fun requireValid(seed: List<String>, restoreHeight: Long?): List<String> {
        val normalized = words(seed.joinToString(" "))
        require(hasSupportedCount(normalized)) { "Unsupported mnemonic length" }
        require(restoreHeight == null || restoreHeight >= 0) { "Invalid restore height" }
        require(normalized.size != 25 || restoreHeight != null) { "Restore height required" }
        return normalized
    }
}
