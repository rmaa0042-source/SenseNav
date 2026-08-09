package com.example.sensenav.data

import android.content.Context
import com.example.sensenav.model.SensoryFilter

/**
 * The sensory bands and search radius set on the map's filter, held on the
 * device only.
 *
 * Kept across launches deliberately. These are an accessibility setting rather
 * than a transient view option: someone who has told the app what counts as too
 * busy for them should not have to say it again every time they open it.
 */
class FilterStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Normalised on the way out, so a value written by an older build - or one
     * whose bounds have since crossed over - cannot produce an unorderable set
     * of bands.
     */
    fun filter(): SensoryFilter = SensoryFilter(
        lowMaxPedestrians = prefs.getInt(KEY_LOW, SensoryFilter.DEFAULT_LOW_MAX),
        mediumMaxPedestrians = prefs.getInt(KEY_MEDIUM, SensoryFilter.DEFAULT_MEDIUM_MAX),
        radiusKm = prefs.getInt(KEY_RADIUS, SensoryFilter.DEFAULT_RADIUS_KM)
    ).normalised()

    /** Returns the stored value, which may differ from the input after clamping. */
    fun save(filter: SensoryFilter): SensoryFilter {
        val cleaned = filter.normalised()
        prefs.edit()
            .putInt(KEY_LOW, cleaned.lowMaxPedestrians)
            .putInt(KEY_MEDIUM, cleaned.mediumMaxPedestrians)
            .putInt(KEY_RADIUS, cleaned.radiusKm)
            .apply()
        return cleaned
    }

    private companion object {
        const val PREFS_NAME = "sensenav_filter"
        const val KEY_LOW = "low_max_pedestrians"
        const val KEY_MEDIUM = "medium_max_pedestrians"
        const val KEY_RADIUS = "radius_km"
    }
}
