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

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.anonero.model.node.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

const val NODE_STORE = "nodes_store"
val jsonDecoder = Json { ignoreUnknownKeys = true }
private const val TAG = "NodeDataStore"

object NodeListSerializer : Serializer<List<Node>> {
    override val defaultValue: List<Node> = emptyList()

    override suspend fun readFrom(input: InputStream): List<Node> {
        val bytes = input.readBytes()
        // DataStore calls readFrom on an empty file before the first write.
        if (bytes.isEmpty()) return defaultValue
        val json = bytes.decodeToString()
        if (json.isBlank()) return defaultValue
        return try {
            jsonDecoder.decodeFromString(ListSerializer(Node.serializer()), json)
        } catch (e: SerializationException) {
            // Do not turn an unreadable store into an empty list: the next updateData
            // would overwrite the user's saved nodes. Surface corruption instead so
            // writes fail closed and the original file is preserved.
            Timber.tag(TAG).e("Saved node list is unreadable")
            throw CorruptionException("Saved node list is unreadable", e)
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).e("Saved node list is invalid")
            throw CorruptionException("Saved node list is invalid", e)
        }
    }

    override suspend fun writeTo(
        t: List<Node>,
        output: OutputStream,
    ) {
        val json = jsonDecoder.encodeToString(ListSerializer(Node.serializer()), t)
        withContext(Dispatchers.IO) {
            output.write(json.encodeToByteArray())
        }
    }
}

private val Context.nodeDataStore: DataStore<List<Node>> by dataStore(
    fileName = NODE_STORE,
    serializer = NodeListSerializer,
)

/**
 * Persisted list of saved nodes.
 *
 * Node records include RPC credentials, so the DataStore file is app-private but not
 * Keystore-encrypted. Treat the Android app sandbox as the protection boundary. Entries are
 * de-duplicated by their serialized node string, and [clearAll] is part of secure wipe.
 */
class NodesRepository(
    private val context: Context,
) {
    // Expose a Flow to observe the list of items
    val nodesFlow: Flow<List<Node>> =
        context.nodeDataStore.data
            .catch { exception ->
                when (exception) {
                    is CorruptionException -> {
                        // Read-only display can continue, but the file stays untouched and
                        // every write keeps failing until the store is repaired or wiped.
                        Timber.tag(TAG).e("Saved node list is corrupt")
                        emit(emptyList())
                    }

                    is IOException -> {
                        emit(emptyList())
                    }

                    else -> {
                        throw exception
                    }
                }
            }

    // Save a list of items
    suspend fun saveItems(items: List<Node>) {
        context.nodeDataStore.updateData { items }
    }

    // Add a new item to the list. Returns false on an unreadable store so the caller can
    // report the failure instead of overwriting saved nodes from a corrupt read.
    suspend fun addItem(item: Node): Boolean =
        try {
            context.nodeDataStore.updateData { currentItems ->
                if (currentItems.find { it.toNodeString() == item.toNodeString() } == null) {
                    currentItems + item
                } else {
                    currentItems
                }
            }
            true
        } catch (e: CorruptionException) {
            Timber.tag(TAG).e("Cannot add node: saved node list is corrupt")
            false
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Cannot add node")
            false
        }

    // Read the current list without observing future changes.
    suspend fun getAll(): List<Node> =
        try {
            context.nodeDataStore.data.first()
        } catch (e: CorruptionException) {
            // A backup must not silently omit saved nodes. Surface the corruption and let
            // the caller fail the export instead of producing an incomplete archive.
            Timber.tag(TAG).e("Cannot export nodes: saved node list is corrupt")
            throw e
        }

    // Remove an item by NodeString. Returns false when the store cannot be read or written.
    suspend fun removeItemByNodeString(nodeString: String): Boolean =
        try {
            context.nodeDataStore.updateData { currentItems ->
                currentItems.filter {
                    it.toNodeString() != nodeString
                }
            }
            true
        } catch (e: CorruptionException) {
            Timber.tag(TAG).e("Cannot remove node: saved node list is corrupt")
            false
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Cannot remove node")
            false
        }

    suspend fun clearAll() {
        context.nodeDataStore.updateData {
            emptyList()
        }
    }
}
