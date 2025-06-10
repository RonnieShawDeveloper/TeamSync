// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/GeofenceScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue

class GeofenceScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            Image(
                painter = painterResource(id = R.drawable.background1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "Active Geofences",
                                color = DarkBlue,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            // UPDATED: Navigate to the new screen
                            navController.navigate(NavRoutes.CREATE_GEOFENCE)
                        },
                        containerColor = DarkBlue
                    ) {
                        Icon(Icons.Filled.Add, "Create New Geofence", tint = Color.White)
                    }
                },
                containerColor = Color.Transparent
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // This is where the list of existing geofences will go.
                    Text(
                        text = "Your active geofences will be listed here. Tap on them to apply settings or view details.",
                        fontSize = 18.sp,
                        color = DarkBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}