package io.anonero.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.anonero.AnonConfig
import io.anonero.jsonDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.InputStream
import java.io.OutputStream

const val LOG_STORE = "logs"
private const val TAG = "LogDataStore"

@Serializable
data class AnonLog(
    val date: Long,
    val tag: String,
    val message: String,
    val priority: Int
)


object LogSerializer : Serializer<List<AnonLog>> {

    override val defaultValue: List<AnonLog> = emptyList()

    override suspend fun readFrom(input: InputStream): List<AnonLog> {
        return try {
            val json = input.readBytes().decodeToString()
            jsonDecoder.decodeFromString(ListSerializer(AnonLog.serializer()), json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun writeTo(t: List<AnonLog>, output: OutputStream) {
        val json = jsonDecoder.encodeToString(ListSerializer(AnonLog.serializer()), t)
        withContext(Dispatchers.IO) {
            output.write(json.encodeToByteArray())
        }
    }
}


private val Context.logsStore: DataStore<List<AnonLog>> by dataStore(
    fileName = LOG_STORE,
    serializer = LogSerializer
)


class LogRepository(private val context: Context) {

    // Expose a Flow to observe the list of items
    val logFlow: Flow<List<AnonLog>> = context.logsStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyList()) // Handle errors by emitting an empty list
            } else {
                throw exception
            }
        }

    suspend fun saveItems(items: List<AnonLog>) {
        context.logsStore.updateData { items }
    }

    // Add a new item to the list,prune old items
    suspend fun addItem(item: AnonLog) {
        context.logsStore.updateData { currentItems ->
            val prunedList = if (currentItems.size > AnonConfig.MAX_LOG_SIZE) {
                currentItems.takeLast(AnonConfig.MAX_LOG_SIZE - 5)
            } else {
                currentItems
            }
            prunedList + item
        }
    }

    // Remove an item by NodeString
    suspend fun remove(logId: AnonLog) {
        context.logsStore.updateData { currentItems ->
            currentItems.filter {
                it.date == logId.date && it.tag == logId.tag && logId.priority == it.priority
                        && it.message == logId.message
            }
        }
    }

    suspend fun clear() {
        context.logsStore.updateData {
            listOf()
        }
        AnonConfig.context?.let { AnonConfig.getLogFile(it).delete() }
    }
}