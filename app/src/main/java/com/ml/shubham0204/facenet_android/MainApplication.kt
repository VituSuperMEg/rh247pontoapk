package com.ml.shubham0204.facenet_android

import android.app.Application
import com.ml.shubham0204.facenet_android.data.ObjectBoxStore
import com.ml.shubham0204.facenet_android.di.AppModule
import com.ml.shubham0204.facenet_android.di.apiModule
import com.ml.shubham0204.facenet_android.utils.CrashReporter
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializa CrashReporter para registrar crashes e logs locais desde o início do app
        try {
            CrashReporter.initialize(this)
            CrashReporter.logEvent(this, "MainApplication iniciado", "INFO")
        } catch (e: Exception) {
            // Evitar falha na inicialização caso logging tenha problemas
        }
        startKoin {
            androidContext(this@MainApplication)
            modules(AppModule().module, apiModule)
        }
        ObjectBoxStore.init(this)
    }
}
