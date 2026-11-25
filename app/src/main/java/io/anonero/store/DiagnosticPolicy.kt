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

package io.anonero.store

/** Only reviewed constant events may cross the persistent diagnostic boundary. */
object DiagnosticPolicy {
    private val events =
        setOf(
            "Could not update Tor proxy; refresh paused",
            "Could not clear temporary backup data",
            "Could not clear historical diagnostics",
            "Could not clear stale backup archive",
            "Wallet reconnect failed; refresh paused",
            "Wallet service start failed",
            "Transport change failed",
            "Node selection failed",
            "Node connection failed",
            "Invalid node configuration",
            "Proxy update failed",
            "Notification update failed",
            "could not close previous wallet",
            "could not persist refreshed wallet state",
            "could not create account",
            "PIN verification failed",
            "PIN throttle reset failed",
            "Backup export failed",
            "Transaction broadcast failed",
            "Uncaught application failure",
            "Restore cache cleanup deferred",
        )

    fun sanitize(item: AnonLog): AnonLog =
        item.copy(
            // Do not persist caller-controlled tags, throwable text, endpoints or payloads.
            tag = "Application",
            message = if (item.message in events) item.message else "Diagnostic details withheld",
            // Minute precision reduces the precision of exported activity timing.
            date = item.date - Math.floorMod(item.date, 60_000L),
        )
}
