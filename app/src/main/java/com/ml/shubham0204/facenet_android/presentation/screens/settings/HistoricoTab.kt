package com.ml.shubham0204.facenet_android.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoricoTab() {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selectedErrorDetails by remember { mutableStateOf<HistoricoSincronizacao?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        if (uiState.historicoSincronizacao.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Nenhum histórico de sincronização encontrado",
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.historicoSincronizacao.sortedByDescending { historico ->
                    try {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        dateFormat.parse(historico.dataHora)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }) { historico ->
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White // cor de fundo
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = historico.dataHora,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = historico.mensagem,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Status: ${historico.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.Gray
                            )
                            
                            // Botão "Ver mais" apenas para erros
                            if (historico.status.lowercase().contains("erro") || 
                                historico.status.lowercase().contains("error") ||
                                historico.status.lowercase().contains("falha") ||
                                historico.status.lowercase().contains("failed")) {
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { 
                                            selectedErrorDetails = historico
                                        }
                                    ) {
                                        Text("Ver mais")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // AlertDialog para mostrar detalhes do erro
    selectedErrorDetails?.let { historico ->
        AlertDialog(
            onDismissRequest = { selectedErrorDetails = null },
            title = {
                Text(
                    text = "Detalhes do Erro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Mensagem amigável
                    Text(
                        text = "Mensagem:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = historico.mensagem,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Detalhes técnicos se disponíveis
                    historico.detalhesTecnicos?.let { detalhes ->
                        Text(
                            text = "Detalhes Técnicos:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = detalhes,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedErrorDetails = null }
                ) {
                    Text("Fechar")
                }
            }
        )
    }
} 