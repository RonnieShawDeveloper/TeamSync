// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Navigation/NavGraph.kt
package com.artificialinsightsllc.teamsync.Navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.dialog
import androidx.navigation.navDeepLink
import com.artificialinsightsllc.teamsync.Screens.GroupCreationScreen
import com.artificialinsightsllc.teamsync.Screens.GroupsListScreen
import com.artificialinsightsllc.teamsync.Screens.LoginScreen
import com.artificialinsightsllc.teamsync.Screens.MainScreen
import com.artificialinsightsllc.teamsync.Screens.SplashScreen
import com.artificialinsightsllc.teamsync.Screens.TeamListScreen
import com.artificialinsightsllc.teamsync.Screens.AddMapMarkerScreen
import com.artificialinsightsllc.teamsync.Screens.PreCheckScreen
import com.artificialinsightsllc.teamsync.Screens.Signup.SignupScreen
import com.artificialinsightsllc.teamsync.Screens.TravelReportScreen
import com.artificialinsightsllc.teamsync.Screens.UserSettingsScreen
import com.artificialinsightsllc.teamsync.Screens.ShutdownScreen
import com.artificialinsightsllc.teamsync.Screens.NotificationScreen
import com.artificialinsightsllc.teamsync.Services.TeamSyncFirebaseMessagingService
import com.artificialinsightsllc.teamsync.Screens.GroupChatScreen
import com.artificialinsightsllc.teamsync.Screens.GroupStatusScreen
import com.artificialinsightsllc.teamsync.Screens.GeofenceManagementScreen
import com.artificialinsightsllc.teamsync.Screens.CreateGeofenceDrawScreen // Import the drawing screen

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AppNavGraph(navController: NavHostController, startDestination: String?) {
    NavHost(navController = navController, startDestination = startDestination ?: NavRoutes.SPLASH) {
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
        composable(NavRoutes.SHUTDOWN) {
            ShutdownScreen(navController).Content()
        }
        composable(NavRoutes.GROUP_CHAT) {
            GroupChatScreen(navController).Content()
        }
        composable(NavRoutes.GROUP_STATUS) {
            GroupStatusScreen(navController).Content()
        }
        composable(NavRoutes.GEOFENCE_MANAGEMENT) {
            GeofenceManagementScreen(navController).Content()
        }
        // NEW: Updated route for CreateGeofenceDrawScreen to accept an optional geofenceZoneId
        composable(
            route = NavRoutes.CREATE_GEOFENCE_DRAW + "?geofenceZoneId={geofenceZoneId}",
            arguments = listOf(navArgument("geofenceZoneId") {
                type = NavType.StringType
                nullable = true // The ID is optional, meaning it's for creation if null, editing if present
                defaultValue = null
            })
        ) { backStackEntry ->
            val geofenceZoneId = backStackEntry.arguments?.getString("geofenceZoneId")
            CreateGeofenceDrawScreen(navController).Content(geofenceZoneId = geofenceZoneId)
        }
        composable(
            route = NavRoutes.NOTIFICATIONS + "?${TeamSyncFirebaseMessagingService.NOTIFICATION_ID_PARAM}={${TeamSyncFirebaseMessagingService.NOTIFICATION_ID_PARAM}}",
            arguments = listOf(
                navArgument(TeamSyncFirebaseMessagingService.NOTIFICATION_ID_PARAM) {
                    type = NavType.IntType
                    defaultValue = -1
                }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "${TeamSyncFirebaseMessagingService.DEEPLINK_SCHEME}://${TeamSyncFirebaseMessagingService.DEEPLINK_HOST}/${TeamSyncFirebaseMessagingService.DEEPLINK_PATH_DETAIL}?${TeamSyncFirebaseMessagingService.NOTIFICATION_ID_PARAM}={${TeamSyncFirebaseMessagingService.NOTIFICATION_ID_PARAM}}"
                },
                navDeepLink {
                    uriPattern = "${TeamSyncFirebaseMessagingService.DEEPLINK_SCHEME}://${TeamSyncFirebaseMessagingService.DEEPLINK_HOST}/${TeamSyncFirebaseMessagingService.DEEPLINK_PATH_LIST}"
                }
            )
        ) { backStackEntry ->
            val notificationId = backStackEntry.arguments?.getInt(TeamSyncFirebaseMessagingService.NOTIFICATION_ID_PARAM)
            NotificationScreen(navController).Content()
        }
        composable(
            route = NavRoutes.TRAVEL_REPORT,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("timeRangeMillis") { type = NavType.LongType }
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
