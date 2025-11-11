package com.ml.shubham0204.facenet_android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.utils.CrashReporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf("Carregando logs...") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Função para carregar logs
    fun loadLogs() {
        isLoading = true
        try {
            logs = CrashReporter.getLogs(context)
        } catch (e: Exception) {
            logs = "Erro ao carregar logs: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    // Carrega logs na primeira vez
    LaunchedEffect(Unit) {
        loadLogs()
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Logs do Sistema",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    // Botão para atualizar
                    IconButton(
                        onClick = { loadLogs() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar logs"
                        )
                    }
                    
                    // Botão para limpar logs
                    IconButton(
                        onClick = {
                            CrashReporter.clearLogs(context)
                            loadLogs()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpar logs"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Área de logs
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Logs Capturados:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = logs,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Informações sobre o crash reporting
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Sobre o Sistema de Logs",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• Os logs são salvos automaticamente quando ocorrem erros\n" +
                           "• Use o botão de atualizar para ver novos logs\n" +
                           "• Os logs são enviados para o Firebase Crashlytics\n" +
                           "• Logs locais são mantidos no dispositivo",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        }
    }
}
