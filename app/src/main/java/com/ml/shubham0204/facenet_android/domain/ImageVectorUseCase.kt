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
            imagesVectorDB.addFaceImageRecord(
                FaceImageRecord(
                    personID = personID,
                    personName = personName,
                    faceEmbedding = embedding,
                ),
            )
            return Result.success(true)
        } else {
            return Result.failure(faceDetectionResult.exceptionOrNull()!!)
        }
    }

    // ✅ CORRIGIDO: From the given frame, return the name of the person by performing
    // face recognition com melhor tratamento de erro
    suspend fun getNearestPersonName(frameBitmap: Bitmap): Pair<RecognitionMetrics?, List<FaceRecognitionResult>> {
        return try {
            android.util.Log.d("ImageVectorUseCase", "🔍 Iniciando reconhecimento facial...")
            
            // Perform face-detection and get the cropped face as a Bitmap
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

                    
                    // Get the embedding for the cropped face (query embedding)
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
                    
                    // Perform nearest-neighbor search
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

                    // Calculate cosine similarity between the nearest-neighbor
                    // and the query embedding
                    val distance = try {
                        cosineDistance(embedding, recognitionResult.faceEmbedding)
                    } catch (e: Exception) {
                        0.0f
                    }

                    if (distance > 0.73) {
                    val spoofThreshold = getSpoofThreshold()
                    val isSpoofDetected = spoofResult != null && spoofResult.isSpoof && spoofResult.score > spoofThreshold
                        
                        if (isSpoofDetected) {
                            faceRecognitionResults.add(
                                FaceRecognitionResult("SPOOF_DETECTED", boundingBox, spoofResult),
                            )
                        } else {
                            android.util.Log.d("ImageVectorUseCase", "✅ Face $index reconhecida como: ${recognitionResult.personName}")
                            if (spoofResult != null) {
                                android.util.Log.d("ImageVectorUseCase", "   - Spoof score: ${spoofResult.score} (threshold: $spoofThreshold) - VÁLIDO")
                                android.util.Log.d("ImageVectorUseCase", "   - isSpoof: ${spoofResult.isSpoof}, score > threshold: ${spoofResult.score > spoofThreshold}")
                            }
                            faceRecognitionResults.add(
                                FaceRecognitionResult(recognitionResult.personName, boundingBox, spoofResult),
                            )
                        }
                    } else {
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
        similarityThreshold: Float = 0.73f // Limiar de similaridade (mais restritivo que reconhecimento)
    ): Result<FaceAlreadyExistsResult> {
        return try {

            val faceDetectionResult = mediapipeFaceDetector.getCroppedFace(imageUri)
            if (faceDetectionResult.isFailure) {
                android.util.Log.e("ImageVectorUseCase", "❌ Erro ao detectar face na imagem")
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
            return Result.failure(e)
        }
    }
    
    // ✅ NOVO: Função para obter threshold dinâmico do spoof detection
    private fun getSpoofThreshold(): Float {
        // Threshold mais permissivo para reduzir falsos positivos
        // Valores possíveis:
        // 0.5f = Muito restritivo (pode bloquear pessoas reais)
        // 0.7f = Moderado
        // 0.8f = Permissivo (recomendado para debugging)
        // 0.9f = Muito permissivo
        return 0.8f
    }
}

// ✅ NOVO: Classe de resultado para verificação de face existente
data class FaceAlreadyExistsResult(
    val exists: Boolean, // Se a face já existe
    val existingFace: FaceImageRecord?, // Face existente (se encontrada)
    val similarity: Float // Nível de similaridade (0.0 a 1.0)
)
