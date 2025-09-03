package com.ml.shubham0204.facenet_android.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesTab(
    onSalvar: () -> Unit,
    onCancelar: () -> Unit,
    onSair: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Configurações Gerais
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configurações Gerais",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = uiState.localizacaoId,
                    onValueChange = { viewModel.updateLocalizacaoId(it) },
                    label = { Text("ID da Localização") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.localizacaoIdError != null
                )
                
                if (uiState.localizacaoIdError != null) {
                    Text(
                        text = uiState.localizacaoIdError!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                OutlinedTextField(
                    value = uiState.codigoSincronizacao,
                    onValueChange = { viewModel.updateCodigoSincronizacao(it) },
                    label = { Text("Código de Sincronização") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.codigoSincronizacaoError != null
                )
                
                if (uiState.codigoSincronizacaoError != null) {
                    Text(
                        text = uiState.codigoSincronizacaoError!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    label = { Text("URL do Servidor") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.serverUrlError != null
                )
                
                if (uiState.serverUrlError != null) {
                    Text(
                        text = uiState.serverUrlError!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Configuração de Entidade
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configuração de Entidade",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Configure a entidade (empresa/órgão) para usar o sistema",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                
                OutlinedTextField(
                    value = uiState.entidadeId,
                    onValueChange = { viewModel.updateEntidadeId(it) },
                    label = { Text("Código da Entidade (9 dígitos)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.entidadeIdError != null
                )
                
                if (uiState.entidadeIdError != null) {
                    Text(
                        text = uiState.entidadeIdError!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Sincronização Automática
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sincronização Automática",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ativar sincronização automática",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = uiState.sincronizacaoAtiva,
                        onCheckedChange = { viewModel.updateSincronizacaoAtiva(it) }
                    )
                }
                
                Button(
                    onClick = { viewModel.sincronizarAgora() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sincronizar Agora")
                }
            }
        }
        
        // Atualizações do Sistema
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Atualizações do Sistema",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Status da verificação
                if (uiState.updateMessage != null) {
                    Text(
                        text = uiState.updateMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.updateMessage!!.startsWith("✅")) Color.Green 
                               else if (uiState.updateMessage!!.startsWith("❌")) Color.Red 
                               else Color.Gray
                    )
                }
                
                // Informações da atualização disponível
                if (uiState.hasUpdate && uiState.availableUpdate != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📱 Nova Versão Disponível",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Green
                            )
                            
                            Text(
                                text = "Versão: ${uiState.availableUpdate!!.version}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Text(
                                text = "Tamanho: ${uiState.availableUpdate!!.fileSizeFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            
                            Text(
                                text = "Última modificação: ${uiState.availableUpdate!!.lastModified}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                // Botões de ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.verificarAtualizacao() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isCheckingUpdate && !uiState.isUpdating
                    ) {
                        Text(if (uiState.isCheckingUpdate) "Verificando..." else "Verificar Atualizações")
                    }
                    
                    if (uiState.hasUpdate) {
                        Button(
                            onClick = { viewModel.atualizarSistema() },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isUpdating,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.Green
                            )
                        ) {
                            Text(if (uiState.isUpdating) "Baixando..." else "Baixar e Instalar")
                        }
                    }
                }
            }
        }
        
        // Informações
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Informações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "ID da Localização: Identificador único da localização",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botões de Ação
        Button(
            onClick = { viewModel.salvarConfiguracoes() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar Configurações")
        }
        
        Button(
            onClick = onCancelar,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color.Gray
            )
        ) {
            Text("Cancelar")
        }
        
        Button(
            onClick = onSair,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color.Red
            )
        ) {
            Text("Sair")
        }
    }
} 