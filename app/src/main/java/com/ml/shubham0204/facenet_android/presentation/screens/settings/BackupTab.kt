package com.ml.shubham0204.facenet_android.presentation.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.data.BackupService
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupService = remember { BackupService(context) }
    
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showMessage by remember { mutableStateOf(false) }
    var showBackupMethodDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showRestoreProgress by remember { mutableStateOf(false) }
    var showRestartAlert by remember { mutableStateOf(false) }
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    
    // Função para mostrar mensagem
    fun displayMessage(text: String) {
        message = text
        showMessage = true
    }
    
    // Função para restaurar backup a partir de URI
    fun restoreBackupFromUri(uri: Uri) {
        scope.launch {
            isLoading = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    // Criar arquivo temporário
                    val tempFile = File(context.cacheDir, "temp_backup.json")
                    tempFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    
                    val result = backupService.restoreBackup(tempFile.absolutePath)
                    result.fold(
                        onSuccess = {
                            displayMessage("Backup restaurado com sucesso!")
                            tempFile.delete() // Limpar arquivo temporário
                        },
                        onFailure = { error ->
                            displayMessage("Erro ao restaurar backup: ${error.message}")
                            tempFile.delete() // Limpar arquivo temporário
                        }
                    )
                } else {
                    displayMessage("Erro ao ler arquivo selecionado")
                }
            } catch (e: Exception) {
                displayMessage("Erro ao processar arquivo: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    
    // Launcher para seleção de arquivo de backup
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                selectedBackupUri = uri
                showRestoreConfirmation = true
            }
        }
    }
    
    // Função para criar backup via Downloads (pen drive)
    fun createBackupToDownloads() {
        scope.launch {
            isLoading = true
            try {
                val result = backupService.createBackup()
                result.fold(
                    onSuccess = { filePath ->
                        displayMessage("Backup criado com sucesso na pasta Downloads!")
                    },
                    onFailure = { error ->
                        displayMessage("Erro ao criar backup: ${error.message}")
                    }
                )
            } finally {
                isLoading = false
            }
        }
    }
    
    // Função para criar backup na nuvem (placeholder)
    fun createBackupToCloud() {
        scope.launch {
            isLoading = true
            try {
                // TODO: Implementar upload para nuvem
                displayMessage("Funcionalidade de backup na nuvem será implementada em breve!")
            } finally {
                isLoading = false
            }
        }
    }
    
    // Função para mostrar modal de seleção
    fun showBackupMethodSelection() {
        showBackupMethodDialog = true
    }
    
    // Função para executar restauração com progresso
    fun executeRestoreWithProgress(uri: Uri) {
        scope.launch {
            showRestoreProgress = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    // Criar arquivo temporário
                    val tempFile = File(context.cacheDir, "temp_backup.json")
                    tempFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    
                    val result = backupService.restoreBackup(tempFile.absolutePath)
                    result.fold(
                        onSuccess = {
                            tempFile.delete() // Limpar arquivo temporário
                            showRestoreProgress = false
                            showRestartAlert = true
                        },
                        onFailure = { error ->
                            tempFile.delete() // Limpar arquivo temporário
                            showRestoreProgress = false
                            displayMessage("Erro ao restaurar backup: ${error.message}")
                        }
                    )
                } else {
                    showRestoreProgress = false
                    displayMessage("Erro ao ler arquivo selecionado")
                }
            } catch (e: Exception) {
                showRestoreProgress = false
                displayMessage("Erro ao processar arquivo: ${e.message}")
            }
        }
    }
    
    // Função para reiniciar a aplicação
    fun restartApplication() {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName = intent?.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
            Process.killProcess(Process.myPid())
        } catch (e: Exception) {
            // Fallback: fechar a aplicação
            Process.killProcess(Process.myPid())
        }
    }
    
    // Função para iniciar restauração
    fun startRestore() {
        val restoreIntent = backupService.createRestoreIntent()
        restoreLauncher.launch(restoreIntent)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título
        Text(
            text = "Backup do Banco de Dados",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Botão para criar backup
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Criar Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Cria um backup completo do banco de dados incluindo funcionários, configurações, pessoas e imagens de face.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Formato do arquivo: codigo_localizacao_20250715_171930.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "O backup será salvo automaticamente na pasta Downloads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showBackupMethodSelection() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criar Backup")
                }
            }
        }
        
        // Botão para restaurar backup
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Restaurar Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Selecione um arquivo de backup para restaurar o banco de dados. Esta ação irá substituir todos os dados atuais.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { startRestore() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Selecionar e Restaurar Backup")
                }
            }
        }
    }
    
    // Modal de seleção de método de backup
    if (showBackupMethodDialog) {
        AlertDialog(
            onDismissRequest = { showBackupMethodDialog = false },
            title = {
                Text(
                    text = "Qual forma você quer escolher?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card para Pen Drive
                    Card(
                        onClick = {
                            showBackupMethodDialog = false
                            createBackupToDownloads()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Via Pen Drive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Salva na pasta Downloads para transferir via USB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    
                    // Card para Nuvem
                    Card(
                        onClick = {
                            showBackupMethodDialog = false
                            createBackupToCloud()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Via Nuvem",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Faz upload do backup diretamente para a nuvem",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showBackupMethodDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Modal de confirmação para restauração
    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { 
                showRestoreConfirmation = false
                selectedBackupUri = null
            },
            title = {
                Text(
                    text = "Confirmar Restauração",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Você deseja realmente restaurar o backup? Esta ação irá substituir todos os dados atuais do sistema e não pode ser desfeita.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmation = false
                        selectedBackupUri?.let { uri ->
                            executeRestoreWithProgress(uri)
                        }
                        selectedBackupUri = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sim, Restaurar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showRestoreConfirmation = false
                        selectedBackupUri = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Modal de progresso da restauração
    if (showRestoreProgress) {
        AlertDialog(
            onDismissRequest = { /* Não permite fechar durante o progresso */ },
            title = {
                Text(
                    text = "Restaurando Backup",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Restaurando dados do backup...\nPor favor, aguarde.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = { /* Sem botões durante o progresso */ }
        )
    }
    
    // Modal de alerta de reinicialização
    if (showRestartAlert) {
        AlertDialog(
            onDismissRequest = { showRestartAlert = false },
            title = {
                Text(
                    text = "Restauração Concluída",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "O backup foi restaurado com sucesso!",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "O sistema precisa ser reiniciado para aplicar todas as alterações e garantir que todos os dados sejam carregados corretamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showRestartAlert = false
                        restartApplication()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reiniciar Aplicação")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartAlert = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Snackbar para mensagens






























    if (showMessage) {
        LaunchedEffect(showMessage) {
            kotlinx.coroutines.delay(3000)
            showMessage = false
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

