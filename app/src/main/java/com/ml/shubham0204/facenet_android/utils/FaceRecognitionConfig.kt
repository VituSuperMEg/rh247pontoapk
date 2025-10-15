package com.ml.shubham0204.facenet_android.utils

/**
 * Configurações centralizadas para o sistema de reconhecimento facial
 * Facilita o ajuste de parâmetros para reduzir falsos positivos
 */
object FaceRecognitionConfig {
    
    // ✅ CONFIGURAÇÕES DE SIMILARIDADE
    object Similarity {
        // Threshold de similaridade para reconhecimento (0.0 a 1.0)
        // Valores mais altos = mais rigoroso, menos falsos positivos
        const val MIN_SIMILARITY_THRESHOLD = 0.71f // Aumentado de 0.78 para 0.85
        
        // Threshold de confiança mínima para aceitar reconhecimento
        const val MIN_CONFIDENCE_SCORE = 0.7f
    }
    
    // ✅ CONFIGURAÇÕES DE QUALIDADE DA FACE
    object FaceQuality {
        // Tamanho mínimo da face em relação à imagem total
        const val MIN_AREA_RATIO = 0.02f // 2% da imagem
        
        // Tamanho máximo da face em relação à imagem total
        const val MAX_AREA_RATIO = 0.5f // 50% da imagem
        
        // Dimensões mínimas absolutas da face
        const val MIN_FACE_WIDTH = 80
        const val MIN_FACE_HEIGHT = 80
        
        // Proporção altura/largura da face (face humana típica)
        const val MIN_ASPECT_RATIO = 0.7f
        const val MAX_ASPECT_RATIO = 1.4f
        
        // Distância máxima do centro da imagem (40% da diagonal)
        const val MAX_DISTANCE_FROM_CENTER_RATIO = 0.4f
    }
    
    // ✅ CONFIGURAÇÕES DE SPOOF DETECTION
    object SpoofDetection {
        // Threshold para detecção de spoof (0.0 a 1.0)
        // Valores mais baixos = mais rigoroso
        const val SPOOF_THRESHOLD = 0.3f // Reduzido de 0.5f para 0.3f
        
        // Multiplicadores de confiança para diferentes tipos de detecção
        const val REAL_FACE_MULTIPLIER = 1.0f
        const val SPOOF_MULTIPLIER = 1.2f
        const val OBJECT_MULTIPLIER = 1.5f
        const val OTHER_MULTIPLIER = 1.3f
    }
    
    // ✅ CONFIGURAÇÕES DE PERFORMANCE
    object Performance {
        // Intervalo mínimo entre reconhecimentos (ms)
        const val MIN_RECOGNITION_INTERVAL_MS = 1000L
        
        // Intervalo entre processamentos de imagem (ms)
        const val IMAGE_PROCESSING_INTERVAL_MS = 1000L
        
        // Máximo de erros consecutivos antes de pausar
        const val MAX_CONSECUTIVE_ERRORS = 3
        
        // Reset automático após tempo sem sucesso (ms)
        const val MEMORY_RESET_INTERVAL_MS = 30000L
    }
    
    // ✅ CONFIGURAÇÕES DE DEBUG
    object Debug {
        // Habilitar logs detalhados
        const val ENABLE_DETAILED_LOGS = true
        
        // Log de validação de qualidade da face
        const val LOG_FACE_QUALITY = true
        
        // Log de detecção de spoof
        const val LOG_SPOOF_DETECTION = true
        
        // Log de resultados de reconhecimento
        const val LOG_RECOGNITION_RESULTS = true
    }
    
    // ✅ MÉTODOS UTILITÁRIOS
    fun getSimilarityThreshold(): Float = Similarity.MIN_SIMILARITY_THRESHOLD
    fun getConfidenceThreshold(): Float = Similarity.MIN_CONFIDENCE_SCORE
    fun getSpoofThreshold(): Float = SpoofDetection.SPOOF_THRESHOLD
    
    fun isFaceQualityValid(areaRatio: Float, aspectRatio: Float, distanceFromCenter: Float, maxDistance: Float): Boolean {
        return areaRatio >= FaceQuality.MIN_AREA_RATIO &&
               areaRatio <= FaceQuality.MAX_AREA_RATIO &&
               aspectRatio >= FaceQuality.MIN_ASPECT_RATIO &&
               aspectRatio <= FaceQuality.MAX_ASPECT_RATIO &&
               distanceFromCenter <= maxDistance
    }
    
    fun calculateConfidenceMultiplier(label: Int): Float {
        return when (label) {
            0 -> SpoofDetection.REAL_FACE_MULTIPLIER
            1 -> SpoofDetection.SPOOF_MULTIPLIER
            2 -> SpoofDetection.OBJECT_MULTIPLIER
            else -> SpoofDetection.OTHER_MULTIPLIER
        }
    }
}
