package com.ml.shubham0204.facenet_android.presentation.screens.ponto_success

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Timer
import java.util.TimerTask
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PontoSuccessScreen(
    ponto: PontosGenericosEntity,
    onNavigateBack: () -> Unit
) {
    var countdown by remember { mutableStateOf(3) }

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
        Scaffold {innerPadding ->
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
                        .height(250.dp),
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
                    }
                }
            }
        }
    }
}
