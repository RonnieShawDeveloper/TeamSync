// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/UnitConverter.kt
package com.artificialinsightsllc.teamsync.Helpers

import kotlin.math.roundToInt

/**
 * Helper object for converting speed and bearing units.
 */
object UnitConverter {

    // --- Speed Conversions ---

    /**
     * Converts speed from meters per second (m/s) to miles per hour (MPH).
     * 1 m/s = 2.23694 MPH
     *
     * @param speedMps Speed in meters per second.
     * @return Speed in miles per hour.
     */
    fun metersPerSecondToMilesPerHour(speedMps: Float): Float {
        return speedMps * 2.23694f
    }

    /**
     * Converts speed from meters per second (m/s) to kilometers per hour (km/h).
     * 1 m/s = 3.6 km/h
     *
     * @param speedMps Speed in meters per second.
     * @return Speed in kilometers per hour.
     */
    fun metersPerSecondToKilometersPerHour(speedMps: Float): Float {
        return speedMps * 3.6f
    }

    /**
     * Converts speed from meters per second (m/s) to knots.
     * 1 m/s = 1.94384 knots
     *
     * @param speedMps Speed in meters per second.
     * @return Speed in knots.
     */
    fun metersPerSecondToKnots(speedMps: Float): Float {
        return speedMps * 1.94384f
    }

    // --- Bearing (Direction) Conversion ---

    /**
     * Converts bearing in degrees (0-360) to a cardinal or intercardinal direction string.
     *
     * @param bearingDegrees Bearing in degrees, where 0 is North.
     * @return A string representation of the cardinal direction (e.g., "N", "NE", "SW").
     */
    fun getCardinalDirection(bearingDegrees: Float): String {
        val directions = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        // Normalize bearing to be within 0-360 and adjust for array indexing
        val index = ((bearingDegrees % 360) / 22.5).roundToInt()
        return directions[index % 16] // Use modulo 16 to wrap around for N (index 0 or 16)
    }
}