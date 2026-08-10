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

    fun hasCompletedOnboarding(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun sensitivityRange(): ClosedFloatingPointRange<Float> {
        val start = prefs.getFloat(KEY_SENSITIVITY_START, DEFAULT_SENSITIVITY_START)
            .coerceIn(0f, 1f)
        val end = prefs.getFloat(KEY_SENSITIVITY_END, DEFAULT_SENSITIVITY_END)
            .coerceIn(start, 1f)
        return start..end
    }

    fun sensoryPreferenceRating(): Int =
        prefs.getInt(KEY_PREFERENCE_RATING, DEFAULT_PREFERENCE_RATING).coerceIn(1, 5)

    /** Returns the stored value, which may differ from the input after trimming. */
    fun saveDisplayName(name: String): String {
        val cleaned = name.trim().take(MAX_NAME_CHARS).ifBlank { DEFAULT_NAME }
        prefs.edit().putString(KEY_NAME, cleaned).apply()
        return cleaned
    }

    fun saveOnboardingProfile(
        name: String,
        sensitivityRange: ClosedFloatingPointRange<Float>,
        preferenceRating: Int
    ): String {
        val cleaned = name.trim().take(MAX_NAME_CHARS).ifBlank { DEFAULT_NAME }
        val start = sensitivityRange.start.coerceIn(0f, 1f)
        val end = sensitivityRange.endInclusive.coerceIn(start, 1f)
        prefs.edit()
            .putString(KEY_NAME, cleaned)
            .putFloat(KEY_SENSITIVITY_START, start)
            .putFloat(KEY_SENSITIVITY_END, end)
            .putInt(KEY_PREFERENCE_RATING, preferenceRating.coerceIn(1, 5))
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
        return cleaned
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

    private companion object {
        const val PREFS_NAME = "sensenav_profile"
        const val KEY_NAME = "display_name"
        const val KEY_PLACE = "saved_place"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_SENSITIVITY_START = "sensitivity_start"
        const val KEY_SENSITIVITY_END = "sensitivity_end"
        const val KEY_PREFERENCE_RATING = "preference_rating"
        const val DEFAULT_NAME = "Matr Kohler"
        const val MAX_NAME_CHARS = 40
        const val DEFAULT_SENSITIVITY_START = 0.05f
        const val DEFAULT_SENSITIVITY_END = 0.35f
        const val DEFAULT_PREFERENCE_RATING = 4
    }
}
