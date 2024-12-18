package com.guzzardo.tictacdoh2

import android.content.Context
import android.location.Location
import androidx.preference.PreferenceManager

internal object Utils {
    const val KEY_REQUESTING_LOCATION_UPDATES = "requesting_location_updates"

    /**
     * Returns true if requesting location updates, otherwise returns false.
     *
     * @param context The [Context].
     */
    @kotlin.jvm.JvmStatic
    fun requestingLocationUpdates(context: android.content.Context?): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context!!)
            .getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false)
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    @JvmStatic
    fun setRequestingLocationUpdates(context: Context?, requestingLocationUpdates: Boolean) {
        if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
                .apply()
        }
    }

    /**
     * Returns the `location` object as a human readable string.
     * @param location  The [Location].
     */
    @JvmStatic
    fun getLocationText(location: Location?): String {
        return if (location == null) "Unknown location" else "(" + location.latitude + ", " + location.longitude + ")"
    }

    @JvmStatic
    fun getLocationTitle(context: Context): String {
        return context.getString(R.string.location_updated)
    }
}