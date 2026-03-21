package com.guildofsmiths.trademesh.service

import com.guildofsmiths.trademesh.data.MessageBusRepository
import com.guildofsmiths.trademesh.data.TransportType
import com.guildofsmiths.trademesh.data.UnifiedMessage
import com.guildofsmiths.trademesh.data.VectorClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ReconciliationEngine(
    private val messageBus: MessageBusRepository,
    private val backendUrl: String
) {
    suspend fun reconcileChannel(channelId: String) = withContext(Dispatchers.IO) {
        try {
            val localIds = messageBus.getMessageIds(channelId)
            val localClock = messageBus.getLocalClock()

            // Step 1: Ask server what's different
            val response = postJson(
                "$backendUrl/api/reconcile",
                JSONObject().apply {
                    put("channelId", channelId)
                    put("localMessageIds", JSONArray(localIds))
                    put("localClock", JSONObject(localClock.state))
                }
            )

            // Step 2: Receive messages we're missing
            val missingOnClient = parseMessages(response.getJSONArray("missingOnClient"))
            if (missingOnClient.isNotEmpty()) {
                messageBus.insertRemoteMessages(missingOnClient)
            }

            // Step 3: Push messages server is missing
            val missingOnServerIds = parseStringArray(response.getJSONArray("missingOnServer"))
            if (missingOnServerIds.isNotEmpty()) {
                val unsyncedMessages = messageBus.getUnsyncedMessages()
                    .filter { it.id in missingOnServerIds }

                if (unsyncedMessages.isNotEmpty()) {
                    pushMessages(unsyncedMessages)
                    messageBus.markSynced(unsyncedMessages.map { it.id })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pushMessages(messages: List<UnifiedMessage>) {
        val arr = JSONArray()
        for (msg in messages) {
            arr.put(JSONObject().apply {
                put("id", msg.id)
                put("channelId", msg.channelId)
                put("senderId", msg.senderId)
                put("senderName", msg.senderName)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
                put("vectorClock", JSONObject(msg.vectorClock.state))
                put("transportType", msg.transportType.name.lowercase())
                put("mediaType", msg.mediaType)
                put("mediaUrl", msg.mediaUrl)
                put("aiGenerated", msg.aiGenerated)
                put("aiModel", msg.aiModel)
            })
        }
        postJson("$backendUrl/api/reconcile/push", JSONObject().put("messages", arr))
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val responseText = conn.inputStream.bufferedReader().readText()
        return JSONObject(responseText)
    }

    private fun parseMessages(arr: JSONArray): List<UnifiedMessage> {
        val result = mutableListOf<UnifiedMessage>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(UnifiedMessage(
                id = obj.getString("id"),
                channelId = obj.getString("channelId"),
                senderId = obj.getString("senderId"),
                senderName = obj.getString("senderName"),
                content = obj.getString("content"),
                timestamp = obj.getLong("timestamp"),
                vectorClock = VectorClock.fromJson(obj.getJSONObject("vectorClock").toString()),
                transportType = TransportType.valueOf(obj.getString("transportType").uppercase()),
                mediaType = obj.optString("mediaType", "TEXT"),
                mediaUrl = obj.optString("mediaUrl", null),
                aiGenerated = obj.optBoolean("aiGenerated", false),
                aiModel = obj.optString("aiModel", null),
                syncedToRemote = true
            ))
        }
        return result
    }

    private fun parseStringArray(arr: JSONArray): Set<String> {
        val result = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            result.add(arr.getString(i))
        }
        return result
    }
}
