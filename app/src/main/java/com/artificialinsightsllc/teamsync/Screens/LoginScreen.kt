// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/LoginScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

// Assuming DarkBlue is accessible without explicit import here, based on previous conversation.
// If not, ensure it's defined in your theme or a common object accessible without import.

@Composable
fun LoginScreen(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                        // Clear the back stack up to the login screen, then inclusive means login is also popped
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
                label = { Text("Email", color = darkBlue) },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                textStyle = LocalTextStyle.current.copy(color = darkBlue),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = darkBlue) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                textStyle = LocalTextStyle.current.copy(color = darkBlue),
                modifier = Modifier.fillMaxWidth()
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
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
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
                color = darkBlue,
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
                color = darkBlue,
                modifier = Modifier
                    .clickable {
                        // TODO: Implement reset password navigation
                    }
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        val versionName = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        Text(
            text = "© $currentYear TeamSync Version: $versionName",
            fontSize = 18.sp,
            color = darkBlue,
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
    }
}
