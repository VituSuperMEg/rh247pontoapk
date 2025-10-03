package com.ml.shubham0204.facenet_android.data.config

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.ml.shubham0204.facenet_android.data.model.EntidadeInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppPreferences(context: Context) : KoinComponent {
    
    companion object {
        private const val PREF_NAME = "app_preferences"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_ENTIDADE_INFO = "entidade_info"
        private const val KEY_TELA_CHEIA_HABILITADA = "tela_cheia_habilitada"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    var serverUrl: String
        get() = sharedPreferences.getString(KEY_SERVER_URL, ServerConfig.BASE_URL) ?: ServerConfig.BASE_URL
        set(value) = sharedPreferences.edit().putString(KEY_SERVER_URL, value).apply()
    
    var autoUpdateCheck: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_UPDATE_CHECK, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, value).apply()
    
    var lastUpdateCheck: Long
        get() = sharedPreferences.getLong(KEY_LAST_UPDATE_CHECK, 0)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()
    
    var entidadeInfo: EntidadeInfo?
        get() {
            val json = sharedPreferences.getString(KEY_ENTIDADE_INFO, null)
            return if (json != null) {
                try {
                    gson.fromJson(json, EntidadeInfo::class.java)
                } catch (e: Exception) {
                    null
                }
            } else null
        }
        set(value) {
            if (value != null) {
                val json = gson.toJson(value)
                sharedPreferences.edit().putString(KEY_ENTIDADE_INFO, json).apply()
            } else {
                sharedPreferences.edit().remove(KEY_ENTIDADE_INFO).apply()
        }
    }
    
    var telaCheiaHabilitada: Boolean
        get() = sharedPreferences.getBoolean(KEY_TELA_CHEIA_HABILITADA, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_TELA_CHEIA_HABILITADA, value).apply()
    
    fun clearPreferences() {
        sharedPreferences.edit().clear().apply()
    }
} 