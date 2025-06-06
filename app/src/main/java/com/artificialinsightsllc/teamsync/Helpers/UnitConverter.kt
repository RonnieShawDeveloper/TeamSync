// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/UnitConverter.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.content.Context
import android.util.DisplayMetrics
import kotlin.math.roundToInt

object UnitConverter {

    // Converts density-independent pixels (dp) to pixels (px).
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    // Converts pixels (px) to density-independent pixels (dp).
    fun pxToDp(context: Context, px: Float): Float {
        return px / (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    // Converts meters per second to miles per hour.
    fun metersPerSecondToMilesPerHour(mps: Float): Float {
        return mps * 2.23694f // 1 m/s = 2.23694 mph
    }

    // Converts meters to miles.
    fun metersToMiles(meters: Double): Double {
        return meters * 0.000621371 // 1 meter = 0.000621371 miles
    }

    // Converts degrees (bearing) to cardinal direction (e.g., "N", "NE", "E").
    fun getCardinalDirection(bearing: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        return directions[((bearing % 360) / 45).roundToInt()]
    }
}
