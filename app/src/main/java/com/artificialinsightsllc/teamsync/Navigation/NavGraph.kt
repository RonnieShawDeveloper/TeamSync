// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Navigation/NavGraph.kt
package com.artificialinsightsllc.teamsync.Navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument // Import navArgument for type safety
import com.artificialinsightsllc.teamsync.Screens.GroupCreationScreen
import com.artificialinsightsllc.teamsync.Screens.GroupsListScreen
import com.artificialinsightsllc.teamsync.Screens.LoginScreen
import com.artificialinsightsllc.teamsync.Screens.MainScreen
import com.artificialinsightsllc.teamsync.Screens.SplashScreen
import com.artificialinsightsllc.teamsync.Screens.TeamListScreen
import com.artificialinsightsllc.teamsync.Screens.AddMapMarkerScreen
import com.artificialinsightsllc.teamsync.Screens.GeofenceScreen
import com.artificialinsightsllc.teamsync.Screens.PreCheckScreen
import com.artificialinsightsllc.teamsync.Screens.Signup.SignupScreen
import com.artificialinsightsllc.teamsync.Screens.TravelReportScreen
import com.artificialinsightsllc.teamsync.Screens.UserSettingsScreen
import com.artificialinsightsllc.teamsync.Screens.ShutdownScreen // NEW IMPORT for ShutdownScreen
import com.artificialinsightsllc.teamsync.Screens.CreateGeofenceScreen // NEW IMPORT


@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = NavRoutes.SPLASH) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(navController)
        }
        composable(NavRoutes.LOGIN) {
            LoginScreen(navController)
        }
        composable(NavRoutes.SIGNUP) {
            SignupScreen(navController)
        }
        composable(NavRoutes.PRE_CHECK) {
            PreCheckScreen(navController).Content()
        }
        composable(NavRoutes.MAIN) {
            MainScreen(navController).Content()
        }
        composable(NavRoutes.CREATE_GROUP) {
            GroupCreationScreen(navController).Content()
        }
        composable(NavRoutes.GROUPS_LIST) {
            GroupsListScreen(navController).Content()
        }
        composable(NavRoutes.TEAM_LIST) {
            TeamListScreen(navController).Content()
        }
        composable(NavRoutes.ADD_MAP_MARKER) {
            AddMapMarkerScreen(navController).Content()
        }
        composable(NavRoutes.USER_SETTINGS) {
            UserSettingsScreen(navController).Content()
        }
        composable(NavRoutes.GEOFENCE) {
            GeofenceScreen(navController).Content()
        }
        composable(NavRoutes.CREATE_GEOFENCE) { // NEW: Add this composable
            CreateGeofenceScreen(navController).Content()
        }
        composable(NavRoutes.SHUTDOWN) { // NEW: Shutdown Screen
            ShutdownScreen(navController).Content()
        }
        composable(
            route = NavRoutes.TRAVEL_REPORT,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("timeRangeMillis") { type = NavType.LongType } // UPDATED: Added timeRangeMillis argument
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            val timeRangeMillis = backStackEntry.arguments?.getLong("timeRangeMillis")

            if (userId != null && timeRangeMillis != null) {
                TravelReportScreen(navController, userId, timeRangeMillis).Content()
            } else {
                Log.e("AppNavGraph", "TravelReportScreen: userId or timeRangeMillis argument is null.")
                navController.popBackStack()
            }
        }
    }
}
