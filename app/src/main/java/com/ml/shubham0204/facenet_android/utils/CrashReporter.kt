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
                // ✅ CRÍTICO: Salvar crash IMEDIATAMENTE de forma síncrona
                saveCrashToFile(context, exception, thread)
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao capturar crash", e)
                // Tenta salvar pelo menos o erro básico
                try {
                    val emergencyFile = File(context.filesDir, "emergency_crash.txt")
                    FileWriter(emergencyFile, true).use { writer ->
                        writer.write("\n=== CRASH DE EMERGÊNCIA ===\n")
                        writer.write("Data: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                        writer.write("Erro: ${exception.message}\n")
                        writer.flush()
                    }
                } catch (emergencyException: Exception) {
                    // Último recurso: apenas log
                    Log.e(TAG, "Falha total ao salvar crash", emergencyException)
                }
            }
            
            // Chama o handler padrão para finalizar o app
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    /**
     * ✅ NOVO: Salva crash de forma síncrona e robusta
     */
    private fun saveCrashToFile(context: Context, exception: Throwable, thread: Thread) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val crashFile = File(context.filesDir, "app_crashes.txt")
            
            // Usa FileOutputStream com flush para garantir escrita imediata
            val fileOutputStream = crashFile.outputStream().buffered()
            fileOutputStream.use { output ->
                val writer = output.writer()
                
                // Cabeçalho do crash
                writer.write("\n========================================\n")
                writer.write("CRASH DETECTADO\n")
                writer.write("========================================\n")
                writer.write("Data/Hora: $timestamp\n")
                writer.write("Thread: ${thread.name} (ID: ${thread.id})\n")
                writer.write("Exceção: ${exception.javaClass.simpleName}\n")
                writer.write("Mensagem: ${exception.message}\n")
                writer.write("\n--- Stack Trace ---\n")
                writer.write(exception.stackTraceToString())
                writer.write("\n")
                
                // Se houver causa raiz, incluir também
                var cause = exception.cause
                var level = 1
                while (cause != null && level <= 5) {
                    writer.write("\n--- Causa Raiz (Nível $level) ---\n")
                    writer.write("Exceção: ${cause.javaClass.simpleName}\n")
                    writer.write("Mensagem: ${cause.message}\n")
                    writer.write(cause.stackTraceToString())
                    writer.write("\n")
                    cause = cause.cause
                    level++
                }
                
                // Informações do sistema
                writer.write("\n--- Informações do Sistema ---\n")
                writer.write("Android Version: ${android.os.Build.VERSION.RELEASE}\n")
                writer.write("SDK Level: ${android.os.Build.VERSION.SDK_INT}\n")
                writer.write("Manufacturer: ${android.os.Build.MANUFACTURER}\n")
                writer.write("Model: ${android.os.Build.MODEL}\n")
                writer.write("Device: ${android.os.Build.DEVICE}\n")
                
                // Memória disponível
                val runtime = Runtime.getRuntime()
                writer.write("\n--- Memória ---\n")
                writer.write("Total Memory: ${runtime.totalMemory() / 1024 / 1024} MB\n")
                writer.write("Free Memory: ${runtime.freeMemory() / 1024 / 1024} MB\n")
                writer.write("Max Memory: ${runtime.maxMemory() / 1024 / 1024} MB\n")
                
                writer.write("\n========================================\n\n")
                
                // CRÍTICO: Força a escrita no disco
                writer.flush()
                output.flush()
            }
            
            // Também salva no log normal
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            FileWriter(logFile, true).use { writer ->
                writer.write("\n[$timestamp] [FATAL] CRASH: ${exception.message}\n")
                writer.flush()
            }
            
            // Log no Logcat também
            Log.e(TAG, "💥 CRASH SALVO EM: ${crashFile.absolutePath}")
            Log.e(TAG, "💥 Exceção: ${exception.javaClass.simpleName}: ${exception.message}")
            
        } catch (e: Exception) {
            // Se falhar, tenta método ainda mais simples
            Log.e(TAG, "❌ Falha ao salvar crash detalhado", e)
            throw e // Re-throw para acionar o fallback
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
     * ✅ NOVO: Obtém todos os crashes salvos
     */
    fun getCrashes(context: Context): String {
        return try {
            val crashFile = File(context.filesDir, "app_crashes.txt")
            val emergencyFile = File(context.filesDir, "emergency_crash.txt")
            
            val crashesText = StringBuilder()
            
            // Crashes normais
            if (crashFile.exists()) {
                crashesText.append("=== CRASHES REGISTRADOS ===\n\n")
                crashesText.append(crashFile.readText())
            }
            
            // Crashes de emergência
            if (emergencyFile.exists()) {
                crashesText.append("\n\n=== CRASHES DE EMERGÊNCIA ===\n\n")
                crashesText.append(emergencyFile.readText())
            }
            
            if (crashesText.isEmpty()) {
                "Nenhum crash registrado ✅"
            } else {
                crashesText.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler crashes", e)
            "Erro ao ler crashes: ${e.message}"
        }
    }
    
    /**
     * ✅ NOVO: Obtém contagem de crashes
     */
    fun getCrashCount(context: Context): Int {
        return try {
            val crashFile = File(context.filesDir, "app_crashes.txt")
            if (crashFile.exists()) {
                val content = crashFile.readText()
                // Conta quantas vezes aparece "CRASH DETECTADO"
                content.split("CRASH DETECTADO").size - 1
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao contar crashes", e)
            0
        }
    }
    
    /**
     * ✅ NOVO: Limpa crashes salvos
     */
    fun clearCrashes(context: Context) {
        try {
            val crashFile = File(context.filesDir, "app_crashes.txt")
            if (crashFile.exists()) {
                crashFile.delete()
                Log.d(TAG, "Crashes limpos com sucesso")
            }
            
            val emergencyFile = File(context.filesDir, "emergency_crash.txt")
            if (emergencyFile.exists()) {
                emergencyFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar crashes", e)
        }
    }
    
    /**
     * ✅ NOVO: Exporta crashes para arquivo externo
     */
    fun exportCrashes(context: Context): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(context.getExternalFilesDir(null), "crashes_export_$timestamp.txt")
            
            val crashes = getCrashes(context)
            FileWriter(exportFile).use { writer ->
                writer.write(crashes)
                writer.flush()
            }
            
            Log.d(TAG, "Crashes exportados para: ${exportFile.absolutePath}")
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar crashes", e)
            null
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
