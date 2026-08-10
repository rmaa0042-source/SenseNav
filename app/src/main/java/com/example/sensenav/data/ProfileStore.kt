package com.example.sensenav.data

import android.content.Context
import com.example.sensenav.model.SavedPlace
import com.google.gson.Gson

/**
 * The name and location shown at the top of the home screen, held on the device
 * only.
 *
 * Like [HistoryStore], this never leaves the phone: a chosen name and home
 * suburb say a lot about a person, and nothing here needs a backend to work.
 */
class ProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun displayName(): String =
        prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME

    fun isLoginRemembered(): Boolean = prefs.getBoolean(KEY_REMEMBER_LOGIN, false)

    fun saveLoginDisplayName(name: String, rememberLogin: Boolean): String {
        val cleaned = cleanDisplayName(name)
        prefs.edit()
            .putString(KEY_NAME, cleaned)
            .putBoolean(KEY_REMEMBER_LOGIN, rememberLogin)
            .apply()
        return cleaned
    }

    /** Returns the stored value, which may differ from the input after trimming. */
    fun saveDisplayName(name: String): String {
        val cleaned = cleanDisplayName(name)
        prefs.edit().putString(KEY_NAME, cleaned).apply()
        return cleaned
    }

    fun clearRememberedLogin() {
        prefs.edit().putBoolean(KEY_REMEMBER_LOGIN, false).apply()
    }

    /**
     * The location the user pinned by hand, or null to follow the device. Null
     * is the default: the app should track where someone actually is unless
     * they have said otherwise.
     */
    fun savedPlace(): SavedPlace? {
        val json = prefs.getString(KEY_PLACE, null) ?: return null
        // A value written by an older build may no longer parse; falling back to
        // device location is a better outcome than crashing the home screen.
        return runCatching { gson.fromJson(json, SavedPlace::class.java) }.getOrNull()
    }

    fun savePlace(place: SavedPlace): SavedPlace {
        prefs.edit().putString(KEY_PLACE, gson.toJson(place)).apply()
        return place
    }

    /** Hands the location back to the device's own positioning. */
    fun clearPlace() {
        prefs.edit().remove(KEY_PLACE).apply()
    }

    private fun cleanDisplayName(name: String): String =
        name.trim().take(MAX_NAME_CHARS).ifBlank { DEFAULT_NAME }

    private companion object {
        const val PREFS_NAME = "sensenav_profile"
        const val KEY_NAME = "display_name"
        const val KEY_PLACE = "saved_place"
        const val KEY_REMEMBER_LOGIN = "remember_login"
        const val DEFAULT_NAME = "Matr Kohler"
        const val MAX_NAME_CHARS = 40
    }
}
