package me.bgregos.foreground.migration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper class to manage migration from taskd to TaskChampion sync.
 * Handles the clean reset and configuration steps for upgrading users.
 */
class MigrationHelper(
    private val filesDir: File,
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        private const val TAG = "MigrationHelper"
        private const val MIGRATION_COMPLETE_KEY = "migration_complete_taskchampion"
        private const val PREVIOUS_SYNC_TYPE_KEY = "previous_sync_type"
        private const val SYNC_MODE_KEY = "settings_sync_mode"
    }

    /**
     * Determines if migration process needs to be run
     */
    suspend fun isMigrationRequired(): Boolean = withContext(Dispatchers.IO) {
        !sharedPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false)
    }

    /**
     * Perform the migration process from taskd to TaskChampion
     */
    suspend fun performMigration(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting migration to TaskChampion")

            // Mark that we're starting the migration
            sharedPreferences.edit().putString(PREVIOUS_SYNC_TYPE_KEY, "taskd").apply()

            // Set sync mode to TaskChampion for new installations
            sharedPreferences.edit().putString(SYNC_MODE_KEY, "taskchampion").apply()

            // Reset existing taskd configuration
            resetTaskDConfiguration()

            // Prepare for TaskChampion
            // Clear any remaining sync settings
            clearSyncSettings()

            // Mark migration as complete
            sharedPreferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()

            Log.i(TAG, "Migration to TaskChampion completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration", e)
            false
        }
    }

    /**
     * Reset all taskd-related configuration and preferences
     */
    private fun resetTaskDConfiguration() {
        // This would clear any old taskd settings
    }

    /**
     * Get the current sync mode from preferences
     */
    suspend fun getCurrentSyncMode(): String = withContext(Dispatchers.IO) {
        return@withContext sharedPreferences.getString(SYNC_MODE_KEY, "taskd") ?: "taskd"
    }

    /**
     * Set the sync mode in preferences
     */
    suspend fun setSyncMode(syncMode: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(SYNC_MODE_KEY, syncMode).apply()
    }

    /**
     * Clear old sync settings that are no longer needed
     */
    private fun clearSyncSettings() {
        val editor = sharedPreferences.edit()

        // Remove deprecated taskd keys
        listOf(
            "RemoteTaskSourceImpl.serverAddress",
            "RemoteTaskSourceImpl.organization",
            "RemoteTaskSourceImpl.user",
            "RemoteTaskSourceImpl.caCertificate",
            "RemoteTaskSourceImpl.privateKeyFile",
            "RemoteTaskSourceImpl.privateCertificate"
        ).forEach { key ->
            editor.remove(key)
        }

        editor.apply()
    }
}
