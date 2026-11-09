package io.anonero.util

fun <T> List<T>.shuffleExcept(vararg fixedIndices: Int): List<T> {
    require(fixedIndices.all { it in indices })
    val result = this.toMutableList()
    val movableElements = indices
        .filter { it !in fixedIndices }
        .map { this[it] }
        .toMutableList()
    movableElements.shuffle()
    var movableIndex = 0
    for (i in indices) {
        if (i !in fixedIndices) {
            result[i] = movableElements[movableIndex++]
        }
    }
    return result
}
