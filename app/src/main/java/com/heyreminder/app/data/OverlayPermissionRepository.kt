package com.heyreminder.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class OverlayPermissionRepository(context: Context) {
    private val applicationContext = context.applicationContext

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(applicationContext)

    fun createSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${applicationContext.packageName}"),
    )
}
