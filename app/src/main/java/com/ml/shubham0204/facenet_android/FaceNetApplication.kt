package com.ml.shubham0204.facenet_android

import android.app.Application
import android.util.Log
import com.ml.shubham0204.facenet_android.utils.CrashReporter

/**
 * Application class para inicializar o crash reporting
 */
class FaceNetApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            CrashReporter.initialize(this)
            
            CrashReporter.logEvent(this, "Aplicação iniciada", "INFO")
            
        } catch (e: Exception) {
            Log.e("FaceNetApplication", "Erro ao inicializar aplicação", e)
        }
    }
}
