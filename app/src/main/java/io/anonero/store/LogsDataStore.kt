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
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.anonero.AnonConfig
import io.anonero.jsonDecoder
import io.anonero.util.StealthMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

const val LOG_STORE = "logs"
private const val TAG = "LogDataStore"

@Serializable
data class AnonLog(
    val date: Long,
    val tag: String,
    val message: String,
    val priority: Int,
)

object LogSerializer : Serializer<List<AnonLog>> {
    override val defaultValue: List<AnonLog> = emptyList()

    override suspend fun readFrom(input: InputStream): List<AnonLog> =
        try {
            val json = input.readBytes().decodeToString()
            jsonDecoder
                .decodeFromString(ListSerializer(AnonLog.serializer()), json)
                .filter { it.priority >= 5 }
                .map(DiagnosticPolicy::sanitize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Logs are diagnostic, capped, and cleared on unlock. A corrupt log store must
            // never break logging or block the app, so reset it instead of failing closed
            // like the security-relevant node store.
            emptyList()
        }

    override suspend fun writeTo(
        t: List<AnonLog>,
        output: OutputStream,
    ) {
        val json = jsonDecoder.encodeToString(ListSerializer(AnonLog.serializer()), t)
        withContext(Dispatchers.IO) {
            output.write(json.encodeToByteArray())
        }
    }
}

private val Context.logsStore: DataStore<List<AnonLog>> by dataStore(
    fileName = LOG_STORE,
    serializer = LogSerializer,
)

/**
 * Persisted application log shown in the Logs screen.
 *
 * `AnonLogTree` writes this store; stealth mode suppresses persistence entirely. The store
 * is capped and pruned, is cleared on a successful unlock and on secure wipe, and must
 * never receive seeds, passphrases, PINs, wallet passwords, or node credentials.
 */
class LogRepository(
    private val context: Context,
) {
    private val epoch = AtomicLong()
    val generation: Long get() = epoch.get()
    private val prefs = context.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)

    val logFlow: Flow<List<AnonLog>> =
        context.logsStore.data
            .map { items -> items.filter { it.priority >= 5 }.map(DiagnosticPolicy::sanitize) }
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyList())
                } else {
                    throw exception
                }
            }

    suspend fun saveItems(items: List<AnonLog>) {
        context.logsStore.updateData {
            if (StealthMode.isEnabled(prefs)) emptyList() else items.filter { it.priority >= 5 }.map(DiagnosticPolicy::sanitize)
        }
    }

    // Check the generation and stealth mode inside the DataStore update. A write
    // queued before a session clear can no longer publish an old diagnostic.
    suspend fun addItem(
        item: AnonLog,
        expectedGeneration: Long = generation,
    ) {
        context.logsStore.updateData { currentItems ->
            if (expectedGeneration != generation || StealthMode.isEnabled(prefs) || item.priority < 5) {
                currentItems
            } else {
                currentItems.takeLast(AnonConfig.MAX_LOG_SIZE - 1) + DiagnosticPolicy.sanitize(item)
            }
        }
    }

    // Remove only the requested diagnostic record.
    suspend fun remove(logId: AnonLog) {
        context.logsStore.updateData { currentItems ->
            currentItems.filter {
                it != logId
            }
        }
    }

    suspend fun clear() {
        epoch.incrementAndGet()
        context.logsStore.updateData {
            listOf()
        }
        AnonConfig.context?.let { AnonConfig.getLogFile(it).delete() }
    }
}
