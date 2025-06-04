// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Navigation/NavGraph.kt
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
import com.artificialinsightsllc.teamsync.Screens.TeamListScreen // NEW IMPORT

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
        composable(NavRoutes.TEAM_LIST) { // NEW BLOCK
            TeamListScreen(navController).Content()
        }
    }
}
