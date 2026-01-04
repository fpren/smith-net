package com.guildofsmiths.trademesh.utils

import com.guildofsmiths.trademesh.planner.types.ExecutionItem
import com.guildofsmiths.trademesh.planner.types.ExecutionItemType
import com.guildofsmiths.trademesh.planner.types.ParsedItemData
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility for serializing/deserializing ExecutionItems to JSON.
 * Used for storing execution items with archived jobs for document regeneration.
 */
object ExecutionItemSerializer {
    
    /**
     * Serialize a list of ExecutionItems to JSON string.
     */
    fun serialize(items: List<ExecutionItem>): String {
        val jsonArray = JSONArray()
        
        items.forEach { item ->
            val jsonItem = JSONObject().apply {
                put("id", item.id)
                put("type", item.type.name)
                put("index", item.index)
                put("lineNumber", item.lineNumber)
                put("section", item.section)
                put("source", item.source)
                put("createdAt", item.createdAt)
                
                // Serialize parsed data based on type
                val parsedJson = JSONObject()
                when (val parsed = item.parsed) {
                    is ParsedItemData.Task -> {
                        parsedJson.put("kind", "Task")
                        parsedJson.put("description", parsed.description)
                    }
                    is ParsedItemData.Material -> {
                        parsedJson.put("kind", "Material")
                        parsedJson.put("description", parsed.description)
                        parsedJson.putOpt("quantity", parsed.quantity)
                        parsedJson.putOpt("unit", parsed.unit)
                    }
                    is ParsedItemData.Labor -> {
                        parsedJson.put("kind", "Labor")
                        parsedJson.put("description", parsed.description)
                        parsedJson.putOpt("hours", parsed.hours)
                        parsedJson.putOpt("role", parsed.role)
                    }
                }
                put("parsed", parsedJson)
            }
            jsonArray.put(jsonItem)
        }
        
        return jsonArray.toString()
    }
    
    /**
     * Deserialize a JSON string back to a list of ExecutionItems.
     */
    fun deserialize(json: String?): List<ExecutionItem> {
        if (json.isNullOrBlank()) return emptyList()
        
        return try {
            val jsonArray = JSONArray(json)
            val items = mutableListOf<ExecutionItem>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                val parsedJson = jsonItem.getJSONObject("parsed")
                
                val type = ExecutionItemType.valueOf(jsonItem.getString("type"))
                
                val parsed: ParsedItemData = when (parsedJson.getString("kind")) {
                    "Task" -> ParsedItemData.Task(
                        description = parsedJson.getString("description")
                    )
                    "Material" -> ParsedItemData.Material(
                        description = parsedJson.getString("description"),
                        quantity = parsedJson.optString("quantity", null),
                        unit = parsedJson.optString("unit", null)
                    )
                    "Labor" -> ParsedItemData.Labor(
                        description = parsedJson.getString("description"),
                        hours = parsedJson.optString("hours", null),
                        role = parsedJson.optString("role", null)
                    )
                    else -> ParsedItemData.Task("Unknown")
                }
                
                items.add(
                    ExecutionItem(
                        id = jsonItem.getString("id"),
                        type = type,
                        index = jsonItem.getInt("index"),
                        lineNumber = jsonItem.getInt("lineNumber"),
                        section = jsonItem.getString("section"),
                        source = jsonItem.getString("source"),
                        parsed = parsed,
                        createdAt = jsonItem.getLong("createdAt")
                    )
                )
            }
            
            items
        } catch (e: Exception) {
            android.util.Log.e("ExecutionItemSerializer", "Failed to deserialize: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Check if a JSON string contains valid execution items.
     */
    fun isValid(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return try {
            JSONArray(json).length() > 0
        } catch (e: Exception) {
            false
        }
    }
}
