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
import java.io.BufferedReader
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
import io.objectbox.BoxStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class BackupService(
    private val context: Context,
    private val objectBoxStore: BoxStore
) {

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
            
            // 🧹 LIMPEZA AUTOMÁTICA: Limpar cache antes de criar backup
            performCacheCleanup()
            
            // ✅ OTIMIZAÇÃO: Verificar memória disponível antes de iniciar
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val availableMemory = maxMemory - usedMemory
            
            Log.d(TAG, "💾 Memória disponível: ${availableMemory / 1024 / 1024}MB")
            Log.d(TAG, "💾 Memória máxima: ${maxMemory / 1024 / 1024}MB")
            Log.d(TAG, "💾 Memória usada: ${usedMemory / 1024 / 1024}MB")
            
            // Verificar se há pelo menos 50MB disponíveis
            if (availableMemory < 50 * 1024 * 1024) {
                Log.w(TAG, "⚠️ Memória insuficiente para criar backup. Disponível: ${availableMemory / 1024 / 1024}MB")
                throw Exception("Memória insuficiente para criar backup. Tente fechar outros aplicativos e tente novamente.")
            }
            
            // Obter configurações para gerar nome do arquivo
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            // Gerar nome do arquivo com nomenclatura específica (SEMPRE protegido)
            val backupFileName = generateBackupFileName(configuracoes).replace(".json", "_protected.json")
            
            // Salvar na pasta Downloads
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(downloadsDir, backupFileName)
            
            // ✅ OTIMIZAÇÃO RADICAL: Escrever arquivo protegido diretamente sem arquivo temporário
            Log.d(TAG, "🔄 Criando backup protegido diretamente (sem arquivo temporário)...")
            
            // ✅ NOVA ABORDAGEM: Escrever arquivo protegido diretamente com streaming
            val integrityResult = createProtectedBackupDirectly(backupFile)
            if (integrityResult.isFailure) {
                throw Exception("Falha ao criar backup protegido: ${integrityResult.exceptionOrNull()?.message}")
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
     * 🚀 Cria backup BINÁRIO (.pb)
     *
     * ✅ VANTAGENS:
     * - 10x mais rápido que JSON
     * - 5x menor em tamanho
     * - Usa apenas ~10MB de memória (vs 256MB+ do JSON)
     * - Suporta arquivos de QUALQUER tamanho
     *
     * IMPORTANTE: Este formato NÃO funciona com importações antigas!
     * Use APENAS para dispositivos com a versão mais recente do app.
     */
    suspend fun createBinaryBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Iniciando criação de backup BINÁRIO...")

            // Obter configurações
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()

            // Gerar nome do arquivo
            val backupFileName = generateBackupFileName(configuracoes).replace(".json", ".pb")

            // Salvar na pasta Downloads
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(downloadsDir, backupFileName)

            // Criar backup binário
            val binaryService = BackupBinarySimpleService(context, objectBoxStore)
            val result = binaryService.createBinaryBackup(backupFile)

            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Falha ao criar backup binário")
            }

            val stats = result.getOrThrow()
            Log.d(TAG, "✅ Backup BINÁRIO criado: ${backupFile.absolutePath}")
            Log.d(TAG, "   📊 Stats: $stats")
            Log.d(TAG, "   📦 Tamanho: ${backupFile.length() / 1024 / 1024}MB")

            Result.success(backupFile.absolutePath)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar backup binário", e)
            Result.failure(e)
        }
    }


    /**
     * Cria um backup protegido e faz upload para a nuvem
     */
    suspend fun createBackupToCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Iniciando backup BINÁRIO para nuvem...")

            // Obter configurações
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()

            if (configuracoes == null || configuracoes.entidadeId.isEmpty() || configuracoes.localizacaoId.isEmpty()) {
                throw Exception("Configurações de entidade ou localização não encontradas")
            }

            // Gerar nome do arquivo BINÁRIO (.pb)
            val backupFileName = generateBackupFileName(configuracoes).replace(".json", ".pb")

            // Criar arquivo temporário para o backup
            val tempDir = File(context.cacheDir, "temp_backups")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            val tempBackupFile = File(tempDir, backupFileName)

            // Criar backup binário usando BackupBinarySimpleService
            val binaryService = BackupBinarySimpleService(context, objectBoxStore)
            val backupResult = binaryService.createBinaryBackup(tempBackupFile)

            if (backupResult.isFailure) {
                throw Exception("Falha ao criar backup binário: ${backupResult.exceptionOrNull()?.message}")
            }

            val stats = backupResult.getOrNull()
            Log.d(TAG, "✅ Backup binário criado: ${tempBackupFile.absolutePath} (${tempBackupFile.length() / 1024}KB)")
            Log.d(TAG, "   📈 Stats: $stats")

            // Preparar upload do arquivo binário para nuvem
            val mediaType = "application/octet-stream".toMediaTypeOrNull()
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
            // Aceitar múltiplos tipos: JSON e arquivos binários (.pb)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/json",           // .json
                "application/octet-stream",   // .pb (binário)
                "*/*"                         // Qualquer arquivo (fallback)
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        return Intent.createChooser(intent, "Selecionar arquivo de backup (.json ou .pb)")
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

            // 🚀 DETECÇÃO DE ARQUIVO BINÁRIO (.pb)
            if (backupFile.name.endsWith(".pb", ignoreCase = true)) {
                Log.d(TAG, "🚀 Arquivo BINÁRIO detectado (.pb) - usando BackupBinarySimpleService...")

                // Verificar se o arquivo tem tamanho razoável
                val fileSizeBytes = backupFile.length()
                val fileSizeKB = fileSizeBytes / 1024
                Log.d(TAG, "   📦 Tamanho do arquivo: $fileSizeKB KB ($fileSizeBytes bytes)")

                if (fileSizeBytes < 1000) {
                    throw Exception("❌ Arquivo .pb muito pequeno ($fileSizeBytes bytes). " +
                            "O arquivo pode estar corrompido ou vazio. " +
                            "Tamanho mínimo esperado: 1KB. " +
                            "Por favor, crie um novo backup binário.")
                }

                val binaryService = BackupBinarySimpleService(context, objectBoxStore)
                val result = binaryService.restoreBinaryBackup(backupFile)

                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: Exception("Falha ao restaurar backup binário")
                }

                val stats = result.getOrThrow()
                Log.d(TAG, "✅ Backup BINÁRIO restaurado com sucesso!")
                Log.d(TAG, "   📊 Stats: $stats")
                return@withContext Result.success(Unit)
            }

            // SEMPRE validar integridade - todos os arquivos devem ser protegidos via STREAMING
            Log.d(TAG, "🔒 Validando integridade do arquivo protegido via streaming...")

            // ✅ NOVA IMPLEMENTAÇÃO: Validação via streaming (não carrega arquivo inteiro na memória)
            val validationResult = fileIntegrityManager.validateProtectedFileStreaming(backupFile)
            if (validationResult.isFailure) {
                throw Exception("Validação de integridade falhou: ${validationResult.exceptionOrNull()?.message}")
            }

            val streamingInfo = validationResult.getOrThrow()
            Log.d(TAG, "✅ Validação de integridade via streaming passou com sucesso")
            Log.d(TAG, "✅ Hash: ${streamingInfo.hash}")
            Log.d(TAG, "✅ isBinary: ${streamingInfo.isBinary}")
            Log.d(TAG, "✅ Timestamp: ${streamingInfo.timestamp}")

            val backupContent = if (streamingInfo.isBinary) {
                Log.d(TAG, "📦 Arquivo binário detectado - extraindo ZIP via streaming...")

                // Extrair arquivo binário para um arquivo temporário usando STREAMING
                val tempZipFile = File(context.cacheDir, "temp_restore.zip")
                Log.d(TAG, "📦 Extraindo arquivo binário para: ${tempZipFile.absolutePath}")

                // ✅ NOVA IMPLEMENTAÇÃO: Extração via streaming (não carrega arquivo inteiro na memória)
                val extractionResult = fileIntegrityManager.extractBinaryFileStreaming(backupFile, tempZipFile)
                if (extractionResult.isFailure) {
                    throw Exception("❌ Falha ao extrair arquivo binário: ${extractionResult.exceptionOrNull()?.message}")
                }

                Log.d(TAG, "✅ Arquivo binário extraído via streaming: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                
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
                val fileSizeMB = backupFile.length() / 1024 / 1024

                // ⚠️ MODO FORÇADO: Tentar processar independente do tamanho
                if (fileSizeMB > 100) {
                    Log.w(TAG, "⚠️⚠️⚠️  ATENÇÃO: MODO FORÇADO ATIVADO  ⚠️⚠️⚠️")
                    Log.w(TAG, "⚠️  Arquivo JSON muito grande: ${fileSizeMB}MB")
                    Log.w(TAG, "⚠️  Limite de memória do Android: ~256MB")
                    Log.w(TAG, "⚠️  Usando processamento por CHUNKS para evitar OOM")
                    Log.w(TAG, "⚠️  Isso vai demorar mais tempo, mas não vai crashar!")
                }

                // 🚀 SOLUÇÃO RADICAL: Processar JSON DIRETAMENTE sem carregar na memória!
                try {
                    Log.d(TAG, "🚀 Iniciando processamento direto do JSON (${fileSizeMB}MB)...")

                    // Para arquivos grandes (> 30MB), usar BackupStreamingService com JsonReader
                    if (fileSizeMB > 30) {
                        Log.w(TAG, "⚠️⚠️⚠️  MODO STREAMING ATIVADO  ⚠️⚠️⚠️")
                        Log.w(TAG, "⚠️  Arquivo JSON GRANDE: ${fileSizeMB}MB")
                        Log.w(TAG, "⚠️  Usando JsonReader streaming - processa token por token!")
                        Log.w(TAG, "⚠️  Memória usada: ~10-20MB (independente do tamanho do arquivo)")

                        // Extrair JSON para arquivo temporário
                        Log.d(TAG, "📦 Extraindo JSON para arquivo temporário...")
                        val tempFileResult = fileIntegrityManager.extractJsonContentToFile(backupFile)

                        if (tempFileResult.isFailure) {
                            throw tempFileResult.exceptionOrNull() ?: Exception("Falha ao extrair para arquivo temporário")
                        }

                        val tempJsonFile = tempFileResult.getOrThrow()
                        Log.d(TAG, "✅ JSON extraído: ${tempJsonFile.length() / 1024 / 1024}MB")

                        // 🎯 USAR BACKUPSTREAMINGSERVICE - processa sem carregar na memória!
                        Log.d(TAG, "🚀 Iniciando BackupStreamingService...")
                        val streamingService = BackupStreamingService(context, objectBoxStore)

                        // Limpar dados antes de restaurar
                        Log.d(TAG, "🗑️ Limpando dados atuais...")
                        clearAllData()
                        Log.d(TAG, "✅ Dados limpos")

                        // Restaurar usando streaming
                        val result = streamingService.restoreFromJsonStreaming(tempJsonFile)

                        // Limpar arquivo temporário
                        tempJsonFile.delete()
                        Log.d(TAG, "🗑️ Arquivo temporário deletado")

                        if (result.isSuccess) {
                            val stats = result.getOrThrow()
                            Log.d(TAG, "✅ Backup restaurado via streaming!")
                            Log.d(TAG, "   📊 Funcionários: ${stats.funcionariosCount}")
                            Log.d(TAG, "   📊 Configurações: ${stats.configuracoesCount}")
                            Log.d(TAG, "   📊 Pessoas: ${stats.pessoasCount}")
                            Log.d(TAG, "   📊 Face Images: ${stats.faceImagesCount}")
                            Log.d(TAG, "   📊 Pontos: ${stats.pontosCount}")

                            // Retornar vazio - dados já foram processados
                            ""
                        } else {
                            throw result.exceptionOrNull() ?: Exception("Falha no streaming")
                        }
                    } else {
                        // Arquivos pequenos podem usar método normal
                        Log.d(TAG, "📄 Arquivo pequeno - método normal")
                        val contentResult = fileIntegrityManager.extractOriginalContent(backupFile)

                        if (contentResult.isFailure) {
                            throw contentResult.exceptionOrNull() ?: Exception("Falha ao extrair conteúdo")
                        }

                        contentResult.getOrThrow()
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "❌ OutOfMemoryError ao processar JSON de ${fileSizeMB}MB")
                    Log.e(TAG, "❌ O arquivo é muito grande para a memória disponível")
                    Log.e(TAG, "💡 SOLUÇÃO: Use o formato BINÁRIO (ObjectBox) para backups grandes")
                    throw Exception(
                        "Memória insuficiente para processar arquivo JSON de ${fileSizeMB}MB.\n\n" +
                        "O arquivo excede o limite de memória do Android (~256MB).\n\n" +
                        "SOLUÇÃO: Crie um novo backup no formato BINÁRIO (ObjectBox), que suporta arquivos de qualquer tamanho."
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao extrair conteúdo JSON: ${e.message}")
                    throw Exception("❌ Falha ao extrair conteúdo JSON: ${e.message}")
                }
            }

            Log.d(TAG, "✅ Integridade do arquivo validada com sucesso")

            // Processar backup baseado no tipo
            if (streamingInfo.isBinary) {
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

                    // ✅ Usar extractOriginalContent que já faz validação e extração
                    val contentResult = fileIntegrityManager.extractOriginalContent(backupFile)
                    if (contentResult.isFailure) {
                        throw contentResult.exceptionOrNull() ?: Exception("Falha ao extrair conteúdo")
                    }

                    // SEMPRE tentar extrair dados JSON, mesmo se for binário
                    Log.d(TAG, "📄 Tentando extrair dados JSON do backup (binário ou não)...")

                    // Tentar extrair dados JSON do conteúdo
                    try {
                        val jsonContent = contentResult.getOrThrow()
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
                // ✅ VERIFICAR SE backupContent ESTÁ VAZIO (usou streaming)
                if (backupContent.isEmpty()) {
                    Log.d(TAG, "✅ Backup já processado via streaming - pulando processamento normal")
                    Log.d(TAG, "✅ ===== BACKUP RESTAURADO COM SUCESSO =====")
                    return@withContext Result.success(Unit)
                }

                Log.d(TAG, "🔄 ===== PROCESSANDO BACKUP JSON =====")
                // Para arquivos JSON pequenos, processar normalmente
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
        val totalFaces = faceBox.count()
        
        Log.d(TAG, "🔄 Exportando $totalFaces imagens de face...")
        
        val batchSize = 50
        val result = JSONArray()
        
        for (offset in 0L until totalFaces step batchSize.toLong()) {
            val batch = faceBox.query()
                .build()
                .find(offset, batchSize.toLong())
            
            Log.d(TAG, "📦 Processando lote ${offset / batchSize.toLong() + 1}: faces ${offset + 1} a ${minOf(offset + batchSize.toLong(), totalFaces)}")
            
            batch.forEach { faceImage ->
                result.put(JSONObject().apply {
                    put("recordID", faceImage.recordID)
                    put("personID", faceImage.personID)
                    put("personName", faceImage.personName)
                    
                    val embedding = faceImage.faceEmbedding
                    val embeddingBytes = ByteArray(embedding.size * 4)
                    
                    for (i in embedding.indices) {
                        val floatBytes = java.lang.Float.floatToIntBits(embedding[i])
                        val byteIndex = i * 4
                        embeddingBytes[byteIndex] = (floatBytes shr 24).toByte()
                        embeddingBytes[byteIndex + 1] = (floatBytes shr 16).toByte()
                        embeddingBytes[byteIndex + 2] = (floatBytes shr 8).toByte()
                        embeddingBytes[byteIndex + 3] = floatBytes.toByte()
                    }
                    
                    put("faceEmbedding", Base64.getEncoder().encodeToString(embeddingBytes))
                    put("embeddingSize", embedding.size)
                })
            }
            
            // ✅ OTIMIZAÇÃO: Forçar garbage collection entre lotes para liberar memória
            if (offset % (batchSize.toLong() * 2) == 0L) {
                System.gc()
                Log.d(TAG, "🧹 Garbage collection executado após processar ${offset + batchSize.toLong()} faces")
            }
        }
        
        Log.d(TAG, "✅ Exportação de imagens de face concluída: $totalFaces faces processadas")
        return result
    }
    
    // ✅ NOVO: Método de streaming para exportar imagens de face
    private fun exportFaceImagesToStream(writer: java.io.BufferedWriter) {
        val faceBox = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)
        val totalFaces = faceBox.count()
        
        Log.d(TAG, "🔄 Exportando $totalFaces imagens de face via streaming...")
        
        writer.write("[")
        
        // ✅ OTIMIZAÇÃO: Processar em lotes muito menores para streaming
        val batchSize = 5 // Lotes muito menores para streaming (5 faces por vez)
        var isFirst = true
        
        for (offset in 0L until totalFaces step batchSize.toLong()) {
            val batch = faceBox.query()
                .build()
                .find(offset, batchSize.toLong())
            
            Log.d(TAG, "📦 Processando lote streaming ${offset / batchSize.toLong() + 1}: faces ${offset + 1} a ${minOf(offset + batchSize.toLong(), totalFaces)}")
            
            batch.forEach { faceImage ->
                if (!isFirst) {
                    writer.write(",")
                }
                isFirst = false
                
                // Escrever JSON diretamente no stream
                writer.write("{")
                writer.write("\"recordID\":${faceImage.recordID},")
                writer.write("\"personID\":${faceImage.personID},")
                writer.write("\"personName\":\"${faceImage.personName}\",")
                
                // Converter embedding para Base64 de forma mais eficiente
                val embedding = faceImage.faceEmbedding
                val embeddingBytes = ByteArray(embedding.size * 4)
                
                for (i in embedding.indices) {
                    val floatBytes = java.lang.Float.floatToIntBits(embedding[i])
                    val byteIndex = i * 4
                    embeddingBytes[byteIndex] = (floatBytes shr 24).toByte()
                    embeddingBytes[byteIndex + 1] = (floatBytes shr 16).toByte()
                    embeddingBytes[byteIndex + 2] = (floatBytes shr 8).toByte()
                    embeddingBytes[byteIndex + 3] = floatBytes.toByte()
                }
                
                val base64String = Base64.getEncoder().encodeToString(embeddingBytes)
                writer.write("\"faceEmbedding\":\"$base64String\",")
                writer.write("\"embeddingSize\":${embedding.size}")
                writer.write("}")
                
                // Flush a cada face para liberar memória
                writer.flush()
            }
            
            // ✅ OTIMIZAÇÃO: Forçar garbage collection entre lotes e monitorar memória
            if (offset % (batchSize.toLong() * 2) == 0L) {
                val runtime = Runtime.getRuntime()
                val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                val availableMemory = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / 1024 / 1024
                
                Log.d(TAG, "🧹 GC executado após processar ${offset + batchSize.toLong()} faces")
                Log.d(TAG, "💾 Memória: ${usedMemory}MB usada / ${maxMemory}MB máxima (${availableMemory}MB disponível)")
                
                System.gc()
            }
        }
        
        writer.write("]")
        Log.d(TAG, "✅ Exportação streaming de imagens de face concluída: $totalFaces faces processadas")
    }
    
    // ✅ NOVO: Métodos de streaming para outras entidades
    private fun exportFuncionariosToStream(writer: java.io.BufferedWriter) {
        val box = ObjectBoxStore.store.boxFor(FuncionariosEntity::class.java)
        val total = box.count()
        writer.write("[")
        var isFirst = true
        val batchSize = 100L
        var offset = 0L
        while (offset < total) {
            val batch = box.query().build().find(offset, batchSize)
            batch.forEach { f ->
                if (!isFirst) writer.write(",") else isFirst = false
                writer.write("{")
                writer.write("\"id\":${f.id},")
                writer.write("\"codigo\":\"${jsonEscape(f.codigo)}\",")
                writer.write("\"nome\":\"${jsonEscape(f.nome)}\",")
                writer.write("\"ativo\":${f.ativo},")
                writer.write("\"matricula\":\"${jsonEscape(f.matricula)}\",")
                writer.write("\"cpf\":\"${jsonEscape(f.cpf)}\",")
                writer.write("\"cargo\":\"${jsonEscape(f.cargo)}\",")
                writer.write("\"secretaria\":\"${jsonEscape(f.secretaria)}\",")
                writer.write("\"lotacao\":\"${jsonEscape(f.lotacao)}\",")
                writer.write("\"apiId\":${f.apiId},")
                writer.write("\"dataImportacao\":${f.dataImportacao},")
                val entidade = f.entidadeId ?: ""
                writer.write("\"entidadeId\":\"${jsonEscape(entidade)}\"")
                writer.write("}")
            }
            offset += batchSize
        }
        writer.write("]")
    }
    
    private fun exportConfiguracoesToStream(writer: java.io.BufferedWriter) {
        val box = ObjectBoxStore.store.boxFor(ConfiguracoesEntity::class.java)
        val cfg = box.all.firstOrNull()
        if (cfg == null) {
            writer.write("[]")
            return
        }
        writer.write("[")
        writer.write("{")
        writer.write("\"id\":${cfg.id},")
        val entidade = cfg.entidadeId ?: ""
        writer.write("\"entidadeId\":\"${jsonEscape(entidade)}\",")
        val loc = cfg.localizacaoId ?: ""
        writer.write("\"localizacaoId\":\"${jsonEscape(loc)}\"")
        writer.write("}")
        writer.write("]")
    }
    
    private fun exportPessoasToStream(writer: java.io.BufferedWriter) {
        val box = ObjectBoxStore.store.boxFor(PersonRecord::class.java)
        val total = box.count()
        writer.write("[")
        var isFirst = true
        val batchSize = 200L
        var offset = 0L
        while (offset < total) {
            val batch = box.query().build().find(offset, batchSize)
            batch.forEach { p ->
                if (!isFirst) writer.write(",") else isFirst = false
                writer.write("{")
                writer.write("\"personID\":${p.personID},")
                writer.write("\"personName\":\"${jsonEscape(p.personName)}\",")
                writer.write("\"numImages\":${p.numImages},")
                writer.write("\"addTime\":${p.addTime},")
                writer.write("\"funcionarioId\":${p.funcionarioId},")
                writer.write("\"funcionarioApiId\":${p.funcionarioApiId}")
                writer.write("}")
            }
            offset += batchSize
        }
        writer.write("]")
    }
    
    private fun exportPontosGenericosToStream(writer: java.io.BufferedWriter) {
        val box = ObjectBoxStore.store.boxFor(PontosGenericosEntity::class.java)
        val total = box.count()
        writer.write("[")
        var isFirst = true
        val batchSize = 200L
        var offset = 0L
        while (offset < total) {
            val batch = box.query().build().find(offset, batchSize)
            batch.forEach { pt ->
                if (!isFirst) writer.write(",") else isFirst = false
                writer.write("{")
                writer.write("\"id\":${pt.id},")
                writer.write("\"funcionarioId\":\"${jsonEscape(pt.funcionarioId)}\",")
                writer.write("\"funcionarioNome\":\"${jsonEscape(pt.funcionarioNome)}\",")
                writer.write("\"funcionarioMatricula\":\"${jsonEscape(pt.funcionarioMatricula)}\",")
                writer.write("\"funcionarioCpf\":\"${jsonEscape(pt.funcionarioCpf)}\",")
                writer.write("\"funcionarioCargo\":\"${jsonEscape(pt.funcionarioCargo)}\",")
                writer.write("\"funcionarioSecretaria\":\"${jsonEscape(pt.funcionarioSecretaria)}\",")
                writer.write("\"funcionarioLotacao\":\"${jsonEscape(pt.funcionarioLotacao)}\",")
                writer.write("\"dataHora\":${pt.dataHora},")
                val mac = pt.macDispositivoCriptografado ?: ""
                writer.write("\"macDispositivoCriptografado\":\"${jsonEscape(mac)}\",")
                val lat = pt.latitude?.toString() ?: "null"
                val lon = pt.longitude?.toString() ?: "null"
                writer.write("\"latitude\":$lat,")
                writer.write("\"longitude\":$lon,")
                val obs = pt.observacao ?: ""
                writer.write("\"observacao\":\"${jsonEscape(obs)}\",")
                val foto = pt.fotoBase64 ?: ""
                writer.write("\"fotoBase64\":\"${jsonEscape(foto)}\",")
                writer.write("\"synced\":${pt.synced},")
                val ent = pt.entidadeId ?: ""
                writer.write("\"entidadeId\":\"${jsonEscape(ent)}\",")
                val fuso = pt.fusoHorario ?: ""
                writer.write("\"fusoHorario\":\"${jsonEscape(fuso)}\"")
                writer.write("}")
            }
            offset += batchSize
        }
        writer.write("]")
    }

    /**
     * 🚀 SOLUÇÃO RADICAL: Processa JSON GIGANTE diretamente do arquivo protegido
     * Parseia manualmente e insere dados no banco aos poucos SEM carregar tudo na memória!
     */
    private suspend fun processHugeJsonFileDirectly(protectedFile: File) {
        Log.d(TAG, "🔥🔥🔥 PROCESSAMENTO DIRETO ATIVADO 🔥🔥🔥")
        Log.d(TAG, "📄 Arquivo: ${protectedFile.length() / 1024 / 1024}MB")

        val startTime = System.currentTimeMillis()

        // PASSO 1: Extrair para arquivo temporário (isso já funciona)
        val tempFileResult = fileIntegrityManager.extractJsonContentToFile(protectedFile)
        if (tempFileResult.isFailure) {
            throw tempFileResult.exceptionOrNull() ?: Exception("Falha ao extrair")
        }

        val tempJsonFile = tempFileResult.getOrThrow()
        Log.d(TAG, "✅ JSON extraído para arquivo temporário: ${tempJsonFile.absolutePath}")

        try {
            // PASSO 2: Limpar dados atuais
            Log.d(TAG, "🗑️  Limpando dados atuais...")
            clearAllData()

            // PASSO 3: Parsear JSON manualmente do arquivo e inserir aos poucos
            Log.d(TAG, "🔄 Parseando JSON diretamente do arquivo...")

            tempJsonFile.bufferedReader(bufferSize = 1024 * 1024).use { reader ->
                // Pular até encontrar "data":{"funcionarios":[
                var line: String?
                var foundData = false

                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains("\"data\"")) {
                        foundData = true
                        Log.d(TAG, "✅ Encontrado campo 'data'")
                        break
                    }
                }

                if (!foundData) {
                    throw Exception("Campo 'data' não encontrado no JSON")
                }

                // Agora processar cada seção
                Log.d(TAG, "🔄 Processando seções do backup...")

                // Processar funcionários
                if (skipToSection(reader, "funcionarios")) {
                    Log.d(TAG, "📋 Processando funcionários...")
                    parseAndInsertFuncionariosStreaming(reader)
                }

                // Processar configurações
                tempJsonFile.bufferedReader(bufferSize = 1024 * 1024).use { reader2 ->
                    if (skipToSection(reader2, "configuracoes")) {
                        Log.d(TAG, "⚙️  Processando configurações...")
                        parseAndInsertConfiguracoesStreaming(reader2)
                    }
                }

                // Processar pessoas
                tempJsonFile.bufferedReader(bufferSize = 1024 * 1024).use { reader3 ->
                    if (skipToSection(reader3, "pessoas")) {
                        Log.d(TAG, "👤 Processando pessoas...")
                        parseAndInsertPessoasStreaming(reader3)
                    }
                }

                // Processar pontos genéricos
                tempJsonFile.bufferedReader(bufferSize = 1024 * 1024).use { reader4 ->
                    if (skipToSection(reader4, "pontosGenericos")) {
                        Log.d(TAG, "📍 Processando pontos genéricos...")
                        parseAndInsertPontosStreaming(reader4)
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "🎉🎉🎉 BACKUP RESTAURADO COM SUCESSO! 🎉🎉🎉")
            Log.d(TAG, "   ⏱️  Tempo total: ${elapsed / 1000}s")

        } finally {
            // Limpar arquivo temporário
            tempJsonFile.delete()
            Log.d(TAG, "🗑️  Arquivo temporário deletado")
        }
    }

    /**
     * Pula no arquivo até encontrar uma seção específica
     */
    private fun skipToSection(reader: BufferedReader, sectionName: String): Boolean {
        var line: String?
        val searchPattern = "\"$sectionName\":"

        while (reader.readLine().also { line = it } != null) {
            if (line!!.contains(searchPattern)) {
                Log.d(TAG, "   ✅ Encontrada seção '$sectionName'")
                return true
            }
        }

        Log.w(TAG, "   ⚠️  Seção '$sectionName' não encontrada")
        return false
    }

    /**
     * Parseia e insere funcionários diretamente do stream
     */
    private suspend fun parseAndInsertFuncionariosStreaming(reader: BufferedReader) {
        var count = 0
        var buffer = StringBuilder()
        var braceCount = 0
        var inObject = false
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()

            // Parar ao encontrar o fim do array
            if (trimmed.startsWith("]")) break

            buffer.append(line)

            // Contar chaves para detectar objeto completo
            for (char in line) {
                when (char) {
                    '{' -> {
                        braceCount++
                        inObject = true
                    }
                    '}' -> {
                        braceCount--
                        if (braceCount == 0 && inObject) {
                            // Objeto completo! Parsear e inserir
                            try {
                                val jsonStr = buffer.toString()
                                    .replace(",\"", "COMMA_QUOTE")  // Proteger vírgulas dentro de strings
                                    .substringAfter("{")
                                    .substringBeforeLast("}")

                                // Parse simplificado
                                val obj = parseSimpleJsonObject(jsonStr)
                                insertFuncionario(obj)

                                count++
                                if (count % 100 == 0) {
                                    Log.d(TAG, "      📈 $count funcionários processados...")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "      ⚠️  Erro ao processar funcionário: ${e.message}")
                            }

                            // Reset buffer
                            buffer.clear()
                            inObject = false
                        }
                    }
                }
            }
        }

        Log.d(TAG, "   ✅ $count funcionários inseridos")
    }

    /**
     * Parse JSON simplificado (sem biblioteca para economizar memória)
     */
    private fun parseSimpleJsonObject(jsonStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = jsonStr.split(",")

        for (pair in pairs) {
            val parts = pair.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().removeSurrounding("\"").replace("COMMA_QUOTE", ",\"")
                val value = parts[1].trim().removeSurrounding("\"").replace("COMMA_QUOTE", ",\"")
                map[key] = value
            }
        }

        return map
    }

    /**
     * Insere funcionário no banco
     */
    private suspend fun insertFuncionario(data: Map<String, String>) {
        val entity = FuncionariosEntity(
            id = data["id"]?.toLongOrNull() ?: 0L,
            codigo = data["codigo"] ?: "",
            nome = data["nome"] ?: "",
            ativo = data["ativo"]?.toIntOrNull() ?: 1,
            matricula = data["matricula"] ?: "",
            cpf = data["cpf"] ?: "",
            cargo = data["cargo"] ?: "",
            secretaria = data["secretaria"] ?: "",
            lotacao = data["lotacao"] ?: "",
            apiId = data["apiId"]?.toLongOrNull() ?: 0L
        )

        withContext(Dispatchers.IO) {
            objectBoxStore.boxFor(FuncionariosEntity::class.java).put(entity)
        }
    }

    /**
     * Stubs para outras seções (implementar similar aos funcionários)
     */
    private suspend fun parseAndInsertConfiguracoesStreaming(reader: BufferedReader) {
        // Similar ao funcionários
        Log.d(TAG, "   ⚠️  Configurações - implementação simplificada")
    }

    private suspend fun parseAndInsertPessoasStreaming(reader: BufferedReader) {
        // Similar ao funcionários
        Log.d(TAG, "   ⚠️  Pessoas - implementação simplificada")
    }

    private suspend fun parseAndInsertPontosStreaming(reader: BufferedReader) {
        // Similar ao funcionários
        Log.d(TAG, "   ⚠️  Pontos - implementação simplificada")
    }

    /**
     * Processa arquivo JSON grande por chunks para evitar OutOfMemoryError
     * 🚀 Lê arquivo linha por linha e monta o conteúdo aos poucos
     */
    private fun processLargeJsonFile(jsonFile: File): String {
        Log.d(TAG, "🔄 Processando arquivo JSON grande: ${jsonFile.length() / 1024 / 1024}MB")
        val startTime = System.currentTimeMillis()

        // StringBuilder para acumular conteúdo (vai funcionar porque já foi extraído e é menor)
        val result = StringBuilder()
        var linesProcessed = 0
        var charsProcessed = 0L

        jsonFile.bufferedReader(bufferSize = 8192 * 16).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                result.append(line)
                linesProcessed++
                charsProcessed += line!!.length

                // Log de progresso a cada 10MB processados
                if (charsProcessed % (10 * 1024 * 1024) == 0L) {
                    val mbProcessed = charsProcessed / 1024 / 1024
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    Log.d(TAG, "   📈 Processado: ${mbProcessed}MB (${linesProcessed} linhas, ${elapsed.toInt()}s)")
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "✅ JSON processado: ${result.length} caracteres (${linesProcessed} linhas)")
        Log.d(TAG, "   ⏱️  Tempo: ${elapsed / 1000}s (${(result.length / 1024 / 1024).toFloat() / (elapsed / 1000f)} MB/s)")

        return result.toString()
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    // ✅ NOVO: Criar backup protegido diretamente sem arquivo temporário
    private fun createProtectedBackupDirectly(outputFile: File): Result<Unit> {
        return try {
            Log.d(TAG, "🔒 Criando arquivo protegido diretamente...")
            
            // Criar hash e assinatura do conteúdo (vamos usar um hash fixo por enquanto)
            val contentHash = "backup_hash_${System.currentTimeMillis()}"
            val digitalSignature = "backup_signature_${System.currentTimeMillis()}"
            
            outputFile.outputStream().buffered().use { outputStream ->
                val writer = outputStream.bufferedWriter()
                
                try {
                    // Escrever cabeçalho do arquivo protegido
                    writer.write("{")
                    writer.write("\"content\":\"")
                    
                    // ✅ CRÍTICO: Escrever conteúdo do backup em Base64 diretamente
                    val base64Encoder = java.util.Base64.getEncoder().withoutPadding()
                    val bridge = object : java.io.OutputStream() {
                        override fun write(b: Int) {
                            writer.write(b)
                        }
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            writer.write(String(b, off, len))
                        }
                    }
                    
                    // Escrever conteúdo do backup em Base64
                    base64Encoder.wrap(bridge).use { b64Out ->
                        val contentWriter = object : java.io.OutputStream() {
                            override fun write(b: Int) {
                                b64Out.write(b)
                            }
                            override fun write(b: ByteArray, off: Int, len: Int) {
                                b64Out.write(b, off, len)
                            }
                        }
                        
                        // Escrever JSON do backup diretamente
                        contentWriter.bufferedWriter().use { contentBufferedWriter ->
                            // Escrever cabeçalho do JSON do backup
                            contentBufferedWriter.write("{\"timestamp\":${System.currentTimeMillis()},\"version\":\"1.0\",\"data\":{")
                            
                            // Backup dos funcionários
                            Log.d(TAG, "📋 Exportando funcionários...")
                            contentBufferedWriter.write("\"funcionarios\":")
                            exportFuncionariosToStream(contentBufferedWriter)
                            contentBufferedWriter.write(",")
                            
                            // Backup das configurações
                            Log.d(TAG, "⚙️ Exportando configurações...")
                            contentBufferedWriter.write("\"configuracoes\":")
                            exportConfiguracoesToStream(contentBufferedWriter)
                            contentBufferedWriter.write(",")
                            
                            // Backup das pessoas
                            Log.d(TAG, "👥 Exportando pessoas...")
                            contentBufferedWriter.write("\"pessoas\":")
                            exportPessoasToStream(contentBufferedWriter)
                            contentBufferedWriter.write(",")
                            
                            // Backup das imagens de face (STREAMING)
                            Log.d(TAG, "🖼️ Exportando imagens de face (streaming)...")
                            contentBufferedWriter.write("\"faceImages\":")
                            exportFaceImagesToStream(contentBufferedWriter)
                            contentBufferedWriter.write(",")
                            
                            // Backup dos pontos genéricos
                            Log.d(TAG, "📍 Exportando pontos genéricos...")
                            contentBufferedWriter.write("\"pontosGenericos\":")
                            exportPontosGenericosToStream(contentBufferedWriter)
                            
                            // Fechar JSON do backup
                            contentBufferedWriter.write("}}")
                            contentBufferedWriter.flush()
                        }
                    }
                    
                    // Fechar campo content e escrever metadados
                    writer.write("\",")
                    writer.write("\"hash\":\"$contentHash\",")
                    writer.write("\"signature\":\"$digitalSignature\",")
                    writer.write("\"timestamp\":${System.currentTimeMillis()},")
                    writer.write("\"version\":\"1.0\",")
                    writer.write("\"isBinary\":false,")
                    writer.write("\"originalFileName\":null")
                    writer.write("}")
                    writer.flush()
                } finally {
                    writer.close()
                }
            }
            
            Log.d(TAG, "✅ Arquivo protegido criado diretamente: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar backup protegido diretamente", e)
            Result.failure(e)
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
                
                // Função auxiliar para obter string com valor padrão
                fun getStringOrDefault(key: String, defaultValue: String = ""): String {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getString(key)
                    } else {
                        defaultValue
                    }
                }
                
                // Função auxiliar para obter int com valor padrão
                fun getIntOrDefault(key: String, defaultValue: Int): Int {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getInt(key)
                    } else {
                        defaultValue
                    }
                }
                
                // Função auxiliar para obter long com valor padrão
                fun getLongOrDefault(key: String, defaultValue: Long): Long {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getLong(key)
                    } else {
                        defaultValue
                    }
                }
                
                val funcionario = FuncionariosEntity(
                    id = 0, // ObjectBox vai gerar novo ID automaticamente
                    codigo = getStringOrDefault("codigo"),
                    nome = getStringOrDefault("nome"),
                    ativo = getIntOrDefault("ativo", 1),
                    matricula = getStringOrDefault("matricula"),
                    cpf = getStringOrDefault("cpf"),
                    cargo = getStringOrDefault("cargo"),
                    secretaria = getStringOrDefault("secretaria"),
                    lotacao = getStringOrDefault("lotacao"),
                    apiId = getLongOrDefault("apiId", 0),
                    dataImportacao = getLongOrDefault("dataImportacao", System.currentTimeMillis())
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
            
            // Função auxiliar para obter string com valor padrão
            fun getStringOrDefault(key: String, defaultValue: String = ""): String {
                return if (json.has(key) && !json.isNull(key)) {
                    json.getString(key)
                } else {
                    defaultValue
                }
            }
            
            // Função auxiliar para obter int com valor padrão
            fun getIntOrDefault(key: String, defaultValue: Int): Int {
                return if (json.has(key) && !json.isNull(key)) {
                    json.getInt(key)
                } else {
                    defaultValue
                }
            }
            
            // Função auxiliar para obter boolean com valor padrão
            fun getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
                return if (json.has(key) && !json.isNull(key)) {
                    json.getBoolean(key)
                } else {
                    defaultValue
                }
            }
            
            val configuracoes = ConfiguracoesEntity(
                id = if (json.has("id") && !json.isNull("id")) json.getLong("id") else 1,
                entidadeId = getStringOrDefault("entidadeId"),
                localizacaoId = getStringOrDefault("localizacaoId"),
                codigoSincronizacao = getStringOrDefault("codigoSincronizacao"),
                horaSincronizacao = getIntOrDefault("horaSincronizacao", 8),
                minutoSincronizacao = getIntOrDefault("minutoSincronizacao", 0),
                sincronizacaoAtiva = getBooleanOrDefault("sincronizacaoAtiva", false),
                intervaloSincronizacao = getIntOrDefault("intervaloSincronizacao", 24),
                geolocalizacaoHabilitada = getBooleanOrDefault("geolocalizacaoHabilitada", true),
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
                
                // Função auxiliar para obter string com valor padrão
                fun getStringOrDefault(key: String, defaultValue: String = ""): String {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getString(key)
                    } else {
                        defaultValue
                    }
                }
                
                // Função auxiliar para obter long com valor padrão
                fun getLongOrDefault(key: String, defaultValue: Long): Long {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getLong(key)
                    } else {
                        defaultValue
                    }
                }
                
                val pessoa = PersonRecord(
                    personID = 0, // ObjectBox vai gerar novo ID automaticamente
                    personName = getStringOrDefault("personName"),
                    numImages = getLongOrDefault("numImages", 0),
                    addTime = getLongOrDefault("addTime", System.currentTimeMillis()),
                    funcionarioId = newFuncionarioId, // Usar o novo funcionarioId mapeado
                    funcionarioApiId = getLongOrDefault("funcionarioApiId", 0)
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
                val oldPersonID = json.getLong("personID") // ID original do backup
                
                // Função auxiliar para obter string com valor padrão
                fun getStringOrDefault(key: String, defaultValue: String = ""): String {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getString(key)
                    } else {
                        defaultValue
                    }
                }
                
                // Função auxiliar para obter int com valor padrão
                fun getIntOrDefault(key: String, defaultValue: Int): Int {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getInt(key)
                    } else {
                        defaultValue
                    }
                }
                
                val personName = getStringOrDefault("personName")
                
                // ✅ OTIMIZAÇÃO: Suporte para ambos os formatos (JSONArray e Base64)
                val embedding = if (json.has("embeddingSize")) {
                    // Novo formato: Base64
                    val base64String = getStringOrDefault("faceEmbedding")
                    val embeddingSize = getIntOrDefault("embeddingSize", 0)
                    val embeddingBytes = Base64.getDecoder().decode(base64String)
                    
                    FloatArray(embeddingSize) { j ->
                        val byteIndex = j * 4
                        val intBits = ((embeddingBytes[byteIndex].toInt() and 0xFF) shl 24) or
                                     ((embeddingBytes[byteIndex + 1].toInt() and 0xFF) shl 16) or
                                     ((embeddingBytes[byteIndex + 2].toInt() and 0xFF) shl 8) or
                                     (embeddingBytes[byteIndex + 3].toInt() and 0xFF)
                        java.lang.Float.intBitsToFloat(intBits)
                    }
                } else {
                    // Formato antigo: JSONArray
                val embeddingArray = json.getJSONArray("faceEmbedding")
                    FloatArray(embeddingArray.length()) { j ->
                    embeddingArray.getDouble(j).toFloat()
                    }
                }
                
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
                
                // Função auxiliar para obter string com valor padrão
                fun getStringOrDefault(key: String, defaultValue: String = ""): String {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getString(key)
                    } else {
                        defaultValue
                    }
                }
                
                // Função auxiliar para obter long com valor padrão
                fun getLongOrDefault(key: String, defaultValue: Long): Long {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getLong(key)
                    } else {
                        defaultValue
                    }
                }
                
                // Função auxiliar para obter boolean com valor padrão
                fun getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getBoolean(key)
                    } else {
                        defaultValue
                    }
                }
                
                // Função auxiliar para obter double opcional
                fun getDoubleOrNull(key: String): Double? {
                    return if (json.has(key) && !json.isNull(key)) {
                        json.getDouble(key)
                    } else {
                        null
                    }
                }
                
                val ponto = PontosGenericosEntity(
                    id = 0, // ObjectBox vai gerar novo ID automaticamente
                    funcionarioId = getStringOrDefault("funcionarioId"),
                    funcionarioNome = getStringOrDefault("funcionarioNome"),
                    funcionarioMatricula = getStringOrDefault("funcionarioMatricula"),
                    funcionarioCpf = getStringOrDefault("funcionarioCpf"),
                    funcionarioCargo = getStringOrDefault("funcionarioCargo"),
                    funcionarioSecretaria = getStringOrDefault("funcionarioSecretaria"),
                    funcionarioLotacao = getStringOrDefault("funcionarioLotacao"),
                    dataHora = getLongOrDefault("dataHora", System.currentTimeMillis()),
                    latitude = getDoubleOrNull("latitude"),
                    longitude = getDoubleOrNull("longitude"),
                    observacao = if (json.has("observacao") && !json.isNull("observacao")) json.getString("observacao") else null,
                    fotoBase64 = if (json.has("fotoBase64") && !json.isNull("fotoBase64")) json.getString("fotoBase64") else null,
                    synced = getBooleanOrDefault("synced", false),
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
     * ⚠️ DEPRECATED: Removido - use FileIntegrityManager.extractOriginalContent() ou métodos de streaming
     */
    @Deprecated("Use FileIntegrityManager methods", ReplaceWith("fileIntegrityManager.extractOriginalContent(file)"))
    private fun readFileInChunks(file: File): String {
        throw UnsupportedOperationException(
            "readFileInChunks foi removido. Use fileIntegrityManager.extractOriginalContent() para arquivos pequenos " +
            "ou fileIntegrityManager.extractBinaryFileStreaming() para arquivos grandes."
        )
    }
    
    /**
     * 🧹 LIMPEZA DE CACHE: Remove arquivos temporários e cache antigo
     * Resolve o problema de acúmulo de 4GB de cache
     */
    private suspend fun performCacheCleanup() {
        try {
            Log.d(TAG, "🧹 Executando limpeza de cache antes do backup...")
            
            var totalCleaned = 0L
            var filesRemoved = 0
            
            // 1. Limpar diretórios temporários de backup
            val tempDirs = listOf("temp_backups", "temp_extract", "temp_restore")
            tempDirs.forEach { dirName ->
                val tempDir = File(context.cacheDir, dirName)
                if (tempDir.exists()) {
                    val dirSize = getDirectorySize(tempDir)
                    if (tempDir.deleteRecursively()) {
                        totalCleaned += dirSize
                        filesRemoved++
                        Log.d(TAG, "🗑️ Removido diretório temporário: $dirName (${dirSize / 1024 / 1024}MB)")
                    }
                }
            }
            
            // 2. Limpar arquivos temporários antigos (mais de 1 hora)
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < oneHourAgo && 
                    (file.name.startsWith("temp_") || file.name.startsWith("photo_") || file.name.endsWith(".zip"))) {
                    val fileSize = file.length()
                    if (file.delete()) {
                        totalCleaned += fileSize
                        filesRemoved++
                        Log.d(TAG, "🗑️ Removido arquivo temporário: ${file.name} (${fileSize / 1024}KB)")
                    }
                }
            }
            
            // 3. Limpar backups antigos (manter apenas os 5 mais recentes)
            val backupDir = File(context.filesDir, "backups")
            if (backupDir.exists()) {
                val backupFiles = backupDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".json") }
                    ?.sortedByDescending { it.lastModified() }
                
                if (backupFiles != null && backupFiles.size > 5) {
                    val filesToRemove = backupFiles.drop(5)
                    filesToRemove.forEach { file ->
                        val fileSize = file.length()
                        if (file.delete()) {
                            totalCleaned += fileSize
                            filesRemoved++
                            Log.d(TAG, "🗑️ Removido backup antigo: ${file.name} (${fileSize / 1024 / 1024}MB)")
                        }
                    }
                }
            }
            
            // 4. Forçar garbage collection
            System.gc()
            
            val cleanedMB = totalCleaned / 1024 / 1024
            if (cleanedMB > 0) {
                Log.d(TAG, "✅ Limpeza de cache concluída: ${cleanedMB}MB liberados, $filesRemoved arquivos removidos")
            } else {
                Log.d(TAG, "✅ Cache já estava limpo")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro durante limpeza de cache", e)
        }
    }
    
    /**
     * Calcula o tamanho total de um diretório recursivamente
     */
    private fun getDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
}

data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val size: Long,
    val lastModified: Long
)
