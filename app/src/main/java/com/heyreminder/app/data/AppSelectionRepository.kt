package com.heyreminder.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val APP_ICON_SIZE_PX = 96

data class InstalledApp(
    val packageName: String,
    val displayName: String,
    val icon: Bitmap?,
)

class AppSelectionRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val installedAppsLoader: () -> List<InstalledApp>,
) {
    constructor(context: Context) : this(
        dataStore = context.applicationContext.userPreferencesDataStore,
        installedAppsLoader = {
            loadLaunchableApps(context.applicationContext)
        },
    )

    val selectedPackages: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SelectedPackagesKey].orEmpty()
        }

    suspend fun loadInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        installedAppsLoader()
    }

    suspend fun setPackageSelected(packageName: String, isSelected: Boolean) {
        dataStore.edit { preferences ->
            val selectedPackages = preferences[SelectedPackagesKey].orEmpty().toMutableSet()
            if (isSelected) {
                selectedPackages += packageName
            } else {
                selectedPackages -= packageName
            }
            preferences[SelectedPackagesKey] = selectedPackages
        }
    }

    private companion object {
        val SelectedPackagesKey = stringSetPreferencesKey("selected_packages")
    }
}

private fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
    }
    val nameCollator = Collator.getInstance(Locale.getDefault())

    return activities
        .asSequence()
        .filter { resolveInfo -> resolveInfo.activityInfo?.packageName != context.packageName }
        .distinctBy { resolveInfo -> resolveInfo.activityInfo?.packageName }
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val displayName = runCatching {
                resolveInfo.loadLabel(packageManager).toString().trim()
            }.getOrDefault("").ifBlank { packageName }
            val icon = runCatching {
                resolveInfo.loadIcon(packageManager).toBitmap(APP_ICON_SIZE_PX)
            }.getOrNull()

            InstalledApp(
                packageName = packageName,
                displayName = displayName,
                icon = icon,
            )
        }
        .sortedWith { first, second ->
            nameCollator.compare(first.displayName, second.displayName)
        }
        .toList()
}

private fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap.width == sizePx && bitmap.height == sizePx) {
        return bitmap
    }

    val renderedBitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(renderedBitmap)
    val originalBounds = copyBounds()
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    bounds = originalBounds
    return renderedBitmap
}
