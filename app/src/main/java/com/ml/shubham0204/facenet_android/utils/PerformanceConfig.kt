package com.ml.shubham0204.facenet_android.utils

/**
 * Configurações de performance para evitar ANR (Application Not Responding)
 * 
 * Este arquivo centraliza todas as configurações relacionadas à performance
 * para facilitar ajustes e evitar problemas de ANR.
 */
object PerformanceConfig {
    
    // ✅ Configurações de reconhecimento facial
    const val MIN_RECOGNITION_INTERVAL_MS = 3000L // 3 segundos entre reconhecimentos
    const val MAX_RECOGNITION_ATTEMPTS = 3 // Máximo de tentativas por ciclo
    const val RECOGNITION_DELAY_MS = 200L // Delay entre tentativas
    
    // ✅ Configurações de processamento de imagem
    const val IMAGE_PROCESSING_INTERVAL_MS = 1000L // 1 segundo entre processamentos
    const val IMAGE_PROCESSING_INTERVAL_ERROR_MS = 2000L // 2 segundos quando há erros
    const val MAX_CONSECUTIVE_ERRORS = 5 // Máximo de erros consecutivos antes de pausar
    const val MAX_FRAME_COUNT = 100 // Reset a cada 100 frames para liberar memória
    
    // ✅ Configurações de UI
    const val UI_UPDATE_DELAY_MS = 2000L // 2 segundos entre atualizações de UI
    const val CAMERA_INIT_DELAY_MS = 1000L // 1 segundo para inicializar câmera
    const val CAMERA_RETRY_DELAY_MS = 2000L // 2 segundos para retry da câmera
    
    // ✅ Configurações de localização
    const val LOCATION_TIMEOUT_MS = 4000L // 4 segundos timeout para localização
    
    // ✅ Configurações de thread pool
    const val CAMERA_THREAD_POOL_SIZE = 2 // 2 threads para processamento de câmera
    
    // ✅ Configurações de memória
    const val MEMORY_RESET_INTERVAL_MS = 30000L // 30 segundos para reset de memória
    
    /**
     * Verifica se é seguro processar baseado no tempo desde o último processamento
     */
    fun canProcess(currentTime: Long, lastProcessTime: Long): Boolean {
        return (currentTime - lastProcessTime) >= IMAGE_PROCESSING_INTERVAL_MS
    }
    
    /**
     * Calcula o intervalo de processamento baseado no número de erros
     */
    fun getProcessingInterval(errorCount: Int): Long {
        return if (errorCount > 2) IMAGE_PROCESSING_INTERVAL_ERROR_MS else IMAGE_PROCESSING_INTERVAL_MS
    }
    
    /**
     * Verifica se deve pausar o processamento baseado no número de erros
     */
    fun shouldPauseProcessing(errorCount: Int): Boolean {
        return errorCount > MAX_CONSECUTIVE_ERRORS
    }
}
