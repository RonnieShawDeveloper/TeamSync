// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/LoginScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Auth.AuthService
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.*

import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue // UPDATED IMPORT

@Composable
fun LoginScreen(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) } // NEW: State for password visibility
    var showAboutDialog by remember { mutableStateOf(false) } // NEW: State for About dialog

    val darkBlue = Color(0xFF0D47A1) // Local definition for clarity, if not globally accessible via MaterialTheme.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { AuthService() }

    // Use DisposableEffect to manage the auth state listener lifecycle
    DisposableEffect(Unit) {
        val listener = object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                val user = firebaseAuth.currentUser
                if (user != null) {
                    // User is logged in or session restored, navigate to PreCheckScreen
                    // PreCheckScreen will handle permissions and then navigate to MainScreen
                    navController.navigate(NavRoutes.PRE_CHECK) {
                        // Clear the back stack so the user can't go back to the login screen
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
                // If user is null, stay on login screen (or navigate to it if not already there)
            }
        }

        authService.auth.addAuthStateListener(listener)

        onDispose {
            // Remove the listener when the composable is disposed to prevent memory leaks
            authService.auth.removeAuthStateListener(listener)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "TeamSync Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = DarkBlue) }, // Using DarkBlue
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                textStyle = LocalTextStyle.current.copy(color = DarkBlue), // Using DarkBlue
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors( // Added colors for better theming consistency
                    focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                    focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                    cursorColor = DarkBlue
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = DarkBlue) }, // Using DarkBlue
                singleLine = true,
                // NEW: Toggle password visibility
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                textStyle = LocalTextStyle.current.copy(color = DarkBlue), // Using DarkBlue
                modifier = Modifier.fillMaxWidth(),
                // NEW: Trailing icon for password visibility toggle
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = DarkBlue)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors( // Added colors for better theming consistency
                    focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                    focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                    cursorColor = DarkBlue
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        val result = authService.login(email.trim(), password.trim())
                        isLoading = false
                        result.onSuccess {
                            // Navigation is now handled by the authStateListener, which will now go to PreCheckScreen
                        }.onFailure {
                            errorMessage = it.message
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkBlue) // Using DarkBlue
            ) {
                Text(
                    text = if (isLoading) "Logging In..." else "Login to TeamSync",
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Don't have an account? Click here",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBlue, // Using DarkBlue
                modifier = Modifier
                    .clickable {
                        navController.navigate(NavRoutes.SIGNUP)
                    }
                    .padding(8.dp),
                textAlign = TextAlign.Center
            )


            Text(
                text = "Forgot Password?",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBlue, // Using DarkBlue
                modifier = Modifier
                    .clickable {
                        // TODO: Implement reset password navigation
                    }
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp)) // Spacer before the new "About" link

            // NEW: About This App link
            Text(
                text = "About This App",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBlue,
                modifier = Modifier
                    .clickable {
                        showAboutDialog = true
                    }
                    .padding(top = 20.dp),
                textAlign = TextAlign.Center
            )
        }

        val versionName = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        Text(
            text = "© $currentYear TeamSync Version: $versionName",
            fontSize = 18.sp,
            color = DarkBlue, // Using DarkBlue
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .navigationBarsPadding(),
            textAlign = TextAlign.Center
        )

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("OK")
                    }
                },
                title = { Text("Login Failed") },
                text = { Text(errorMessage ?: "") }
            )
        }

        // NEW: About This App Dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                // Set containerColor to transparent white
                containerColor = Color.White.copy(alpha = 0.7f),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "About TeamSync",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkBlue, // Use DarkBlue for title
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    // Use Card inside text composable to get the rounded corners and shadow effect
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.0f)),
                        // elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(4.dp) // Padding inside the card
                        ) {
                            Text(
                                text = "TeamSync is a robust Android application designed to keep teams, families, and friends connected through real-time location sharing and streamlined group coordination.\n\n",
                                fontSize = 16.sp,
                                color = DarkBlue // Dark blue text
                            )
                            Text(
                                text = "Developed by Artificial Insights, LLC.\n",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkBlue,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Development Team Lead: \nRonnie Shaw, Certified Android Developer.\n\n",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkBlue, // Dark blue text
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Built natively on Android using Kotlin and Jetpack Compose, TeamSync leverages cutting-edge technologies including Firebase for secure authentication, real-time data synchronization with Firestore, and cloud storage for rich media. Google Maps Platform powers intuitive location tracking and custom map markers.\n\n",
                                fontSize = 16.sp,
                                color = DarkBlue // Dark blue text
                            )
                            Text(
                                text = "Our mission is to provide a seamless and reliable platform for enhanced communication, safety, and operational efficiency for any group needing to stay in sync on the go.",
                                fontSize = 16.sp,
                                color = DarkBlue // Dark blue text
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)) {
                        Text("Close", color = Color.White)
                    }
                }
            )
        }
    }
}
