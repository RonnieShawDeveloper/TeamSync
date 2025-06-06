// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/TravelReportScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Helpers.TravelReportGenerator
import com.artificialinsightsllc.teamsync.Helpers.TimeFormatter
import com.artificialinsightsllc.teamsync.Models.TravelReportEntry
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.R
import androidx.compose.foundation.layout.systemBarsPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream


class TravelReportScreen(private val navController: NavHostController, private val userId: String) {

    @Composable
    fun Content() {
        val context = LocalContext.current
        val firestoreService = remember { FirestoreService() }
        val coroutineScope = rememberCoroutineScope()
        val DarkBlue = Color(0xFF0D47A1)
        val LightCream = Color(0xFFFFFDD0)

        var isLoading by remember { mutableStateOf(true) }
        var reportEntries by remember { mutableStateOf<List<TravelReportEntry>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showScreen by remember { mutableStateOf(false) } // For animation visibility

        LaunchedEffect(userId) {
            showScreen = true // Trigger slide-in animation
            isLoading = true
            errorMessage = null
            reportEntries = emptyList()

            try {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (24 * 60 * 60 * 1000L) // Last 24 hours

                val locationsResult = firestoreService.getLocationsHistoryForUser(userId, startTime, endTime)

                if (locationsResult.isSuccess) {
                    val rawLocations = locationsResult.getOrNull() ?: emptyList()
                    if (rawLocations.isEmpty()) {
                        errorMessage = "No location history found for the last 24 hours."
                        Log.d("TravelReportScreen", "No location history for user $userId.")
                    } else {
                        Log.d("TravelReportScreen", "Fetched ${rawLocations.size} raw locations. Generating report...")
                        val generatedReport = TravelReportGenerator.generateReport(rawLocations, context)
                        reportEntries = generatedReport.sortedByDescending {
                            when (it) {
                                is TravelReportEntry.Stationary -> it.startTimeMillis
                                is TravelReportEntry.Travel -> it.startTimeMillis
                                is TravelReportEntry.DataGap -> it.startTimeMillis
                            }
                        }
                        if (generatedReport.isEmpty()) {
                            errorMessage = "No significant travel or stationary events found in the last 24 hours."
                        }
                    }
                } else {
                    errorMessage = "Failed to load location history: ${locationsResult.exceptionOrNull()?.message}"
                    Log.e("TravelReportScreen", "Error fetching location history: ${locationsResult.exceptionOrNull()?.message}")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                errorMessage = "An unexpected error occurred: ${e.message}"
                Log.e("TravelReportScreen", "Unexpected error: ${e.message}")
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = R.drawable.background1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            showScreen = false // Trigger slide-out animation
                            delay(550) // Wait for animation to complete
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DarkBlue)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Travel Report",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DarkBlue,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(48.dp)) // To balance the back button
                }

                Spacer(Modifier.height(16.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        Text(
                            text = "Generating report...",
                            color = DarkBlue.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "An unknown error occurred.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                } else if (reportEntries.isEmpty()) {
                    Text(
                        text = "No activity found for this period.",
                        color = DarkBlue.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(reportEntries) { entry ->
                            when (entry) {
                                is TravelReportEntry.Stationary -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(0.95f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = LightCream.copy(alpha = 0.9f))
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Stationary Period",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = DarkBlue
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "From: ${TimeFormatter.formatTimestampToDateTime(entry.startTimeMillis)}",
                                                color = DarkBlue.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "To: ${TimeFormatter.formatTimestampToDateTime(entry.endTimeMillis)}",
                                                color = DarkBlue.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "Duration: ${TimeFormatter.formatDuration(entry.durationMillis)}",
                                                color = DarkBlue.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "At: ${entry.address}",
                                                color = DarkBlue,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                is TravelReportEntry.Travel -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(0.95f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.1f))
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Travel Segment",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = DarkBlue
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "From: ${TimeFormatter.formatTimestampToDateTime(entry.startTimeMillis)}",
                                                color = DarkBlue.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "To: ${TimeFormatter.formatTimestampToDateTime(entry.endTimeMillis)}",
                                                color = DarkBlue.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "Duration: ${TimeFormatter.formatDuration(entry.durationMillis)}",
                                                color = DarkBlue.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "Distance: ${String.format("%.2f", entry.distanceMiles)} miles",
                                                color = DarkBlue,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Avg Speed: ${String.format("%.1f", entry.averageMph)} MPH",
                                                color = DarkBlue,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (entry.startCity != null || entry.startState != null) {
                                                Text(
                                                    text = "Start: ${entry.startCity?.plus(", ") ?: ""}${entry.startState ?: ""}",
                                                    color = DarkBlue.copy(alpha = 0.8f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                            if (entry.endCity != null || entry.endState != null) {
                                                Text(
                                                    text = "End: ${entry.endCity?.plus(", ") ?: ""}${entry.endState ?: ""}",
                                                    color = DarkBlue.copy(alpha = 0.8f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                is TravelReportEntry.DataGap -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(0.95f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f))
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Data Gap",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = Color.DarkGray
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "From: ${TimeFormatter.formatTimestampToDateTime(entry.startTimeMillis)}",
                                                color = Color.DarkGray.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "To: ${TimeFormatter.formatTimestampToDateTime(entry.endTimeMillis)}",
                                                color = Color.DarkGray.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = "No data recorded for: ${TimeFormatter.formatDuration(entry.durationMillis)}",
                                                color = Color.DarkGray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
