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

package io.anonero.util

import android.content.SharedPreferences
import android.os.SystemClock
import timber.log.Timber

/**
 * A rate limit for the lock-screen PIN.
 *
 * The fifth counted attempt starts a 30-second wait before another check. Subsequent
 * attempts add 30 seconds up to five minutes. The caller records the attempt before
 * verifying the PIN and clears the count after successful authentication.
 *
 * Waits use elapsed time since boot, independent of wall-clock adjustments. If the
 * current elapsed time precedes the stored value, restart the wait from the current time.
 */
object PinThrottle {
    private const val ATTEMPTS = "pin_attempts"
    private const val LAST = "pin_last_attempt"
    private const val STEP_MS = 30_000L

    private fun waitFor(attempts: Int) = (attempts - 4).coerceIn(0, 10) * STEP_MS

    // Ms until the next attempt is allowed, 0 if it is allowed now.
    @Synchronized
    fun waitLeft(
        prefs: SharedPreferences,
        now: Long = SystemClock.elapsedRealtime(),
    ): Long {
        var last = prefs.getLong(LAST, 0)
        // boot clock went backwards: the phone rebooted, so the wait starts over from now
        if (now < last) {
            last = now
            // Keep the gate closed for at least one step if rebasing cannot be saved.
            if (!prefs.commitEdit { putLong(LAST, now) }) {
                return waitFor(prefs.getInt(ATTEMPTS, 0)).coerceAtLeast(STEP_MS)
            }
        }
        return (last + waitFor(prefs.getInt(ATTEMPTS, 0)) - now).coerceAtLeast(0)
    }

    // Return zero only after the attempt reaches disk. A failed write aborts PIN verification.
    @Synchronized
    fun tryAttempt(
        prefs: SharedPreferences,
        now: Long = SystemClock.elapsedRealtime(),
    ): Long {
        val left = waitLeft(prefs, now)
        if (left > 0) return left
        val recorded =
            prefs.commitEdit {
                putInt(ATTEMPTS, prefs.getInt(ATTEMPTS, 0) + 1)
                putLong(LAST, now)
            }
        check(recorded) { "PIN attempt persistence failed" }
        return 0
    }

    // A failed cleanup must not abort an already authenticated wallet open.
    @Synchronized
    fun reset(prefs: SharedPreferences) {
        val cleared =
            prefs.commitEdit {
                remove(ATTEMPTS)
                remove(LAST)
            }
        if (!cleared) {
            Timber.w("PIN throttle reset failed")
        }
    }
}
