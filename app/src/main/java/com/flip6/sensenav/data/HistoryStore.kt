package com.flip6.sensenav.data

import android.content.Context
import com.flip6.sensenav.model.Refuge
import com.flip6.sensenav.model.SearchResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Recent searches and recently viewed refuges, held on the device only.
 *
 * This is the user's own browsing history, so it deliberately never leaves the
 * phone: it lives in the app's private SharedPreferences and is not sent to the
 * routing or search backends. Clearing the app's data removes it.
 *
 * Both lists are most-recent-first, de-duplicated by id, and capped at
 * [MAX_ENTRIES] so the file cannot grow without bound.
 */
class HistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun recentSearches(): List<SearchResult> =
        decode(prefs.getString(KEY_SEARCHES, null), SEARCH_LIST_TYPE)

    fun recentlyViewed(): List<Refuge> =
        decode(prefs.getString(KEY_VIEWED, null), REFUGE_LIST_TYPE)

    /** Records a search the user acted on, and returns the updated list. */
    fun recordSearch(result: SearchResult): List<SearchResult> =
        save(KEY_SEARCHES, moveToFront(recentSearches(), result) { it.id })

    /** Records a refuge the user opened, and returns the updated list. */
    fun recordViewed(refuge: Refuge): List<Refuge> =
        save(KEY_VIEWED, moveToFront(recentlyViewed(), refuge) { it.id })

    fun clearSearches(): List<SearchResult> = save(KEY_SEARCHES, emptyList())

    fun clearViewed(): List<Refuge> = save(KEY_VIEWED, emptyList())

    /** Re-visiting an entry promotes it rather than adding a duplicate. */
    private fun <T> moveToFront(existing: List<T>, entry: T, id: (T) -> String): List<T> =
        (listOf(entry) + existing.filterNot { id(it) == id(entry) }).take(MAX_ENTRIES)

    private fun <T> save(key: String, value: List<T>): List<T> {
        prefs.edit().putString(key, gson.toJson(value)).apply()
        return value
    }

    private fun <T> decode(json: String?, type: Type): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        // A stored list written by an older build may no longer parse. Dropping
        // it costs the user their history; crashing the screen costs more.
        return runCatching { gson.fromJson<List<T>>(json, type) }.getOrNull().orEmpty()
    }

    private companion object {
        const val PREFS_NAME = "sensenav_history"
        const val KEY_SEARCHES = "recent_searches"
        const val KEY_VIEWED = "recently_viewed"
        const val MAX_ENTRIES = 10

        val SEARCH_LIST_TYPE: Type = object : TypeToken<List<SearchResult>>() {}.type
        val REFUGE_LIST_TYPE: Type = object : TypeToken<List<Refuge>>() {}.type
    }
}
