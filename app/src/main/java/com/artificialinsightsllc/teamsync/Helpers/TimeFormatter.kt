// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/TimeFormatter.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormatter {

    /**
     * Returns a string describing 'when' something happened in relation to the current time.
     * For example, "2 hours ago", "yesterday", "3 days ago".
     *
     * @param timestampMillis The timestamp in milliseconds to format.
     * @return A relative time string.
     */
    fun getRelativeTimeSpanString(timestampMillis: Long): CharSequence {
        return DateUtils.getRelativeTimeSpanString(timestampMillis, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS)
    }

    /**
     * Formats a timestamp into a human-readable date and time string (e.g., "MM/dd/yyyy hh:mm:ss a").
     *
     * @param timestampMillis The timestamp in milliseconds to format.
     * @return Formatted date and time string.
     */
    fun formatTimestampToDateTime(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date(timestampMillis))
    }

    /**
     * Formats a duration in milliseconds into a human-readable string (e.g., "1h 30m 15s").
     * If the duration is less than a minute, it will show seconds.
     *
     * @param durationMillis The duration in milliseconds.
     * @return Formatted duration string.
     */
    fun formatDuration(durationMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60

        return when {
            hours > 0 -> String.format("%dh %02dm %02ds", hours, minutes, seconds)
            minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }
}
