// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MarkerInfoDialog.kt
package com.artificialinsightsllc.teamsync.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artificialinsightsllc.teamsync.Helpers.TimeFormatter // <--- NEW IMPORT
import com.artificialinsightsllc.teamsync.Helpers.UnitConverter // <--- NEW IMPORT
import kotlinx.coroutines.delay // <--- NEW IMPORT

// Custom color for dark blue text (re-declare or import from a common place if available)
// val DarkBlue = Color(0xFF00008B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerInfoDialog(
    title: String,
    timestamp: Long, // <--- CHANGED: Now accepts raw timestamp
    speed: Float?,   // <--- NEW: Accepts speed
    bearing: Float?, // <--- NEW: Accepts bearing
    onDismissRequest: () -> Unit // Callback to dismiss the dialog
) {
    // State to hold the dynamically updated time ago string
    var timeAgoString by remember { mutableStateOf("") }

    // LaunchedEffect to update the timeAgoString every second
    LaunchedEffect(timestamp) { // Re-run if timestamp changes (e.g., if dialog is reused for another marker)
        while (true) {
            timeAgoString = TimeFormatter.getRelativeTimeSpanString(timestamp).toString()
            delay(1000) // Update every second
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                // Dynamically build the snippet content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Updated $timeAgoString", // Use the dynamic timeAgoString
                        fontSize = 14.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    speed?.let {
                        Text(
                            text = "Speed: ${String.format("%.1f", UnitConverter.metersPerSecondToMilesPerHour(it))} MPH",
                            fontSize = 14.sp,
                            color = DarkBlue.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    bearing?.let {
                        Text(
                            text = "Direction: ${UnitConverter.getCardinalDirection(it)}",
                            fontSize = 14.sp,
                            color = DarkBlue.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                ) {
                    Text("Close", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}