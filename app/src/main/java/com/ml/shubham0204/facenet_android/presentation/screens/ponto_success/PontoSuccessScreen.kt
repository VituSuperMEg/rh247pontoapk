package com.ml.shubham0204.facenet_android.presentation.screens.ponto_success

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Timer
import java.util.TimerTask
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PontoSuccessScreen(
    ponto: PontosGenericosEntity,
    onNavigateBack: () -> Unit
) {
    var countdown by mutableStateOf(3)
    
    // ✅ CORRIGIDO: DisposableEffect com Timer e Handler para thread principal
    DisposableEffect(Unit) {
        android.util.Log.d("PontoSuccessScreen", "🕐 Iniciando countdown de 3 segundos...")
        
        val timer = Timer()
        val handler = Handler(Looper.getMainLooper())
        var currentCount = 3
        
        val task = object : TimerTask() {
            override fun run() {
                currentCount--
                countdown = currentCount
                android.util.Log.d("PontoSuccessScreen", "⏰ Countdown: $countdown segundos restantes")
                
                if (currentCount <= 0) {
                    android.util.Log.d("PontoSuccessScreen", "✅ Countdown finalizado! Navegando de volta...")
                    timer.cancel()
                    
                    // ✅ CORRIGIDO: Executar navegação na thread principal
                    handler.post {
                        onNavigateBack()
                    }
                }
            }
        }
        
        // Executar a cada 1 segundo
        timer.scheduleAtFixedRate(task, 1000, 1000)
        
        onDispose {
            timer.cancel()
            android.util.Log.d("PontoSuccessScreen", "🔄 Timer cancelado")
        }
    }

    FaceNetAndroidTheme {
        Scaffold(
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF9F9F9))
                    .padding(innerPadding)
            ) {
                Card(
                    modifier = Modifier
                        .width(600.dp)
                        .padding(16.dp)
                        .align(Alignment.Center)
                        .height(450.dp)
                        .shadow(elevation = 0.5.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                      
                                               
                        Text(
                            text = "Ponto Registrado Com Sucesso!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Olá,",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        
                        Text(
                            text = ponto.funcionarioNome,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val date = Date(ponto.dataHora)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Data do Ponto",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = dateFormat.format(date),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2)
                                )
                            }
                            
                            Column {
                                Text(
                                    text = "Hora do Ponto",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = timeFormat.format(date),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (ponto.latitude != null && ponto.longitude != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Latitude - Longitude",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${ponto.latitude} | ${ponto.longitude}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        if (ponto.fotoBase64?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                               
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // // Progress indicator
                        // CircularProgressIndicator(
                        //     modifier = Modifier.size(24.dp),
                        //     color = Color(0xFF1976D2),
                        //     strokeWidth = 2.dp
                        // )
                        
                        // Spacer(modifier = Modifier.height(8.dp))
                        
                        // // ✅ MELHORADO: Indicador visual do countdown
                        // Column(
                        //     modifier = Modifier.fillMaxWidth(),
                        //     horizontalAlignment = Alignment.CenterHorizontally
                        // ) {
                             
                        //     Row(
                        //         horizontalArrangement = Arrangement.Center,
                        //         verticalAlignment = Alignment.CenterVertically
                        //     ) {
                        //         Text(
                        //             text = "Fechando automaticamente em ",
                        //             style = MaterialTheme.typography.bodySmall,
                        //             color = Color.Gray,
                        //             textAlign = TextAlign.Center
                        //         )
                        //         Text(
                        //             text = "$countdown",
                        //             style = MaterialTheme.typography.headlineSmall,
                        //             color = if (countdown <= 1) Color.Red else Color(0xFF1976D2),
                        //             fontWeight = FontWeight.Bold,
                        //             textAlign = TextAlign.Center
                        //         )
                        //         Text(
                        //             text = " segundos...",
                        //             style = MaterialTheme.typography.bodySmall,
                        //             color = Color.Gray,
                        //             textAlign = TextAlign.Center
                        //         )
                        //     }
                        // }
                        
                        // Spacer(modifier = Modifier.height(16.dp))
                        
                        // Botão Fechar
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Fechar Agora",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
} 