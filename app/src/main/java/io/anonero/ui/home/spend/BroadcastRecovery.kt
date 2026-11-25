package io.anonero.ui.home.spend

import kotlinx.coroutines.CancellationException

/**
 * Publishes acceptance before refreshing and storing local state. A false result
 * describes recovery failure after submission, so it must never trigger a resend.
 */
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
