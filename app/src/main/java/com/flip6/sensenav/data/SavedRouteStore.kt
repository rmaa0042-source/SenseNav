package com.flip6.sensenav.data

import android.content.Context
import com.flip6.sensenav.model.SavedRoute
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Routes the user has kept, held on the device only.
 *
 * Which routes somebody walks, and from where, is as revealing as their search
 * history, so this follows [HistoryStore] and never leaves the phone.
 */
class SavedRouteStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saved(): List<SavedRoute> {
        val json = prefs.getString(KEY_ROUTES, null)
        if (json.isNullOrBlank()) return emptyList()
        // A list written by an older build may no longer parse. Losing the saved
        // routes is better than failing the screen that shows them.
        return runCatching { gson.fromJson<List<SavedRoute>>(json, ROUTE_LIST_TYPE) }
            .getOrNull()
            .orEmpty()
    }

    fun isSaved(id: String): Boolean = saved().any { it.id == id }

    /** Adds or removes the route, and returns the list as it now stands. */
    fun toggle(route: SavedRoute): List<SavedRoute> {
        val current = saved()
        val updated = if (current.any { it.id == route.id }) {
            current.filterNot { it.id == route.id }
        } else {
            (listOf(route) + current).take(MAX_ENTRIES)
        }
        prefs.edit().putString(KEY_ROUTES, gson.toJson(updated)).apply()
        return updated
    }

    fun clear(): List<SavedRoute> {
        prefs.edit().remove(KEY_ROUTES).apply()
        return emptyList()
    }

    private companion object {
        const val PREFS_NAME = "sensenav_saved_routes"
        const val KEY_ROUTES = "routes"
        const val MAX_ENTRIES = 20

        val ROUTE_LIST_TYPE: Type = object : TypeToken<List<SavedRoute>>() {}.type
    }
}
