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
import com.artificialinsightsllc.teamsync.Screens.TeamListScreen
import com.artificialinsightsllc.teamsync.Screens.AddMapMarkerScreen // NEW IMPORT

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
        composable(NavRoutes.ADD_MAP_MARKER) { // NEW BLOCK
            AddMapMarkerScreen(navController).Content()
        }
    }
}
