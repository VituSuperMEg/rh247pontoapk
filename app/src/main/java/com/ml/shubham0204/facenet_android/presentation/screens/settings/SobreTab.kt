package com.ml.shubham0204.facenet_android.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun SobreTab() {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    var versao by remember { mutableStateOf("1.2") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sistema de Ponto",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Versão 1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
                
                Text(
                    text = "Sistema de ponto eletrônico com reconhecimento facial",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Informações do Sistema",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Desenvolvido por RH247",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "© 2024 Todos os direitos reservados",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Atualizações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Status da verificação
                if (uiState.updateMessage != null) {
                    Text(
                        text = uiState.updateMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.updateMessage!!.startsWith("✅")) androidx.compose.ui.graphics.Color.Green 
                               else if (uiState.updateMessage!!.startsWith("❌")) androidx.compose.ui.graphics.Color.Red 
                               else androidx.compose.ui.graphics.Color.Gray
                    )
                }
                
            
                Button(
                    onClick = { viewModel.verificarAtualizacao() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isCheckingUpdate && !uiState.isUpdating
                ) {
                    Text(if (uiState.isCheckingUpdate) "Verificando..." else "Verificar Atualização")
                }
                
                Button(
                    onClick = { viewModel.downloadDiretoAtualizacaoComVersao(versao) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isUpdating && versao.isNotBlank(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color.Green
                    )
                ) {
                    Text(if (uiState.isUpdating) "Baixando..." else "Download Direto v$versao")
                }
                
                Button(
                    onClick = { viewModel.atualizarSistema() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isUpdating && uiState.hasUpdate
                ) {
                    Text(if (uiState.isUpdating) "Atualizando..." else "Atualizar Sistema")
                }
            }
        }
    }
} 