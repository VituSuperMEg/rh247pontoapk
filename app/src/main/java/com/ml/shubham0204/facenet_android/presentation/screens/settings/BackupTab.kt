package com.ml.shubham0204.facenet_android.presentation.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.data.BackupService
import com.ml.shubham0204.facenet_android.utils.BackupTestHelper
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient
import com.ml.shubham0204.facenet_android.data.api.BackupListResponse
import com.ml.shubham0204.facenet_android.data.ObjectBoxStore
import com.ml.shubham0204.facenet_android.data.api.BackupListRequest
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.utils.CacheManager
import okhttp3.ResponseBody
// Imports USB comentados - funcionalidade desabilitada
// import com.ml.shubham0204.facenet_android.utils.USBUtils
// import com.ml.shubham0204.facenet_android.utils.USBStorageInfo
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupFileInfo(
    val fileName: String,
    val fileDate: String,
    val fileTime: String
)

data class OnlineBackupFile(
    val fileName: String,
    val displayName: String,
    val fileDate: String,
    val fileTime: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupService = remember { BackupService(context, ObjectBoxStore.store) }
    val backupTestHelper = remember { BackupTestHelper(context) }
    
    var isLoadingBackup by remember { mutableStateOf(false) }
    var isLoadingRestore by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showMessage by remember { mutableStateOf(false) }
    var showBackupMethodDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showRestoreProgress by remember { mutableStateOf(false) }
    var showRestartAlert by remember { mutableStateOf(false) }
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var backupFileInfo by remember { mutableStateOf<BackupFileInfo?>(null) }
    var showRestoreMethodDialog by remember { mutableStateOf(false) }
    var showOnlineBackupList by remember { mutableStateOf(false) }
    var onlineBackupFiles by remember { mutableStateOf<List<OnlineBackupFile>>(emptyList()) }
    var selectedOnlineBackup by remember { mutableStateOf<OnlineBackupFile?>(null) }
    
    fun displayMessage(text: String) {
        message = text
        showMessage = true
    }
    
    fun extractBackupFileInfo(uri: Uri): BackupFileInfo? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    
                    val fileName = if (displayNameIndex >= 0) {
                        it.getString(displayNameIndex) ?: "backup_desconhecido"
                    } else {
                        "backup_desconhecido"
                    }
                    
                    val datePattern = Regex("backup_(\\d{8})_(\\d{6})")
                    val match = datePattern.find(fileName)
                    
                    if (match != null) {
                        val dateStr = match.groupValues[1] 
                        val timeStr = match.groupValues[2] 
                        
                        val year = dateStr.substring(0, 4)
                        val month = dateStr.substring(4, 6)
                        val day = dateStr.substring(6, 8)
                        val formattedDate = "$day/$month/$year"
                        
                        val hour = timeStr.substring(0, 2)
                        val minute = timeStr.substring(2, 4)
                        val second = timeStr.substring(4, 6)
                        val formattedTime = "$hour:$minute:$second"
                        
                        BackupFileInfo(fileName, formattedDate, formattedTime)
                    } else {
                        val lastModifiedIndex = it.getColumnIndex("last_modified")
                        val lastModified = if (lastModifiedIndex >= 0) {
                            it.getLong(lastModifiedIndex)
                        } else {
                            System.currentTimeMillis()
                        }
                        
                        val date = Date(lastModified)
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))
                        
                        BackupFileInfo(fileName, dateFormat.format(date), timeFormat.format(date))
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
        fun restoreBackupFromUri(uri: Uri) {
        scope.launch {
            isLoadingRestore = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    // Preserve original filename with extension for proper format detection
                    val originalFileName = extractBackupFileInfo(uri)?.fileName ?: "temp_backup"
                    val tempFile = File(context.cacheDir, originalFileName)
                    tempFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    
                    val result = backupService.restoreBackup(tempFile.absolutePath)
                    result.fold(
                        onSuccess = {
                            displayMessage("Backup restaurado com sucesso!")
                            tempFile.delete() 
                        },
                        onFailure = { error ->
                            displayMessage("Erro ao restaurar backup: ${error.message}")
                            tempFile.delete()
                        }
                    )
                } else {
                    displayMessage("Erro ao ler arquivo selecionado")
                }
            } catch (e: Exception) {
                displayMessage("Erro ao processar arquivo: ${e.message}")
            } finally {
                isLoadingRestore = false
            }
        }
    }
    
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                selectedBackupUri = uri
                backupFileInfo = extractBackupFileInfo(uri)
                showRestoreConfirmation = true
            }
        }
    }
    
    fun createBackupToDownloads() {
        scope.launch {
            isLoadingBackup = true
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
                isLoadingBackup = false
            }
        }
    }

    fun createBinaryBackup() {
        scope.launch {
            isLoadingBackup = true
            try {
                val result = backupService.createBinaryBackup()
                result.fold(
                    onSuccess = { filePath ->
                        displayMessage("🚀 Backup BINÁRIO criado com sucesso! Arquivo: ${File(filePath).name}")
                    },
                    onFailure = { error ->
                        displayMessage("❌ Erro ao criar backup binário: ${error.message}")
                    }
                )
            } finally {
                isLoadingBackup = false
            }
        }
    }

    fun createBackupToCloud() {
        scope.launch {
            isLoadingBackup = true
            try {
                val result = backupService.createBackupToCloud()
                result.fold(
                    onSuccess = { message ->
                        displayMessage(message)
                    },
                    onFailure = { error ->
                        displayMessage("Erro ao fazer backup na nuvem: ${error.message}")
                    }
                )
            } finally {
                isLoadingBackup = false
            }
        }
    }
    
    fun showBackupMethodSelection() {
        showBackupMethodDialog = true
    }
    
    fun executeRestoreWithProgress(uri: Uri) {
        scope.launch {
            showRestoreProgress = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    // Preserve original filename with extension for proper format detection
                    val originalFileName = extractBackupFileInfo(uri)?.fileName ?: "temp_backup"
                    val tempFile = File(context.cacheDir, originalFileName)
                    tempFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    
                    val result = backupService.restoreBackup(tempFile.absolutePath)
                    result.fold(
                        onSuccess = {
                            tempFile.delete() 
                            showRestoreProgress = false
                            displayMessage("Backup restaurado com sucesso!")
                            showRestartAlert = true
                        },
                        onFailure = { error ->
                            tempFile.delete() 
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
    
    fun restartApplication() {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName = intent?.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
            Process.killProcess(Process.myPid())
        } catch (e: Exception) {
            Process.killProcess(Process.myPid())
        }
    }
    
    fun startRestore() {
        showRestoreMethodDialog = true
    }
    
    fun startLocalRestore() {
        val restoreIntent = backupService.createRestoreIntent()
        restoreLauncher.launch(restoreIntent)
    }
    
    fun parseOnlineBackupFileName(fileName: String): OnlineBackupFile {
        return try {
            // Formato esperado: codigo_cliente_localizacao_id_YYYYMMDD_HHMMSS.json
            val pattern = Regex("(.+)_(\\d{8})_(\\d{6})\\.json")
            val match = pattern.find(fileName)
            
            if (match != null) {
                val prefix = match.groupValues[1]
                val dateStr = match.groupValues[2]
                val timeStr = match.groupValues[3]
                
                val year = dateStr.substring(0, 4)
                val month = dateStr.substring(4, 6)
                val day = dateStr.substring(6, 8)
                val formattedDate = "$day/$month/$year"
                
                val hour = timeStr.substring(0, 2)
                val minute = timeStr.substring(2, 4)
                val second = timeStr.substring(4, 6)
                val formattedTime = "$hour:$minute:$second"
                
                OnlineBackupFile(
                    fileName = fileName,
                    displayName = "Backup $formattedDate $formattedTime",
                    fileDate = formattedDate,
                    fileTime = formattedTime
                )
            } else {
                OnlineBackupFile(
                    fileName = fileName,
                    displayName = fileName,
                    fileDate = "Data desconhecida",
                    fileTime = "Hora desconhecida"
                )
            }
        } catch (e: Exception) {
            OnlineBackupFile(
                fileName = fileName,
                displayName = fileName,
                fileDate = "Data desconhecida",
                fileTime = "Hora desconhecida"
            )
        }
    }
    
    fun startOnlineRestore() {
        scope.launch {
            isLoadingRestore = true
            try {
                val configuracoesDao = ConfiguracoesDao()
                val configuracoes = configuracoesDao.getConfiguracoes()
                val appPreferences = AppPreferences(context)
                
                if (configuracoes?.localizacaoId.isNullOrBlank()) {
                    displayMessage("Localização ID não configurada. Configure nas configurações primeiro.")
                    return@launch
                }
                
                // Log para debug
                android.util.Log.d("BackupTab", "🔍 Buscando backups online...")
                android.util.Log.d("BackupTab", "📋 Entidade: ${configuracoes.entidadeId}")
                android.util.Log.d("BackupTab", "📋 Localização: ${configuracoes.localizacaoId}")
                
                val apiService = RetrofitClient.instance
                
                android.util.Log.d("BackupTab", "📤 Enviando requisição para localização: ${configuracoes.localizacaoId}")
                
                val response = apiService.listBackupsFromCloud(configuracoes.entidadeId, configuracoes.localizacaoId)
                
                android.util.Log.d("BackupTab", "📥 Resposta recebida: ${response.code()} - ${response.message()}")
                
                if (response.isSuccessful) {
                    val backupList = response.body()
                    android.util.Log.d("BackupTab", "📋 Lista de backups: $backupList")
                    
                    if (backupList != null && backupList.arquivo.isNotEmpty()) {
                        android.util.Log.d("BackupTab", "📁 ${backupList.arquivo.size} backups encontrados")
                        backupList.arquivo.forEach { fileName ->
                            android.util.Log.d("BackupTab", "   📄 $fileName")
                        }
                        
                        val onlineFiles = backupList.arquivo.map { fileName ->
                            parseOnlineBackupFileName(fileName)
                        }
                        onlineBackupFiles = onlineFiles
                        showOnlineBackupList = true
                        android.util.Log.d("BackupTab", "✅ Modal de lista de backups será exibido")
                    } else {
                        android.util.Log.d("BackupTab", "⚠️ Nenhum backup encontrado ou lista vazia")
                        displayMessage("Nenhum backup encontrado na nuvem para esta localização.")
                    }
                } else {
                    android.util.Log.e("BackupTab", "❌ Erro na resposta: ${response.code()} - ${response.message()}")
                    displayMessage("Erro ao buscar backups online: ${response.message()}")
                }
            } catch (e: Exception) {
                displayMessage("Erro ao conectar com o servidor: ${e.message}")
            } finally {
                isLoadingRestore = false
            }
        }
    }
    
    fun downloadAndRestoreOnlineBackup(onlineBackup: OnlineBackupFile) {
        scope.launch {
            showRestoreProgress = true
            try {
                val configuracoesDao = ConfiguracoesDao()
                val configuracoes = configuracoesDao.getConfiguracoes()
                val appPreferences = AppPreferences(context)
                
                if (configuracoes == null) {
                    displayMessage("Configurações não encontradas.")
                    return@launch
                }
                
                val apiService = RetrofitClient.instance
                val response = apiService.downloadSpecificBackupFile(
                    configuracoes.entidadeId, 
                    configuracoes.localizacaoId, 
                    onlineBackup.fileName
                )
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val tempFile = File(context.cacheDir, "temp_online_backup")
                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().copyTo(output)
                        }
                        
                        // Log para debug
                        android.util.Log.d("BackupTab", "📁 Arquivo baixado: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                        
                        // Ler apenas os primeiros 200 caracteres sem carregar o arquivo inteiro
                        val firstChars = try {
                            tempFile.inputStream().use { inputStream ->
                                val buffer = ByteArray(200)
                                val bytesRead = inputStream.read(buffer)
                                if (bytesRead > 0) {
                                    String(buffer, 0, bytesRead)
                                } else {
                                    "Arquivo vazio"
                                }
                            }
                        } catch (e: Exception) {
                            "Erro ao ler arquivo: ${e.message}"
                        }
                        android.util.Log.d("BackupTab", "📄 Primeiros 200 caracteres: $firstChars")
                        
                        val result = backupService.restoreBackup(tempFile.absolutePath)
                        result.fold(
                            onSuccess = {
                                tempFile.delete()
                                showRestoreProgress = false
                                displayMessage("Backup restaurado com sucesso!")
                                showRestartAlert = true
                            },
                            onFailure = { error ->
                                tempFile.delete()
                                showRestoreProgress = false
                                displayMessage("Erro ao restaurar backup: ${error.message}")
                            }
                        )
                    } else {
                        showRestoreProgress = false
                        displayMessage("Erro ao baixar arquivo de backup")
                    }
                } else {
                    showRestoreProgress = false
                    displayMessage("Erro ao baixar backup: ${response.message()}")
                }
            } catch (e: Exception) {
                showRestoreProgress = false
                displayMessage("Erro ao processar backup online: ${e.message}")
            }
        }
    }
    
    fun testBackupSystem() {
        scope.launch {
            isLoadingBackup = true
            try {
                displayMessage("Iniciando teste do sistema de backup...")
                backupTestHelper.testBackupSystem()
                displayMessage("Teste concluído! Verifique os logs para detalhes.")
            } catch (e: Exception) {
                displayMessage("Erro no teste: ${e.message}")
            } finally {
                isLoadingBackup = false
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Backup/Restauração do Banco de Dados",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
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

                Button(
                    onClick = { showBackupMethodSelection() },
                    enabled = !isLoadingBackup,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF264064)
                    )
                ) {
                    if (isLoadingBackup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Gerar Backup")
                }
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
                    enabled = !isLoadingRestore,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF264064)
                    )
                ) {
                    if (isLoadingRestore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Restaurar Backup")
                }
            }
        }
        
        // ✅ NOVO: Seção de Limpeza de Cache
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Limpeza de Cache",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Limpa arquivos temporários, cache de imagens e dados desnecessários para liberar espaço de armazenamento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { 
                            scope.launch {
                                try {
                                    val cacheManager = CacheManager(context, AppPreferences(context))
                                    val result = cacheManager.performQuickCacheCleanup()
                                    result.fold(
                                        onSuccess = { message ->
                                            displayMessage("✅ $message")
                                        },
                                        onFailure = { error ->
                                            displayMessage("❌ Erro: ${error.message}")
                                        }
                                    )
                                } catch (e: Exception) {
                                    displayMessage("❌ Erro: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Limpeza Rápida")
                    }
                    
                    Button(
                        onClick = { 
                            scope.launch {
                                try {
                                    val cacheManager = CacheManager(context, AppPreferences(context))
                                    val result = cacheManager.performCompleteCacheCleanup()
                                    result.fold(
                                        onSuccess = { message ->
                                            displayMessage("✅ $message")
                                        },
                                        onFailure = { error ->
                                            displayMessage("❌ Erro: ${error.message}")
                                        }
                                    )
                                } catch (e: Exception) {
                                    displayMessage("❌ Erro: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336)
                        )
                    ) {
                        Text("Limpeza Completa")
                    }
                }
            }
        }
                
    }
    
    // Modal de seleção de método de restauração
    if (showRestoreMethodDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreMethodDialog = false },
            title = {
                Text(
                    text = "Como você quer restaurar?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        onClick = {
                            showRestoreMethodDialog = false
                            startLocalRestore()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Local",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Selecionar arquivo dos Meus Arquivos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    
                    Card(
                        onClick = {
                            showRestoreMethodDialog = false
                            startOnlineRestore()
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
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(0xFF264064)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Online",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Baixar backup da nuvem",
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
                    onClick = { showRestoreMethodDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            onClick = {
                                showBackupMethodDialog = false
                                createBackupToDownloads()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Local (JSON)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Backup tradicional JSON",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        Card(
                            onClick = {
                                showBackupMethodDialog = false
                                createBackupToCloud()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color(0xFF264064)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Online",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Upload para nuvem",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // NOVO: Backup Binário
                    Card(
                        onClick = {
                            showBackupMethodDialog = false
                            createBinaryBackup()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2E7D32)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "🚀 Binário (.pb) - RECOMENDADO",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "10x mais rápido | 5x menor | Suporta arquivos enormes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
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
    
    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { 
                showRestoreConfirmation = false
                selectedBackupUri = null
                backupFileInfo = null
            },
            title = {
                Text(
                    text = "Confirmar Restauração",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (backupFileInfo != null) {
                        Text(
                            text = "Você deseja restaurar o backup \"${backupFileInfo!!.fileName}\" do dia ${backupFileInfo!!.fileDate} feito às ${backupFileInfo!!.fileTime}?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Você deseja realmente restaurar o backup?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Esta ação irá substituir todos os dados atuais do sistema e não pode ser desfeita.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        containerColor = Color(0xFF264064)
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
                        backupFileInfo = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    if (showRestoreProgress) {
        AlertDialog(
            onDismissRequest = { },
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
                        containerColor = Color(0xFF264064)
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
    
    // Modal de erro de USB (COMENTADO)
    /*
    if (showUSBErrorDialog) {
        AlertDialog(
            onDismissRequest = { showUSBErrorDialog = false },
            title = {
                Text(
                    text = "Pen Drive USB Não Encontrado",
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
                        imageVector = Icons.Default.UsbOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = usbErrorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Para usar o backup direto no pen drive, conecte um pen drive USB ao tablet e tente novamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showUSBErrorDialog = false
                        // Atualizar status do USB
                        usbStorageInfo = USBUtils.getUSBStorageInfo(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF264064)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificar Novamente")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUSBErrorDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    */
    
    // Modal de lista de backups online
    if (showOnlineBackupList) {
        AlertDialog(
            onDismissRequest = { 
                showOnlineBackupList = false
                onlineBackupFiles = emptyList()
            },
            title = {
                Text(
                    text = "Backups Disponíveis na Nuvem",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (onlineBackupFiles.isEmpty()) {
                    Text(
                        text = "Nenhum backup encontrado.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(onlineBackupFiles) { backupFile ->
                            Card(
                                onClick = {
                                    selectedOnlineBackup = backupFile
                                    showOnlineBackupList = false
                                    downloadAndRestoreOnlineBackup(backupFile)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = backupFile.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Data: ${backupFile.fileDate} às ${backupFile.fileTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Arquivo: ${backupFile.fileName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showOnlineBackupList = false
                        onlineBackupFiles = emptyList()
                    }
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

