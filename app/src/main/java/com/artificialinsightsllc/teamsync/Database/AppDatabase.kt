package com.artificialinsightsllc.teamsync.Database // NEW PACKAGE: Database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.artificialinsightsllc.teamsync.Models.NotificationEntity

@Database(entities = [NotificationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "teamsync_database" // Database name
                )
                    .fallbackToDestructiveMigration() // Simple migration strategy for development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
