package com.artificialinsightsllc.teamsync.Navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.artificialinsightsllc.teamsync.Screens.GroupCreationScreen
import com.artificialinsightsllc.teamsync.Screens.GroupsListScreen
import com.artificialinsightsllc.teamsync.Screens.LoginScreen
import com.artificialinsightsllc.teamsync.Screens.MainScreen
import com.artificialinsightsllc.teamsync.Screens.Signup.SignupScreen
import com.artificialinsightsllc.teamsync.Screens.SplashScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = NavRoutes.SPLASH) { // Still starts at SPLASH
        composable(NavRoutes.SPLASH) {
            SplashScreen(navController)
        }
        composable(NavRoutes.LOGIN) {
            // Assuming LoginScreen is also a class with a Content() function
            // If LoginScreen is a direct @Composable fun, then just LoginScreen(navController) is fine.
            LoginScreen(navController) // Please ensure this is correct based on your LoginScreen.kt
        }
        composable(NavRoutes.SIGNUP) {
            // Assuming SignupScreen is also a class with a Content() function
            SignupScreen(navController) // Please ensure this is correct based on your SignupScreen.kt
        }
        composable(NavRoutes.MAIN) {
            // **** THIS IS THE CRUCIAL CHANGE ****
            MainScreen(navController).Content()
        }
        composable(NavRoutes.CREATE_GROUP) { // <--- ADD THIS BLOCK
            GroupCreationScreen(navController).Content()
        }
        composable(NavRoutes.GROUPS_LIST) { // <--- ADD THIS BLOCK
            GroupsListScreen(navController).Content()
        }
    }
}