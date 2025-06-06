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

    // Constants for stationary detection (Pass 1)
    private const val STATIONARY_RADIUS_METERS = 50.0 // Points within this radius are considered stationary
    private const val MIN_STATIONARY_DURATION_MILLIS = 5 * 60 * 1000L // 10 minutes
    private const val MAX_ACCEPTABLE_GAP_MILLIS = 15 * 60 * 1000L // 15 minutes - if gap is larger, new segment starts

    // Constants for gap bridging (Pass 2)
    private const val STATIONARY_BRIDGE_RADIUS_METERS = 100.0 // Max distance between two stationary segments to bridge a gap
    private const val TRAVEL_BRIDGE_MAX_TELEPORT_DISTANCE_METERS = 2000.0 // Max distance jump in a travel gap to still consider it continuous travel
    private const val TRAVEL_BRIDGE_MAX_GAP_DURATION_MILLIS = 30 * 60 * 1000L // 30 minutes - max gap duration to bridge travel

    /**
     * Generates a travel report from a list of historical location data points.
     * This function implements a two-pass approach:
     * Pass 1: Segments raw data into initial Stationary, Travel, and DataGap entries.
     * Pass 2: Processes the initial report to merge adjacent segments across DataGaps
     * if they represent a continuation of the same activity (stationary at same place,
     * or continuous travel).
     *
     * @param locations A chronological list of raw Locations data points.
     * @param context Application context for geocoding.
     * @return A list of TravelReportEntry objects representing the user's activity.
     */
    suspend fun generateReport(locations: List<Locations>, context: Context): List<TravelReportEntry> {
        if (locations.isEmpty()) {
            return emptyList()
        }

        // Ensure locations are sorted by timestamp
        val sortedLocations = locations.sortedBy { it.timestamp }

        // --- PASS 1: Initial Segmentation ---
        val initialReport = mutableListOf<TravelReportEntry>()
        var currentIndex = 0
        while (currentIndex < sortedLocations.size) {
            val segmentStartLocation = sortedLocations[currentIndex]
            var segmentEndIndex = currentIndex

            // Add DataGap if there's a significant time gap since the previous point
            if (currentIndex > 0) {
                val previousLocation = sortedLocations[currentIndex - 1]
                val timeGap = segmentStartLocation.timestamp - previousLocation.timestamp
                if (timeGap > MAX_ACCEPTABLE_GAP_MILLIS) {
                    initialReport.add(
                        TravelReportEntry.DataGap(
                            startTimeMillis = previousLocation.timestamp,
                            endTimeMillis = segmentStartLocation.timestamp,
                            durationMillis = timeGap
                        )
                    )
                }
            }

            // Attempt to find a stationary segment
            var potentialStationaryPoints = mutableListOf<Locations>()
            potentialStationaryPoints.add(segmentStartLocation)
            var tempIndex = currentIndex + 1

            while (tempIndex < sortedLocations.size) {
                val nextLocation = sortedLocations[tempIndex]
                val distanceToStart = calculateDistance(
                    segmentStartLocation.latitude, segmentStartLocation.longitude,
                    nextLocation.latitude, nextLocation.longitude
                ).toDouble()

                val timeGapWithinSegment = nextLocation.timestamp - sortedLocations[tempIndex - 1].timestamp

                if (distanceToStart <= STATIONARY_RADIUS_METERS && timeGapWithinSegment <= MAX_ACCEPTABLE_GAP_MILLIS) {
                    potentialStationaryPoints.add(nextLocation)
                    segmentEndIndex = tempIndex
                    tempIndex++
                } else {
                    break
                }
            }

            val stationaryDuration = sortedLocations[segmentEndIndex].timestamp - segmentStartLocation.timestamp

            if (potentialStationaryPoints.size > 1 && stationaryDuration >= MIN_STATIONARY_DURATION_MILLIS) {
                // Found a valid stationary segment
                val avgLat = potentialStationaryPoints.map { it.latitude }.average()
                val avgLon = potentialStationaryPoints.map { it.longitude }.average()
                val address = geocodeLocation(context, avgLat, avgLon)
                initialReport.add(
                    TravelReportEntry.Stationary(
                        startTimeMillis = segmentStartLocation.timestamp,
                        endTimeMillis = sortedLocations[segmentEndIndex].timestamp,
                        durationMillis = stationaryDuration,
                        latitude = avgLat,
                        longitude = avgLon,
                        address = address
                    )
                )
                currentIndex = segmentEndIndex + 1
            } else {
                // Not a stationary segment, or too short. Treat as part of a travel segment.
                var travelStartLocation = sortedLocations[currentIndex]
                var travelEndIndex = currentIndex
                var totalDistanceMeters = 0.0
                var travelDurationMillisInSegment = 0L // Cumulative duration within the current travel segment

                tempIndex = currentIndex + 1
                while (tempIndex < sortedLocations.size) {
                    val prevLocation = sortedLocations[tempIndex - 1]
                    val nextLocation = sortedLocations[tempIndex]

                    val distance = calculateDistance(
                        prevLocation.latitude, prevLocation.longitude,
                        nextLocation.latitude, nextLocation.longitude
                    ).toDouble()
                    val timeBetweenPoints = nextLocation.timestamp - prevLocation.timestamp

                    if (timeBetweenPoints > MAX_ACCEPTABLE_GAP_MILLIS) {
                        break
                    }

                    totalDistanceMeters += distance
                    travelDurationMillisInSegment += timeBetweenPoints
                    travelEndIndex = tempIndex
                    tempIndex++
                }

                val totalSegmentDuration = sortedLocations[travelEndIndex].timestamp - travelStartLocation.timestamp

                // Only add a travel entry if there was actual movement (distance) or a significant duration covered.
                if (totalDistanceMeters > STATIONARY_RADIUS_METERS || totalSegmentDuration >= MIN_STATIONARY_DURATION_MILLIS) {
                    val endTime = sortedLocations[travelEndIndex].timestamp
                    val avgSpeedMps = if (totalSegmentDuration > 0) (totalDistanceMeters / (totalSegmentDuration / 1000.0)) else 0.0
                    val avgSpeedMph = UnitConverter.metersPerSecondToMilesPerHour(avgSpeedMps.toFloat()).toDouble()
                    val distanceMiles = UnitConverter.metersToMiles(totalDistanceMeters)

                    val (startCity, startState) = getCityState(context, travelStartLocation.latitude, travelStartLocation.longitude)
                    val (endCity, endState) = getCityState(context, sortedLocations[travelEndIndex].latitude, sortedLocations[travelEndIndex].longitude)

                    initialReport.add(
                        TravelReportEntry.Travel(
                            startTimeMillis = travelStartLocation.timestamp,
                            endTimeMillis = endTime,
                            durationMillis = totalSegmentDuration,
                            startLatitude = travelStartLocation.latitude,
                            startLongitude = travelStartLocation.longitude,
                            endLatitude = sortedLocations[travelEndIndex].latitude,
                            endLongitude = sortedLocations[travelEndIndex].longitude,
                            distanceMiles = distanceMiles,
                            averageMph = avgSpeedMph,
                            startCity = startCity,
                            startState = startState,
                            endCity = endCity,
                            endState = endState
                        )
                    )
                }
                currentIndex = travelEndIndex + 1
            }
        }
        Log.d("TravelReportGenerator", "Pass 1 completed. Generated ${initialReport.size} initial report entries.")

        // --- PASS 2: Gap Bridging ---
        val finalReport = processGaps(initialReport, context)
        Log.d("TravelReportGenerator", "Pass 2 completed. Final report has ${finalReport.size} entries.")

        return finalReport
    }

    /**
     * Pass 2: Processes the initial report to merge segments across DataGaps.
     * This function iterates through the report and attempts to combine consecutive
     * Stationary or Travel segments if they are separated by a DataGap and
     * meet specific bridging criteria.
     *
     * @param initialReport The list of TravelReportEntry objects from Pass 1.
     * @param context Application context for geocoding.
     * @return A refined list of TravelReportEntry objects with gaps bridged where appropriate.
     */
    private suspend fun processGaps(initialReport: List<TravelReportEntry>, context: Context): List<TravelReportEntry> {
        val finalReport = mutableListOf<TravelReportEntry>()
        var i = 0
        while (i < initialReport.size) {
            val currentEntry = initialReport[i]

            if (currentEntry is TravelReportEntry.DataGap) {
                // Try to bridge this gap
                if (i > 0 && i < initialReport.size - 1) {
                    val prevEntry = finalReport.lastOrNull() // Get the last entry added to finalReport
                    val nextEntry = initialReport[i + 1]

                    var bridged = false

                    if (prevEntry is TravelReportEntry.Stationary && nextEntry is TravelReportEntry.Stationary) {
                        val distanceBetweenStationaryPoints = calculateDistance(
                            prevEntry.latitude, prevEntry.longitude,
                            nextEntry.latitude, nextEntry.longitude
                        ).toDouble()

                        if (distanceBetweenStationaryPoints <= STATIONARY_BRIDGE_RADIUS_METERS) {
                            Log.d("TravelReportGenerator", "Bridging Stationary gap.")
                            // Merge stationary segments
                            val combinedLat = (prevEntry.latitude + nextEntry.latitude) / 2
                            val combinedLon = (prevEntry.longitude + nextEntry.longitude) / 2
                            val combinedAddress = geocodeLocation(context, combinedLat, combinedLon)

                            finalReport[finalReport.lastIndex] = TravelReportEntry.Stationary(
                                startTimeMillis = prevEntry.startTimeMillis,
                                endTimeMillis = nextEntry.endTimeMillis,
                                durationMillis = nextEntry.endTimeMillis - prevEntry.startTimeMillis,
                                latitude = combinedLat,
                                longitude = combinedLon,
                                address = combinedAddress
                            )
                            i += 2 // Skip DataGap and nextEntry
                            bridged = true
                        }
                    } else if (prevEntry is TravelReportEntry.Travel && nextEntry is TravelReportEntry.Travel) {
                        val distanceBetweenTravelSegments = calculateDistance(
                            prevEntry.endLatitude, prevEntry.endLongitude,
                            nextEntry.startLatitude, nextEntry.startLongitude
                        ).toDouble()

                        if (distanceBetweenTravelSegments <= TRAVEL_BRIDGE_MAX_TELEPORT_DISTANCE_METERS &&
                            currentEntry.durationMillis <= TRAVEL_BRIDGE_MAX_GAP_DURATION_MILLIS) {
                            Log.d("TravelReportGenerator", "Bridging Travel gap.")
                            // Merge travel segments
                            val combinedDistanceMiles = prevEntry.distanceMiles + nextEntry.distanceMiles
                            val combinedDurationMillis = nextEntry.endTimeMillis - prevEntry.startTimeMillis
                            val combinedAvgMph = if (combinedDurationMillis > 0) {
                                (combinedDistanceMiles / (combinedDurationMillis / 3600000.0))
                            } else 0.0

                            val (startCity, startState) = getCityState(context, prevEntry.startLatitude, prevEntry.startLongitude)
                            val (endCity, endState) = getCityState(context, nextEntry.endLatitude, nextEntry.endLongitude)

                            finalReport[finalReport.lastIndex] = TravelReportEntry.Travel(
                                startTimeMillis = prevEntry.startTimeMillis,
                                endTimeMillis = nextEntry.endTimeMillis,
                                durationMillis = combinedDurationMillis,
                                startLatitude = prevEntry.startLatitude,
                                startLongitude = prevEntry.startLongitude,
                                endLatitude = nextEntry.endLatitude,
                                endLongitude = nextEntry.endLongitude,
                                distanceMiles = combinedDistanceMiles,
                                averageMph = combinedAvgMph,
                                startCity = startCity,
                                startState = startState,
                                endCity = endCity,
                                endState = endState
                            )
                            i += 2 // Skip DataGap and nextEntry
                            bridged = true
                        }
                    }

                    if (!bridged) {
                        // If not bridged, add the DataGap and the next entry normally
                        finalReport.add(currentEntry)
                        i++ // Move to next entry (the DataGap)
                    }
                } else {
                    // DataGap at the beginning or end, cannot bridge
                    finalReport.add(currentEntry)
                    i++
                }
            } else {
                // Not a DataGap, just add it to the final report
                finalReport.add(currentEntry)
                i++
            }
        }
        return finalReport
    }

    /**
     * Calculates the distance between two geographical points using the Haversine formula
     * (or Location.distanceBetween, which is more accurate).
     * @param lat1 Latitude of the first point.
     * @param lon1 Longitude of the first point.
     * @param lat2 Latitude of the second point.
     * @param lon2 Longitude of the second point.
     * @return Distance in meters.
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        // This is the most accurate way to calculate distance between two LatLngs in Android.
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Performs reverse geocoding to get a human-readable address from latitude and longitude.
     * @param context Application context.
     * @param latitude Latitude of the location.
     * @param longitude Longitude of the location.
     * @return A formatted address string, or an error message if geocoding fails.
     */
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

    /**
     * Performs reverse geocoding to get city and state from latitude and longitude.
     * @param context Application context.
     * @param latitude Latitude of the location.
     * @param longitude Longitude of the location.
     * @return A Pair of nullable Strings: first for city, second for state. Returns Pair(null, null) if not found or on error.
     */
    private suspend fun getCityState(context: Context, latitude: Double, longitude: Double): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                if (!Geocoder.isPresent()) {
                    Log.e("Geocoding", "Geocoder is not present on this device, cannot get city/state.")
                    return@withContext Pair(null, null)
                }
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                val city = address?.locality
                val state = address?.adminArea
                Log.d("Geocoding", "City/State for $latitude, $longitude: $city, $state")
                Pair(city, state)
            } catch (e: IOException) {
                Log.e("Geocoding", "City/State geocoding failed for $latitude, $longitude: ${e.message}")
                Pair(null, null)
            } catch (e: IllegalArgumentException) {
                Log.e("Geocoding", "Invalid LatLng for city/state geocoding: $latitude, $longitude: ${e.message}")
                Pair(null, null)
            } catch (e: Exception) {
                Log.e("Geocoding", "Unexpected city/state geocoding error for $latitude, $longitude: ${e.message}")
                Pair(null, null)
            }
        }
    }
}
