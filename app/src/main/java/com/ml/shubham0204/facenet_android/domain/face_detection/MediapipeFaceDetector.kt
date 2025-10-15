package com.ml.shubham0204.facenet_android.domain.face_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.toRect
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.ml.shubham0204.facenet_android.domain.AppException
import com.ml.shubham0204.facenet_android.domain.ErrorCode
import com.ml.shubham0204.facenet_android.utils.FaceRecognitionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream

// Utility class for interacting with Mediapipe's Face Detector
// See https://ai.google.dev/edge/mediapipe/solutions/vision/face_detector/android
@Single
class MediapipeFaceDetector(
    private val context: Context,
) {
    // The model is stored in the assets folder
    private val modelName = "blaze_face_short_range.tflite"
    private val baseOptions = BaseOptions.builder().setModelAssetPath(modelName).build()
    private val faceDetectorOptions =
        FaceDetector.FaceDetectorOptions
            .builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .build()
    private val faceDetector = FaceDetector.createFromOptions(context, faceDetectorOptions)

    suspend fun getCroppedFace(imageUri: Uri): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            var imageInputStream =
                context.contentResolver.openInputStream(imageUri)
                    ?: return@withContext Result.failure<Bitmap>(
                        AppException(ErrorCode.FACE_DETECTOR_FAILURE),
                    )
            var imageBitmap = BitmapFactory.decodeStream(imageInputStream)
            imageInputStream.close()

            // Re-create an input-stream to reset its position
            // InputStream returns false with markSupported(), hence we cannot
            // reset its position
            // Without recreating the inputStream, no exif-data is read
            imageInputStream =
                context.contentResolver.openInputStream(imageUri)
                    ?: return@withContext Result.failure<Bitmap>(
                        AppException(ErrorCode.FACE_DETECTOR_FAILURE),
                    )
            val exifInterface = ExifInterface(imageInputStream)
            imageBitmap =
                when (
                    exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(imageBitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(imageBitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(imageBitmap, 270f)
                    else -> imageBitmap
                }
            imageInputStream.close()

            // We need exactly one face in the image, in other cases, return the
            // necessary errors
            val faces = faceDetector.detect(BitmapImageBuilder(imageBitmap).build()).detections()
            if (faces.size > 1) {
                return@withContext Result.failure<Bitmap>(AppException(ErrorCode.MULTIPLE_FACES))
            } else if (faces.size == 0) {
                return@withContext Result.failure<Bitmap>(AppException(ErrorCode.NO_FACE))
            } else {
                // Validate the bounding box and
                // return the cropped face
                val rect = faces[0].boundingBox().toRect()
                if (validateRect(imageBitmap, rect)) {
                    val croppedBitmap =
                        Bitmap.createBitmap(
                            imageBitmap,
                            rect.left,
                            rect.top,
                            rect.width(),
                            rect.height(),
                        )
                    return@withContext Result.success(croppedBitmap)
                } else {
                    return@withContext Result.failure<Bitmap>(
                        AppException(ErrorCode.FACE_DETECTOR_FAILURE),
                    )
                }
            }
        }

    // Detects multiple faces from the `frameBitmap`
    // and returns pairs of (croppedFace , boundingBoxRect)
    // Used by ImageVectorUseCase.kt
    suspend fun getAllCroppedFaces(frameBitmap: Bitmap): List<Pair<Bitmap, Rect>> =
        withContext(Dispatchers.IO) {
            val detections = faceDetector
                .detect(BitmapImageBuilder(frameBitmap).build())
                .detections()
            
            return@withContext detections
                .filter { detection -> 
                    val rect = detection.boundingBox().toRect()
                    validateRect(frameBitmap, rect) && validateFaceQuality(frameBitmap, rect)
                }
                .map { detection -> detection.boundingBox().toRect() }
                .map { rect ->
                    val croppedBitmap =
                        Bitmap.createBitmap(
                            frameBitmap,
                            rect.left,
                            rect.top,
                            rect.width(),
                            rect.height(),
                        )
                    Pair(croppedBitmap, rect)
                }
        }

    // DEBUG: For testing purpose, saves the Bitmap to the app's private storage
    fun saveBitmap(
        context: Context,
        image: Bitmap,
        name: String,
    ) {
        val fileOutputStream = FileOutputStream(File(context.filesDir.absolutePath + "/$name.png"))
        image.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
    }

    private fun rotateBitmap(
        source: Bitmap,
        degrees: Float,
    ): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
    }

    // Check if the bounds of `boundingBox` fit within the
    // limits of `cameraFrameBitmap`
    private fun validateRect(
        cameraFrameBitmap: Bitmap,
        boundingBox: Rect,
    ): Boolean =
        boundingBox.left >= 0 &&
            boundingBox.top >= 0 &&
            (boundingBox.left + boundingBox.width()) < cameraFrameBitmap.width &&
            (boundingBox.top + boundingBox.height()) < cameraFrameBitmap.height
    
    // ✅ NOVO: Validação de qualidade da face para evitar objetos
    private fun validateFaceQuality(
        frameBitmap: Bitmap,
        boundingBox: Rect,
    ): Boolean {
        val width = boundingBox.width()
        val height = boundingBox.height()
        val area = width * height
        val frameArea = frameBitmap.width * frameBitmap.height
        
        // ✅ Filtro 1: Tamanho mínimo da face usando configurações centralizadas
        val areaRatio = area.toFloat() / frameArea.toFloat()
        if (areaRatio < FaceRecognitionConfig.FaceQuality.MIN_AREA_RATIO) {
            android.util.Log.d("MediapipeFaceDetector", "❌ Face muito pequena: ${String.format("%.3f", areaRatio)}")
            return false
        }
        
        // ✅ Filtro 2: Dimensões mínimas absolutas
        if (width < FaceRecognitionConfig.FaceQuality.MIN_FACE_WIDTH || 
            height < FaceRecognitionConfig.FaceQuality.MIN_FACE_HEIGHT) {
            android.util.Log.d("MediapipeFaceDetector", "❌ Face com dimensões muito pequenas: ${width}x${height}")
            return false
        }
        
        // ✅ Filtro 3: Proporção da face usando configurações centralizadas
        val aspectRatio = height.toFloat() / width.toFloat()
        if (aspectRatio < FaceRecognitionConfig.FaceQuality.MIN_ASPECT_RATIO || 
            aspectRatio > FaceRecognitionConfig.FaceQuality.MAX_ASPECT_RATIO) {
            android.util.Log.d("MediapipeFaceDetector", "❌ Proporção inválida: ${String.format("%.2f", aspectRatio)}")
            return false
        }
        
        // ✅ Filtro 4: Verificar se a face não está muito nas bordas
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
        
        if (distanceFromCenter > maxDistance) {
            android.util.Log.d("MediapipeFaceDetector", "❌ Face muito nas bordas: distância=${String.format("%.1f", distanceFromCenter)}")
            return false
        }
        
        // ✅ Filtro 5: Verificar se a face não é muito grande (pode ser um objeto)
        if (areaRatio > FaceRecognitionConfig.FaceQuality.MAX_AREA_RATIO) {
            android.util.Log.d("MediapipeFaceDetector", "❌ Face muito grande: ${String.format("%.3f", areaRatio)}")
            return false
        }
        
        android.util.Log.d("MediapipeFaceDetector", "✅ Face válida: área=${String.format("%.3f", areaRatio)}, proporção=${String.format("%.2f", aspectRatio)}")
        return true
    }
}
