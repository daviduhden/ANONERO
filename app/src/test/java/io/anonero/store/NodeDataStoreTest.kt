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

package io.anonero.store

import androidx.datastore.core.CorruptionException
import io.anonero.model.node.Node
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class NodeDataStoreTest {
    @Test
    fun `empty and blank stores read as an empty list`() =
        runBlocking {
            assertEquals(
                emptyList<Node>(),
                NodeListSerializer.readFrom(ByteArrayInputStream(ByteArray(0))),
            )
            assertEquals(
                emptyList<Node>(),
                NodeListSerializer.readFrom(ByteArrayInputStream("  \n".toByteArray())),
            )
            assertEquals(
                emptyList<Node>(),
                NodeListSerializer.readFrom(ByteArrayInputStream("[]".toByteArray())),
            )
        }

    @Test
    fun `corrupt store fails closed instead of dropping saved nodes`() {
        assertThrows(CorruptionException::class.java) {
            runBlocking {
                NodeListSerializer.readFrom(ByteArrayInputStream("{\"nodes\":".toByteArray()))
            }
        }
    }
}
