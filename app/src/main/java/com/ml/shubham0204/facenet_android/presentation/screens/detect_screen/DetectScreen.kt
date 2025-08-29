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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
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

private val cameraPermissionStatus = mutableStateOf(false)
private val cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
private lateinit var cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectScreen(
    onOpenFaceListClick: (() -> Unit),
    onNavigateBack: (() -> Unit) = {},
    onPontoSuccess: (PontosGenericosEntity) -> Unit = {}
) {
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(),
                    title = {
                        Text(
                            text = "Registrar Ponto",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Voltar",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenFaceListClick) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Open Face List",
                            )
                        }
                        IconButton(
                            onClick = {
                                if (cameraFacing.intValue == CameraSelector.LENS_FACING_BACK) {
                                    cameraFacing.intValue = CameraSelector.LENS_FACING_FRONT
                                } else {
                                    cameraFacing.intValue = CameraSelector.LENS_FACING_BACK
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) { 
                ScreenUI(onPontoSuccess = onPontoSuccess) 
            }
        }
    }
}

@Composable
private fun ScreenUI(onPontoSuccess: (PontosGenericosEntity) -> Unit) {
    val viewModel: DetectScreenViewModel = koinViewModel()
    
    // Observar mudanças no estado de sucesso
    val showSuccessScreen by remember { viewModel.showSuccessScreen }
    val savedPonto by remember { viewModel.savedPonto }
    val isProcessingRecognition by remember { viewModel.isProcessingRecognition }
    
    // LaunchedEffect para navegar para tela de sucesso
    LaunchedEffect(showSuccessScreen, savedPonto) {
        if (showSuccessScreen && savedPonto != null) {
            onPontoSuccess(savedPonto!!)
            viewModel.resetRecognition()
        }
    }
    
    // LaunchedEffect para iniciar reconhecimento automaticamente
    LaunchedEffect(Unit) {
        delay(1000) // Aguardar 1 segundo para a câmera inicializar
        viewModel.processFaceRecognition()
    }
    
    var faceDetectionOverlay by remember { mutableStateOf<FaceDetectionOverlay?>(null) }
    
    Box {
        Camera(viewModel, onOverlayCreated = { overlay ->
            faceDetectionOverlay = overlay
        })
        
        // Indicador de processamento
        if (isProcessingRecognition) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Reconhecendo rosto...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        DelayedVisibility(viewModel.getNumPeople() > 0) {
            val metrics by remember { viewModel.faceDetectionMetricsState }
            Column {
                Text(
                    text = "Recognition on ${viewModel.getNumPeople()} face(s)",
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.weight(1f))
                metrics?.let {
                    Text(
                        text =
                            "face detection: ${it.timeFaceDetection} ms" +
                                "\nface embedding: ${it.timeFaceEmbedding} ms" +
                                "\nvector search: ${it.timeVectorSearch} ms\n" +
                                "spoof detection: ${it.timeFaceSpoofDetection} ms",
                        color = Color.White,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        DelayedVisibility(viewModel.getNumPeople() == 0L) {
            Text(
                text = "No images in database",
                color = Color.White,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color.Blue, RoundedCornerShape(16.dp))
                        .padding(8.dp),
                textAlign = TextAlign.Center,
            )
        }
        AppAlertDialog()
    }

    // LaunchedEffect para monitorar pessoa reconhecida
    LaunchedEffect(faceDetectionOverlay) {
        if (faceDetectionOverlay != null) {
            while (true) {
                delay(500) // Verificar a cada 500ms
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
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { 
                val overlay = FaceDetectionOverlay(lifecycleOwner, context, viewModel)
                onOverlayCreated(overlay)
                overlay
            },
            update = { overlay ->
                overlay.initializeCamera(cameraFacing)
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
                "Allow Camera Permissions\nThe app cannot work without the camera permission.",
                textAlign = TextAlign.Center,
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
