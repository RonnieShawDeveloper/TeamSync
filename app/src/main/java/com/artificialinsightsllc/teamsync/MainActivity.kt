// In file: app/src/main/java/com/artificialinsightsllc/teamsync/MainActivity.kt
package com.artificialinsightsllc.teamsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.artificialinsightsllc.teamsync.Navigation.AppNavGraph
import com.artificialinsightsllc.teamsync.ui.theme.TeamSyncTheme
import androidx.fragment.app.FragmentActivity
// import com.artificialinsightsllc.teamsync.Services.GroupMonitorService // <--- REMOVE THIS IMPORT

class MainActivity : FragmentActivity() {

    // Removed GroupMonitorService declaration here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Removed GroupMonitorService initialization here

        setContent {
            TeamSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(navController)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Removed GroupMonitorService shutdown here, now handled by Application class
    }
}