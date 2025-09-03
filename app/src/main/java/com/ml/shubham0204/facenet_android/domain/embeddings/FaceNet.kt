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
import kotlin.math.pow
import kotlin.math.sqrt

// Derived from the original project:
// https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android/blob/master/app/src/main/java/com/ml/quaterion/facenetdetection/model/FaceNetModel.kt
// Utility class for FaceNet model
@Single
class FaceNet(
    context: Context,
    useGpu: Boolean = false, // ✅ CORRIGIDO: Desabilitar GPU por padrão para evitar problemas
    useXNNPack: Boolean = true,
) {
    // Input image size for FaceNet model.
    private val imgSize = 160

    // Output embedding size
    private val embeddingDim = 512

    private var interpreter: Interpreter
    private val imageTensorProcessor =
        ImageProcessor
            .Builder()
            .add(ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(StandardizeOp())
            .build()

    init {
        // ✅ CORRIGIDO: Inicialização mais robusta do TensorFlow Lite
        val interpreterOptions = try {
            Interpreter.Options().apply {
                // ✅ CORRIGIDO: Configuração mais segura
                if (useGpu) {
                    try {
                        val compatibilityList = CompatibilityList()
                        if (compatibilityList.isDelegateSupportedOnThisDevice) {
                            android.util.Log.d("FaceNet", "📱 GPU delegate suportado, configurando...")
                            val gpuDelegate = GpuDelegate(compatibilityList.bestOptionsForThisDevice)
                            addDelegate(gpuDelegate)
                            android.util.Log.d("FaceNet", "✅ GPU delegate configurado com sucesso")
                        } else {
                            android.util.Log.w("FaceNet", "⚠️ GPU delegate não suportado neste dispositivo")
                            // Fallback para CPU
                            numThreads = 4
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FaceNet", "❌ Erro ao configurar GPU delegate: ${e.message}")
                        // Fallback para CPU em caso de erro
                        numThreads = 4
                    }
                } else {
                    // ✅ CORRIGIDO: Configuração otimizada para CPU
                    numThreads = 4
                    android.util.Log.d("FaceNet", "📱 Usando CPU com $numThreads threads")
                }
                
                // ✅ CORRIGIDO: Configurações mais seguras
                useXNNPACK = useXNNPack && !useGpu // XNNPACK pode conflitar com GPU
                useNNAPI = false // ✅ CORRIGIDO: Desabilitar NNAPI para evitar conflitos
                
                android.util.Log.d("FaceNet", "📱 Configurações: GPU=$useGpu, XNNPACK=$useXNNPack, NNAPI=$useNNAPI")
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceNet", "❌ Erro ao criar opções do interpreter: ${e.message}")
            // Configuração de emergência
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

    // ✅ CORRIGIDO: Gets an face embedding using FaceNet com melhor tratamento de erro
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

    // ✅ CORRIGIDO: Run the FaceNet model com melhor tratamento de erro
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

    // ✅ CORRIGIDO: Resize the given bitmap and convert it to a ByteBuffer
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

    // ✅ CORRIGIDO: Op to perform standardization com melhor tratamento de erro
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
}
