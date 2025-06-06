// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/TravelReportEntry.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * A sealed class representing an entry in a user's travel report.
 * It can either be a period where the user was stationary, a segment where they were traveling,
 * or a period where no data was recorded.
 */
sealed class TravelReportEntry {

    /**
     * Represents a period where the user was stationary at a particular location.
     *
     * @property startTimeMillis The start timestamp of the stationary period in milliseconds.
     * @property endTimeMillis The end timestamp of the stationary period in milliseconds.
     * @property durationMillis The duration of the stationary period in milliseconds.
     * @property latitude The average latitude of the stationary location.
     * @property longitude The average longitude of the stationary location.
     * @property address The reverse geocoded address of the stationary location.
     */
    data class Stationary(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val durationMillis: Long,
        val latitude: Double,
        val longitude: Double,
        val address: String
    ) : TravelReportEntry()

    /**
     * Represents a segment where the user was traveling.
     *
     * @property startTimeMillis The start timestamp of the travel segment in milliseconds.
     * @property endTimeMillis The end timestamp of the travel segment in milliseconds.
     * @property durationMillis The duration of the travel segment in milliseconds.
     * @property startLatitude The latitude of the start point of the travel segment.
     * @property startLongitude The longitude of the start point of the travel segment.
     * @property endLatitude The latitude of the end point of the travel segment.
     * @property endLongitude The longitude of the end point of the travel segment.
     * @property distanceMiles The total distance traveled during this segment in miles.
     * @property averageMph The average speed during this segment in miles per hour.
     */
    data class Travel(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val durationMillis: Long,
        val startLatitude: Double,
        val startLongitude: Double,
        val endLatitude: Double,
        val endLongitude: Double,
        val distanceMiles: Double,
        val averageMph: Double
    ) : TravelReportEntry()

    /**
     * Represents a period during which no location data was recorded.
     *
     * @property startTimeMillis The start timestamp of the data gap in milliseconds.
     * @property endTimeMillis The end timestamp of the data gap in milliseconds.
     * @property durationMillis The duration of the data gap in milliseconds.
     */
    data class DataGap(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val durationMillis: Long
    ) : TravelReportEntry()
}
