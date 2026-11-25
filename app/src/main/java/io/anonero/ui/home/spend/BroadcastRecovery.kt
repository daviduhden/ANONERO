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
