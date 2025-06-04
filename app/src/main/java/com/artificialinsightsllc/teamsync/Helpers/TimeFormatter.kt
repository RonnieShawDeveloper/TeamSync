// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/TimeFormatter.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.text.format.DateUtils

/**
 * Helper object for formatting time-related strings.
 */
object TimeFormatter {

    /**
     * Converts a Unix timestamp (in milliseconds) to a human-readable relative time span string.
     * Examples: "5 minutes ago", "yesterday", "3 hours ago".
     *
     * @param timestampMillis The timestamp in milliseconds since the epoch.
     * @return A human-readable string representing the time elapsed since the timestamp.
     */
    fun getRelativeTimeSpanString(timestampMillis: Long): CharSequence {
        // DateUtils.getRelativeTimeSpanString is a built-in Android utility
        // It handles various time units (seconds, minutes, hours, days, etc.)
        // and provides localization.
        return DateUtils.getRelativeTimeSpanString(
            timestampMillis,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS // Granularity: update every second
        )
    }

    /**
     * Optional: You might also want a function to format a specific time.
     * For example, if the update was "more than a day ago", you might want to show "June 3, 2025, 10:30 AM".
     * For now, getRelativeTimeSpanString is sufficient as per your request.
     */
}