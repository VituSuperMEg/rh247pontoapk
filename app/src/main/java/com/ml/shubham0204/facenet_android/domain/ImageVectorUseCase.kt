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
                    android.util.Log.d("ImageVectorUseCase", "🔍 Processando face $index/${faceDetectionResult.size}")
                    
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

                    val spoofResult = try {
                        faceSpoofDetector.detectSpoof(frameBitmap, boundingBox)
                    } catch (e: Exception) {
                        android.util.Log.e("ImageVectorUseCase", "❌ Erro na detecção de spoof para face $index: ${e.message}")
                        // Continuar sem spoof detection
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
                        android.util.Log.e("ImageVectorUseCase", "❌ Erro no cálculo de similaridade para face $index: ${e.message}")
                        0.0f // Distância mínima em caso de erro
                    }
                    
                    android.util.Log.d("ImageVectorUseCase", "📊 Face $index - Distância: $distance, Pessoa: ${recognitionResult.personName}")
                    
                    // If the distance > 0.6, we recognize the person
                    // else we conclude that the face does not match enough
                    if (distance > 0.6) {
                        android.util.Log.d("ImageVectorUseCase", "✅ Face $index reconhecida como: ${recognitionResult.personName}")
                        faceRecognitionResults.add(
                            FaceRecognitionResult(recognitionResult.personName, boundingBox, spoofResult),
                        )
                    } else {
                        android.util.Log.d("ImageVectorUseCase", "❌ Face $index não reconhecida (distância: $distance)")
                        faceRecognitionResults.add(
                            FaceRecognitionResult("Not recognized", boundingBox, spoofResult),
                        )
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("ImageVectorUseCase", "❌ Erro geral ao processar face $index: ${e.message}")
                    // ✅ CORRIGIDO: Usar boundingBox do resultado atual
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

            android.util.Log.d("ImageVectorUseCase", "✅ Reconhecimento concluído: ${faceRecognitionResults.size} resultados")
            Pair(metrics, faceRecognitionResults)
            
        } catch (e: Exception) {
            android.util.Log.e("ImageVectorUseCase", "❌ Erro fatal no reconhecimento: ${e.message}")
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

    // Get all face images for a specific person
    fun getImagesByPersonID(personID: Long): List<FaceImageRecord> {
        return imagesVectorDB.getFaceImagesByPersonID(personID)
    }
}
