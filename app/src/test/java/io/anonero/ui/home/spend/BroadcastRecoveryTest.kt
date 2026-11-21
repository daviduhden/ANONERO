package io.anonero.ui.home.spend

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BroadcastRecoveryTest {
    @Test
    fun acceptancePrecedesRecovery() {
        var accepted = false
        val recovered =
            completeAcceptedBroadcast(
                publishAcceptance = { accepted = true },
                refreshAndStore = {
                    assertTrue(accepted)
                    true
                },
            )
        assertTrue(accepted)
        assertTrue(recovered)
    }

    @Test
    fun unsuccessfulStoreDoesNotUndoAcceptance() {
        var accepted = false
        val recovered =
            completeAcceptedBroadcast(
                publishAcceptance = { accepted = true },
                refreshAndStore = { false },
            )
        assertTrue(accepted)
        assertFalse(recovered)
    }

    @Test
    fun recoveryExceptionDoesNotUndoAcceptance() {
        var accepted = false
        val recovered =
            completeAcceptedBroadcast(
                publishAcceptance = { accepted = true },
                refreshAndStore = { throw IOException("fixture") },
            )
        assertTrue(accepted)
        assertFalse(recovered)
    }

    @Test
    fun cancellationPropagatesAfterPublishingAcceptance() {
        var accepted = false
        val cancelled = CancellationException("fixture")
        val thrown =
            assertThrows(CancellationException::class.java) {
                completeAcceptedBroadcast(
                    publishAcceptance = { accepted = true },
                    refreshAndStore = { throw cancelled },
                )
            }
        assertTrue(accepted)
        assertSame(cancelled, thrown)
    }
}
