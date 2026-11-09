package io.anonero.model


class BalanceInfo(val rawUnlocked: Long, val rawLocked: Long) {

    val isUnlockedBalanceZero: Boolean
        get() = rawUnlocked == 0L
    val isLockedBalanceZero: Boolean
        get() = rawLocked == 0L

    val total: Long get() = rawUnlocked + rawLocked
}