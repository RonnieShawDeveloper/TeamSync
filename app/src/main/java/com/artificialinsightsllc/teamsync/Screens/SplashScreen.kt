package com.artificialinsightsllc.teamsync.Screens

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavHostController) {
    var showBackground by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }
    var startExit by remember { mutableStateOf(false) }
    var fadeOutBackground by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showBackground = true
        delay(500)
        showLogo = true
        delay(2000)
        startExit = true
        delay(500)
        fadeOutBackground = true
        delay(500)
        navController.navigate(NavRoutes.LOGIN) {
            popUpTo(NavRoutes.LOGIN) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // fallback
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showBackground && !fadeOutBackground,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            Image(
                painter = painterResource(id = R.drawable.background1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Slide and bounce animation
        val offsetX by animateDpAsState(
            targetValue = when {
                !showLogo -> (-300).dp
                startExit -> 1000.dp
                else -> 0.dp
            },
            animationSpec = if (!showLogo) spring() else tween(durationMillis = 500, easing = FastOutLinearInEasing),
            label = "logoOffset"
        )

        if (showLogo) {
            Image(
                painter = painterResource(id = R.drawable.splashscreen),
                contentDescription = "Splash Logo",
                modifier = Modifier
                    .padding(8.dp)
                    .offset(x = offsetX)
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
            )
        }
    }
}
