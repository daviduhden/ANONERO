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

package io.anonero.ui.home.graph.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendScreenRouteTest {
    @Test
    fun `omitted, empty and zero URI amounts mean no prefilled amount`() {
        assertEquals(0L, SendScreenRoute.parseAmountParameter(null))
        assertEquals(0L, SendScreenRoute.parseAmountParameter(""))
        assertEquals(0L, SendScreenRoute.parseAmountParameter("  "))
        assertEquals(0L, SendScreenRoute.parseAmountParameter("0"))
        assertEquals(0L, SendScreenRoute.parseAmountParameter("0.0"))
        assertEquals(0L, SendScreenRoute.parseAmountParameter("0.000000000000"))
    }

    @Test
    fun `positive URI amounts are converted exactly to atomic units`() {
        assertEquals(1_000_000_000_000L, SendScreenRoute.parseAmountParameter("1"))
        assertEquals(1L, SendScreenRoute.parseAmountParameter("0.000000000001"))
        assertEquals(12_345_678_901_234L, SendScreenRoute.parseAmountParameter("12.345678901234"))
        assertEquals(1_000_000_000_000L, SendScreenRoute.parseAmountParameter(" 1 "))
    }

    @Test
    fun `malformed, negative and over-precise URI amounts are rejected`() {
        assertNull(SendScreenRoute.parseAmountParameter("-1"))
        assertNull(SendScreenRoute.parseAmountParameter("+1"))
        assertNull(SendScreenRoute.parseAmountParameter("1e3"))
        assertNull(SendScreenRoute.parseAmountParameter("1,5"))
        assertNull(SendScreenRoute.parseAmountParameter("1.0000000000001"))
        assertNull(SendScreenRoute.parseAmountParameter("9223372.036854775808"))
    }
}
