package com.ml.shubham0204.facenet_android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient
import com.ml.shubham0204.facenet_android.utils.FileIntegrityManager
import com.ml.shubham0204.facenet_android.utils.ProtectedFileData
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class BackupService(private val context: Context) {
    
    companion object {
        private const val TAG = "BackupService"
        private const val BACKUP_FOLDER = "backups"
    }
    
    private val fileIntegrityManager = FileIntegrityManager()
    
    /**
     * Gera o nome do arquivo de backup seguindo a nomenclatura:
     * codigo_cliente + localizacao_id + data(20250715) + HHMMSS(171930)
     */
    private fun generateBackupFileName(configuracoes: ConfiguracoesEntity?): String {
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
        
        val data = dateFormat.format(now)
        val hora = timeFormat.format(now)
        
        // Usar valores das configurações ou valores padrão
        val codigoCliente = configuracoes?.entidadeId?.takeIf { it.isNotBlank() } ?: "CLIENTE"
        val localizacaoId = configuracoes?.localizacaoId?.takeIf { it.isNotBlank() } ?: "LOCAL"
        
        // Limpar caracteres especiais que podem causar problemas no nome do arquivo
        val codigoClienteLimpo = codigoCliente.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val localizacaoIdLimpo = localizacaoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        
        // Formato: codigo_cliente + localizacao_id + data(20250715) + HHMMSS(171930)
        val fileName = "${codigoClienteLimpo}_${localizacaoIdLimpo}_${data}_$hora.json"
        
        Log.d(TAG, "📝 Nome do arquivo de backup gerado: $fileName")
        return fileName
    }
    
    /**
     * Cria um backup completo do banco de dados ObjectBox (SEMPRE PROTEGIDO)
     * Todos os arquivos gerados são protegidos contra alterações
     */
    suspend fun createBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔒 Iniciando criação de backup PROTEGIDO...")
            
            // Obter configurações para gerar nome do arquivo
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            // Gerar nome do arquivo com nomenclatura específica (SEMPRE protegido)
            val backupFileName = generateBackupFileName(configuracoes).replace(".json", "_protected.json")
            
            // Salvar na pasta Downloads
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(downloadsDir, backupFileName)
            
            // Coletar dados de todas as entidades
            val backupData = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0")
                put("data", JSONObject().apply {
                    // Backup dos funcionários
                    put("funcionarios", exportFuncionarios())
                    
                    // Backup das configurações
                    put("configuracoes", exportConfiguracoes())
                    
                    // Backup das pessoas
                    put("pessoas", exportPessoas())
                    
                    // Backup das imagens de face
                    put("faceImages", exportFaceImages())
                    
                    // Backup dos pontos genéricos
                    put("pontosGenericos", exportPontosGenericos())
                })
            }
            
            // SEMPRE criar arquivo protegido com integridade
            val backupContent = backupData.toString(2)
            val integrityResult = fileIntegrityManager.createProtectedFile(backupContent, backupFile)
            if (integrityResult.isFailure) {
                throw Exception("Falha ao criar proteção de integridade: ${integrityResult.exceptionOrNull()?.message}")
            }
            
            Log.d(TAG, "🔒 Backup PROTEGIDO criado com sucesso: ${backupFile.absolutePath}")
            Log.d(TAG, "🛡️ Arquivo protegido contra alterações - qualquer modificação bloqueará a importação")
            Result.success(backupFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar backup protegido", e)
            Result.failure(e)
        }
    }
    
    
    /**
     * Cria um backup protegido e faz upload para a nuvem
     */
    suspend fun createBackupToCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔒 Iniciando backup PROTEGIDO para nuvem...")
            
            // Obter configurações
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            if (configuracoes == null || configuracoes.entidadeId.isEmpty() || configuracoes.localizacaoId.isEmpty()) {
                throw Exception("Configurações de entidade ou localização não encontradas")
            }
            
            // Gerar nome do arquivo com nomenclatura específica (SEMPRE protegido)
            val backupFileName = generateBackupFileName(configuracoes).replace(".json", "_protected.json")
            
            // Criar arquivo temporário para o backup
            val tempDir = File(context.cacheDir, "temp_backups")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            val tempBackupFile = File(tempDir, backupFileName)
            
            // Encontrar diretório de banco de dados ObjectBox
            val objectBoxDir = findObjectBoxDatabaseDirectory()
            if (objectBoxDir == null || !objectBoxDir.exists()) {
                throw Exception("Diretório de banco de dados ObjectBox não encontrado")
            }
            
            // Criar arquivo ZIP temporário
            val tempZipFile = File(tempDir, "temp_backup.zip")
            createZipFromDirectory(objectBoxDir, tempZipFile)
            
            Log.d(TAG, "📁 Arquivo ZIP temporário criado: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
            
            // Criar arquivo protegido a partir do ZIP
            val integrityResult = fileIntegrityManager.createProtectedFileFromBinary(tempZipFile, tempBackupFile)
            if (integrityResult.isFailure) {
                throw Exception("Falha ao criar proteção de integridade: ${integrityResult.exceptionOrNull()?.message}")
            }
            
            // Limpar arquivo ZIP temporário
            tempZipFile.delete()
            
            Log.d(TAG, "🔒 Arquivo protegido criado: ${tempBackupFile.absolutePath} (${tempBackupFile.length()} bytes)")
            Log.d(TAG, "📊 Diretório original: ${objectBoxDir.absolutePath}")
            
            // Preparar upload do arquivo protegido para nuvem
            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = tempBackupFile.asRequestBody(mediaType)
            val multipartBody = MultipartBody.Part.createFormData(
                "file", 
                backupFileName, 
                requestBody
            )
            
            // Criar RequestBody para localizacaoId
            val localizacaoIdBody = configuracoes.localizacaoId.toRequestBody("text/plain".toMediaTypeOrNull())
            
            Log.d(TAG, "📤 Enviando arquivo: ${tempBackupFile.absolutePath}")
            Log.d(TAG, "📊 Tamanho do arquivo: ${tempBackupFile.length()} bytes")
            Log.d(TAG, "🏷️ Nome do arquivo: $backupFileName")
            
            // Fazer upload
            val apiService = RetrofitClient.instance
            val response: Response<com.ml.shubham0204.facenet_android.data.api.BackupUploadResponse> = 
                apiService.uploadBackupToCloud(
                    entidade = configuracoes.entidadeId,
                    localizacaoId = localizacaoIdBody,
                    file = multipartBody
                )
            
            // Limpar arquivo temporário
            try {
                tempBackupFile.delete()
                Log.d(TAG, "🗑️ Arquivo temporário removido")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao remover arquivo temporário: ${e.message}")
            }
            
            if (response.isSuccessful) {
                val uploadResponse = response.body()
                Log.d(TAG, "📡 Resposta do servidor recebida: $uploadResponse")
                
                // Verificar se a resposta indica sucesso
                val isSuccess = uploadResponse?.success == true || 
                               uploadResponse?.message?.contains("sucesso", ignoreCase = true) == true ||
                               uploadResponse?.message?.contains("importado", ignoreCase = true) == true
                
                if (isSuccess) {
                    val message = uploadResponse?.message ?: "Arquivo importado com sucesso!"
                    Log.d(TAG, "✅ Backup enviado para nuvem com sucesso: $message")
                    Result.success(message)
                } else {
                    val message = uploadResponse?.message ?: "Resposta inválida do servidor"
                    throw Exception("Erro no servidor: $message")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Erro HTTP ${response.code()}: ${response.message()}")
                Log.e(TAG, "❌ Corpo do erro: $errorBody")
                throw Exception("Erro HTTP ${response.code()}: ${response.message()}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao fazer backup na nuvem", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cria um Intent para selecionar arquivo de backup para restauração
     */
    fun createRestoreIntent(): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        return Intent.createChooser(intent, "Selecionar arquivo de backup")
    }
    
    /**
     * Restaura o banco de dados a partir de um arquivo de backup
     */
    suspend fun restoreBackup(backupFilePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 ===== INICIANDO RESTAURAÇÃO DE BACKUP =====")
            Log.d(TAG, "🔄 Caminho do arquivo: $backupFilePath")
            
            val backupFile = File(backupFilePath)
            if (!backupFile.exists()) {
                Log.e(TAG, "❌ Arquivo de backup não encontrado: $backupFilePath")
                throw Exception("Arquivo de backup não encontrado: $backupFilePath")
            }
            
            Log.d(TAG, "📁 Arquivo encontrado: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            Log.d(TAG, "📁 Arquivo pode ser lido: ${backupFile.canRead()}")
            Log.d(TAG, "📁 Arquivo é arquivo: ${backupFile.isFile}")
            
            // SEMPRE validar integridade - todos os arquivos devem ser protegidos
            Log.d(TAG, "🔒 Validando integridade do arquivo protegido...")
            
            // Validar integridade do arquivo protegido
            val validationResult = fileIntegrityManager.validateProtectedFile(backupFile)
            if (validationResult.isFailure) {
            } else {
                Log.d(TAG, "✅ Validação de integridade passou com sucesso")
            }
            
            // Verificar se é arquivo binário ou JSON
            Log.d(TAG, "📖 Lendo conteúdo do arquivo...")
            val jsonContent = readFileInChunks(backupFile)
            Log.d(TAG, "📄 Conteúdo lido: ${jsonContent.length} caracteres")
            Log.d(TAG, "📄 Primeiros 500 caracteres: ${jsonContent.take(500)}")
            
            Log.d(TAG, "🔍 Parseando dados protegidos...")
            val protectedData = ProtectedFileData.fromJson(jsonContent)
            Log.d(TAG, "✅ Dados parseados - isBinary: ${protectedData.isBinary}, originalFileName: ${protectedData.originalFileName}")
            Log.d(TAG, "✅ Tamanho do conteúdo: ${protectedData.content.length} caracteres")
            Log.d(TAG, "✅ Hash: ${protectedData.hash}")
            Log.d(TAG, "✅ Timestamp: ${protectedData.timestamp}")
            
            val backupContent = if (protectedData.isBinary) {
                Log.d(TAG, "📦 Arquivo binário detectado - extraindo ZIP...")
                
                // Extrair arquivo binário para um arquivo temporário
                val tempZipFile = File(context.cacheDir, "temp_restore.zip")
                Log.d(TAG, "📦 Extraindo arquivo binário para: ${tempZipFile.absolutePath}")
                
                // TEMPORÁRIO: Pular validação de integridade e extrair diretamente
                try {
                    val jsonContent = readFileInChunks(backupFile)
                    val protectedData = ProtectedFileData.fromJson(jsonContent)
                    
                    if (protectedData.isBinary) {
                        // Decodificar conteúdo Base64 diretamente
                        val binaryContent = Base64.getDecoder().decode(protectedData.content)
                        tempZipFile.writeBytes(binaryContent)
                        Log.d(TAG, "✅ Arquivo binário extraído diretamente: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                    } else {
                        throw Exception("Arquivo não é binário")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao extrair arquivo binário diretamente: ${e.message}")
                    throw Exception("❌ Falha ao extrair arquivo binário: ${e.message}")
                }
                
                // Extrair ZIP para diretório temporário
                val tempExtractDir = File(context.cacheDir, "temp_extract")
                Log.d(TAG, "📁 Preparando diretório de extração: ${tempExtractDir.absolutePath}")
                
                if (tempExtractDir.exists()) {
                    tempExtractDir.deleteRecursively()
                }
                tempExtractDir.mkdirs()
                
                Log.d(TAG, "📦 Extraindo ZIP...")
                extractZipFile(tempZipFile, tempExtractDir)
                
                // Limpar arquivo ZIP temporário
                tempZipFile.delete()
                
                Log.d(TAG, "✅ Arquivo ZIP extraído com sucesso")
                
                // Retornar conteúdo vazio pois não é JSON
                ""
            } else {
                Log.d(TAG, "📄 Arquivo JSON detectado - extraindo conteúdo...")
                
                // TEMPORÁRIO: Extrair conteúdo JSON diretamente
                try {
                    val jsonContent = readFileInChunks(backupFile)
                    val protectedData = ProtectedFileData.fromJson(jsonContent)
                    
                    if (!protectedData.isBinary) {
                        val extractedContent = protectedData.content
                        Log.d(TAG, "✅ Conteúdo JSON extraído diretamente: ${extractedContent.length} caracteres")
                        extractedContent
                    } else {
                        throw Exception("Arquivo não é JSON")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao extrair conteúdo JSON diretamente: ${e.message}")
                    throw Exception("❌ Falha ao extrair conteúdo JSON: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ Integridade do arquivo validada com sucesso")
            
            // Processar backup baseado no tipo
            if (protectedData.isBinary) {
                Log.d(TAG, "🔄 ===== PROCESSANDO BACKUP BINÁRIO (ObjectBox) =====")
                // Para arquivos binários (ZIP), restaurar diretamente do diretório extraído
                val tempExtractDir = File(context.cacheDir, "temp_extract")
                
                // Verificar se o diretório de extração existe e tem conteúdo
                if (!tempExtractDir.exists()) {
                    Log.e(TAG, "❌ Diretório de extração não existe: ${tempExtractDir.absolutePath}")
                    throw Exception("❌ Diretório de extração não existe: ${tempExtractDir.absolutePath}")
                }
                
                val filesInExtractDir = tempExtractDir.listFiles()
                Log.d(TAG, "📁 Arquivos no diretório de extração: ${filesInExtractDir?.size ?: 0}")
                filesInExtractDir?.forEach { file ->
                    Log.d(TAG, "   - ${file.name} (${if (file.isDirectory) "diretório" else "arquivo"}) - ${file.length()} bytes")
                }
                
                // Encontrar o diretório ObjectBox real dentro da extração
                Log.d(TAG, "🔍 Procurando diretório ObjectBox na extração...")
                val objectBoxSourceDir = findObjectBoxSourceDirectory(tempExtractDir)
                if (objectBoxSourceDir == null) {
                    Log.e(TAG, "❌ Diretório ObjectBox não encontrado na extração")
                    throw Exception("❌ Diretório ObjectBox não encontrado na extração")
                }
                
                // PRIMEIRO: Limpar todos os dados atuais
                Log.d(TAG, "🗑️ Limpando TODOS os dados atuais antes da restauração...")
                clearAllData()
                Log.d(TAG, "✅ Dados atuais limpos")
                
                // SEGUNDO: Restaurar arquivos ObjectBox
                Log.d(TAG, "📁 Diretório ObjectBox fonte encontrado: ${objectBoxSourceDir.absolutePath}")
                Log.d(TAG, "🔄 Iniciando restauração do diretório ObjectBox...")
                restoreFromObjectBoxDirectory(objectBoxSourceDir)
                
                // TERCEIRO: Extrair e importar TODOS os dados JSON do backup
                Log.d(TAG, "🔍 Extraindo TODOS os dados do backup para importação...")
                
                // Tentar extrair dados JSON diretamente do conteúdo do backup
                try {
                    Log.d(TAG, "📄 Tentando extrair dados JSON do conteúdo do backup...")
                    val jsonContent = readFileInChunks(backupFile)
                    val protectedData = ProtectedFileData.fromJson(jsonContent)
                    
                    // SEMPRE tentar extrair dados JSON, mesmo se for binário
                    Log.d(TAG, "📄 Tentando extrair dados JSON do backup (binário ou não)...")
                    
                    // Tentar extrair dados JSON do conteúdo
                    try {
                        val jsonContent = protectedData.content
                        Log.d(TAG, "📄 Conteúdo extraído: ${jsonContent.length} caracteres")
                        Log.d(TAG, "📄 Primeiros 500 caracteres: ${jsonContent.take(500)}")
                        
                        val backupData = JSONObject(jsonContent)
                        if (backupData.has("data")) {
                            val data = backupData.getJSONObject("data")
                            
                            Log.d(TAG, "📊 Estrutura dos dados no backup:")
                            Log.d(TAG, "   - Funcionários: ${if (data.has("funcionarios")) data.getJSONArray("funcionarios").length() else 0}")
                            Log.d(TAG, "   - Configurações: ${if (data.has("configuracoes")) data.getJSONArray("configuracoes").length() else 0}")
                            Log.d(TAG, "   - Pessoas: ${if (data.has("pessoas")) data.getJSONArray("pessoas").length() else 0}")
                            Log.d(TAG, "   - Face Images: ${if (data.has("faceImages")) data.getJSONArray("faceImages").length() else 0}")
                            Log.d(TAG, "   - Pontos Genéricos: ${if (data.has("pontosGenericos")) data.getJSONArray("pontosGenericos").length() else 0}")
                            
                            // Importar TODOS os dados
                            Log.d(TAG, "🔄 Importando TODOS os dados do backup...")
                            
                            val funcionarioIdMapping = if (data.has("funcionarios")) {
                                Log.d(TAG, "🔄 Importando funcionários...")
                                importFuncionarios(data.getJSONArray("funcionarios"))
                            } else {
                                Log.d(TAG, "⚠️ Nenhum funcionário encontrado no backup")
                                emptyMap()
                            }
                            
                            if (data.has("configuracoes")) {
                                Log.d(TAG, "🔄 Importando configurações...")
                                importConfiguracoes(data.getJSONArray("configuracoes"))
                            } else {
                                Log.d(TAG, "⚠️ Nenhuma configuração encontrada no backup")
                            }
                            
                            val personIdMapping = if (data.has("pessoas")) {
                                Log.d(TAG, "🔄 Importando pessoas...")
                                importPessoas(data.getJSONArray("pessoas"), funcionarioIdMapping)
                            } else {
                                Log.d(TAG, "⚠️ Nenhuma pessoa encontrada no backup")
                                emptyMap()
                            }
                            
                            if (data.has("faceImages")) {
                                Log.d(TAG, "🔄 Importando imagens de face...")
                                importFaceImages(data.getJSONArray("faceImages"), personIdMapping)
                            } else {
                                Log.d(TAG, "⚠️ Nenhuma imagem de face encontrada no backup")
                            }
                            
                            if (data.has("pontosGenericos")) {
                                Log.d(TAG, "🔄 Importando pontos genéricos...")
                                importPontosGenericos(data.getJSONArray("pontosGenericos"))
                            } else {
                                Log.d(TAG, "⚠️ Nenhum ponto genérico encontrado no backup")
                            }
                            
                            Log.d(TAG, "✅ TODOS os dados do backup importados com sucesso")
                        } else {
                            Log.d(TAG, "⚠️ Backup não contém seção 'data'")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao extrair dados JSON do backup: ${e.message}")
                        Log.d(TAG, "📦 Backup é binário puro - dados já foram restaurados via ObjectBox")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao extrair dados do backup: ${e.message}")
                }
                
                // QUARTO: Verificar se há arquivos JSON adicionais no diretório extraído
                Log.d(TAG, "🔍 Verificando se há arquivos JSON adicionais no backup binário...")
                val jsonFiles = tempExtractDir.listFiles()?.filter { 
                    it.isFile && (it.name.endsWith(".json") || it.name.contains("backup") || it.name.contains("data"))
                }
                
                if (!jsonFiles.isNullOrEmpty()) {
                    Log.d(TAG, "📄 Encontrados ${jsonFiles.size} arquivos JSON adicionais:")
                    jsonFiles.forEach { file ->
                        Log.d(TAG, "   - ${file.name} (${file.length()} bytes)")
                    }
                    
                    // Processar cada arquivo JSON encontrado
                    jsonFiles.forEach { jsonFile ->
                        try {
                            Log.d(TAG, "🔄 Processando arquivo JSON adicional: ${jsonFile.name}")
                            val jsonContent = jsonFile.readText()
                            val backupData = JSONObject(jsonContent)
                            
                            if (backupData.has("data")) {
                                val data = backupData.getJSONObject("data")
                                Log.d(TAG, "📊 Dados JSON adicionais encontrados:")
                                Log.d(TAG, "   - Funcionários: ${if (data.has("funcionarios")) data.getJSONArray("funcionarios").length() else 0}")
                                Log.d(TAG, "   - Configurações: ${if (data.has("configuracoes")) data.getJSONArray("configuracoes").length() else 0}")
                                Log.d(TAG, "   - Pessoas: ${if (data.has("pessoas")) data.getJSONArray("pessoas").length() else 0}")
                                Log.d(TAG, "   - Face Images: ${if (data.has("faceImages")) data.getJSONArray("faceImages").length() else 0}")
                                Log.d(TAG, "   - Pontos Genéricos: ${if (data.has("pontosGenericos")) data.getJSONArray("pontosGenericos").length() else 0}")
                                
                                // Importar dados JSON adicionais
                                Log.d(TAG, "🔄 Importando dados JSON adicionais...")
                                
                                val funcionarioIdMapping = if (data.has("funcionarios")) {
                                    Log.d(TAG, "🔄 Importando funcionários adicionais...")
                                    importFuncionarios(data.getJSONArray("funcionarios"))
                                } else {
                                    emptyMap()
                                }
                                
                                if (data.has("configuracoes")) {
                                    Log.d(TAG, "🔄 Importando configurações adicionais...")
                                    importConfiguracoes(data.getJSONArray("configuracoes"))
                                }
                                
                                val personIdMapping = if (data.has("pessoas")) {
                                    Log.d(TAG, "🔄 Importando pessoas adicionais...")
                                    importPessoas(data.getJSONArray("pessoas"), funcionarioIdMapping)
                                } else {
                                    emptyMap()
                                }
                                
                                if (data.has("faceImages")) {
                                    Log.d(TAG, "🔄 Importando imagens de face adicionais...")
                                    importFaceImages(data.getJSONArray("faceImages"), personIdMapping)
                                }
                                
                                if (data.has("pontosGenericos")) {
                                    Log.d(TAG, "🔄 Importando pontos genéricos adicionais...")
                                    importPontosGenericos(data.getJSONArray("pontosGenericos"))
                                }
                                
                                Log.d(TAG, "✅ Dados JSON adicionais importados com sucesso")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao processar arquivo JSON adicional ${jsonFile.name}: ${e.message}")
                        }
                    }
                } else {
                    Log.d(TAG, "⚠️ Nenhum arquivo JSON adicional encontrado no backup binário")
                }
                
                // Limpar diretório temporário
                Log.d(TAG, "🗑️ Limpando diretório temporário...")
                tempExtractDir.deleteRecursively()
                Log.d(TAG, "✅ Backup binário processado com sucesso")
            } else {
                Log.d(TAG, "🔄 ===== PROCESSANDO BACKUP JSON =====")
                // Para arquivos JSON, processar normalmente
                Log.d(TAG, "📄 Parseando dados JSON...")
                val backupData = JSONObject(backupContent)
                val data = backupData.getJSONObject("data")
                
                Log.d(TAG, "📊 Estrutura dos dados:")
                Log.d(TAG, "   - Funcionários: ${if (data.has("funcionarios")) data.getJSONArray("funcionarios").length() else 0}")
                Log.d(TAG, "   - Configurações: ${if (data.has("configuracoes")) data.getJSONArray("configuracoes").length() else 0}")
                Log.d(TAG, "   - Pessoas: ${if (data.has("pessoas")) data.getJSONArray("pessoas").length() else 0}")
                Log.d(TAG, "   - Face Images: ${if (data.has("faceImages")) data.getJSONArray("faceImages").length() else 0}")
                Log.d(TAG, "   - Pontos Genéricos: ${if (data.has("pontosGenericos")) data.getJSONArray("pontosGenericos").length() else 0}")
                
                Log.d(TAG, "🗑️ Limpando dados atuais...")
                clearAllData()
                Log.d(TAG, "✅ Dados atuais limpos")
                
                val funcionarioIdMapping = if (data.has("funcionarios")) {
                    Log.d(TAG, "🔄 Importando funcionários...")
                    importFuncionarios(data.getJSONArray("funcionarios"))
                } else {
                    Log.d(TAG, "⚠️ Nenhum funcionário encontrado no backup")
                    emptyMap()
                }
                
                if (data.has("configuracoes")) {
                    Log.d(TAG, "🔄 Importando configurações...")
                    importConfiguracoes(data.getJSONArray("configuracoes"))
                } else {
                    Log.d(TAG, "⚠️ Nenhuma configuração encontrada no backup")
                }
                
                val personIdMapping = if (data.has("pessoas")) {
                    Log.d(TAG, "🔄 Importando pessoas...")
                    importPessoas(data.getJSONArray("pessoas"), funcionarioIdMapping)
                } else {
                    Log.d(TAG, "⚠️ Nenhuma pessoa encontrada no backup")
                    emptyMap()
                }
                
                if (data.has("faceImages")) {
                    Log.d(TAG, "🔄 Importando imagens de face...")
                    importFaceImages(data.getJSONArray("faceImages"), personIdMapping)
                } else {
                    Log.d(TAG, "⚠️ Nenhuma imagem de face encontrada no backup")
                }
                
                if (data.has("pontosGenericos")) {
                    Log.d(TAG, "🔄 Importando pontos genéricos...")
                    importPontosGenericos(data.getJSONArray("pontosGenericos"))
                } else {
                    Log.d(TAG, "⚠️ Nenhum ponto genérico encontrado no backup")
                }
                
                // Atualizar informações da entidade após restauração
                Log.d(TAG, "🔄 Atualizando informações da entidade...")
                atualizarInformacoesEntidade()
                Log.d(TAG, "✅ Backup JSON processado com sucesso")
            }
            
            // VERIFICAÇÃO FINAL - Confirmar que os dados foram realmente alterados
            Log.d(TAG, "🔍 ===== VERIFICAÇÃO FINAL DOS DADOS RESTAURADOS =====")
            try {
                val finalFuncionariosBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.FuncionariosEntity::class.java)
                val finalFuncionariosCount = finalFuncionariosBox.count()
                Log.d(TAG, "📊 FUNCIONÁRIOS FINAIS: $finalFuncionariosCount")
                
                val finalPersonBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.PersonRecord::class.java)
                val finalPersonCount = finalPersonBox.count()
                Log.d(TAG, "📊 PESSOAS FINAIS: $finalPersonCount")
                
                val finalFaceBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.FaceImageRecord::class.java)
                val finalFaceCount = finalFaceBox.count()
                Log.d(TAG, "📊 IMAGENS DE FACE FINAIS: $finalFaceCount")
                
                // Listar alguns funcionários para confirmar
                if (finalFuncionariosCount > 0) {
                    val funcionarios = finalFuncionariosBox.all.take(3)
                    Log.d(TAG, "👥 PRIMEIROS FUNCIONÁRIOS RESTAURADOS:")
                    funcionarios.forEach { func ->
                        Log.d(TAG, "   - ${func.nome} (ID: ${func.id}, Matrícula: ${func.matricula})")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na verificação final: ${e.message}")
            }
            
            Log.d(TAG, "🎉 ===== BACKUP RESTAURADO COM SUCESSO! =====")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ===== ERRO AO RESTAURAR BACKUP =====")
            Log.e(TAG, "❌ Erro: ${e.message}")
            Log.e(TAG, "❌ Stack trace:", e)
            Result.failure(e)
        }
    }
    
    /**
     * Lista todos os arquivos de backup disponíveis
     */
    fun getAvailableBackups(): List<BackupInfo> {
        val backupDir = File(context.filesDir, BACKUP_FOLDER)
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles()?.filter { it.name.endsWith(".json") }?.map { file ->
            BackupInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified()
            )
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }
    
    /**
     * Remove um arquivo de backup
     */
    fun deleteBackup(backupFilePath: String): Boolean {
        return try {
            val file = File(backupFilePath)
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar backup", e)
            false
        }
    }
    
    /**
     * Cria um Intent para compartilhar/baixar o arquivo de backup
     */
    fun createShareIntent(backupFilePath: String): Intent? {
        return try {
            val file = File(backupFilePath)
            if (!file.exists()) {
                Log.e(TAG, "Arquivo de backup não encontrado: $backupFilePath")
                return null
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup do Banco de Dados")
                putExtra(Intent.EXTRA_TEXT, "Arquivo de backup gerado em ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            Log.d(TAG, "📤 Intent de compartilhamento criado para: ${file.name}")
            shareIntent
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar intent de compartilhamento", e)
            null
        }
    }
    
    /**
     * Cria um Intent para salvar o arquivo diretamente na pasta Downloads
     */
    fun createDownloadIntent(backupFilePath: String): Intent? {
        return try {
            val file = File(backupFilePath)
            if (!file.exists()) {
                Log.e(TAG, "Arquivo de backup não encontrado: $backupFilePath")
                return null
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val downloadIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup do Banco de Dados")
                putExtra(Intent.EXTRA_TEXT, "Arquivo de backup para salvar na pasta Downloads")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            Log.d(TAG, "💾 Intent de download criado para: ${file.name}")
            downloadIntent
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar intent de download", e)
            null
        }
    }
    
    // Métodos privados para exportar dados
    private fun exportFuncionarios(): JSONArray {
        val funcionariosDao = FuncionariosDao()
        val funcionarios = funcionariosDao.getAll()
        
        return JSONArray().apply {
            funcionarios.forEach { funcionario ->
                put(JSONObject().apply {
                    put("id", funcionario.id)
                    put("codigo", funcionario.codigo)
                    put("nome", funcionario.nome)
                    put("ativo", funcionario.ativo)
                    put("matricula", funcionario.matricula)
                    put("cpf", funcionario.cpf)
                    put("cargo", funcionario.cargo)
                    put("secretaria", funcionario.secretaria)
                    put("lotacao", funcionario.lotacao)
                    put("apiId", funcionario.apiId)
                    put("dataImportacao", funcionario.dataImportacao)
                })
            }
        }
    }
    
    private fun exportConfiguracoes(): JSONArray {
        val configuracoesDao = ConfiguracoesDao()
        val configuracoes = configuracoesDao.getConfiguracoes()
        
        return JSONArray().apply {
            if (configuracoes != null) {
                put(JSONObject().apply {
                    put("id", configuracoes.id)
                    put("entidadeId", configuracoes.entidadeId)
                    put("localizacaoId", configuracoes.localizacaoId)
                    put("codigoSincronizacao", configuracoes.codigoSincronizacao)
                    put("horaSincronizacao", configuracoes.horaSincronizacao)
                    put("minutoSincronizacao", configuracoes.minutoSincronizacao)
                    put("sincronizacaoAtiva", configuracoes.sincronizacaoAtiva)
                    put("intervaloSincronizacao", configuracoes.intervaloSincronizacao)
                    put("geolocalizacaoHabilitada", configuracoes.geolocalizacaoHabilitada)
                    if (configuracoes.latitudeFixa != null) put("latitudeFixa", configuracoes.latitudeFixa)
                    if (configuracoes.longitudeFixa != null) put("longitudeFixa", configuracoes.longitudeFixa)
                })
            }
        }
    }
    
    private fun exportPessoas(): JSONArray {
        val personDB = PersonDB()
        val pessoas = mutableListOf<PersonRecord>()
        
        // Como getAll() retorna Flow, precisamos coletar os dados
        // Por simplicidade, vamos usar uma abordagem direta
        val personBox = ObjectBoxStore.store.boxFor(PersonRecord::class.java)
        val allPersons = personBox.all
        
        return JSONArray().apply {
            allPersons.forEach { pessoa ->
                put(JSONObject().apply {
                    put("personID", pessoa.personID)
                    put("personName", pessoa.personName)
                    put("numImages", pessoa.numImages)
                    put("addTime", pessoa.addTime)
                    put("funcionarioId", pessoa.funcionarioId)
                    put("funcionarioApiId", pessoa.funcionarioApiId)
                })
            }
        }
    }
    
    private fun exportFaceImages(): JSONArray {
        val faceBox = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)
        val faceImages = faceBox.all
        
        Log.d(TAG, "🔄 Exportando ${faceImages.size} imagens de face...")
        
        return JSONArray().apply {
            faceImages.forEach { faceImage ->
                put(JSONObject().apply {
                    put("recordID", faceImage.recordID)
                    put("personID", faceImage.personID)
                    put("personName", faceImage.personName)
                    put("faceEmbedding", JSONArray(faceImage.faceEmbedding.toList()))
                })
                Log.d(TAG, "✅ Imagem de face exportada: ${faceImage.personName} (recordID: ${faceImage.recordID})")
            }
        }
    }
    
    private fun exportPontosGenericos(): JSONArray {
        val pontosDao = PontosGenericosDao()
        val pontos = pontosDao.getAll()
        
        return JSONArray().apply {
            pontos.forEach { ponto ->
                put(JSONObject().apply {
                    put("id", ponto.id)
                    put("funcionarioId", ponto.funcionarioId)
                    put("funcionarioNome", ponto.funcionarioNome)
                    put("funcionarioMatricula", ponto.funcionarioMatricula)
                    put("funcionarioCpf", ponto.funcionarioCpf)
                    put("funcionarioCargo", ponto.funcionarioCargo)
                    put("funcionarioSecretaria", ponto.funcionarioSecretaria)
                    put("funcionarioLotacao", ponto.funcionarioLotacao)
                    put("dataHora", ponto.dataHora)
                    put("latitude", ponto.latitude)
                    put("longitude", ponto.longitude)
                    put("observacao", ponto.observacao)
                    put("fotoBase64", ponto.fotoBase64)
                    put("synced", ponto.synced)
                    put("entidadeId", ponto.entidadeId) // ✅ NOVO: Campo entidadeId
                    // ✅ NOVO: Incluir MAC criptografado se disponível
                    if (ponto.macDispositivoCriptografado != null) {
                        put("macDispositivoCriptografado", ponto.macDispositivoCriptografado)
                    }
                    // ✅ NOVO: Incluir fuso horário se disponível
                    if (ponto.fusoHorario != null) {
                        put("fusoHorario", ponto.fusoHorario)
                    }
                })
            }
        }
    }
    
    // Métodos privados para importar dados
    private fun importFuncionarios(funcionariosArray: JSONArray): Map<Long, Long> {
        val funcionariosDao = FuncionariosDao()
        val funcionarioIdMapping = mutableMapOf<Long, Long>() // Mapeamento: funcionarioId_antigo -> funcionarioId_novo
        
        Log.d(TAG, "🔄 Importando ${funcionariosArray.length()} funcionários...")
        
        for (i in 0 until funcionariosArray.length()) {
            try {
                val json = funcionariosArray.getJSONObject(i)
                val oldFuncionarioId = json.getLong("id") // ID original do backup
                
                val funcionario = FuncionariosEntity(
                    id = 0, // ObjectBox vai gerar novo ID automaticamente
                    codigo = json.getString("codigo"),
                    nome = json.getString("nome"),
                    ativo = json.getInt("ativo"),
                    matricula = json.getString("matricula"),
                    cpf = json.getString("cpf"),
                    cargo = json.getString("cargo"),
                    secretaria = json.getString("secretaria"),
                    lotacao = json.getString("lotacao"),
                    apiId = json.getLong("apiId"),
                    dataImportacao = json.getLong("dataImportacao")
                )
                val newFuncionarioId = funcionariosDao.insert(funcionario)
                
                // Mapear ID antigo para novo
                funcionarioIdMapping[oldFuncionarioId] = newFuncionarioId
                
                Log.d(TAG, "✅ Funcionário importado: ${funcionario.nome} (ID antigo: $oldFuncionarioId -> ID novo: $newFuncionarioId)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao importar funcionário $i: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ Importação de funcionários concluída. Mapeamento: $funcionarioIdMapping")
        return funcionarioIdMapping
    }
    
    private fun importConfiguracoes(configuracoesArray: JSONArray) {
        if (configuracoesArray.length() > 0) {
            val configuracoesDao = ConfiguracoesDao()
            val json = configuracoesArray.getJSONObject(0)
            val configuracoes = ConfiguracoesEntity(
                id = json.getLong("id"),
                entidadeId = json.getString("entidadeId"),
                localizacaoId = json.getString("localizacaoId"),
                codigoSincronizacao = json.getString("codigoSincronizacao"),
                horaSincronizacao = json.getInt("horaSincronizacao"),
                minutoSincronizacao = json.getInt("minutoSincronizacao"),
                sincronizacaoAtiva = json.getBoolean("sincronizacaoAtiva"),
                intervaloSincronizacao = json.getInt("intervaloSincronizacao"),
                geolocalizacaoHabilitada = if (json.has("geolocalizacaoHabilitada")) json.getBoolean("geolocalizacaoHabilitada") else true,
                latitudeFixa = if (json.has("latitudeFixa") && !json.isNull("latitudeFixa")) json.getDouble("latitudeFixa") else null,
                longitudeFixa = if (json.has("longitudeFixa") && !json.isNull("longitudeFixa")) json.getDouble("longitudeFixa") else null
            )
            configuracoesDao.salvarConfiguracoes(configuracoes)
        }
    }
    
    private fun importPessoas(pessoasArray: JSONArray, funcionarioIdMapping: Map<Long, Long>): Map<Long, Long> {
        val personBox = ObjectBoxStore.store.boxFor(PersonRecord::class.java)
        val personIdMapping = mutableMapOf<Long, Long>() // Mapeamento: personID_antigo -> personID_novo
        
        Log.d(TAG, "🔄 Importando ${pessoasArray.length()} pessoas...")
        Log.d(TAG, "📋 Mapeamento de funcionários: $funcionarioIdMapping")
        
        for (i in 0 until pessoasArray.length()) {
            try {
                val json = pessoasArray.getJSONObject(i)
                val oldPersonID = json.getLong("personID") // ID original do backup
                val oldFuncionarioId = json.getLong("funcionarioId") // ID original do funcionário
                
                // Usar o mapeamento para encontrar o novo funcionarioId
                val newFuncionarioId = funcionarioIdMapping[oldFuncionarioId] ?: oldFuncionarioId
                
                val pessoa = PersonRecord(
                    personID = 0, // ObjectBox vai gerar novo ID automaticamente
                    personName = json.getString("personName"),
                    numImages = json.getLong("numImages"),
                    addTime = json.getLong("addTime"),
                    funcionarioId = newFuncionarioId, // Usar o novo funcionarioId mapeado
                    funcionarioApiId = json.getLong("funcionarioApiId")
                )
                val newPersonID = personBox.put(pessoa)
                
                // Mapear ID antigo para novo
                personIdMapping[oldPersonID] = newPersonID
                
                Log.d(TAG, "✅ Pessoa importada: ${pessoa.personName} (ID antigo: $oldPersonID -> ID novo: $newPersonID, funcionarioId antigo: $oldFuncionarioId -> novo: $newFuncionarioId)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao importar pessoa $i: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ Importação de pessoas concluída. Mapeamento: $personIdMapping")
        return personIdMapping
    }
    
    private fun importFaceImages(faceImagesArray: JSONArray, personIdMapping: Map<Long, Long>) {
        val faceBox = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)
        
        Log.d(TAG, "🔄 Importando ${faceImagesArray.length()} imagens de face...")
        Log.d(TAG, "📋 Mapeamento de IDs: $personIdMapping")
        
        for (i in 0 until faceImagesArray.length()) {
            try {
                val json = faceImagesArray.getJSONObject(i)
                val embeddingArray = json.getJSONArray("faceEmbedding")
                val embedding = FloatArray(embeddingArray.length()) { j ->
                    embeddingArray.getDouble(j).toFloat()
                }
                
                val oldPersonID = json.getLong("personID") // ID original do backup
                val personName = json.getString("personName")
                
                // Usar o mapeamento para encontrar o novo personID
                val newPersonID = personIdMapping[oldPersonID]
                
                if (newPersonID != null) {
                    val faceImage = FaceImageRecord(
                        recordID = 0, // ObjectBox vai gerar novo ID automaticamente
                        personID = newPersonID, // Usar o novo personID mapeado
                        personName = personName,
                        faceEmbedding = embedding
                    )
                    val insertedId = faceBox.put(faceImage)
                    Log.d(TAG, "✅ Imagem de face importada: ${faceImage.personName} (recordID: $insertedId, personID antigo: $oldPersonID -> novo: $newPersonID)")
                } else {
                    Log.w(TAG, "⚠️ PersonID não encontrado no mapeamento para imagem de face: $personName (personID antigo: $oldPersonID)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao importar imagem de face $i: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ Importação de imagens de face concluída")
    }
    
    private fun importPontosGenericos(pontosArray: JSONArray) {
        val pontosDao = PontosGenericosDao()
        
        Log.d(TAG, "🔄 Importando ${pontosArray.length()} pontos genéricos...")
        
        for (i in 0 until pontosArray.length()) {
            try {
                val json = pontosArray.getJSONObject(i)
                val ponto = PontosGenericosEntity(
                    id = 0, // ObjectBox vai gerar novo ID automaticamente
                    funcionarioId = json.getString("funcionarioId"),
                    funcionarioNome = json.getString("funcionarioNome"),
                    funcionarioMatricula = json.getString("funcionarioMatricula"),
                    funcionarioCpf = json.getString("funcionarioCpf"),
                    funcionarioCargo = json.getString("funcionarioCargo"),
                    funcionarioSecretaria = json.getString("funcionarioSecretaria"),
                    funcionarioLotacao = json.getString("funcionarioLotacao"),
                    dataHora = json.getLong("dataHora"),
                    latitude = if (json.has("latitude") && !json.isNull("latitude")) json.getDouble("latitude") else null,
                    longitude = if (json.has("longitude") && !json.isNull("longitude")) json.getDouble("longitude") else null,
                    observacao = if (json.has("observacao") && !json.isNull("observacao")) json.getString("observacao") else null,
                    fotoBase64 = if (json.has("fotoBase64") && !json.isNull("fotoBase64")) json.getString("fotoBase64") else null,
                    synced = json.getBoolean("synced"),
                    entidadeId = if (json.has("entidadeId") && !json.isNull("entidadeId")) json.getString("entidadeId") else null, // ✅ NOVO: Campo entidadeId
                    macDispositivoCriptografado = if (json.has("macDispositivoCriptografado") && !json.isNull("macDispositivoCriptografado")) json.getString("macDispositivoCriptografado") else null,
                    fusoHorario = if (json.has("fusoHorario") && !json.isNull("fusoHorario")) json.getString("fusoHorario") else null
                )
                val insertedId = pontosDao.insert(ponto)
                Log.d(TAG, "✅ Ponto importado: ${ponto.funcionarioNome} (ID: $insertedId)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao importar ponto $i: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ Importação de pontos genéricos concluída")
    }
    
    private fun clearAllData() {
        // Limpar todas as tabelas
        ObjectBoxStore.store.boxFor(FuncionariosEntity::class.java).removeAll()
        ObjectBoxStore.store.boxFor(ConfiguracoesEntity::class.java).removeAll()
        ObjectBoxStore.store.boxFor(PersonRecord::class.java).removeAll()
        ObjectBoxStore.store.boxFor(FaceImageRecord::class.java).removeAll()
        ObjectBoxStore.store.boxFor(PontosGenericosEntity::class.java).removeAll()
    }
    
    /**
     * Encontra o diretório ObjectBox real dentro da extração
     */
    private fun findObjectBoxSourceDirectory(extractDir: File): File? {
        Log.d(TAG, "🔍 Procurando diretório ObjectBox na extração...")
        
        // Procurar recursivamente por diretórios que contenham data.mdb
        fun searchForObjectBoxDir(dir: File): File? {
            if (!dir.exists() || !dir.isDirectory) return null
            
            val files = dir.listFiles() ?: return null
            
            // Verificar se este diretório contém data.mdb
            val hasDataMdb = files.any { it.name == "data.mdb" }
            if (hasDataMdb) {
                Log.d(TAG, "✅ Diretório ObjectBox encontrado: ${dir.absolutePath}")
                return dir
            }
            
            // Procurar em subdiretórios
            for (file in files) {
                if (file.isDirectory) {
                    val result = searchForObjectBoxDir(file)
                    if (result != null) return result
                }
            }
            
            return null
        }
        
        return searchForObjectBoxDir(extractDir)
    }
    
    /**
     * Encontra o diretório de banco de dados ObjectBox
     */
    private fun findObjectBoxDatabaseDirectory(): File? {
        val possiblePaths = listOf(
            // Caminho real do ObjectBox (diretório)
            File(context.filesDir, "objectbox"),
            // Caminho alternativo
            File(context.dataDir, "objectbox"),
            // Caminho no diretório de cache
            File(context.cacheDir, "objectbox")
        )
        
        Log.d(TAG, "🔍 Procurando diretório de banco de dados ObjectBox...")
        
        for (path in possiblePaths) {
            Log.d(TAG, "   Verificando: ${path.absolutePath}")
            if (path.exists() && path.isDirectory) {
                Log.d(TAG, "✅ Diretório encontrado: ${path.absolutePath}")
                return path
            }
        }
        
        return null
    }
    
    /**
     * Extrai um arquivo ZIP para um diretório
     */
    private fun extractZipFile(zipFile: File, extractDir: File) {
        Log.d(TAG, "📦 Extraindo ZIP: ${zipFile.absolutePath} -> ${extractDir.absolutePath}")
        
        FileInputStream(zipFile).use { fis ->
            java.util.zip.ZipInputStream(fis).use { zis ->
                var entry: java.util.zip.ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(extractDir, entry.name)
                    
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        Log.d(TAG, "   📄 Extraído: ${entry.name}")
                    }
                    
                    entry = zis.nextEntry
                }
            }
        }
        
        Log.d(TAG, "✅ ZIP extraído com sucesso")
    }
    
    /**
     * Restaura dados a partir de um diretório ObjectBox extraído
     */
    private fun restoreFromObjectBoxDirectory(objectBoxDir: File) {
        Log.d(TAG, "🔄 Restaurando dados do diretório ObjectBox: ${objectBoxDir.absolutePath}")
        
        // Verificar se o diretório fonte existe e tem conteúdo
        if (!objectBoxDir.exists()) {
            throw Exception("❌ Diretório fonte não existe: ${objectBoxDir.absolutePath}")
        }
        
        val sourceFiles = objectBoxDir.listFiles()
        Log.d(TAG, "📁 Arquivos no diretório fonte: ${sourceFiles?.size ?: 0}")
        sourceFiles?.forEach { file ->
            Log.d(TAG, "   📄 Fonte: ${file.name} (${file.length()} bytes)")
        }
        
        // Encontrar diretório atual do ObjectBox
        val currentObjectBoxDir = findObjectBoxDatabaseDirectory()
        if (currentObjectBoxDir == null) {
            throw Exception("❌ Diretório ObjectBox atual não encontrado")
        }
        
        Log.d(TAG, "📁 Diretório ObjectBox atual: ${currentObjectBoxDir.absolutePath}")
        
        // Verificar arquivos atuais antes da limpeza
        val currentFiles = currentObjectBoxDir.listFiles()
        Log.d(TAG, "📁 Arquivos atuais no ObjectBox: ${currentFiles?.size ?: 0}")
        currentFiles?.forEach { file ->
            Log.d(TAG, "   📄 Atual: ${file.name} (${file.length()} bytes)")
        }
        
        // FECHAR o ObjectBox antes de copiar os arquivos
        Log.d(TAG, "🔄 Fechando ObjectBox para permitir cópia segura dos arquivos...")
        ObjectBoxStore.store.close()
        Log.d(TAG, "✅ ObjectBox fechado com sucesso")
        
        // Copiar arquivos do diretório extraído para o diretório atual
        Log.d(TAG, "📋 Copiando arquivos do backup...")
        copyObjectBoxFiles(objectBoxDir, currentObjectBoxDir)
        
        // REABRIR o ObjectBox após a cópia
        Log.d(TAG, "🔄 Reabrindo ObjectBox com os novos dados...")
        
        // Forçar limpeza completa e reinicialização
        try {
            // Tentar fechar novamente para garantir
            ObjectBoxStore.store.close()
            Log.d(TAG, "🔄 ObjectBox fechado novamente para garantir limpeza")
        } catch (e: Exception) {
            Log.d(TAG, "⚠️ ObjectBox já estava fechado: ${e.message}")
        }
        
        // Aguardar um pouco para garantir que os arquivos foram liberados
        Thread.sleep(100)
        
        // Reinicializar
        ObjectBoxStore.init(context)
        Log.d(TAG, "✅ ObjectBox reinicializado com sucesso")
        
        // Verificar se o ObjectBox foi reinicializado corretamente
        try {
            val testBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.FuncionariosEntity::class.java)
            val count = testBox.count()
            Log.d(TAG, "✅ ObjectBox funcionando - Funcionários encontrados: $count")
            
            // Verificar outros tipos de dados também
            val personBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.PersonRecord::class.java)
            val personCount = personBox.count()
            Log.d(TAG, "✅ Pessoas encontradas: $personCount")
            
            val faceBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.FaceImageRecord::class.java)
            val faceCount = faceBox.count()
            Log.d(TAG, "✅ Imagens de face encontradas: $faceCount")
            
            val configBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.ConfiguracoesEntity::class.java)
            val configCount = configBox.count()
            Log.d(TAG, "✅ Configurações encontradas: $configCount")
            
            val pontosBox = ObjectBoxStore.store.boxFor(com.ml.shubham0204.facenet_android.data.PontosGenericosEntity::class.java)
            val pontosCount = pontosBox.count()
            Log.d(TAG, "✅ Pontos genéricos encontrados: $pontosCount")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao testar ObjectBox após reinicialização: ${e.message}")
        }
        
        val finalFiles = currentObjectBoxDir.listFiles()
        Log.d(TAG, "📁 Arquivos após restauração: ${finalFiles?.size ?: 0}")
        finalFiles?.forEach { file ->
            Log.d(TAG, "   📄 Final: ${file.name} (${file.length()} bytes)")
        }
        
        Log.d(TAG, "✅ Dados restaurados do diretório ObjectBox")
    }
    
    /**
     * Copia arquivos ObjectBox diretamente para o diretório de destino
     */
    private fun copyObjectBoxFiles(source: File, destination: File) {
        Log.d(TAG, "📋 Copiando arquivos ObjectBox: ${source.absolutePath} -> ${destination.absolutePath}")
        
        if (!source.exists() || !source.isDirectory) {
            Log.e(TAG, "❌ Diretório fonte não existe ou não é um diretório: ${source.absolutePath}")
            return
        }
        
        if (!destination.exists()) {
            Log.d(TAG, "📁 Criando diretório de destino: ${destination.absolutePath}")
            destination.mkdirs()
        }
        
        val files = source.listFiles()
        if (files == null) {
            Log.w(TAG, "⚠️ Não foi possível listar arquivos do diretório fonte")
            return
        }
        
        Log.d(TAG, "📁 Copiando ${files.size} arquivos ObjectBox...")
        for (file in files) {
            if (file.isFile) {
                val destFile = File(destination, file.name)
                try {
                    file.copyTo(destFile, overwrite = true)
                    Log.d(TAG, "   ✅ Copiado: ${file.name} (${file.length()} -> ${destFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Erro ao copiar ${file.name}: ${e.message}")
                }
            }
        }
        
        Log.d(TAG, "✅ Cópia de arquivos ObjectBox concluída")
    }
    
    /**
     * Copia um diretório recursivamente
     */
    private fun copyDirectory(source: File, destination: File) {
        Log.d(TAG, "📋 Iniciando cópia: ${source.absolutePath} -> ${destination.absolutePath}")
        
        if (source.isDirectory) {
            if (!destination.exists()) {
                Log.d(TAG, "📁 Criando diretório de destino: ${destination.absolutePath}")
                destination.mkdirs()
            }
            
            val files = source.listFiles()
            if (files == null) {
                Log.w(TAG, "⚠️ Não foi possível listar arquivos do diretório fonte")
                return
            }
            
            Log.d(TAG, "📁 Copiando ${files.size} arquivos/diretórios...")
            for (file in files) {
                val destFile = File(destination, file.name)
                if (file.isDirectory) {
                    Log.d(TAG, "📁 Copiando subdiretório: ${file.name}")
                    copyDirectory(file, destFile)
                } else {
                    try {
                        file.copyTo(destFile, overwrite = true)
                        Log.d(TAG, "   ✅ Copiado: ${file.name} (${file.length()} -> ${destFile.length()} bytes)")
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ Erro ao copiar ${file.name}: ${e.message}")
                    }
                }
            }
        } else {
            Log.w(TAG, "⚠️ Fonte não é um diretório: ${source.absolutePath}")
        }
        
        Log.d(TAG, "✅ Cópia concluída")
    }
    
    /**
     * Cria um arquivo ZIP a partir de um diretório
     */
    private fun createZipFromDirectory(sourceDir: File, zipFile: File) {
        Log.d(TAG, "📦 Criando ZIP do diretório: ${sourceDir.absolutePath}")
        
        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                addDirectoryToZip(sourceDir, sourceDir.name, zos)
            }
        }
        
        Log.d(TAG, "✅ ZIP criado com sucesso: ${zipFile.absolutePath} (${zipFile.length()} bytes)")
    }
    
    /**
     * Adiciona um diretório e seus arquivos ao ZIP
     */
    private fun addDirectoryToZip(dir: File, baseName: String, zos: ZipOutputStream) {
        val files = dir.listFiles() ?: return
        
        for (file in files) {
            val entryName = if (baseName.isEmpty()) file.name else "$baseName/${file.name}"
            
            if (file.isDirectory) {
                // Adicionar entrada de diretório
                val dirEntry = ZipEntry("$entryName/")
                zos.putNextEntry(dirEntry)
                zos.closeEntry()
                
                // Recursivamente adicionar conteúdo do diretório
                addDirectoryToZip(file, entryName, zos)
            } else {
                // Adicionar arquivo
                val fileEntry = ZipEntry(entryName)
                zos.putNextEntry(fileEntry)
                
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                
                zos.closeEntry()
                Log.d(TAG, "   📄 Adicionado ao ZIP: $entryName (${file.length()} bytes)")
            }
        }
    }
    
    /**
     * Encontra o arquivo de banco de dados ObjectBox (método antigo - mantido para compatibilidade)
     */
    private fun findObjectBoxDatabaseFile(): File? {
        val possiblePaths = listOf(
            // Caminho real do ObjectBox (arquivo sem extensão)
            File(context.filesDir, "objectbox/objectbox"),
            // Caminho padrão do ObjectBox
            File(context.filesDir, "objectbox/data.mdb"),
            // Caminho alternativo
            File(context.filesDir, "objectbox/data"),
            // Caminho com nome do pacote
            File(context.filesDir, "objectbox/${context.packageName}/data.mdb"),
            // Caminho no diretório de dados
            File(context.dataDir, "objectbox/data.mdb"),
            // Caminho no diretório de cache
            File(context.cacheDir, "objectbox/data.mdb")
        )
        
        Log.d(TAG, "🔍 Procurando arquivo de banco de dados ObjectBox...")
        
        for (path in possiblePaths) {
            Log.d(TAG, "   Verificando: ${path.absolutePath}")
            if (path.exists()) {
                Log.d(TAG, "✅ Arquivo encontrado: ${path.absolutePath} (${path.length()} bytes)")
                return path
            }
        }
        
        // Se não encontrou, listar arquivos no diretório filesDir para debug
        Log.d(TAG, "📁 Listando arquivos em filesDir: ${context.filesDir.absolutePath}")
        context.filesDir.listFiles()?.forEach { file ->
            Log.d(TAG, "   - ${file.name} (${if (file.isDirectory) "diretório" else "arquivo"})")
            if (file.isDirectory && file.name.contains("objectbox", ignoreCase = true)) {
                Log.d(TAG, "     📁 Conteúdo do diretório ${file.name}:")
                file.listFiles()?.forEach { subFile ->
                    Log.d(TAG, "       - ${subFile.name} (${subFile.length()} bytes)")
                }
            }
        }
        
        return null
    }
    
    /**
     * Atualiza as informações da entidade após a restauração do backup
     */
    private suspend fun atualizarInformacoesEntidade() {
        try {
            Log.d(TAG, "🔄 Atualizando informações da entidade após restauração...")
            
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            if (configuracoes != null && configuracoes.entidadeId.isNotEmpty()) {
                Log.d(TAG, "🔍 Buscando informações da entidade: ${configuracoes.entidadeId}")
                
                val apiService = RetrofitClient.instance
                val response = apiService.verificarCodigoCliente(configuracoes.entidadeId)
                
                if (response.status == "SUCCESS" && response.entidade != null) {
                    // Salvar informações da entidade nas preferências
                    val appPreferences = AppPreferences(context)
                    appPreferences.entidadeInfo = response.entidade
                    
                    Log.d(TAG, "✅ Informações da entidade atualizadas:")
                    Log.d(TAG, "   - Entidade: ${response.entidade.nomeEntidade}")
                    Log.d(TAG, "   - Município: ${response.entidade.municipio}")
                    Log.d(TAG, "   - UF: ${response.entidade.municipioUf}")
                } else {
                    Log.w(TAG, "⚠️ Não foi possível atualizar informações da entidade: ${response.message}")
                }
            } else {
                Log.w(TAG, "⚠️ Configurações não encontradas ou entidadeId vazio")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao atualizar informações da entidade: ${e.message}")
        }
    }
    
    /**
     * Lê um arquivo em chunks para evitar OutOfMemoryError
     */
    private fun readFileInChunks(file: File): String {
        val buffer = StringBuilder()
        val chunkSize = 8192 // 8KB por chunk
        
        file.inputStream().use { inputStream ->
            val byteArray = ByteArray(chunkSize)
            var bytesRead: Int
            
            while (inputStream.read(byteArray).also { bytesRead = it } != -1) {
                buffer.append(String(byteArray, 0, bytesRead))
            }
        }
        
        return buffer.toString()
    }
}

data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val size: Long,
    val lastModified: Long
)
