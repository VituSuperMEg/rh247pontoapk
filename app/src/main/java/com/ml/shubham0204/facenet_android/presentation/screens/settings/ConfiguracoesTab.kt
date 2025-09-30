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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fine = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            viewModel.fetchAndSetCurrentLocation()
        }
    }
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
                    onValueChange = { newValue ->
                        // Aceitar apenas números
                        val filteredValue = newValue.filter { it.isDigit() }
                        viewModel.updateLocalizacaoId(filteredValue)
                    },
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
                    onValueChange = { newValue ->
                        val filteredValue = newValue.filter { it.isDigit() }
                        viewModel.updateCodigoSincronizacao(filteredValue)
                    },
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
                    onValueChange = { newValue ->
                        // Aceitar apenas números e limitar a 9 dígitos
                        val filteredValue = newValue.filter { it.isDigit() }.take(9)
                        viewModel.updateEntidadeId(filteredValue)
                    },
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

        Card(
            modifier = Modifier
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Geolocalização",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.geolocalizacaoHabilitada,
                        onCheckedChange = { checked ->
                            viewModel.updateGeolocalizacaoHabilitada(checked)
                            if (checked) {
                                val fineGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val coarseGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!fineGranted && !coarseGranted) {
                                    val activity = context as? Activity
                                    val showRationaleFine = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) } ?: true
                                    val showRationaleCoarse = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION) } ?: true
                                    if (showRationaleFine || showRationaleCoarse) {
                                        locationPermissionLauncher.launch(arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ))
                                    } else {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                } else {
                                    viewModel.fetchAndSetCurrentLocation()
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quando habilitado, o app solicitará permissão e incluirá latitude/longitude ao registrar ponto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val fineGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val coarseGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val granted = fineGranted || coarseGranted
                    Text(
                        text = if (granted) "Permissão: CONCEDIDA" else "Permissão: NEGADA",
                        color = if (granted) Color(0xFF7DD97D) else Color(0xFFD97D7D),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        if (!granted) {
                            locationPermissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        } else {
                            viewModel.fetchAndSetCurrentLocation()
                        }
                    }) { Text("Atualizar localização") }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = uiState.latitudeFixa,
                        onValueChange = { v -> viewModel.updateLatitudeFixa(v.filter { it.isDigit() || it == '.' || it == '-' }) },
                        label = { Text("Latitude fixa") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = uiState.longitudeFixa,
                        onValueChange = { v -> viewModel.updateLongitudeFixa(v.filter { it.isDigit() || it == '.' || it == '-' }) },
                        label = { Text("Longitude fixa") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Se definidos, os pontos usarão estas coordenadas e não solicitarão GPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
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