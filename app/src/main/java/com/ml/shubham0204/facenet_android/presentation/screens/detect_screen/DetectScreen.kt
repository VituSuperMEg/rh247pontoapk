package com.ml.shubham0204.facenet_android.presentation.screens.detect_screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.ml.shubham0204.facenet_android.R
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.presentation.components.AppAlertDialog
import com.ml.shubham0204.facenet_android.presentation.components.DelayedVisibility
import com.ml.shubham0204.facenet_android.presentation.components.FaceDetectionOverlay
import com.ml.shubham0204.facenet_android.presentation.components.createAlertDialog
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.text.font.FontWeight

private val cameraPermissionStatus = mutableStateOf(false)
private val cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) // Aqui troco camara
private lateinit var cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectScreen(
    onOpenFaceListClick: (() -> Unit),
    onNavigateBack: (() -> Unit) = {},
    onPontoSuccess: (PontosGenericosEntity) -> Unit = {},
    onAdminAccessClick: (() -> Unit) = {}
) {
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) { 
                ScreenUI(onPontoSuccess = onPontoSuccess) 
            }
        }
        
        // Botão administrativo no bottom
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = onAdminAccessClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars).height(55.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF264064)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Acesso Administrativo",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Acessar Painel Administrativo",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DateTimeHeader() {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = Date()
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE, dd 'DE' MMMM", Locale("pt", "BR"))
            
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).uppercase().replace(".", "")
            
            delay(1000) // Atualizar a cada segundo
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentDate,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ScreenUI(onPontoSuccess: (PontosGenericosEntity) -> Unit) {
    val viewModel: DetectScreenViewModel = koinViewModel()
    
    val showSuccessScreen by remember { viewModel.showSuccessScreen }
    val savedPonto by remember { viewModel.savedPonto }
    val isProcessingRecognition by remember { viewModel.isProcessingRecognition }
    
    // LaunchedEffect para navegar para tela de sucesso
    LaunchedEffect(showSuccessScreen, savedPonto) {
        if (showSuccessScreen && savedPonto != null) {
            onPontoSuccess(savedPonto!!)
            viewModel.resetRecognition()
            // ✅ CORRIGIDO: Aguardar um pouco e reiniciar o reconhecimento
            delay(3000) // Aumentado para 3 segundos
            viewModel.processFaceRecognition()
        }
    }
    
    LaunchedEffect(Unit) {
        delay(2000) 
        
        viewModel.checkAndClearDatabase()
        
        viewModel.processFaceRecognition()
    }
    
    LaunchedEffect(isProcessingRecognition) {
        if (!isProcessingRecognition && !showSuccessScreen) {
            delay(3000)
            viewModel.processFaceRecognition()
        }
    }
    
    var faceDetectionOverlay by remember { mutableStateOf<FaceDetectionOverlay?>(null) }
    
    Box {
        Camera(viewModel, onOverlayCreated = { overlay ->
            faceDetectionOverlay = overlay
        })
        
        // Header com data e hora no topo
        DateTimeHeader()
        
        // Indicador de processamento
        if (isProcessingRecognition) {
            // Box(
            //     modifier = Modifier
            //         .fillMaxSize()
            //         .background(Color.Black.copy(alpha = 0.7f)),
            //     contentAlignment = Alignment.Center
            // ) {
            //     Column(
            //         horizontalAlignment = Alignment.CenterHorizontally
            //     ) {
            //         // CircularProgressIndicator(
            //         //     color = Color.White,
            //         //     modifier = Modifier.size(64.dp)
            //         // )
            //         // Spacer(modifier = Modifier.height(16.dp))
            //         // Text(
            //         //     text = "Reconhecendo rosto...",
            //         //     color = Color.White,
            //         //     style = MaterialTheme.typography.bodyLarge
            //         // )
            //     }
            // }
        }
        
        DelayedVisibility(viewModel.getNumPeople() > 0L) {
            val metrics by remember { viewModel.faceDetectionMetricsState }
            Column {
                // ✅ NOVO: Mostrar quantas pessoas estão cadastradas
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(16.dp)
//                        .background(
//                            color = Color.Green.copy(alpha = 0.8f),
//                            shape = RoundedCornerShape(12.dp)
//                        )
//                        .padding(16.dp),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Text(
//                        text = "✅ ${viewModel.getNumPeople()} pessoa(s) cadastrada(s) no sistema",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = Color.White,
//                        fontWeight = FontWeight.Bold
//                    )
//                }
            }
        }
        DelayedVisibility(viewModel.getNumPeople() == 0L) {
            // ✅ NOVO: Mostrar aviso quando não há faces cadastradas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(
                        color = Color.Red.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ NENHUMA FACE CADASTRADA",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cadastre faces na tela de Funcionários Importados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        AppAlertDialog()

        // ✅ NOVO: Mostrar aviso quando spoofing for detectado
        val lastRecognizedPersonName by remember { viewModel.lastRecognizedPersonName }
        DelayedVisibility(lastRecognizedPersonName == "SPOOF_DETECTED") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(
                        color = Color.Red.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🚫 FOTO DETECTADA",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "O sistema detectou que você está usando uma foto.\nUse seu rosto real para registrar o ponto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    LaunchedEffect(faceDetectionOverlay) {
        if (faceDetectionOverlay != null) {
            while (isActive) { // ✅ CORRIGIDO: Usar isActive para verificar se o job ainda está ativo
                try {
                    delay(1000) // ✅ CORRIGIDO: Aumentado para 1 segundo para reduzir sobrecarga
                    val recognizedPerson = faceDetectionOverlay?.getLastRecognizedPerson()
                    
                    // Log para debug
                    if (recognizedPerson != null && recognizedPerson != "Not recognized") {
                        android.util.Log.d("DetectScreen", "🔄 Monitorando pessoa: $recognizedPerson")
                        viewModel.setLastRecognizedPersonName(recognizedPerson)
                        
                        // ✅ NOVO: Capturar foto atual quando pessoa for reconhecida
                        val currentBitmap = faceDetectionOverlay?.getCurrentFrameBitmap()
                        if (currentBitmap != null) {
                            viewModel.setCurrentFaceBitmap(currentBitmap)
                            android.util.Log.d("DetectScreen", "📸 Foto capturada do frame atual")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DetectScreen", "❌ Erro no monitoramento: ${e.message}")
                    break // Sair do loop em caso de erro
                }
            }
        }
    }

    val recognizedPerson by remember { viewModel.recognizedPerson }
    recognizedPerson?.let { funcionario ->
        if (funcionario.ativo == 0) {
            DelayedVisibility(true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            color = Color.Red.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🚫 ACESSO NEGADO",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = funcionario.nome,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Funcionário INATIVO\nPOOF inválido - Procure o RH",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun Camera(
    viewModel: DetectScreenViewModel,
    onOverlayCreated: (FaceDetectionOverlay) -> Unit = {}
) {
    val context = LocalContext.current
    cameraPermissionStatus.value =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val cameraFacing by remember { cameraFacing }
    val lifecycleOwner = LocalLifecycleOwner.current

    cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                cameraPermissionStatus.value = true
            } else {
                camaraPermissionDialog()
            }
        }

    DelayedVisibility(cameraPermissionStatus.value) {
        var isComponentVisible by remember { mutableStateOf(false) }
        var cameraInitialized by remember { mutableStateOf(false) }
        
        LaunchedEffect(Unit) {
            isComponentVisible = true
        }
        
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { 
                val overlay = FaceDetectionOverlay(lifecycleOwner, context, viewModel)
                onOverlayCreated(overlay)
                overlay
            },
            update = { overlay ->
                // ✅ CORRIGIDO: Inicializar câmera apenas uma vez quando o componente estiver visível
                if (isComponentVisible && !cameraInitialized) {
                    try {
                        overlay.initializeCamera(cameraFacing)
                        cameraInitialized = true
                        android.util.Log.d("DetectScreen", "✅ Câmera inicializada com sucesso")
                    } catch (e: Exception) {
                        android.util.Log.e("DetectScreen", "❌ Erro ao inicializar câmera: ${e.message}")
                    }
                }
            },
        )
    }
    DelayedVisibility(!cameraPermissionStatus.value) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Allow Camera Permissions\nThe app cannot work without the camera permission",
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(text = "Allow")
            }
        }
    }
}

private fun camaraPermissionDialog() {
    createAlertDialog(
        "Camera Permission",
        "The app couldn't function without the camera permission.",
        "ALLOW",
        "CLOSE",
        onPositiveButtonClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
        onNegativeButtonClick = {
            // TODO: Handle deny camera permission action
            //       close the app
        },
    )
}
