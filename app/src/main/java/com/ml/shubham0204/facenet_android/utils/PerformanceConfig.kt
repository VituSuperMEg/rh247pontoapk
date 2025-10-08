package com.ml.shubham0204.facenet_android.utils

 
object PerformanceConfig {
    
    const val MIN_RECOGNITION_INTERVAL_MS = 1000L // 3 segundos entre reconhecimentos
    const val MAX_RECOGNITION_ATTEMPTS = 2 // Máximo de tentativas por ciclo
    const val RECOGNITION_DELAY_MS = 100L // Delay entre tentativas
    
    const val IMAGE_PROCESSING_INTERVAL_MS = 1000L // 1 segundo entre processamentos
    const val IMAGE_PROCESSING_INTERVAL_ERROR_MS = 1000L // 1 segundos quando há erros
    const val MAX_CONSECUTIVE_ERRORS = 3 // Máximo de erros consecutivos antes de pausar
    const val MAX_FRAME_COUNT = 100 // Reset a cada 100 frames para liberar memória
    
    const val UI_UPDATE_DELAY_MS = 2000L // 2 segundos entre atualizações de UI
    const val CAMERA_INIT_DELAY_MS = 1000L // 1 segundo para inicializar câmera
    const val CAMERA_RETRY_DELAY_MS = 2000L // 2 segundos para retry da câmera
    
    const val LOCATION_TIMEOUT_MS = 1000L // 4 segundos timeout para localização
    
    const val CAMERA_THREAD_POOL_SIZE = 2 // 2 threads para processamento de câmera
    
    const val MEMORY_RESET_INTERVAL_MS = 30000L // 30 segundos para reset de memória
    
  
    fun canProcess(currentTime: Long, lastProcessTime: Long): Boolean {
        return (currentTime - lastProcessTime) >= IMAGE_PROCESSING_INTERVAL_MS
    }
    
  
    fun getProcessingInterval(errorCount: Int): Long {
        return if (errorCount > 2) IMAGE_PROCESSING_INTERVAL_ERROR_MS else IMAGE_PROCESSING_INTERVAL_MS
    }
    
 
    fun shouldPauseProcessing(errorCount: Int): Boolean {
        return errorCount > MAX_CONSECUTIVE_ERRORS
    }
}
