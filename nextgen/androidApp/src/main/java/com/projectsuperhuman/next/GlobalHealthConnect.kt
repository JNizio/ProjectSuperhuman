package com.projectsuperhuman.next

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

/**
 * One Health Connect entry point for Project Superhuman.
 *
 * A user connects Health Connect once. Every supported wearable domain is then requested
 * together and every manual sync refreshes all supported domains instead of only the screen
 * the user happened to press the button from.
 */
internal object GlobalHealthConnect {
    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun corePermissions(): Set<String> = MiniMetricsHealthConnect.permissions + SleepHealthConnect.permission

    fun requestPermissions(context: Context): Set<String> {
        val core = corePermissions()
        return if (MiniMetricsHealthConnect.backgroundReadAvailable(context)) {
            core + MiniMetricsHealthConnect.backgroundReadPermission
        } else core
    }

    suspend fun grantedPermissions(context: Context): Set<String> =
        MiniMetricsHealthConnect.grantedPermissions(context)

    suspend fun hasAnyCorePermission(context: Context): Boolean {
        val granted = grantedPermissions(context)
        return corePermissions().any(granted::contains)
    }

    suspend fun hasAllCorePermissions(context: Context): Boolean {
        val granted = grantedPermissions(context)
        return corePermissions().all(granted::contains)
    }

    suspend fun hasBackgroundPermission(context: Context): Boolean =
        MiniMetricsHealthConnect.hasBackgroundReadPermission(context)

    /**
     * SleepHealthConnect.sync() is the global orchestration point as well as the sleep importer.
     * It refreshes sleep, mini metrics and advanced heart-rate analysis in one pass.
     */
    suspend fun sync(context: Context): GlobalHealthSyncResult {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) {
            return GlobalHealthSyncResult(false, "Health Connect isn’t available on this device")
        }
        if (!hasAnyCorePermission(context)) {
            return GlobalHealthSyncResult(false, "Connect Health Connect to start syncing")
        }
        val sleep = SleepHealthConnect.sync(context)
        return GlobalHealthSyncResult(
            success = sleep.success || hasAnyCorePermission(context),
            message = sleep.message
        )
    }
}

internal data class GlobalHealthSyncResult(
    val success: Boolean,
    val message: String
)
