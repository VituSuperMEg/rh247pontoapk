package com.ml.shubham0204.facenet_android.domain.face_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.toRect
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.ml.shubham0204.facenet_android.domain.AppException
import com.ml.shubham0204.facenet_android.domain.ErrorCode
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
            return@withContext faceDetector
                .detect(BitmapImageBuilder(frameBitmap).build())
                .detections()
                .filter { detection ->
                    val categories = detection.categories()
                    val confidence = if (categories.isNotEmpty()) {
                        categories[0].score()
                    } else {
                        0f
                    }
                
                    
                    if (confidence < 0.76f) { // Mínimo 70% de confiança
                        return@filter false
                    }
                    
                    val rect = detection.boundingBox().toRect()
                    
                    if (!validateRect(frameBitmap, rect)) {
                        android.util.Log.w("MediapipeFaceDetector", "⚠️ Bounding box fora dos limites")
                        return@filter false
                    }
                    
                    val aspectRatio = rect.width().toFloat() / rect.height().toFloat()
                    if (aspectRatio < 0.5f || aspectRatio > 2.0f) {
                        return@filter false
                    }

                    // ✅ OTIMIZADO: Aumentado de 40px para 80px para melhor qualidade
                    val minSize = 80
                    if (rect.width() < minSize || rect.height() < minSize) {
                        android.util.Log.w("MediapipeFaceDetector", "⚠️ Face muito pequena: ${rect.width()}x${rect.height()}px (mínimo: ${minSize}px)")
                        return@filter false
                    }

                    true
                }
                .map { detection -> detection.boundingBox().toRect() }
                .mapNotNull { rect ->
                    try {
                        val croppedBitmap =
                            Bitmap.createBitmap(
                                frameBitmap,
                                rect.left,
                                rect.top,
                                rect.width(),
                                rect.height(),
                            )

                        // ✅ NOVO: Verificar qualidade da imagem (nitidez)
                        val sharpness = calculateImageSharpness(croppedBitmap)
                        val minSharpness = 30.0 // Threshold ajustável

                        if (sharpness < minSharpness) {
                            android.util.Log.w("MediapipeFaceDetector", "⚠️ Imagem desfocada rejeitada (nitidez: $sharpness < $minSharpness)")
                            return@mapNotNull null
                        }

                        android.util.Log.d("MediapipeFaceDetector", "✅ Face aceita (nitidez: $sharpness)")
                        Pair(croppedBitmap, rect)
                    } catch (e: Exception) {
                        android.util.Log.e("MediapipeFaceDetector", "Erro ao processar face: ${e.message}")
                        null
                    }
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

    // ✅ NOVO: Calcula variância de Laplaciano para detectar blur (desfoque)
    // Valores baixos indicam imagem desfocada
    private fun calculateImageSharpness(bitmap: Bitmap): Double {
        try {
            // Converter para escala de cinza e calcular Laplaciano
            val width = bitmap.width
            val height = bitmap.height
            var variance = 0.0
            var mean = 0.0
            var count = 0

            // Amostragem: processar apenas parte da imagem para performance
            val step = 4
            for (y in 1 until height - 1 step step) {
                for (x in 1 until width - 1 step step) {
                    // Obter valores de cinza dos pixels vizinhos
                    val center = Color.red(bitmap.getPixel(x, y))
                    val top = Color.red(bitmap.getPixel(x, y - 1))
                    val bottom = Color.red(bitmap.getPixel(x, y + 1))
                    val left = Color.red(bitmap.getPixel(x - 1, y))
                    val right = Color.red(bitmap.getPixel(x + 1, y))

                    // Operador Laplaciano simplificado
                    val laplacian = kotlin.math.abs(4 * center - top - bottom - left - right).toDouble()
                    mean += laplacian
                    count++
                }
            }

            mean /= count

            // Calcular variância
            for (y in 1 until height - 1 step step) {
                for (x in 1 until width - 1 step step) {
                    val center = Color.red(bitmap.getPixel(x, y))
                    val top = Color.red(bitmap.getPixel(x, y - 1))
                    val bottom = Color.red(bitmap.getPixel(x, y + 1))
                    val left = Color.red(bitmap.getPixel(x - 1, y))
                    val right = Color.red(bitmap.getPixel(x + 1, y))

                    val laplacian = kotlin.math.abs(4 * center - top - bottom - left - right).toDouble()
                    variance += (laplacian - mean).pow(2)
                }
            }

            variance /= count
            return variance
        } catch (e: Exception) {
            android.util.Log.e("MediapipeFaceDetector", "Erro ao calcular nitidez: ${e.message}")
            return 100.0 // Valor padrão para permitir processamento
        }
    }
}
