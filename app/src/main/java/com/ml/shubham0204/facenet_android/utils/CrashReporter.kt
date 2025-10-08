package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.util.Log
// import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Classe para capturar e reportar crashes e logs do aplicativo
 */
object CrashReporter {
    
    private const val TAG = "CrashReporter"
    private const val LOG_FILE_NAME = "app_crash_logs.txt"
    
    /**
     * Inicializa o Crashlytics e configura o logging local
     */
    fun initialize(context: Context) {
        try {
            // Firebase desabilitado temporariamente - apenas logging local
            // val crashlytics = FirebaseCrashlytics.getInstance()
            // crashlytics.setCrashlyticsCollectionEnabled(true)
            
            // Configura captura de crashes não tratados
            setupUncaughtExceptionHandler(context)
            
            // Log de inicialização
            logEvent(context, "App inicializado", "INFO")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar CrashReporter", e)
        }
    }
    
    /**
     * Configura o handler para capturar crashes não tratados
     */
    private fun setupUncaughtExceptionHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                // Log do crash
                val crashMessage = "CRASH DETECTADO: ${exception.javaClass.simpleName}: ${exception.message}"
                logEvent(context, crashMessage, "ERROR")
                
                // Log da stack trace completa
                val stackTrace = exception.stackTraceToString()
                logEvent(context, "Stack Trace: $stackTrace", "ERROR")
                
                // Log de informações do sistema
                logEvent(context, "Thread: ${thread.name}, ID: ${thread.id}", "ERROR")
                
                // Força a escrita dos logs
                forceFlushLogs(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao capturar crash", e)
            }
            
            // Chama o handler padrão para finalizar o app
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    /**
     * Força a escrita dos logs no arquivo
     */
    private fun forceFlushLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                // Força a sincronização do arquivo
                file.outputStream().use { it.flush() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao forçar escrita dos logs", e)
        }
    }
    
    /**
     * Registra um evento no log
     */
    fun logEvent(context: Context, message: String, level: String = "INFO") {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] [$level] $message"
        
        // Log no console
        when (level) {
            "ERROR" -> Log.e(TAG, message)
            "WARN" -> Log.w(TAG, message)
            "DEBUG" -> Log.d(TAG, message)
            else -> Log.i(TAG, message)
        }
        
        // Salva no arquivo local
        saveToFile(context, logMessage)
        
        // Envia para Crashlytics se for erro (desabilitado temporariamente)
        // if (level == "ERROR") {
        //     FirebaseCrashlytics.getInstance().log(logMessage)
        // }
    }
    
    /**
     * Registra uma exceção
     */
    fun logException(context: Context, exception: Throwable, errorContext: String = "") {
        val message = if (errorContext.isNotEmpty()) "Erro em: $errorContext" else "Erro capturado"
        
        // Log local
        logEvent(context, "$message: ${exception.message}", "ERROR")
        
        // Envia para Crashlytics (desabilitado temporariamente)
        // FirebaseCrashlytics.getInstance().apply {
        //     setCustomKey("error_context", errorContext)
        //     recordException(exception)
        // }
    }
    
    /**
     * Limpa os logs antigos
     */
    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar logs", e)
        }
    }
    
    /**
     * Versão com contexto passado como parâmetro
     */
    fun getLogs(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                file.readText()
            } else {
                "Nenhum log encontrado"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler logs", e)
            "Erro ao ler logs: ${e.message}"
        }
    }
    
    /**
     * Salva log com contexto
     */
    fun saveToFile(context: Context, message: String) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            
            // Usa FileWriter com flush para garantir que seja salvo
            FileWriter(file, true).use { writer ->
                writer.appendLine(message)
                writer.flush() // Força a escrita imediata
            }
            
            // Log adicional no Logcat para debug
            Log.d(TAG, "Log salvo: $message")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar log no arquivo", e)
        }
    }
}
