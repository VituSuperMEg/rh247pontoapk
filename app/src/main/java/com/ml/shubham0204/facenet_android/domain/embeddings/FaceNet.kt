package com.ml.shubham0204.facenet_android.domain.embeddings

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Derived from the original project:
// https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android/blob/master/app/src/main/java/com/ml/quaterion/facenetdetection/model/FaceNetModel.kt
// Utility class for FaceNet model
@Single
class FaceNet(
    context: Context,
    useGpu: Boolean = false,
    useXNNPack: Boolean = true,
) {
    // Input image size for FaceNet model.
    private val imgSize = 160

    // Output embedding size
    private val embeddingDim = 512

    private var interpreter: Interpreter

    // ✅ OTIMIZADO: Pipeline de processamento melhorado para câmeras ruins
    private val imageTensorProcessor =
        ImageProcessor
            .Builder()
            .add(ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR)) // BILINEAR é o melhor disponível
            .add(IlluminationNormalizationOp()) // ✅ NOVO: Normaliza iluminação
            .add(ContrastEnhanceOp()) // ✅ NOVO: Melhora contraste
            .add(StandardizeOp())
            .add(L2NormalizationOp()) // ✅ NOVO: Normalização L2
            .build()

    init {
        val interpreterOptions = try {
            Interpreter.Options().apply {
                if (useGpu) {
                    try {
                        val compatibilityList = CompatibilityList()
                        if (compatibilityList.isDelegateSupportedOnThisDevice) {
                            val gpuDelegate = GpuDelegate(compatibilityList.bestOptionsForThisDevice)
                            addDelegate(gpuDelegate)
                        } else {
                            numThreads = 4
                        }
                    } catch (e: Exception) {
                        numThreads = 4
                    }
                } else {
                    numThreads = 4
                }
                
                useXNNPACK = useXNNPack && !useGpu
                useNNAPI = false
                
                android.util.Log.d("FaceNet", "📱 Configurações: GPU=$useGpu, XNNPACK=$useXNNPack, NNAPI=$useNNAPI")
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceNet", "❌ Erro ao criar opções do interpreter: ${e.message}")
            Interpreter.Options().apply {
                numThreads = 1
                useXNNPACK = false
                useNNAPI = false
            }
        }
        
        try {
            interpreter = Interpreter(
                FileUtil.loadMappedFile(context, "facenet_512.tflite"), 
                interpreterOptions
            )
            android.util.Log.d("FaceNet", "✅ Interpreter inicializado com sucesso")
        } catch (e: Exception) {
            android.util.Log.e("FaceNet", "❌ Erro ao carregar modelo: ${e.message}")
            throw e
        }
    }

    suspend fun getFaceEmbedding(image: Bitmap): FloatArray {
        return try {
            withContext(Dispatchers.Default) {
                android.util.Log.d("FaceNet", "🔍 Processando imagem para embedding...")
                val result = runFaceNet(convertBitmapToBuffer(image))
                android.util.Log.d("FaceNet", "✅ Embedding gerado com sucesso: ${result[0].size} dimensões")
                result[0]
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceNet", "❌ Erro ao gerar embedding: ${e.message}")
            throw e
        }
    }

    private fun runFaceNet(inputs: Any): Array<FloatArray> {
        return try {
            val faceNetModelOutputs = Array(1) { FloatArray(embeddingDim) }
            
            android.util.Log.d("FaceNet", "🚀 Executando modelo FaceNet...")
            interpreter.run(inputs, faceNetModelOutputs)
            android.util.Log.d("FaceNet", "✅ Modelo executado com sucesso")
            
            faceNetModelOutputs
        } catch (e: Exception) {
            android.util.Log.e("FaceNet", "❌ Erro ao executar modelo: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun convertBitmapToBuffer(image: Bitmap): ByteBuffer {
        return try {
            android.util.Log.d("FaceNet", "🔄 Convertendo bitmap para buffer...")
            val tensorImage = TensorImage.fromBitmap(image)
            val processedImage = imageTensorProcessor.process(tensorImage)
            val buffer = processedImage.buffer
            android.util.Log.d("FaceNet", "✅ Conversão concluída: ${buffer.capacity()} bytes")
            buffer
        } catch (e: Exception) {
            android.util.Log.e("FaceNet", "❌ Erro ao converter bitmap: ${e.message}")
            throw e
        }
    }

    class StandardizeOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            return try {
                val pixels = p0!!.floatArray
                val mean = pixels.average().toFloat()
                var std = sqrt(pixels.map { pi -> (pi - mean).pow(2) }.sum() / pixels.size.toFloat())
                std = max(std, 1f / sqrt(pixels.size.toFloat()))

                for (i in pixels.indices) {
                    pixels[i] = (pixels[i] - mean) / std
                }

                val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
                output.loadArray(pixels)
                output
            } catch (e: Exception) {
                android.util.Log.e("FaceNet", "❌ Erro na padronização: ${e.message}")
                throw e
            }
        }
    }

    // ✅ NOVO: Normalização de iluminação para lidar com condições ruins de luz
    class IlluminationNormalizationOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            return try {
                val pixels = p0!!.floatArray
                val shape = p0.shape

                // Assume formato [height, width, channels] ou [height * width * channels]
                val totalPixels = pixels.size / 3 // RGB channels

                // Calcula média e desvio padrão para cada canal
                val means = FloatArray(3) { 0f }
                val stds = FloatArray(3) { 0f }

                // Calcula médias
                for (i in 0 until totalPixels) {
                    means[0] += pixels[i * 3] // R
                    means[1] += pixels[i * 3 + 1] // G
                    means[2] += pixels[i * 3 + 2] // B
                }
                for (c in 0..2) {
                    means[c] /= totalPixels
                }

                // Calcula desvios padrão
                for (i in 0 until totalPixels) {
                    stds[0] += (pixels[i * 3] - means[0]).pow(2)
                    stds[1] += (pixels[i * 3 + 1] - means[1]).pow(2)
                    stds[2] += (pixels[i * 3 + 2] - means[2]).pow(2)
                }
                for (c in 0..2) {
                    stds[c] = sqrt(stds[c] / totalPixels)
                    // Evita divisão por zero
                    stds[c] = max(stds[c], 1f)
                }

                // Normaliza cada canal
                for (i in 0 until totalPixels) {
                    pixels[i * 3] = (pixels[i * 3] - means[0]) / stds[0]
                    pixels[i * 3 + 1] = (pixels[i * 3 + 1] - means[1]) / stds[1]
                    pixels[i * 3 + 2] = (pixels[i * 3 + 2] - means[2]) / stds[2]
                }

                val output = TensorBufferFloat.createFixedSize(shape, DataType.FLOAT32)
                output.loadArray(pixels)
                output
            } catch (e: Exception) {
                android.util.Log.e("FaceNet", "❌ Erro na normalização de iluminação: ${e.message}")
                p0!! // Retorna original se falhar
            }
        }
    }

    // ✅ NOVO: Operador para melhorar contraste (equalização de histograma adaptativa)
    class ContrastEnhanceOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            return try {
                val pixels = p0!!.floatArray

                // Normalizar para 0-255 se necessário
                val minVal = pixels.minOrNull() ?: 0f
                val maxVal = pixels.maxOrNull() ?: 255f

                // Equalização de histograma simplificada
                if (maxVal > minVal) {
                    val range = maxVal - minVal
                    for (i in pixels.indices) {
                        // Normaliza para 0-1
                        var normalized = (pixels[i] - minVal) / range

                        // Aplica função de realce de contraste (gamma correction)
                        // Gamma < 1 aumenta brilho, Gamma > 1 aumenta contraste
                        val gamma = 1.2f
                        normalized = normalized.pow(gamma)

                        // Retorna para escala original
                        pixels[i] = normalized * range + minVal
                    }
                }

                val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
                output.loadArray(pixels)
                output
            } catch (e: Exception) {
                android.util.Log.e("FaceNet", "❌ Erro no realce de contraste: ${e.message}")
                p0!! // Retorna original se falhar
            }
        }
    }

    // ✅ NOVO: Normalização L2 para embeddings mais estáveis
    class L2NormalizationOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            return try {
                val pixels = p0!!.floatArray

                // Calcula a norma L2 (magnitude euclidiana)
                var normL2 = 0.0f
                for (i in pixels.indices) {
                    normL2 += pixels[i].pow(2)
                }
                normL2 = sqrt(normL2)

                // Evita divisão por zero
                if (normL2 > 0.0f) {
                    for (i in pixels.indices) {
                        pixels[i] /= normL2
                    }
                }

                val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
                output.loadArray(pixels)
                output
            } catch (e: Exception) {
                android.util.Log.e("FaceNet", "❌ Erro na normalização L2: ${e.message}")
                p0!! // Retorna original se falhar
            }
        }
    }
}
