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
