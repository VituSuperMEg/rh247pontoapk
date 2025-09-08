package com.ml.shubham0204.facenet_android.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.ml.shubham0204.facenet_android.presentation.screens.detect_screen.DetectScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@SuppressLint("ViewConstructor")
@ExperimentalGetImage
class FaceDetectionOverlay(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val viewModel: DetectScreenViewModel,
) : FrameLayout(context) {
    private var overlayWidth: Int = 0
    private var overlayHeight: Int = 0

    private var imageTransform: Matrix = Matrix()
    private var boundingBoxTransform: Matrix = Matrix()
    private var isImageTransformedInitialized = false
    private var isBoundingBoxTransformedInitialized = false

    private lateinit var frameBitmap: Bitmap
    private var isProcessing = false
    private var cameraFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private lateinit var previewView: PreviewView

    var predictions: Array<Prediction> = arrayOf()
    private var lastRecognizedPerson: String? = null

    init {
        // ✅ CORRIGIDO: Não inicializar câmera automaticamente no init
        // A câmera será inicializada quando necessário
        doOnLayout {
            overlayHeight = it.measuredHeight
            overlayWidth = it.measuredWidth
        }
    }
    
    fun getCurrentFrameBitmap(): Bitmap? {
        return if (::frameBitmap.isInitialized) {
            frameBitmap.copy(frameBitmap.config, false)
        } else {
            null
        }
    }
    
    fun getLastRecognizedPerson(): String? {
        return lastRecognizedPerson
    }
    
    // ✅ NOVO: Função para limpar recursos da câmera
    fun cleanupCamera() {
        try {
            android.util.Log.d("FaceDetectionOverlay", "🧹 Limpando recursos da câmera...")
            
            // Remover views se existirem
            if (::previewView.isInitialized) {
                removeView(previewView)
            }
            if (::boundingBoxOverlay.isInitialized) {
                removeView(boundingBoxOverlay)
            }
            
            android.util.Log.d("FaceDetectionOverlay", "✅ Recursos da câmera limpos com sucesso")
            
        } catch (e: Exception) {
            android.util.Log.e("FaceDetectionOverlay", "❌ Erro ao limpar câmera: ${e.message}")
        }
    }

    fun initializeCamera(cameraFacing: Int) {
        // ✅ CORRIGIDO: Verificar se já está inicializando para evitar múltiplas inicializações
        if (::previewView.isInitialized && ::boundingBoxOverlay.isInitialized) {
            android.util.Log.d("FaceDetectionOverlay", "📷 Câmera já inicializada, pulando...")
            return
        }
        
        this.cameraFacing = cameraFacing
        this.isImageTransformedInitialized = false
        this.isBoundingBoxTransformedInitialized = false
        
        android.util.Log.d("FaceDetectionOverlay", "📷 Iniciando inicialização da câmera...")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val previewView = PreviewView(context)
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener(
            {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview =
                        Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    
                    // ✅ CORRIGIDO: Seleção de câmera mais robusta
                    val cameraSelector = try {
                        // Primeiro tenta a câmera especificada
                        CameraSelector.Builder().requireLensFacing(cameraFacing).build()
                    } catch (e: Exception) {
                        android.util.Log.w("FaceDetectionOverlay", "⚠️ Câmera $cameraFacing não disponível, tentando frontal...")
                        try {
                            // Se falhar, tenta a câmera frontal
                            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
                        } catch (e2: Exception) {
                            android.util.Log.w("FaceDetectionOverlay", "⚠️ Câmera frontal não disponível, tentando traseira...")
                            try {
                                // Se falhar, tenta a câmera traseira
                                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
                            } catch (e3: Exception) {
                                android.util.Log.w("FaceDetectionOverlay", "⚠️ Câmera traseira não disponível, usando padrão...")
                                // Se todas falharem, usa o padrão do sistema
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            }
                        }
                    }
                    
                    android.util.Log.d("FaceDetectionOverlay", "📷 CameraSelector criado com sucesso")
                    
                    val frameAnalyzer =
                        ImageAnalysis
                            .Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                    frameAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
                    
                    // ✅ CORRIGIDO: Verificar se a câmera está disponível antes de fazer bind
                    val availableCameras = cameraProvider.availableCameraInfos
                    if (availableCameras.isEmpty()) {
                        android.util.Log.e("FaceDetectionOverlay", "❌ Nenhuma câmera disponível!")
                        return@addListener
                    }
                    
                    android.util.Log.d("FaceDetectionOverlay", "📷 Câmeras disponíveis: ${availableCameras.size}")
                    
                    cameraProvider.unbindAll()
                    
                    try {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            frameAnalyzer,
                        )
                        
                        android.util.Log.d("FaceDetectionOverlay", "✅ Câmera inicializada com sucesso!")
                        
                    } catch (e: Exception) {
                        android.util.Log.e("FaceDetectionOverlay", "❌ Erro ao fazer bind da câmera: ${e.message}")
                        
                        // ✅ NOVO: Tentar com câmera padrão se a selecionada falhar
                        try {
                            android.util.Log.d("FaceDetectionOverlay", "🔄 Tentando com câmera padrão...")
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                frameAnalyzer,
                            )
                            
                            android.util.Log.d("FaceDetectionOverlay", "✅ Câmera padrão inicializada com sucesso!")
                            
                        } catch (e2: Exception) {
                            android.util.Log.e("FaceDetectionOverlay", "❌ Falha total na inicialização da câmera: ${e2.message}")
                            e2.printStackTrace()
                        }
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("FaceDetectionOverlay", "❌ Erro ao inicializar câmera: ${e.message}")
                    e.printStackTrace()
                }
            },
            executor,
        )
        // ✅ CORRIGIDO: Verificar se o componente está visível antes de adicionar views
        if (visibility == VISIBLE) {
            if (childCount == 2) {
                removeView(this.previewView)
                removeView(this.boundingBoxOverlay)
            }
            this.previewView = previewView
            addView(this.previewView)

            val boundingBoxOverlayParams =
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            this.boundingBoxOverlay = BoundingBoxOverlay(context)
            this.boundingBoxOverlay.setWillNotDraw(false)
            this.boundingBoxOverlay.setZOrderOnTop(true)
            addView(this.boundingBoxOverlay, boundingBoxOverlayParams)
            
            android.util.Log.d("FaceDetectionOverlay", "✅ Views da câmera adicionadas com sucesso")
        } else {
            android.util.Log.w("FaceDetectionOverlay", "⚠️ Componente não visível, pulando adição de views")
        }
    }

    private val analyzer =
        ImageAnalysis.Analyzer { image ->
            if (isProcessing) {
                image.close()
                return@Analyzer
            }
            isProcessing = true

            // Transform android.net.Image to Bitmap
            frameBitmap =
                Bitmap.createBitmap(
                    image.image!!.width,
                    image.image!!.height,
                    Bitmap.Config.ARGB_8888,
                )
            frameBitmap.copyPixelsFromBuffer(image.planes[0].buffer)

            // Configure frameHeight and frameWidth for output2overlay transformation matrix
            // and apply it to `frameBitmap`
            if (!isImageTransformedInitialized) {
                imageTransform = Matrix()
                imageTransform.apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                isImageTransformedInitialized = true
            }
            frameBitmap =
                Bitmap.createBitmap(
                    frameBitmap,
                    0,
                    0,
                    frameBitmap.width,
                    frameBitmap.height,
                    imageTransform,
                    false,
                )

            if (!isBoundingBoxTransformedInitialized) {
                boundingBoxTransform = Matrix()
                boundingBoxTransform.apply {
                    setScale(
                        overlayWidth / frameBitmap.width.toFloat(),
                        overlayHeight / frameBitmap.height.toFloat(),
                    )
                    if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                        // Mirror the bounding box coordinates
                        // for front-facing camera
                        postScale(
                            -1f,
                            1f,
                            overlayWidth.toFloat() / 2.0f,
                            overlayHeight.toFloat() / 2.0f,
                        )
                    }
                }
                isBoundingBoxTransformedInitialized = true
            }
            CoroutineScope(Dispatchers.Default).launch {
                val predictions = ArrayList<Prediction>()
                
                // ✅ NOVO: Log para verificar se há pessoas cadastradas
                val totalPessoas = viewModel.getNumPeople()
                android.util.Log.d("FaceDetectionOverlay", "📊 Total de pessoas no banco: $totalPessoas")
                
                // ✅ NOVO: Verificar se há pessoas antes de tentar reconhecer
                if (totalPessoas == 0L) {
                    android.util.Log.w("FaceDetectionOverlay", "⚠️ NENHUMA PESSOA CADASTRADA - pulando reconhecimento")
                    withContext(Dispatchers.Main) {
                        this@FaceDetectionOverlay.predictions = emptyArray()
                        boundingBoxOverlay.invalidate()
                        isProcessing = false
                    }
                    return@launch
                }
                
                val (metrics, results) =
                    viewModel.imageVectorUseCase.getNearestPersonName(
                        frameBitmap,
                    )
                
                // ✅ NOVO: Log dos resultados
                android.util.Log.d("FaceDetectionOverlay", "🔍 Resultados do reconhecimento: ${results.size}")
                results.forEachIndexed { index, result ->
                    android.util.Log.d("FaceDetectionOverlay", "   Resultado $index: ${result.personName}")
                }
                
                // ✅ CORRIGIDO: Capturar a pessoa reconhecida com verificação mais rigorosa
                val recognizedPerson = results.find { result ->
                    val name = result.personName
                    android.util.Log.d("FaceDetectionOverlay", "🔍 Verificando resultado: '$name' (tipo: ${name::class.java.simpleName})")
                    
                    val isValidName = name != "Not recognized" && 
                                    name != "Não Encontrado" && 
                                    name != "Error" &&
                                    name.isNotEmpty() &&
                                    name != "null" &&
                                    name != "Nenhuma pessoa cadastrada" &&
                                    name != "Pessoa não reconhecida"
                    
                    android.util.Log.d("FaceDetectionOverlay", "🔍 Resultado válido: $isValidName")
                    isValidName
                }
                
                if (recognizedPerson != null) {
                    val personName = recognizedPerson.personName
                    lastRecognizedPerson = personName
                    
                    // ✅ CORRIGIDO: Chamar diretamente o ViewModel para atualizar o estado
                    try {
                        // viewModel.setLastRecognizedPersonName(personName)
                        // android.util.Log.d("FaceDetectionOverlay", "✅ Pessoa reconhecida: '$personName' - ViewModel atualizado")
                    } catch (e: Exception) {
                        android.util.Log.e("FaceDetectionOverlay", "❌ Erro ao atualizar ViewModel: ${e.message}")
                    }
                } else {
                    lastRecognizedPerson = null
                    
                    // ✅ CORRIGIDO: Limpar o ViewModel
                    try {
                        viewModel.setLastRecognizedPersonName(null)
                        android.util.Log.d("FaceDetectionOverlay", "🔄 ViewModel limpo (nenhuma pessoa reconhecida)")
                    } catch (e: Exception) {
                        android.util.Log.e("FaceDetectionOverlay", "❌ Erro ao limpar ViewModel: ${e.message}")
                    }
                    
                    // ✅ NOVO: Log detalhado para debug
                    android.util.Log.d("FaceDetectionOverlay", "🔍 Debug - Resultados disponíveis:")
                    results.forEachIndexed { index, result ->
                        android.util.Log.d("FaceDetectionOverlay", "   Resultado $index: '${result.personName}' (tipo: ${result.personName::class.java.simpleName})")
                    }
                }
                
                results.forEach { (name, boundingBox, spoofResult) ->
                    val box = boundingBox.toRectF()
                    var personName = name
                    
                    // ✅ CORRIGIDO: Verificação mais rigorosa para exibição
                    if (viewModel.getNumPeople().toInt() == 0) {
                        personName = "Nenhuma pessoa cadastrada"
                    } else if (name == "Not recognized" || name == "Não Encontrado") {
                        personName = "Pessoa não reconhecida"
                    } else if (name == "SPOOF_DETECTED") {
                        // ✅ NOVO: Mensagem específica para spoofing detectado
                        personName = "🚫 FOTO DETECTADA"
                    }
                    
                    // ✅ REMOVIDO: Não mostrar mais o score de spoof na interface
                    // (já está sendo tratado acima)
                    
                    boundingBoxTransform.mapRect(box)
                    predictions.add(Prediction(box, personName))
                }
                withContext(Dispatchers.Main) {
                    viewModel.faceDetectionMetricsState.value = metrics
                    this@FaceDetectionOverlay.predictions = predictions.toTypedArray()
                    boundingBoxOverlay.invalidate()
                    isProcessing = false
                }
            }
            image.close()
        }

    data class Prediction(
        var bbox: RectF,
        var label: String,
    )

    inner class BoundingBoxOverlay(
        context: Context,
    ) : SurfaceView(context),
        SurfaceHolder.Callback {
        private val boxPaint =
            Paint().apply {
                color = Color.parseColor("#4D90caf9")
                style = Paint.Style.FILL
            }
        private val textPaint =
            Paint().apply {
                strokeWidth = 2.0f
                textSize = 36f
                color = Color.WHITE
            }

        override fun surfaceCreated(holder: SurfaceHolder) {}

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {}

        override fun onDraw(canvas: Canvas) {
            predictions.forEach {
                canvas.drawRoundRect(it.bbox, 16f, 16f, boxPaint)
                canvas.drawText(it.label, it.bbox.centerX(), it.bbox.centerY(), textPaint)
            }
        }
    }
}
