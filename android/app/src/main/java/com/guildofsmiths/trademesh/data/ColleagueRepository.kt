package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Colleague(
    val id: String,
    val name: String,
    val phone: String,
    val trade: String,
    val note: String,
    val addedAt: Long,
    val lastMessagedAt: Long? = null,
    val smithnetUserId: String? = null,
    val publicId: String? = null,
    val source: String = "manual",
)

/**
 * Colleague/crew contacts storage.
 * Fellow tradespeople (not clients) that the user wants to keep in contacts.
 * SharedPreferences-backed singleton, same pattern as ClientRepository.
 */
object ColleagueRepository {

    private const val PREFS_NAME = "trademesh_colleagues"
    private const val PREFS_KEY = "colleagues"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAll(): List<Colleague> {
        val json = prefs?.getString(PREFS_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                Colleague(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    phone = obj.optString("phone", ""),
                    trade = obj.optString("trade", ""),
                    note = obj.optString("note", ""),
                    addedAt = obj.optLong("addedAt", 0L),
                    lastMessagedAt = if (obj.has("lastMessagedAt")) obj.optLong("lastMessagedAt") else null,
                    smithnetUserId = obj.optString("smithnetUserId", "").ifBlank { null },
                    publicId = obj.optString("publicId", "").ifBlank { null },
                    source = obj.optString("source", "manual"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getById(id: String): Colleague? = getAll().find { it.id == id }

    fun add(name: String, phone: String, trade: String, note: String): Colleague {
        val colleague = Colleague(
            id = UUID.randomUUID().toString().take(8),
            name = name.trim(),
            phone = phone.trim(),
            trade = trade.trim(),
            note = note.trim(),
            addedAt = System.currentTimeMillis(),
            source = "manual",
        )
        val all = getAll().toMutableList()
        all.add(colleague)
        save(all)
        return colleague
    }

    /**
     * Add a colleague from a SmithNet profile we pulled from the directory.
     * If a record for this user already exists, return the existing one.
     * Source is one of "team" (tapped from org list) or "search" (from lookup).
     */
    fun addFromProfile(profile: ProfileRow, source: String): Colleague {
        val existing = getAll().firstOrNull { it.smithnetUserId == profile.id }
        if (existing != null) return existing
        val colleague = Colleague(
            id = UUID.randomUUID().toString().take(8),
            name = profile.display_name.trim(),
            phone = "",
            trade = profile.trade.orEmpty().trim(),
            note = "",
            addedAt = System.currentTimeMillis(),
            smithnetUserId = profile.id,
            publicId = profile.public_id,
            source = source,
        )
        val all = getAll().toMutableList()
        all.add(colleague)
        save(all)
        return colleague
    }

    fun update(id: String, name: String? = null, phone: String? = null, trade: String? = null, note: String? = null) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return
        val existing = all[index]
        all[index] = existing.copy(
            name = name?.trim() ?: existing.name,
            phone = phone?.trim() ?: existing.phone,
            trade = trade?.trim() ?: existing.trade,
            note = note?.trim() ?: existing.note
        )
        save(all)
    }

    fun updateLastMessaged(id: String) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return
        all[index] = all[index].copy(lastMessagedAt = System.currentTimeMillis())
        save(all)
    }

    fun remove(id: String) {
        val all = getAll().toMutableList()
        all.removeAll { it.id == id }
        save(all)
    }

    private fun save(colleagues: List<Colleague>) {
        val array = JSONArray()
        colleagues.forEach { c ->
            array.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("trade", c.trade)
                put("note", c.note)
                put("addedAt", c.addedAt)
                c.lastMessagedAt?.let { put("lastMessagedAt", it) }
                c.smithnetUserId?.let { put("smithnetUserId", it) }
                c.publicId?.let { put("publicId", it) }
                put("source", c.source)
            })
        }
        prefs?.edit()?.putString(PREFS_KEY, array.toString())?.apply()
    }
}
