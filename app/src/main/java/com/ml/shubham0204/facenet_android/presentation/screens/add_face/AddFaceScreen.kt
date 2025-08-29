package com.ml.shubham0204.facenet_android.presentation.screens.add_face

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import coil.compose.AsyncImage
import com.ml.shubham0204.facenet_android.presentation.components.AppProgressDialog
import com.ml.shubham0204.facenet_android.presentation.components.DelayedVisibility
import com.ml.shubham0204.facenet_android.presentation.components.FaceDetectionOverlay
import com.ml.shubham0204.facenet_android.presentation.components.hideProgressDialog
import com.ml.shubham0204.facenet_android.presentation.components.showProgressDialog
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.delay
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import com.ml.shubham0204.facenet_android.presentation.theme.customBlue
import java.io.File
import androidx.core.content.FileProvider
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import java.nio.ByteBuffer
import kotlinx.coroutines.isActive


// Variáveis globais para câmera
private val cameraPermissionStatus = mutableStateOf(false)
private val cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) // Câmera frontal por padrão
private lateinit var cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun AddFaceScreen(
    personName: String = "",
    funcionarioCpf: String = "",
    funcionarioCargo: String = "",
    funcionarioOrgao: String = "",
    funcionarioLotacao: String = "",
    funcionarioId: Long = 0, // ✅ NOVO: Adicionar funcionarioId
    onNavigateBack: (() -> Unit) = {},
) {
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                val viewModel: AddFaceScreenViewModel = koinViewModel()
                
                LaunchedEffect(funcionarioId) {
                    if (funcionarioId > 0) {
                        viewModel.funcionarioId = funcionarioId
                    }
                }
                
                ScreenUI(
                    viewModel = viewModel, 
                    personName = personName,
                    funcionarioCpf = funcionarioCpf,
                    funcionarioCargo = funcionarioCargo,
                    funcionarioOrgao = funcionarioOrgao,
                    funcionarioLotacao = funcionarioLotacao,
                    onNavigateBack = onNavigateBack
                )
                ImageReadProgressDialog(viewModel, onNavigateBack)
            }
        }
    }
}

@Composable
private fun ScreenUI(
    viewModel: AddFaceScreenViewModel, 
    personName: String,
    funcionarioCpf: String,
    funcionarioCargo: String,
    funcionarioOrgao: String,
    funcionarioLotacao: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    var personNameState by remember { 
        if (personName.isNotEmpty()) {
            mutableStateOf(personName)
        } else {
            viewModel.personNameState
        }
    }
    
    // Estado para controlar se está na tela de captura
    var isInCaptureMode by remember { mutableStateOf(false) }
    var showSuccessScreen by remember { mutableStateOf(false) }
    
    if (showSuccessScreen) {
        // ✅ DEBUG: Log das fotos capturadas
        android.util.Log.d("AddFaceScreen", "📸 === TELA DE SUCESSO ===")
        android.util.Log.d("AddFaceScreen", "📸 Total de fotos: ${viewModel.selectedImageURIs.value.size}")
        viewModel.selectedImageURIs.value.forEachIndexed { index, uri ->
            android.util.Log.d("AddFaceScreen", "📸 Foto $index: $uri")
        }
        
        // Tela de sucesso
        SuccessScreen(
            personName = personNameState,
            funcionarioCpf = funcionarioCpf,
            funcionarioCargo = funcionarioCargo,
            funcionarioOrgao = funcionarioOrgao,
            funcionarioLotacao = funcionarioLotacao,
            capturedPhotos = viewModel.selectedImageURIs.value,
            onBackToEmployees = onNavigateBack
        )
    } else if (isInCaptureMode) {
        // Tela de captura de fotos
        CapturePhotosScreen(
            personName = personNameState,
            funcionarioCpf = funcionarioCpf,
            funcionarioCargo = funcionarioCargo,
            funcionarioOrgao = funcionarioOrgao,
            funcionarioLotacao = funcionarioLotacao,
            viewModel = viewModel,
            onBackToForm = { isInCaptureMode = false },
            onSuccess = { showSuccessScreen = true }
        )
    } else {
        // Tela de formulário
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 50.dp),
        ) {
            // Campo Nome (editável)
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = personNameState,
                onValueChange = { personNameState = it },
                label = { Text(text = "Nome da pessoa") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Campo CPF (somente leitura)
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = funcionarioCpf,
                onValueChange = { },
                label = { Text(text = "CPF") },
                singleLine = true,
                enabled = false,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Campo Cargo (somente leitura)
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = funcionarioCargo,
                onValueChange = { },
                label = { Text(text = "Cargo") },
                singleLine = true,
                enabled = false,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Campo Órgão (somente leitura)
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = funcionarioOrgao,
                onValueChange = { },
                label = { Text(text = "Órgão") },
                singleLine = true,
                enabled = false,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Campo Lotação (somente leitura)
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = funcionarioLotacao,
                onValueChange = { },
                label = { Text(text = "Lotação") },
                singleLine = true,
                enabled = false,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            // Informações sobre as fotos
            Text(
                text = "Fotos capturadas: ${viewModel.selectedImageURIs.value.size}/3",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Última foto cadastrada
            if (viewModel.selectedImageURIs.value.isNotEmpty()) {
                Text(
                    text = "Última foto cadastrada:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = viewModel.selectedImageURIs.value.last(),
                            contentDescription = "Última foto capturada",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    enabled = personNameState.isNotEmpty(),
                    onClick = { isInCaptureMode = true },
                ) {
                    Icon(imageVector = Icons.Default.Camera, contentDescription = "Cadastrar Facial")
                    Text(text = "Cadastrar Facial")
                }
                
                DelayedVisibility(viewModel.selectedImageURIs.value.size >= 3) {
                    Button(onClick = { 
                        viewModel.updatePersonName(personNameState)
                        viewModel.addImages() 
                    }) { 
                        Text(text = "Adicionar ao banco") 
                    }
                }
            }
            
            if (viewModel.selectedImageURIs.value.isEmpty()) {
                // Text(
                //     text = "Clique em 'Capturar Fotos' para tirar 3 fotos do funcionário",
                //     style = MaterialTheme.typography.bodySmall,
                //     color = MaterialTheme.colorScheme.onSurfaceVariant
                // )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ImagesGrid(viewModel)
        }
    }
}

@Composable
private fun CapturePhotosScreen(
    personName: String,
    funcionarioCpf: String,
    funcionarioCargo: String,
    funcionarioOrgao: String,
    funcionarioLotacao: String,
    viewModel: AddFaceScreenViewModel,
    onBackToForm: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    
    cameraPermissionStatus.value = ActivityCompat.checkSelfPermission(
        context, 
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    
    cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionStatus.value = true
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionStatus.value) {
            // Câmera integrada
            IntegratedCameraCapture(
                personName = personName,
                funcionarioCpf = funcionarioCpf,
                funcionarioCargo = funcionarioCargo,
                funcionarioOrgao = funcionarioOrgao,
                funcionarioLotacao = funcionarioLotacao,
                viewModel = viewModel,
                onBackToForm = onBackToForm,
                onSuccess = onSuccess
            )
        } else {
            // Solicitar permissão
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Permissão da Câmera Necessária",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "O app precisa da permissão da câmera para capturar fotos do funcionário.",
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                ) {
                    Text("Permitir Câmera")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBackToForm
                ) {
                    Text("Voltar")
                }
            }
        }
    }
}

@Composable
private fun IntegratedCameraCapture(
    personName: String,
    funcionarioCpf: String,
    funcionarioCargo: String,
    funcionarioOrgao: String,
    funcionarioLotacao: String,
    viewModel: AddFaceScreenViewModel,
    onBackToForm: () -> Unit,
    onSuccess: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraFacing by remember { cameraFacing }
    
    // Estados para controle da captura
    var isFaceDetected by remember { mutableStateOf(false) }
    var isFaceCentered by remember { mutableStateOf(false) }
    var isStable by remember { mutableStateOf(false) }
    var captureCountdown by remember { mutableStateOf(0) }
    var currentPhotoIndex by remember { mutableStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<androidx.camera.core.ImageCapture?>(null) }
    
    // LaunchedEffect para capturar fotos automaticamente
    LaunchedEffect(Unit) {
        android.util.Log.d("AddFaceScreen", "🚀 === INICIANDO CAPTURA AUTOMÁTICA ===")
        
        var photoCount = 0
        val totalPhotos = 3
        
        while (photoCount < totalPhotos && isActive) {
            android.util.Log.d("AddFaceScreen", "📸 === CAPTURA ${photoCount + 1}/$totalPhotos ===")
            
            // Simular detecção de face
            viewModel.setFaceDetectionStatus("Detectando face...")
            delay(1000)
            
            // Simular centralização
            viewModel.setFaceDetectionStatus("Centralizando...")
            delay(1000)
            
            // Simular estabilização
            viewModel.setFaceDetectionStatus("Estabilizando...")
            delay(1000)
            
            // Contagem regressiva
            for (i in 3 downTo 1) {
                viewModel.setFaceDetectionStatus("Capturando em $i...")
                delay(1000)
            }
            
            // Capturar foto
            viewModel.setFaceDetectionStatus("Capturando foto ${photoCount + 1}...")
            
            try {
                val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                
                // ✅ NOVO: Usar OutputFileOptions em vez de ImageCapturedCallback
                val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile).build()
                
                imageCapture?.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                            val uri = Uri.fromFile(photoFile)
                            viewModel.addSelectedImageURI(uri)
                            android.util.Log.d("AddFaceScreen", "✅ Foto ${photoCount + 1} capturada e salva: $uri")
                        }
                        
                        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                            android.util.Log.e("AddFaceScreen", "❌ Erro ao capturar foto ${photoCount + 1}: ${exception.message}")
                        }
                    }
                )
                
                photoCount++
                android.util.Log.d("AddFaceScreen", "📊 Progresso: $photoCount/$totalPhotos fotos capturadas")
                
                // Aguardar um pouco antes da próxima captura
                delay(2000)
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreen", "❌ Erro na captura ${photoCount + 1}: ${e.message}")
                e.printStackTrace()
            }
        }
        
        android.util.Log.d("AddFaceScreen", "🎉 === CAPTURA CONCLUÍDA ===")
        android.util.Log.d("AddFaceScreen", "📸 Total de fotos capturadas: ${viewModel.selectedImageURIs.value.size}")
        
        // ✅ NOVO: Salvar automaticamente após capturar todas as fotos
        if (viewModel.selectedImageURIs.value.size == totalPhotos) {
            android.util.Log.d("AddFaceScreen", "💾 === SALVANDO FACES AUTOMATICAMENTE ===")
            
            // ✅ NOVO: Atualizar o nome da pessoa no viewModel antes de salvar
            viewModel.updatePersonName(personName)
            android.util.Log.d("AddFaceScreen", "📝 Nome atualizado no viewModel: $personName")
            
            viewModel.addImages()
        } else {
            android.util.Log.e("AddFaceScreen", "❌ ERRO: Nem todas as fotos foram capturadas!")
        }
        
        viewModel.setFaceDetectionStatus("Captura concluída!")
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { 
                val previewView = androidx.camera.view.PreviewView(context)
                previewView.implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build()
                    
                    // ✅ NOVO: ImageCapture em vez de ImageAnalysis
                    val capture = androidx.camera.core.ImageCapture.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                        .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    
                    val cameraSelector = androidx.camera.core.CameraSelector.Builder()
                        .requireLensFacing(cameraFacing)
                        .build()
                    
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture // ✅ NOVO: Usar ImageCapture
                        )
                        
                        // Armazenar referência do ImageCapture
                        imageCapture = capture
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(context))
                
                previewView
            }
        )
        
        // Overlay com instruções e status
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) { 
                IconButton(onClick = onBackToForm) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "Foto ${currentPhotoIndex + 1}/3",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Instruções centrais
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Círculo de foco - AUMENTADO O TAMANHO
                Box(
                    modifier = Modifier
                        .size(500   .dp) // Aumentado de 200dp para 280dp
                        .border(
                            width = 2.dp, // Aumentado de 3dp para 4dp
                            color = when {
                                isStable -> Color.Green
                                isFaceCentered -> Color.Yellow
                                isFaceDetected -> Color(0xFFFF9800) // Orange
                                else -> Color.White
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isStable && captureCountdown > 0) {
                        Text(
                            text = captureCountdown.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isStable) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Capturando",
                            tint = Color.Green,
                            modifier = Modifier.size(64.dp) // Aumentado de 48dp para 64dp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Status e instruções
                Text(
                    text = when {
                        currentPhotoIndex >= 3 -> "Todas as fotos foram capturadas!"
                        isStable && captureCountdown > 0 -> "Mantenha-se estável..."
                        isStable -> "Capturando foto..."
                        isFaceCentered -> "Rosto centralizado! Aguarde estabilizar..."
                        isFaceDetected -> "Rosto detectado! Centralize o rosto..."
                        else -> "Posicione seu rosto no círculo"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = personName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Progresso das fotos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
                    Card(
                        modifier = Modifier
                            .size(60.dp)
                            .padding(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (index < currentPhotoIndex) Color.Green else Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < currentPhotoIndex) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Foto capturada",
                                    tint = Color.White
                                )
                            } else {
                                Text(
                                    text = (index + 1).toString(),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImagesGrid(viewModel: AddFaceScreenViewModel) {
    val uris by remember { viewModel.selectedImageURIs }
    LazyVerticalGrid(columns = GridCells.Fixed(2)) {
        items(uris) { AsyncImage(model = it, contentDescription = null) }
    }
}

@Composable
private fun ImageReadProgressDialog(
    viewModel: AddFaceScreenViewModel,
    onNavigateBack: () -> Unit,
) {
    val isProcessing by remember { viewModel.isProcessingImages }
    val numImagesProcessed by remember { viewModel.numImagesProcessed }
    val showSuccessScreen by remember { viewModel.showSuccessScreen }
    val context = LocalContext.current
    
    AppProgressDialog()
    
    if (isProcessing) {
        showProgressDialog()
        android.util.Log.d("ImageReadProgressDialog", "🔄 Processando imagens...")
    } else {
        hideProgressDialog()
        
        // ✅ CORRIGIDO: Só navegar de volta se não estiver na tela de sucesso
        if (numImagesProcessed > 0 && !showSuccessScreen) {
            android.util.Log.d("ImageReadProgressDialog", "✅ Processamento concluído, navegando de volta")
            Toast.makeText(context, "Added to database", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }
}

@Composable
private fun SuccessScreen(
    personName: String,
    funcionarioCpf: String,
    funcionarioCargo: String,
    funcionarioOrgao: String,
    funcionarioLotacao: String,
    capturedPhotos: List<Uri>, // ✅ NOVO: Lista de fotos capturadas
    onBackToEmployees: () -> Unit
) {
    // ✅ DEBUG: Log das fotos recebidas
    android.util.Log.d("SuccessScreen", "📸 === SUCCESS SCREEN ===")
    android.util.Log.d("SuccessScreen", "📸 Fotos recebidas: ${capturedPhotos.size}")
    capturedPhotos.forEachIndexed { index, uri ->
        android.util.Log.d("SuccessScreen", "📸 Foto $index: $uri")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ícone de sucesso
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = Color.Green,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sucesso",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Título de sucesso
        Text(
            text = "Facial Cadastrada com Sucesso!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "A face do funcionário foi cadastrada no sistema",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Dados do funcionário
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Dados do Funcionário",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Campo Nome
                InfoField(
                    label = "Nome",
                    value = personName
                )
                
                // Campo CPF
                InfoField(
                    label = "CPF",
                    value = formatCPF(funcionarioCpf)
                )
                
                // Campo Cargo
                InfoField(
                    label = "Cargo",
                    value = funcionarioCargo
                )
                
                // Campo Órgão
                InfoField(
                    label = "Órgão",
                    value = funcionarioOrgao
                )
                
                // Campo Lotação
                InfoField(
                    label = "Lotação",
                    value = funcionarioLotacao
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ✅ NOVO: Seção de fotos capturadas
        if (capturedPhotos.isNotEmpty()) {
            android.util.Log.d("SuccessScreen", "📸 Mostrando seção de fotos com ${capturedPhotos.size} fotos")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Fotos Capturadas (${capturedPhotos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Grid de fotos
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(capturedPhotos.size) { index ->
                            android.util.Log.d("SuccessScreen", "📸 Renderizando foto $index: ${capturedPhotos[index]}")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 2.dp
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = capturedPhotos[index],
                                        contentDescription = "Foto ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    // Número da foto
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.7f),
                                                shape = CircleShape
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            android.util.Log.w("SuccessScreen", "⚠️ Nenhuma foto para mostrar")
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Botão para voltar
        Button(
            onClick = onBackToEmployees,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = customBlue
            )
        ) {
            Text("Voltar para Funcionários")
        }
    }
}

@Composable
private fun InfoField(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifEmpty { "Não informado" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ✅ NOVO: Função para formatar CPF
private fun formatCPF(cpf: String): String {
    return if (cpf.length >= 11) {
        "${cpf.substring(0, 3)}.***.***-${cpf.substring(9, 11)}"
    } else {
        cpf
    }
}
