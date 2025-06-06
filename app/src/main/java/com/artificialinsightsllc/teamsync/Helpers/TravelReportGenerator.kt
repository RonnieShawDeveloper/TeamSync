// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/TravelReportGenerator.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.TravelReportEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Helper object responsible for analyzing a list of raw location data points
 * and generating a structured travel report (stationary periods and travel segments).
 */
object TravelReportGenerator {

    // Constants for stationary detection
    private const val STATIONARY_RADIUS_METERS = 50.0 // Points within this radius are considered stationary
    private const val MIN_STATIONARY_DURATION_MILLIS = 10 * 60 * 1000L // 10 minutes
    private const val MAX_ACCEPTABLE_GAP_MILLIS = 15 * 60 * 1000L // 15 minutes - if gap is larger, new segment starts

    /**
     * Generates a travel report from a list of historical location data points.
     * It segments the data into stationary periods and travel segments.
     *
     * @param locations A chronological list of raw Locations data points.
     * @param context Application context for geocoding.
     * @return A list of TravelReportEntry objects representing the user's activity.
     */
    suspend fun generateReport(locations: List<Locations>, context: Context): List<TravelReportEntry> {
        val reportEntries = mutableListOf<TravelReportEntry>()
        if (locations.isEmpty()) {
            return reportEntries
        }

        // Ensure locations are sorted by timestamp (Firestore query should handle this, but for safety)
        val sortedLocations = locations.sortedBy { it.timestamp }

        var currentIndex = 0
        while (currentIndex < sortedLocations.size) {
            val segmentStartLocation = sortedLocations[currentIndex]
            var segmentEndIndex = currentIndex

            // --- Attempt to find a stationary segment first ---
            var potentialStationaryPoints = mutableListOf<Locations>()
            potentialStationaryPoints.add(segmentStartLocation)
            var tempIndex = currentIndex + 1

            while (tempIndex < sortedLocations.size) {
                val nextLocation = sortedLocations[tempIndex]
                val distanceToStart = calculateDistance(
                    segmentStartLocation.latitude, segmentStartLocation.longitude,
                    nextLocation.latitude, nextLocation.longitude
                )

                val timeGap = nextLocation.timestamp - sortedLocations[tempIndex - 1].timestamp

                if (distanceToStart <= STATIONARY_RADIUS_METERS && timeGap <= MAX_ACCEPTABLE_GAP_MILLIS) {
                    potentialStationaryPoints.add(nextLocation)
                    segmentEndIndex = tempIndex // Expand segment end
                    tempIndex++
                } else {
                    // Current point is too far or there's a big time gap
                    break
                }
            }

            val stationaryDuration = sortedLocations[segmentEndIndex].timestamp - segmentStartLocation.timestamp

            if (potentialStationaryPoints.size > 1 && stationaryDuration >= MIN_STATIONARY_DURATION_MILLIS) {
                // Found a valid stationary segment
                val avgLat = potentialStationaryPoints.map { it.latitude }.average()
                val avgLon = potentialStationaryPoints.map { it.longitude }.average()
                val address = geocodeLocation(context, avgLat, avgLon)
                reportEntries.add(
                    TravelReportEntry.Stationary(
                        startTimeMillis = segmentStartLocation.timestamp,
                        endTimeMillis = sortedLocations[segmentEndIndex].timestamp,
                        durationMillis = stationaryDuration,
                        latitude = avgLat,
                        longitude = avgLon,
                        address = address
                    )
                )
                currentIndex = segmentEndIndex + 1 // Move past the stationary segment
            } else {
                // Not a stationary segment, or too short. Treat as part of a travel segment.
                // This means the 'stationary' buffer failed, so we're starting a travel segment
                // or continuing one if the previous wasn't a confirmed stationary period.
                var travelStartLocation = sortedLocations[currentIndex]
                var travelEndIndex = currentIndex
                var totalDistanceMeters = 0.0
                var travelDurationMillis = 0L

                tempIndex = currentIndex + 1
                while (tempIndex < sortedLocations.size) {
                    val prevLocation = sortedLocations[tempIndex - 1]
                    val nextLocation = sortedLocations[tempIndex]

                    val distance = calculateDistance(
                        prevLocation.latitude, prevLocation.longitude,
                        nextLocation.latitude, nextLocation.longitude
                    )
                    val timeBetweenPoints = nextLocation.timestamp - prevLocation.timestamp

                    if (distance > (STATIONARY_RADIUS_METERS / 2.0) && timeBetweenPoints <= MAX_ACCEPTABLE_GAP_MILLIS) {
                        totalDistanceMeters += distance
                        travelDurationMillis += timeBetweenPoints
                        travelEndIndex = tempIndex
                        tempIndex++
                    } else if (timeBetweenPoints > MAX_ACCEPTABLE_GAP_MILLIS && totalDistanceMeters > 0) {
                        break
                    } else if (timeBetweenPoints > MAX_ACCEPTABLE_GAP_MILLIS && totalDistanceMeters == 0.0 && tempIndex > currentIndex + 1) {
                        break
                    } else {
                        totalDistanceMeters += distance
                        travelDurationMillis += timeBetweenPoints
                        travelEndIndex = tempIndex
                        tempIndex++
                    }
                }

                if (totalDistanceMeters > 0.0 || travelDurationMillis > 0L) {
                    val endTime = sortedLocations[travelEndIndex].timestamp
                    val avgSpeedMps = if (travelDurationMillis > 0) (totalDistanceMeters / (travelDurationMillis / 1000.0)) else 0.0
                    val avgSpeedMph = UnitConverter.metersPerSecondToMilesPerHour(avgSpeedMps.toFloat()).toDouble()
                    val distanceMiles = UnitConverter.metersToMiles(totalDistanceMeters).toDouble() // FIX: Corrected typo

                    reportEntries.add(
                        TravelReportEntry.Travel(
                            startTimeMillis = travelStartLocation.timestamp,
                            endTimeMillis = endTime,
                            durationMillis = endTime - travelStartLocation.timestamp,
                            startLatitude = travelStartLocation.latitude,
                            startLongitude = travelStartLocation.longitude,
                            endLatitude = sortedLocations[travelEndIndex].latitude,
                            endLongitude = sortedLocations[travelEndIndex].longitude,
                            distanceMiles = distanceMiles,
                            averageMph = avgSpeedMph
                        )
                    )
                }
                currentIndex = travelEndIndex + 1
            }
        }

        Log.d("TravelReportGenerator", "Generated ${reportEntries.size} report entries.")
        return reportEntries
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }


    private suspend fun geocodeLocation(context: Context, latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                if (!Geocoder.isPresent()) {
                    Log.e("Geocoding", "Geocoder is not present on this device.")
                    return@withContext "Address lookup failed (Geocoder not available)"
                }
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"
            } catch (e: IOException) {
                Log.e("Geocoding", "Geocoding failed for $latitude, $longitude: ${e.message}")
                "Address lookup failed (Network/IO error)"
            } catch (e: IllegalArgumentException) {
                Log.e("Geocoding", "Invalid LatLng for geocoding: $latitude, $longitude: ${e.message}")
                "Invalid location (Coordinates error)"
            } catch (e: Exception) {
                Log.e("Geocoding", "Unexpected geocoding error for $latitude, $longitude: ${e.message}")
                "Address lookup failed (Unknown error)"
            }
        }
    }
}
