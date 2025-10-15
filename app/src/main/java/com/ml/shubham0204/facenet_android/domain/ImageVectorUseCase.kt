package com.ml.shubham0204.facenet_android.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.ml.shubham0204.facenet_android.data.FaceImageRecord
import com.ml.shubham0204.facenet_android.data.ImagesVectorDB
import com.ml.shubham0204.facenet_android.data.RecognitionMetrics
import com.ml.shubham0204.facenet_android.domain.embeddings.FaceNet
import com.ml.shubham0204.facenet_android.domain.face_detection.FaceSpoofDetector
import com.ml.shubham0204.facenet_android.domain.face_detection.MediapipeFaceDetector
import com.ml.shubham0204.facenet_android.utils.CrashReporter
import com.ml.shubham0204.facenet_android.utils.FaceRecognitionConfig
import org.koin.core.annotation.Single
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

@Single
class ImageVectorUseCase(
    private val mediapipeFaceDetector: MediapipeFaceDetector,
    private val faceSpoofDetector: FaceSpoofDetector,
    private val imagesVectorDB: ImagesVectorDB,
    private val faceNet: FaceNet,
) {
    data class FaceRecognitionResult(
        val personName: String,
        val boundingBox: Rect,
        val spoofResult: FaceSpoofDetector.FaceSpoofResult? = null,
    )

    // Add the person's image to the database
    suspend fun addImage(
        personID: Long,
        personName: String,
        imageUri: Uri,
    ): Result<Boolean> {
        // Perform face-detection and get the cropped face as a Bitmap
        val faceDetectionResult = mediapipeFaceDetector.getCroppedFace(imageUri)
        if (faceDetectionResult.isSuccess) {
            // Get the embedding for the cropped face, and store it
            // in the database, along with `personId` and `personName`
            val embedding = faceNet.getFaceEmbedding(faceDetectionResult.getOrNull()!!)
            
            // ✅ NOVO: Salvar o caminho da imagem original
            val originalImagePath = imageUri.path
            
            imagesVectorDB.addFaceImageRecord(
                FaceImageRecord(
                    personID = personID,
                    personName = personName,
                    faceEmbedding = embedding,
                    originalImagePath = originalImagePath, // ✅ NOVO: Salvar caminho da imagem
                ),
            )
            
            android.util.Log.d("ImageVectorUseCase", "✅ Imagem salva: $originalImagePath")
            return Result.success(true)
        } else {
            return Result.failure(faceDetectionResult.exceptionOrNull()!!)
        }
    }
    
    
    // ✅ NOVO: Função para adicionar múltiplas imagens de uma vez
    suspend fun addMultipleImages(
        personID: Long,
        personName: String,
        imageUris: List<Uri>,
    ): Result<Boolean> {
        try {
            android.util.Log.d("ImageVectorUseCase", "📸 Adicionando ${imageUris.size} imagens para $personName")
            
            var successCount = 0
            for ((index, imageUri) in imageUris.withIndex()) {
                android.util.Log.d("ImageVectorUseCase", "📸 Processando imagem ${index + 1}/${imageUris.size}: $imageUri")
                
                val result = addImage(personID, personName, imageUri)
                if (result.isSuccess) {
                    successCount++
                    android.util.Log.d("ImageVectorUseCase", "✅ Imagem ${index + 1} adicionada com sucesso")
                } else {
                    android.util.Log.e("ImageVectorUseCase", "❌ Erro ao adicionar imagem ${index + 1}: ${result.exceptionOrNull()?.message}")
                }
            }
            
            android.util.Log.d("ImageVectorUseCase", "📊 Resultado: $successCount/${imageUris.size} imagens adicionadas")
            
            return if (successCount == imageUris.size) {
                Result.success(true)
            } else {
                Result.failure(Exception("Apenas $successCount de ${imageUris.size} imagens foram adicionadas"))
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ImageVectorUseCase", "❌ Erro ao adicionar múltiplas imagens: ${e.message}")
            return Result.failure(e)
        }
    }

    suspend fun getNearestPersonName(frameBitmap: Bitmap): Pair<RecognitionMetrics?, List<FaceRecognitionResult>> {
        return try {
            android.util.Log.d("ImageVectorUseCase", "🔍 Iniciando reconhecimento facial...")
            
            val (faceDetectionResult, t1) = try {
                measureTimedValue { mediapipeFaceDetector.getAllCroppedFaces(frameBitmap) }
            } catch (e: Exception) {
                android.util.Log.e("ImageVectorUseCase", "❌ Erro na detecção facial: ${e.message}")
                return Pair(null, listOf())
            }
            
            android.util.Log.d("ImageVectorUseCase", "📸 Faces detectadas: ${faceDetectionResult.size}")
            
            val faceRecognitionResults = ArrayList<FaceRecognitionResult>()
            var avgT2 = 0L
            var avgT3 = 0L
            var avgT4 = 0L

            for ((index, result) in faceDetectionResult.withIndex()) {
                try {

                    val (croppedBitmap, boundingBox) = result
                    
                    val (embedding, t2) = try {
                        measureTimedValue { faceNet.getFaceEmbedding(croppedBitmap) }
                    } catch (e: Exception) {
                        android.util.Log.e("ImageVectorUseCase", "❌ Erro ao gerar embedding para face $index: ${e.message}")
                        faceRecognitionResults.add(FaceRecognitionResult("Error", boundingBox))
                        continue
                    }
                    
                    avgT2 += t2.toLong(DurationUnit.MILLISECONDS)
                    android.util.Log.d("ImageVectorUseCase", "✅ Embedding gerado para face $index")
                    
                    val (recognitionResult, t3) = try {
                        measureTimedValue { imagesVectorDB.getNearestEmbeddingPersonName(embedding) }
                    } catch (e: Exception) {
                        android.util.Log.e("ImageVectorUseCase", "❌ Erro na busca por similaridade para face $index: ${e.message}")
                        faceRecognitionResults.add(FaceRecognitionResult("Error", boundingBox))
                        continue
                    }
                    
                    avgT3 += t3.toLong(DurationUnit.MILLISECONDS)
                    
                    if (recognitionResult == null) {
                        android.util.Log.d("ImageVectorUseCase", "⚠️ Face $index não reconhecida")
                        faceRecognitionResults.add(FaceRecognitionResult("Not recognized", boundingBox))
                        continue
                    }

                    val enableSpoofDetection = true
                    
                    val spoofResult = if (enableSpoofDetection) {
                        try {
                            faceSpoofDetector.detectSpoof(frameBitmap, boundingBox)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    
                    if (spoofResult != null) {
                        avgT4 += spoofResult.timeMillis
                    }

                    val distance = try {
                        cosineDistance(embedding, recognitionResult.faceEmbedding)
                    } catch (e: Exception) {
                        0.0f
                    }

                    // ✅ MELHORADO: Threshold mais rigoroso para reduzir falsos positivos
                    val similarityThreshold = FaceRecognitionConfig.getSimilarityThreshold()
                    val distanceThreshold = 1.0f - similarityThreshold 
                    
                    // ✅ NOVO: Verificar qualidade da face detectada
                    val faceQuality = validateFaceQuality(boundingBox, frameBitmap)
                    
                    if (distance <= distanceThreshold && faceQuality.isValid) {
                        val spoofThreshold = getSpoofThreshold()
                        val isSpoofDetected = spoofResult != null && spoofResult.isSpoof && spoofResult.score > spoofThreshold
                        
                        // ✅ NOVO: Verificação adicional de confiança
                        val confidenceScore = calculateConfidenceScore(distance, faceQuality, spoofResult)
                        
                        if (isSpoofDetected || confidenceScore < FaceRecognitionConfig.getConfidenceThreshold()) {
                            faceRecognitionResults.add(
                                FaceRecognitionResult("SPOOF_DETECTED", boundingBox, spoofResult),
                            )
                        } else {
                            faceRecognitionResults.add(
                                FaceRecognitionResult(recognitionResult.personName, boundingBox, spoofResult),
                            )
                        }
                    } else {
                        val reason = if (!faceQuality.isValid) "INVALID_FACE_QUALITY" else "LOW_SIMILARITY"
                        faceRecognitionResults.add(
                            FaceRecognitionResult("Not recognized", boundingBox, spoofResult),
                        )
                    }
                    
                } catch (e: Exception) {
                    val (_, boundingBox) = result
                    faceRecognitionResults.add(FaceRecognitionResult("Error", boundingBox))
                }
            }
            
            val metrics = if (faceDetectionResult.isNotEmpty()) {
                RecognitionMetrics(
                    timeFaceDetection = t1.toLong(DurationUnit.MILLISECONDS),
                    timeFaceEmbedding = avgT2 / faceDetectionResult.size,
                    timeVectorSearch = avgT3 / faceDetectionResult.size,
                    timeFaceSpoofDetection = avgT4 / faceDetectionResult.size,
                )
            } else {
                null
            }

            Pair(metrics, faceRecognitionResults)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, listOf())
        }
    }

    private fun cosineDistance(
        x1: FloatArray,
        x2: FloatArray,
    ): Float {
        var mag1 = 0.0f
        var mag2 = 0.0f
        var product = 0.0f
        for (i in x1.indices) {
            mag1 += x1[i].pow(2)
            mag2 += x2[i].pow(2)
            product += x1[i] * x2[i]
        }
        mag1 = sqrt(mag1)
        mag2 = sqrt(mag2)
        return product / (mag1 * mag2)
    }

    fun removeImages(personID: Long) {
        imagesVectorDB.removeFaceRecordsWithPersonID(personID)
    }

    fun getImagesByPersonID(personID: Long): List<FaceImageRecord> {
        return imagesVectorDB.getFaceImagesByPersonID(personID)
    }

    suspend fun checkIfFaceAlreadyExists(
        imageUri: Uri,
        currentPersonID: Long? = null, // ID da pessoa atual (para permitir atualização da própria face)
        similarityThreshold: Float = 0.78f // Limiar de similaridade (mais restritivo que reconhecimento)
    ): Result<FaceAlreadyExistsResult> {
        return try {

            val faceDetectionResult = mediapipeFaceDetector.getCroppedFace(imageUri)
            if (faceDetectionResult.isFailure) {
                android.util.Log.e("ImageVectorUseCase", "❌ Erro ao detectar face na imagem")
                // Log do erro para crash reporting (comentado por enquanto)
                // faceDetectionResult.exceptionOrNull()?.let { exception ->
                //     CrashReporter.logException(applicationContext, exception, "checkIfFaceAlreadyExists - Face Detection")
                // }
                return Result.failure(faceDetectionResult.exceptionOrNull()!!)
            }
            
            val croppedFace = faceDetectionResult.getOrNull()!!

            val newEmbedding = faceNet.getFaceEmbedding(croppedFace)

            // Buscar a face mais similar no banco
            val nearestFace = imagesVectorDB.getNearestEmbeddingPersonName(newEmbedding)
            
            if (nearestFace == null) {
                android.util.Log.d("ImageVectorUseCase", "✅ Face não encontrada no sistema - pode cadastrar")
                return Result.success(FaceAlreadyExistsResult(false, null, 0.0f))
            }
            
            // Calcular similaridade com a face mais próxima
            val similarity = cosineDistance(newEmbedding, nearestFace.faceEmbedding)
            android.util.Log.d("ImageVectorUseCase", "📊 Similaridade com face existente: $similarity")
            android.util.Log.d("ImageVectorUseCase", "📊 Face existente pertence a: ${nearestFace.personName} (ID: ${nearestFace.personID})")
            
            // Verificar se a similaridade é alta o suficiente para considerar a mesma face
            if (similarity >= similarityThreshold) {
                // Se for a mesma pessoa, permitir (para atualização)
                if (currentPersonID != null && nearestFace.personID == currentPersonID) {
                    android.util.Log.d("ImageVectorUseCase", "✅ Face pertence à mesma pessoa - permitindo atualização")
                    return Result.success(FaceAlreadyExistsResult(false, null, similarity))
                } else {
                    android.util.Log.w("ImageVectorUseCase", "⚠️ Face já existe no sistema para outra pessoa!")
                    return Result.success(FaceAlreadyExistsResult(true, nearestFace, similarity))
                }
            } else {
                android.util.Log.d("ImageVectorUseCase", "✅ Face é suficientemente diferente - pode cadastrar")
                return Result.success(FaceAlreadyExistsResult(false, null, similarity))
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ImageVectorUseCase", "❌ Erro ao verificar face existente: ${e.message}")
            e.printStackTrace()
            // Log do erro para crash reporting (comentado por enquanto)
            // CrashReporter.logException(applicationContext, e, "checkIfFaceAlreadyExists - General Error")
            return Result.failure(e)
        }
    }
    
    // ✅ NOVO: Função para obter threshold dinâmico do spoof detection
    private fun getSpoofThreshold(): Float {
        return FaceRecognitionConfig.getSpoofThreshold()
    }
    
    // ✅ NOVO: Validação de qualidade da face detectada
    private fun validateFaceQuality(boundingBox: Rect, frameBitmap: Bitmap): FaceQualityResult {
        val width = boundingBox.width()
        val height = boundingBox.height()
        val area = width * height
        val frameArea = frameBitmap.width * frameBitmap.height
        
        // Verificar tamanho mínimo da face usando configurações centralizadas
        val areaRatio = area.toFloat() / frameArea.toFloat()
        val aspectRatio = height.toFloat() / width.toFloat()
        
        // Verificar se a face não está muito pequena
        val isSizeValid = areaRatio >= FaceRecognitionConfig.FaceQuality.MIN_AREA_RATIO && 
                         width >= FaceRecognitionConfig.FaceQuality.MIN_FACE_WIDTH && 
                         height >= FaceRecognitionConfig.FaceQuality.MIN_FACE_HEIGHT
        
        // Verificar se a proporção é razoável para uma face humana
        val isAspectRatioValid = aspectRatio >= FaceRecognitionConfig.FaceQuality.MIN_ASPECT_RATIO && 
                                aspectRatio <= FaceRecognitionConfig.FaceQuality.MAX_ASPECT_RATIO
        
        // Verificar se a face está centralizada na imagem (não muito nas bordas)
        val centerX = boundingBox.centerX()
        val centerY = boundingBox.centerY()
        val frameCenterX = frameBitmap.width / 2
        val frameCenterY = frameBitmap.height / 2
        val distanceFromCenter = kotlin.math.sqrt(
            ((centerX - frameCenterX) * (centerX - frameCenterX) + 
             (centerY - frameCenterY) * (centerY - frameCenterY)).toDouble()
        ).toFloat()
        val maxDistance = kotlin.math.sqrt(
            (frameBitmap.width * frameBitmap.width + frameBitmap.height * frameBitmap.height).toDouble()
        ).toFloat() * FaceRecognitionConfig.FaceQuality.MAX_DISTANCE_FROM_CENTER_RATIO
        
        val isPositionValid = distanceFromCenter <= maxDistance
        
        val isValid = isSizeValid && isAspectRatioValid && isPositionValid
        
        android.util.Log.d("ImageVectorUseCase", "🔍 Validação de qualidade: " +
            "tamanho=${isSizeValid}, proporção=${isAspectRatioValid}, posição=${isPositionValid}, " +
            "área=${String.format("%.3f", areaRatio)}, proporção=${String.format("%.2f", aspectRatio)}")
        
        return FaceQualityResult(
            isValid = isValid,
            areaRatio = areaRatio,
            aspectRatio = aspectRatio,
            distanceFromCenter = distanceFromCenter
        )
    }
    
    // ✅ NOVO: Cálculo de pontuação de confiança
    private fun calculateConfidenceScore(
        distance: Float, 
        faceQuality: FaceQualityResult, 
        spoofResult: SpoofDetectionResult?
    ): Float {
        var confidence = 1.0f - distance // Baseado na similaridade
        
        // Penalizar por baixa qualidade da face
        if (faceQuality.areaRatio < 0.02f) confidence *= 0.8f // Face muito pequena
        if (faceQuality.aspectRatio < 0.9f || faceQuality.aspectRatio > 1.3f) confidence *= 0.9f // Proporção estranha
        if (faceQuality.distanceFromCenter > faceQuality.distanceFromCenter * 0.5f) confidence *= 0.9f // Muito nas bordas
        
        // Penalizar por detecção de spoof
        if (spoofResult != null && spoofResult.isSpoof) {
            confidence *= (1.0f - spoofResult.score * 0.5f) // Reduzir confiança baseado no score de spoof
        }
        
        return kotlin.math.max(0.0f, kotlin.math.min(1.0f, confidence))
    }
}

// ✅ NOVO: Classe de resultado para verificação de face existente
data class FaceAlreadyExistsResult(
    val exists: Boolean, // Se a face já existe
    val existingFace: FaceImageRecord?, // Face existente (se encontrada)
    val similarity: Float // Nível de similaridade (0.0 a 1.0)
)

// ✅ NOVO: Classe de resultado para validação de qualidade da face
data class FaceQualityResult(
    val isValid: Boolean, // Se a face atende aos critérios de qualidade
    val areaRatio: Float, // Proporção da área da face em relação à imagem total
    val aspectRatio: Float, // Proporção altura/largura da face
    val distanceFromCenter: Float // Distância do centro da imagem
)
