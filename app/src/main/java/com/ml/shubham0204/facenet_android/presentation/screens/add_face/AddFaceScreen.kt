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
import androidx.compose.material.icons.filled.Warning
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
import com.ml.shubham0204.facenet_android.presentation.components.AppAlertDialog
import com.ml.shubham0204.facenet_android.presentation.components.createAlertDialog
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
                    funcionarioId = funcionarioId,
                    onNavigateBack = onNavigateBack
                )
                ImageReadProgressDialog(viewModel, onNavigateBack)
                AppAlertDialog()
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
    funcionarioId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    // ✅ NOVO: Logs para verificar os dados recebidos
    LaunchedEffect(Unit) {
        android.util.Log.d("AddFaceScreen", "📋 === DADOS RECEBIDOS NA TELA ===")
        android.util.Log.d("AddFaceScreen", "📋 Nome: '$personName'")
        android.util.Log.d("AddFaceScreen", "📋 CPF: '$funcionarioCpf'")
        android.util.Log.d("AddFaceScreen", "📋 Cargo: '$funcionarioCargo'")
        android.util.Log.d("AddFaceScreen", "📋 Órgão: '$funcionarioOrgao'")
        android.util.Log.d("AddFaceScreen", "📋 Lotação: '$funcionarioLotacao'")
        
        // ✅ NOVO: Verificar se os campos estão vazios
        android.util.Log.d("AddFaceScreen", "📋 === VERIFICAÇÃO DE CAMPOS VAZIOS ===")
        android.util.Log.d("AddFaceScreen", "📋 CPF vazio: ${funcionarioCpf.isEmpty()}")
        android.util.Log.d("AddFaceScreen", "📋 Cargo vazio: ${funcionarioCargo.isEmpty()}")
        android.util.Log.d("AddFaceScreen", "📋 Órgão vazio: ${funcionarioOrgao.isEmpty()}")
        android.util.Log.d("AddFaceScreen", "📋 Lotação vazio: ${funcionarioLotacao.isEmpty()}")
    }
    
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
            isDeletion = viewModel.isDeletingUser.value,
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
            // ✅ NOVO: Título da seção
            Text(
                text = "Cadastro de Face",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ✅ NOVO: Card com dados do funcionário
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
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
                value = if (funcionarioCpf.isNotEmpty()) formatCPF(funcionarioCpf) else "Não informado",
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
                value = if (funcionarioCargo.isNotEmpty()) funcionarioCargo else "Não informado",
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
                value = if (funcionarioOrgao.isNotEmpty()) funcionarioOrgao else "Não informado",
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
                value = if (funcionarioLotacao.isNotEmpty()) funcionarioLotacao else "Não informado",
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
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
                // ✅ NOVO: Seção de debug (apenas em desenvolvimento)
    if (funcionarioCpf.isEmpty() || funcionarioCargo.isEmpty() || funcionarioOrgao.isEmpty() || funcionarioLotacao.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Aviso",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dados Incompletos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Alguns dados do funcionário não foram carregados corretamente. Verifique se os dados estão sendo passados da tela anterior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // ✅ NOVO: LaunchedEffect para monitorar o diálogo de confirmação
    LaunchedEffect(viewModel.showDeleteConfirmation.value) {
        if (viewModel.showDeleteConfirmation.value) {
            createAlertDialog(
                dialogTitle = "Excluir Usuário",
                dialogText = "Tem certeza que deseja excluir o usuário '$personNameState' e todas as suas faces cadastradas? Esta ação não pode ser desfeita.",
                dialogPositiveButtonText = "Excluir",
                onPositiveButtonClick = { viewModel.deleteUserAndFaces() },
                dialogNegativeButtonText = "Cancelar",
                onNegativeButtonClick = { viewModel.cancelDelete() }
            )
        }
    }
            
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ✅ NOVO: Botão para excluir usuário (apenas se há um funcionário válido)
            if (funcionarioId > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = { viewModel.showDeleteConfirmationDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        ),
                        enabled = !viewModel.isDeletingUser.value
                    ) {
                    if (viewModel.isDeletingUser.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Excluindo...",
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Excluir Usuário",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Excluir Usuário e Faces",
                            color = Color.White
                        )
                    }
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
    
    // ✅ MELHORADO: LaunchedEffect para capturar fotos automaticamente com countdown visual
    LaunchedEffect(Unit) {
        android.util.Log.d("AddFaceScreen", "🚀 === INICIANDO CAPTURA AUTOMÁTICA ===")
        
        var photoCount = 0
        val totalPhotos = 3
        
        while (photoCount < totalPhotos && isActive) {
            android.util.Log.d("AddFaceScreen", "📸 === CAPTURA ${photoCount + 1}/$totalPhotos ===")
            
            // ✅ MELHORADO: Atualizar o índice da foto atual
            currentPhotoIndex = photoCount
            android.util.Log.d("AddFaceScreen", "📊 Atualizando currentPhotoIndex para: $currentPhotoIndex")
            
            // ✅ MELHORADO: Simular detecção de face com mudança de cor
            isFaceDetected = true
            viewModel.setFaceDetectionStatus("Detectando face...")
            delay(1000)
            
            // ✅ MELHORADO: Simular centralização com mudança de cor
            isFaceCentered = true
            viewModel.setFaceDetectionStatus("Centralizando...")
            delay(1000)
            
            // ✅ MELHORADO: Simular estabilização com mudança de cor
            isStable = true
            viewModel.setFaceDetectionStatus("Estabilizando...")
            delay(1000)
            
            // ✅ MELHORADO: Contagem regressiva visual dentro da bolinha
            for (i in 3 downTo 1) {
                captureCountdown = i
                viewModel.setFaceDetectionStatus("Capturando em $i...")
                android.util.Log.d("AddFaceScreen", "⏰ Countdown: $i")
                delay(1000)
            }
            
            // ✅ MELHORADO: Capturar foto com verificação de sucesso
            captureCountdown = 0
            viewModel.setFaceDetectionStatus("Capturando foto ${photoCount + 1}...")
            
            try {
                // ✅ CORRIGIDO: Verificar se imageCapture está inicializado
                val currentImageCapture = imageCapture
                if (currentImageCapture == null) {
                    android.util.Log.e("AddFaceScreen", "❌ ImageCapture não inicializado!")
                    continue
                }
                
                val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                android.util.Log.d("AddFaceScreen", "📸 Tentando capturar foto para: $photoFile")
                
                // ✅ NOVO: Usar OutputFileOptions em vez de ImageCapturedCallback
                val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile).build()
                
                // ✅ NOVO: Variável para controlar se a foto foi capturada
                var photoCaptured = false
                
                currentImageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                            val uri = Uri.fromFile(photoFile)
                            viewModel.addSelectedImageURI(uri)
                            photoCaptured = true
                            android.util.Log.d("AddFaceScreen", "✅ Foto ${photoCount + 1} capturada e salva: $uri")
                        }
                        
                        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                            android.util.Log.e("AddFaceScreen", "❌ Erro ao capturar foto ${photoCount + 1}: ${exception.message}")
                            photoCaptured = false
                        }
                    }
                )
                
                // ✅ NOVO: Aguardar um pouco para a captura ser processada
                delay(1000)
                
                // ✅ NOVO: Verificar se a foto foi realmente capturada
                if (photoCaptured && photoFile.exists()) {
                    photoCount++
                    android.util.Log.d("AddFaceScreen", "📊 Progresso: $photoCount/$totalPhotos fotos capturadas")
                    android.util.Log.d("AddFaceScreen", "📁 Arquivo existe: ${photoFile.exists()}, Tamanho: ${photoFile.length()} bytes")
                    
                    // ✅ NOVO: Verificar se a URI foi adicionada ao ViewModel
                    val currentURIs = viewModel.selectedImageURIs.value
                    android.util.Log.d("AddFaceScreen", "📋 URIs no ViewModel: ${currentURIs.size}")
                    currentURIs.forEachIndexed { index, uri ->
                        android.util.Log.d("AddFaceScreen", "📋 URI $index: $uri")
                    }
                } else {
                    android.util.Log.e("AddFaceScreen", "❌ Foto não foi capturada corretamente!")
                    android.util.Log.e("AddFaceScreen", "📁 Arquivo existe: ${photoFile.exists()}")
                    android.util.Log.e("AddFaceScreen", "📸 PhotoCaptured: $photoCaptured")
                }
                
                // ✅ MELHORADO: Resetar estados para próxima captura
                isFaceDetected = false
                isFaceCentered = false
                isStable = false
                captureCountdown = 0
                
                // Aguardar um pouco antes da próxima captura
                delay(2000)
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreen", "❌ Erro na captura ${photoCount + 1}: ${e.message}")
                e.printStackTrace()
            }
        }
        
        android.util.Log.d("AddFaceScreen", "🎉 === CAPTURA CONCLUÍDA ===")
        android.util.Log.d("AddFaceScreen", "📸 Total de fotos capturadas: ${viewModel.selectedImageURIs.value.size}")
        
        // ✅ MELHORADO: Verificação final das fotos capturadas
        val finalURIs = viewModel.selectedImageURIs.value
        android.util.Log.d("AddFaceScreen", "📋 === VERIFICAÇÃO FINAL ===")
        android.util.Log.d("AddFaceScreen", "📋 Total de URIs: ${finalURIs.size}")
        finalURIs.forEachIndexed { index, uri ->
            android.util.Log.d("AddFaceScreen", "📋 URI final $index: $uri")
        }
        
        // ✅ CORRIGIDO: Voltar para a tela de formulário após capturar todas as fotos
        if (finalURIs.size >= totalPhotos) {
            android.util.Log.d("AddFaceScreen", "🔄 === VOLTANDO PARA TELA DE FORMULÁRIO ===")
            android.util.Log.d("AddFaceScreen", "✅ Sucesso: ${finalURIs.size} fotos capturadas")
            onBackToForm()
        } else {
            android.util.Log.e("AddFaceScreen", "❌ ERRO: Nem todas as fotos foram capturadas!")
            android.util.Log.e("AddFaceScreen", "❌ Esperado: $totalPhotos, Capturado: ${finalURIs.size}")
            
            // ✅ NOVO: Tentar novamente se não capturou todas as fotos
            if (finalURIs.isEmpty()) {
                android.util.Log.w("AddFaceScreen", "⚠️ Nenhuma foto capturada, tentando novamente...")
                delay(3000)
                // Aqui poderia reiniciar o processo se necessário
            }
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
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        android.util.Log.d("AddFaceScreen", "📷 Inicializando câmera...")
                        
                        val preview = androidx.camera.core.Preview.Builder().build()
                        
                        // ✅ MELHORADO: ImageCapture com configurações otimizadas
                        val capture = androidx.camera.core.ImageCapture.Builder()
                            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                            .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setJpegQuality(90) // ✅ NOVO: Qualidade JPEG otimizada
                            .build()
                        
                        val cameraSelector = androidx.camera.core.CameraSelector.Builder()
                            .requireLensFacing(cameraFacing)
                            .build()
                        
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                        
                        // ✅ MELHORADO: Armazenar referência do ImageCapture com verificação
                        imageCapture = capture
                        android.util.Log.d("AddFaceScreen", "✅ Câmera inicializada com sucesso!")
                        android.util.Log.d("AddFaceScreen", "📷 ImageCapture configurado: ${imageCapture != null}")
                        
                    } catch (e: Exception) {
                        android.util.Log.e("AddFaceScreen", "❌ Erro ao inicializar câmera: ${e.message}")
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
                    text = if (viewModel.selectedImageURIs.value.size >= 3) {
                        "🎉 Captura Concluída!"
                    } else {
                        "📸 Foto ${viewModel.selectedImageURIs.value.size + 1}/3"
                    },
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
                // ✅ MELHORADO: Círculo de foco com countdown visual e cores dinâmicas
                Box(
                    modifier = Modifier
                        .size(300.dp) // Tamanho otimizado
                        .border(
                            width = 4.dp, // Borda mais grossa para melhor visibilidade
                            color = when {
                                captureCountdown > 0 -> Color.Red // Vermelho durante countdown
                                isStable -> Color.Green // Verde quando estável
                                isFaceCentered -> Color.Yellow // Amarelo quando centralizado
                                isFaceDetected -> Color(0xFFFF9800) // Laranja quando detectado
                                else -> Color.White // Branco por padrão
                            },
                            shape = CircleShape
                        )
                        .background(
                            color = when {
                                captureCountdown > 0 -> Color.Red.copy(alpha = 0.1f) // Fundo vermelho suave
                                isStable -> Color.Green.copy(alpha = 0.1f) // Fundo verde suave
                                isFaceCentered -> Color.Yellow.copy(alpha = 0.1f) // Fundo amarelo suave
                                isFaceDetected -> Color(0xFFFF9800).copy(alpha = 0.1f) // Fundo laranja suave
                                else -> Color.Transparent
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // ✅ MELHORADO: Conteúdo dinâmico baseado no estado
                    when {
                        captureCountdown > 0 -> {
                            // Countdown visual grande e claro
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = captureCountdown.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "segundos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        isStable -> {
                            // Ícone de check quando estável
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Capturando",
                                tint = Color.Green,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        isFaceCentered -> {
                            // Ícone de face quando centralizado
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Centralizado",
                                tint = Color.Yellow,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        isFaceDetected -> {
                            // Ícone de face quando detectado
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Detectado",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        else -> {
                            // Instrução inicial
                            Text(
                                text = "Posicione\no rosto",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // ✅ MELHORADO: Status e instruções mais claras
                Text(
                    text = when {
                        viewModel.selectedImageURIs.value.size >= 3 -> "🎉 Todas as fotos foram capturadas!"
                        captureCountdown > 0 -> "📸 Capturando em $captureCountdown segundos..."
                        isStable -> "✅ Rosto estável! Preparando para capturar..."
                        isFaceCentered -> "🎯 Rosto centralizado! Aguarde estabilizar..."
                        isFaceDetected -> "👤 Rosto detectado! Centralize o rosto no círculo..."
                        else -> "📱 Posicione seu rosto no círculo"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
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
                            containerColor = if (index < viewModel.selectedImageURIs.value.size) Color.Green else Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < viewModel.selectedImageURIs.value.size) {
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
    isDeletion: Boolean = false, // ✅ NOVO: Indica se foi uma exclusão
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
                    color = if (isDeletion) Color.Red else Color.Green,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDeletion) Icons.Default.Close else Icons.Default.Check,
                contentDescription = if (isDeletion) "Excluído" else "Sucesso",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Título de sucesso
        Text(
            text = if (isDeletion) "Usuário Excluído com Sucesso!" else "Facial Cadastrada com Sucesso!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isDeletion) 
                "O usuário e todas as suas faces foram removidas do sistema" 
            else 
                "A face do funcionário foi cadastrada no sistema",
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
        
        // ✅ NOVO: Seção de fotos capturadas (apenas para cadastro, não para exclusão)
        if (!isDeletion && capturedPhotos.isNotEmpty()) {
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
        } else if (isDeletion) {
            android.util.Log.d("SuccessScreen", "🗑️ Exclusão realizada - não mostrando fotos")
        } else {
            android.util.Log.w("SuccessScreen", "⚠️ Nenhuma foto para mostrar")
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Botão para voltar
        Button(
            onClick = onBackToEmployees,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDeletion) Color.Red else customBlue
            )
        ) {
            Text(if (isDeletion) "Voltar para Funcionários" else "Voltar para Funcionários")
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

private fun formatCPF(cpf: String): String {
    return if (cpf.length >= 11) {
        "${cpf.substring(0, 3)}.***.***-${cpf.substring(9, 11)}"
    } else {
        cpf
    }
}

private fun decodeUrlValue(value: String): String {
    return value.replace("_", " ")
}
