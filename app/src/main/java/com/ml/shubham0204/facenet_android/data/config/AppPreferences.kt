package com.ml.shubham0204.facenet_android.data.config

import android.content.Context
import android.content.SharedPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppPreferences(context: Context) : KoinComponent {
    
    companion object {
        private const val PREF_NAME = "app_preferences"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    var serverUrl: String
        get() = sharedPreferences.getString(KEY_SERVER_URL, ServerConfig.BASE_URL) ?: ServerConfig.BASE_URL
        set(value) = sharedPreferences.edit().putString(KEY_SERVER_URL, value).apply()
    
    var autoUpdateCheck: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_UPDATE_CHECK, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, value).apply()
    
    var lastUpdateCheck: Long
        get() = sharedPreferences.getLong(KEY_LAST_UPDATE_CHECK, 0)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()
    
    fun clearPreferences() {
        sharedPreferences.edit().clear().apply()
    }
} 