// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/ShutdownScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.artificialinsightsllc.teamsync.Auth.AuthService
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.Services.LocationTrackingService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth // Import FirebaseAuth

class ShutdownScreen(private val navController: NavHostController) {
    @Composable
    fun Content() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val authService = remember { AuthService() }
        val firestoreService = remember { FirestoreService() }
        val groupMonitorService = (context.applicationContext as TeamSyncApplication).groupMonitorService
        val currentUserId = authService.getCurrentUser()?.uid

        var showScreen by remember { mutableStateOf(false) } // For animation visibility

        LaunchedEffect(Unit) {
            showScreen = true // Trigger slide-in animation
        }

        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 500, delayMillis = 50)) { fullWidth ->
                -fullWidth
            } + scaleIn(animationSpec = tween(500, delayMillis = 50), initialScale = 0.8f) + fadeIn(animationSpec = tween(500, delayMillis = 50)),
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 500)) { fullWidth ->
                -fullWidth
            } + scaleOut(animationSpec = tween(500), targetScale = 0.8f) + fadeOut(animationSpec = tween(500))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.background1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(WindowInsets.systemBars.asPaddingValues())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Main", tint = DarkBlue)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Shutdown Options",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkBlue,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(48.dp)) // To balance the back button
                    }

                    Spacer(Modifier.height(16.dp))

                    // Option 1: Stop Tracking & Remain Logged In (Listed First)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Stop Tracking & Remain Logged In",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkBlue
                            )
                            Text(
                                text = "Your account will remain signed in, and you will stay attached to all your groups (until they naturally expire). However, your current active group will be cleared, and all location tracking and reporting services will be stopped. The app will return to the home screen.",
                                fontSize = 14.sp,
                                color = DarkBlue.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        if (currentUserId != null) {
                                            // 1. Clear the active group
                                            firestoreService.updateUserSelectedActiveGroup(currentUserId, null)
                                                .onSuccess {
                                                    Log.d("ShutdownScreen", "Cleared selected active group for user $currentUserId.")
                                                    // GroupMonitorService will detect this and stop LocationTrackingService
                                                    Toast.makeText(context, "Location tracking stopped. Remaining logged in.", Toast.LENGTH_LONG).show()

                                                    // 2. Send app to home screen
                                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                        addCategory(Intent.CATEGORY_HOME)
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK // Ensure it's a new task
                                                    }
                                                    context.startActivity(homeIntent)
                                                    // Finish the current activity stack
                                                    // This will ensure no lingering MainScreen or other screens are visible.
                                                    // We cast context to Activity because startActivity is an Activity method
                                                    // and we want to finish the current activity.
                                                    (context as? android.app.Activity)?.finishAndRemoveTask()

                                                }
                                                .onFailure { e ->
                                                    Log.e("ShutdownScreen", "Failed to clear active group: ${e.message}", e)
                                                    Toast.makeText(context, "Error stopping tracking: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                        } else {
                                            Toast.makeText(context, "No user logged in to stop tracking.", Toast.LENGTH_SHORT).show()
                                            // If not logged in, just send to home
                                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                addCategory(Intent.CATEGORY_HOME)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(homeIntent)
                                            (context as? android.app.Activity)?.finishAndRemoveTask()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                            ) {
                                Text("Stop Tracking", color = Color.White)
                            }
                        }
                    }

                    // Option 2: Complete Logout & Full Shutdown (Listed Second)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Complete Logout & Full Shutdown",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkBlue
                            )
                            Text(
                                text = "This option will fully sign you out of your TeamSync account. All tracking services will be terminated, and you will be signed out of all active groups. Your account data will be retained for future logins. The app will return to the login screen.",
                                fontSize = 14.sp,
                                color = DarkBlue.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        if (authService.getCurrentUser() != null) {
                                            authService.logout() // This triggers GroupMonitorService's auth listener
                                            // GroupMonitorService's auth listener will handle stopping all services
                                            // and LoginScreen's DisposableEffect will handle navigating to LoginScreen.
                                            Toast.makeText(context, "Successfully logged out and services shut down.", Toast.LENGTH_LONG).show()
                                            // No explicit navigation needed here; the AuthStateListener in LoginScreen handles it.
                                        } else {
                                            Toast.makeText(context, "No user to log out.", Toast.LENGTH_SHORT).show()
                                            navController.navigate(NavRoutes.LOGIN) {
                                                popUpTo(NavRoutes.MAIN) { inclusive = true }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                            ) {
                                Text("Logout & Shutdown", color = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Cancel Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                showScreen = false // Trigger slide-out animation
                                delay(550) // Wait for animation to complete
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(50.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue.copy(alpha = 0.6f))
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
