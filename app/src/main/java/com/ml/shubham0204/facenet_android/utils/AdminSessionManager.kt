package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.content.SharedPreferences

class AdminSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("admin_session", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_IS_ADMIN_LOGGED_IN = "is_admin_logged_in"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
        private const val SESSION_DURATION = 24 * 60 * 60 * 1000L // 24 horas em millisegundos
    }
    
    fun setAdminLoggedIn() {
        prefs.edit()
            .putBoolean(KEY_IS_ADMIN_LOGGED_IN, true)
            .putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
    
    fun isAdminLoggedIn(): Boolean {
        val isLoggedIn = prefs.getBoolean(KEY_IS_ADMIN_LOGGED_IN, false)
        val loginTimestamp = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()
        
        // Verifica se a sessão ainda é válida (não expirou)
        val isSessionValid = (currentTime - loginTimestamp) < SESSION_DURATION
        
        return isLoggedIn && isSessionValid
    }
    
    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_ADMIN_LOGGED_IN, false)
            .putLong(KEY_LOGIN_TIMESTAMP, 0L)
            .apply()
    }
    
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
