package com.heyreminder.app.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private const val USER_PREFERENCES_STORE_NAME = "user_preferences"

internal val Context.userPreferencesDataStore by preferencesDataStore(
    name = USER_PREFERENCES_STORE_NAME,
)
