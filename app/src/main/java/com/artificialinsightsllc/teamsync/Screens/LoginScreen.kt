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
import kotlinx.coroutines.launch
import java.util.*



@Composable
fun LoginScreen(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { AuthService() }

    // --- ADDED LAUNCHED EFFECT BLOCK ---
    LaunchedEffect(Unit) { // 'Unit' ensures this runs only once when the composable enters composition
        if (authService.getCurrentUser() != null) {
            // User is already logged in, navigate to main screen
            navController.navigate(NavRoutes.MAIN) {
                // Clear the back stack so the user can't go back to the login screen
                popUpTo(NavRoutes.LOGIN) { inclusive = true }
            }
        }
    }
    // --- END OF ADDED BLOCK ---

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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = DarkBlue) }, // Using DarkBlue
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                textStyle = LocalTextStyle.current.copy(color = DarkBlue), // Using DarkBlue
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
                            navController.navigate(NavRoutes.MAIN) {
                                popUpTo(NavRoutes.LOGIN) { inclusive = true }
                            }
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
    }
}
