package com.heyreminder.app.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

class UsageAccessRepository(context: Context) {
    private val appContext = context.applicationContext

    fun hasUsageAccess(): Boolean {
        val appOpsManager = appContext.getSystemService(AppOpsManager::class.java)
            ?: return false

        return runCatching {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                appContext.applicationInfo.uid,
                appContext.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    companion object {
        fun createSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        fun createFallbackSettingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)
    }
}

