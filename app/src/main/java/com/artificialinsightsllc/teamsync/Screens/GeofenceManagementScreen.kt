// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/GeofenceManagementScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Models.GeofenceZone
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.ViewModels.GeofenceAndIncidentViewModel
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GeofenceManagementScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(viewModel: GeofenceAndIncidentViewModel = viewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        val activeGeofenceZones by viewModel.activeGeofenceZones.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

        var showScreen by remember { mutableStateOf(false) }
        var showConfirmDeleteDialog by remember { mutableStateOf(false) }
        var zoneToDelete by remember { mutableStateOf<GeofenceZone?>(null) }

        LaunchedEffect(Unit) {
            showScreen = true
        }

        // Observe error messages from ViewModel
        LaunchedEffect(errorMessage) {
            errorMessage?.let { message ->
                // You can show a Toast, Snackbar, or AlertDialog here
                // For now, let's use a simple Toast. A more robust error display might be a separate dialog.
                // Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage() // Clear the message after showing
            }
        }


        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 500, delayMillis = 50)) { fullWidth ->
                -fullWidth
            } + fadeIn(animationSpec = tween(500, delayMillis = 50)),
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 500)) { fullWidth ->
                -fullWidth
            } + fadeOut(animationSpec = tween(500))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                                    text = "Manage Geofences",
                                    color = DarkBlue,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.White.copy(alpha = 0.7f)
                            ),
                            navigationIcon = {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        showScreen = false
                                        delay(550) // Wait for exit animation
                                        navController.popBackStack()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DarkBlue)
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                navController.navigate(NavRoutes.CREATE_GEOFENCE_DRAW) // Navigate to new creation mode
                            },
                            containerColor = DarkBlue
                        ) {
                            Icon(Icons.Filled.Add, "Add New Geofence", tint = Color.White)
                        }
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.6f)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = DarkBlue)
                            }
                        } else if (activeGeofenceZones.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No active geofences for this group. Tap '+' to create one!",
                                    color = DarkBlue.copy(alpha = 0.7f),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(activeGeofenceZones, key = { it.id }) { zone ->
                                    GeofenceZoneListItem(
                                        zone = zone,
                                        onEditClick = {
                                            viewModel.setSelectedGeofenceZone(it) // Set selected zone in ViewModel
                                            navController.navigate("${NavRoutes.CREATE_GEOFENCE_DRAW}?geofenceZoneId=${it.id}") // NEW: Navigate to edit mode
                                        },
                                        onDeleteClick = {
                                            zoneToDelete = it
                                            showConfirmDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Confirmation Dialog for Deletion
        if (showConfirmDeleteDialog && zoneToDelete != null) {
            AlertDialog(
                onDismissRequest = { showConfirmDeleteDialog = false; zoneToDelete = null },
                title = { Text("Delete Geofence?") },
                text = { Text("Are you sure you want to delete '${zoneToDelete!!.name}'? This action cannot be undone and will also delete its rules and assignments.") },
                confirmButton = {
                    Button(
                        onClick = {
                            zoneToDelete?.id?.let { viewModel.deleteGeofenceZone(it) }
                            showConfirmDeleteDialog = false
                            zoneToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDeleteDialog = false; zoneToDelete = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = LightCream
            )
        }
    }

    @Composable
    fun GeofenceZoneListItem(zone: GeofenceZone, onEditClick: (GeofenceZone) -> Unit, onDeleteClick: (GeofenceZone) -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clickable { onEditClick(zone) }, // Clickable to edit details
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = LightCream.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = zone.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBlue,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = zone.type, // e.g., "PRESET", "INCIDENT_AD_HOC"
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkBlue.copy(alpha = 0.7f)
                    )
                }
                zone.description?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { onEditClick(zone) }) {
                        Icon(Icons.Filled.Edit, "Edit Geofence", tint = DarkBlue)
                    }
                    IconButton(onClick = { onDeleteClick(zone) }) {
                        Icon(Icons.Filled.Delete, "Delete Geofence", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
