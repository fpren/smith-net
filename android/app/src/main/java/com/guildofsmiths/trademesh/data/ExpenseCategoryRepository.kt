package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-customizable expense categories. Backed by SharedPreferences.
 * Built-ins ship on first launch and can be renamed/hidden but not deleted.
 * Custom categories can be freely added and deleted (with reassignment prompts when in use).
 *
 * JobExpense.category stores the stable slug id (e.g. "material", "fuel",
 * or a user slug like "airfare"), which this repo resolves to display name,
 * short code, and color.
 */
object ExpenseCategoryRepository {

    private const val PREFS_NAME = "expense_categories"
    private const val KEY_CATEGORIES = "categories_json"
    private const val KEY_INITIALIZED = "initialized"

    private var prefs: SharedPreferences? = null

    private val _categories = MutableStateFlow<List<com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef>>(defaults())
    val categories: StateFlow<List<com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef>> = _categories.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val already = prefs?.getBoolean(KEY_INITIALIZED, false) ?: false
        if (!already) {
            save(defaults())
            prefs?.edit()?.putBoolean(KEY_INITIALIZED, true)?.apply()
        } else {
            load()
        }
    }

    fun get(id: String): com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef? =
        _categories.value.firstOrNull { it.id == id }

    /** Resolve a category; fall back to a synthetic one so unknown ids still render. */
    fun resolve(id: String): com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef =
        get(id) ?: com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef(
            id = id,
            displayName = id.replace('_', ' ').replaceFirstChar { it.uppercase() },
            shortCode = "[${id.take(1).uppercase()}]",
            colorHex = "#666666",
            builtIn = false
        )

    fun visible(): List<com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef> =
        _categories.value.filter { !it.hidden }.sortedBy { it.sortOrder }

    fun add(displayName: String, shortCode: String, colorHex: String = "#8C6B2A"): com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef {
        val slug = slugify(displayName, existingIds = _categories.value.map { it.id }.toSet())
        val newCat = com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef(
            id = slug,
            displayName = displayName,
            shortCode = shortCode,
            colorHex = colorHex,
            builtIn = false,
            sortOrder = (_categories.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        )
        save(_categories.value + newCat)
        return newCat
    }

    fun update(id: String, mutate: (com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef) -> com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef) {
        save(_categories.value.map { if (it.id == id) mutate(it) else it })
    }

    fun setHidden(id: String, hidden: Boolean) = update(id) { it.copy(hidden = hidden) }

    fun delete(id: String) {
        val target = get(id) ?: return
        if (target.builtIn) return
        save(_categories.value.filter { it.id != id })
    }

    fun reorder(ids: List<String>) {
        val ordered = ids.mapIndexedNotNull { idx, cid ->
            _categories.value.firstOrNull { it.id == cid }?.copy(sortOrder = idx)
        }
        val missing = _categories.value.filterNot { it.id in ids }
        save(ordered + missing)
    }

    private fun save(list: List<com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef>) {
        _categories.value = list.sortedBy { it.sortOrder }
        prefs?.edit()?.putString(KEY_CATEGORIES, serialize(_categories.value))?.apply()
    }

    private fun load() {
        val json = prefs?.getString(KEY_CATEGORIES, null) ?: return
        val parsed = deserialize(json)
        if (parsed.isNotEmpty()) _categories.value = parsed.sortedBy { it.sortOrder }
    }

    private fun serialize(list: List<com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("displayName", c.displayName)
                put("shortCode", c.shortCode)
                put("colorHex", c.colorHex)
                put("hidden", c.hidden)
                put("builtIn", c.builtIn)
                put("sortOrder", c.sortOrder)
            })
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    shortCode = o.optString("shortCode", "[?]"),
                    colorHex = o.optString("colorHex", "#8C6B2A"),
                    hidden = o.optBoolean("hidden", false),
                    builtIn = o.optBoolean("builtIn", false),
                    sortOrder = o.optInt("sortOrder", 0)
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun slugify(name: String, existingIds: Set<String>): String {
        val base = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "category" }
        if (base !in existingIds) return base
        var i = 2
        while ("${base}_$i" in existingIds) i++
        return "${base}_$i"
    }

    private fun defaults(): List<com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef> = listOf(
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("material",         "Material",        "[M]",  "#8C6B2A", builtIn = true, sortOrder = 0),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("labor",            "Labor",           "[L]",  "#5A8C76", builtIn = true, sortOrder = 1),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("subcontractor",    "Subcontractor",   "[S]",  "#6B5E8C", builtIn = true, sortOrder = 2),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("permit_fee",       "Permit/Fee",      "[P]",  "#8C3A3A", builtIn = true, sortOrder = 3),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("equipment_rental", "Rental",          "[R]",  "#3A6B8C", builtIn = true, sortOrder = 4),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("disposal",         "Disposal",        "[D]",  "#555555", builtIn = true, sortOrder = 5),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("fuel",             "Fuel",            "[F]",  "#A67C00", builtIn = true, sortOrder = 6),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("tool_charge",      "Tool Use",        "[T]",  "#667788", builtIn = true, sortOrder = 7),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("callback",         "Callback",        "[C]",  "#B54B4B", builtIn = true, sortOrder = 8),
        com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef("mileage",          "Mileage",         "[Mi]", "#4A7FA8", builtIn = true, sortOrder = 9)
    )
}
