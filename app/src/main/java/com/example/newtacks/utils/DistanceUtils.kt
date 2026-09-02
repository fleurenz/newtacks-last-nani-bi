package com.example.newtacks.utils

import java.util.Locale

object DistanceUtils {
    /**
     * Formats distance in meters to a readable string (m or km).
     * @param meters distance in meters
     * @return Formatted string like "450 m" or "1.2 km"
     */
    fun formatDistance(meters: Float): String {
        return if (meters < 1000) {
            String.format(Locale.getDefault(), "%d m", meters.toInt())
        } else {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000)
        }
    }
}