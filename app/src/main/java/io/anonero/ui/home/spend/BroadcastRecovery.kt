package io.anonero.ui.home.spend

import kotlinx.coroutines.CancellationException

/** Local recovery can fail independently of a successful wallet2 submission. */
internal fun completeAcceptedBroadcast(
    publishAcceptance: () -> Unit,
    refreshAndStore: () -> Boolean,
): Boolean {
    publishAcceptance()
    return try {
        refreshAndStore()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
