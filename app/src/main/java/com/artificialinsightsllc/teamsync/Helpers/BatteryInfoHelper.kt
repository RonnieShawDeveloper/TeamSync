// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/BatteryInfoHelper.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

object BatteryInfoHelper {

    /**
     * Retrieves the current battery level and charging status of the device.
     * This method uses a sticky broadcast and does not require any special permissions.
     *
     * @param context The application context.
     * @return A Pair where:
     * - first: The current battery level as a percentage (0-100), or null if unavailable.
     * - second: A String representing the charging status ("CHARGING", "DISCHARGING", "FULL", "NOT_CHARGING", "UNKNOWN"), or null if unavailable.
     */
    fun getBatteryInfo(context: Context): Pair<Int?, String?> {
        val batteryStatus: Intent? = try {
            // Register a null receiver to get the sticky broadcast of ACTION_BATTERY_CHANGED
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            Log.e("BatteryInfoHelper", "Failed to register receiver for battery info: ${e.message}")
            null
        }

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        // FIX: Changed from BatteryManager.BATTERY_STATUS_CHARGING to BatteryManager.EXTRA_STATUS
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val percentage = if (level != -1 && scale > 0) (level * 100) / scale else null

        val chargingStatusString = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }

        Log.d("BatteryInfoHelper", "Battery Info: Level=${percentage}, Status=${chargingStatusString}")
        return Pair(percentage, chargingStatusString)
    }
}
