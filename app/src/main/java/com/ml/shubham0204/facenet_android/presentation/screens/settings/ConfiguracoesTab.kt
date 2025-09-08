package com.ml.shubham0204.facenet_android.presentation.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    
    var horaDropdownExpanded by remember { mutableStateOf(false) }
    var minutoDropdownExpanded by remember { mutableStateOf(false) }
    var intervaloDropdownExpanded by remember { mutableStateOf(false) }
    
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
            colors = CardDefaults.cardColors(
                containerColor = Color.White // cor de fundo
            ),
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
                
//                OutlinedTextField(
//                    value = uiState.serverUrl,
//                    onValueChange = { viewModel.updateServerUrl(it) },
//                    label = { Text("URL do Servidor") },
//                    modifier = Modifier.fillMaxWidth(),
//                    isError = uiState.serverUrlError != null
//                )
//
//                if (uiState.serverUrlError != null) {
//                    Text(
//                        text = uiState.serverUrlError!!,
//                        color = Color.Red,
//                        style = MaterialTheme.typography.bodySmall
//                    )
//                }
            }
        }
        
        // Configuração de Entidade
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White // cor de fundo
            ),
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
            colors = CardDefaults.cardColors(
                containerColor = Color.White // cor de fundo
            ),
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
                
                if (uiState.sincronizacaoAtiva) {
                    Text(
                        text = "Configure o horário e intervalo para sincronização automática",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    // Seletor de Horário
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Seletor de Hora
                        ExposedDropdownMenuBox(
                            expanded = horaDropdownExpanded,
                            onExpandedChange = { horaDropdownExpanded = !horaDropdownExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "${uiState.horaSincronizacao.toString().padStart(2, '0')}",
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Hora") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = horaDropdownExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = horaDropdownExpanded,
                                onDismissRequest = { horaDropdownExpanded = false }
                            ) {
                                (0..23).forEach { hora ->
                                    DropdownMenuItem(
                                        text = { Text("${hora.toString().padStart(2, '0')}") },
                                        onClick = { viewModel.updateHoraSincronizacao(hora); horaDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                        
                        // Seletor de Minuto
                        ExposedDropdownMenuBox(
                            expanded = minutoDropdownExpanded,
                            onExpandedChange = { minutoDropdownExpanded = !minutoDropdownExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "${uiState.minutoSincronizacao.toString().padStart(2, '0')}",
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Minuto") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minutoDropdownExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = minutoDropdownExpanded,
                                onDismissRequest = { minutoDropdownExpanded = false }
                            ) {
                                (0..59).forEach { minuto ->
                                    DropdownMenuItem(
                                        text = { Text("${minuto.toString().padStart(2, '0')}") },
                                        onClick = { viewModel.updateMinutoSincronizacao(minuto); minutoDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Seletor de Intervalo
                    ExposedDropdownMenuBox(
                        expanded = intervaloDropdownExpanded,
                        onExpandedChange = { intervaloDropdownExpanded = !intervaloDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = when {
                                uiState.intervaloSincronizacao < 60 -> "${uiState.intervaloSincronizacao} min"
                                uiState.intervaloSincronizacao == 60 -> "1 hora"
                                else -> "${uiState.intervaloSincronizacao / 60} horas"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Intervalo de Sincronização") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervaloDropdownExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = intervaloDropdownExpanded,
                            onDismissRequest = { intervaloDropdownExpanded = false }
                        ) {
                            // Intervalos em minutos
                            listOf(1, 5, 10, 20, 30).forEach { intervalo ->
                                DropdownMenuItem(
                                    text = { Text("${intervalo} min") },
                                    onClick = { viewModel.updateIntervaloSincronizacao(intervalo); intervaloDropdownExpanded = false }
                                )
                            }
                            // Intervalos em horas
                            listOf(1, 2, 4, 6, 8, 12, 24).forEach { intervalo ->
                                DropdownMenuItem(
                                    text = { Text("${intervalo} ${if (intervalo == 1) "hora" else "horas"}") },
                                    onClick = { viewModel.updateIntervaloSincronizacao(intervalo * 60); intervaloDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
            }
        }
        

        Spacer(modifier = Modifier.height(16.dp))
        
        // Botões de Ação
        Button(
            onClick = { viewModel.salvarConfiguracoes() },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF264064)
            ),
        ) {
            Text("Salvar Configurações")
        }

        OutlinedButton(
            onClick = onCancelar,
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent // garante que não tenha fundo
            ),
            border = BorderStroke(1.dp, Color(0xFF264064))
        ) {
            Text("Cancelar", color = Color(0xFF264064))
        }
        
        Button(
            onClick = onSair,
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF264064)
            ),
        ) {
            Text("Sair")
        }

        OutlinedButton(
            onClick = { viewModel.sincronizarAgora() },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent // garante que não tenha fundo
            ),
            border = BorderStroke(1.dp, Color(0xFF264064))
        ) {
            Text("Sincronizar Agora", color = Color(0xFF264064))
        }
    }
} 