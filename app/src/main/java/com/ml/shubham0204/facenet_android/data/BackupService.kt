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
        
        val codigoCliente = configuracoes?.entidadeId?.takeIf { it.isNotBlank() } ?: "CLIENTE"
        val localizacaoId = configuracoes?.localizacaoId?.takeIf { it.isNotBlank() } ?: "LOCAL"
        
        val codigoClienteLimpo = codigoCliente.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val localizacaoIdLimpo = localizacaoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        
        val fileName = "${codigoClienteLimpo}_${localizacaoIdLimpo}_${data}_$hora.json"
        
        Log.d(TAG, "📝 Nome do arquivo de backup gerado: $fileName")
        return fileName
    }

    suspend fun createBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔒 Iniciando criação de backup PROTEGIDO...")
            
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            val backupFileName = generateBackupFileName(configuracoes).replace(".json", "_protected.json")
            
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(downloadsDir, backupFileName)
            
            val backupData = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0")
                put("data", JSONObject().apply {
                    put("funcionarios", exportFuncionarios())
                    
                    put("configuracoes", exportConfiguracoes())
                    
                    put("pessoas", exportPessoas())
                    
                    put("faceImages", exportFaceImages())
                    
                    put("pontosGenericos", exportPontosGenericos())
                })
            }
            
            val backupContent = backupData.toString(2)
            val integrityResult = fileIntegrityManager.createProtectedFile(backupContent, backupFile)
            if (integrityResult.isFailure) {
                throw Exception("Falha ao criar proteção de integridade: ${integrityResult.exceptionOrNull()?.message}")
            }
          
            Result.success(backupFile.absolutePath)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    

    suspend fun createBackupToCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            if (configuracoes == null || configuracoes.entidadeId.isEmpty() || configuracoes.localizacaoId.isEmpty()) {
                throw Exception("Configurações de entidade ou localização não encontradas")
            }
            
            val backupFileName = generateBackupFileName(configuracoes)
            
            val tempDir = File(context.cacheDir, "temp_backups")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            val tempBackupFile = File(tempDir, backupFileName)
            
            val backupData = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0")
                put("data", JSONObject().apply {
                    put("funcionarios", exportFuncionarios())
                    
                    put("configuracoes", exportConfiguracoes())
                    
                    put("pessoas", exportPessoas())
                    
                    put("faceImages", exportFaceImages())
                    
                    put("pontosGenericos", exportPontosGenericos())
                })
            }
            
            val backupContent = backupData.toString(2)
            tempBackupFile.writeText(backupContent)
            
            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = tempBackupFile.asRequestBody(mediaType)
            val multipartBody = MultipartBody.Part.createFormData(
                "file", 
                backupFileName, 
                requestBody
            )
            
            val localizacaoIdBody = configuracoes.localizacaoId.toRequestBody("text/plain".toMediaTypeOrNull())
            

            val apiService = RetrofitClient.instance
            val response: Response<com.ml.shubham0204.facenet_android.data.api.BackupUploadResponse> = 
                apiService.uploadBackupToCloud(
                    entidade = configuracoes.entidadeId,
                    localizacaoId = localizacaoIdBody,
                    file = multipartBody
                )
            
            try {
                tempBackupFile.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao remover arquivo temporário: ${e.message}")
            }
            
            if (response.isSuccessful) {
                val uploadResponse = response.body()
                Log.d(TAG, "📡 Resposta do servidor recebida: $uploadResponse")
                
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

    fun createRestoreIntent(): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        return Intent.createChooser(intent, "Selecionar arquivo de backup")
    }

    private suspend fun processJsonBackup(backupFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonContent = readFileInChunks(backupFile)
        
            val jsonObject = JSONObject(jsonContent)
            
            if (jsonObject.has("content")) {
                val base64Content = jsonObject.getString("content")
                
                val decodedContent = Base64.getDecoder().decode(base64Content)
                
                val isZipContent = decodedContent.size >= 4 && 
                    decodedContent[0] == 0x50.toByte() && 
                    decodedContent[1] == 0x4B.toByte()
                
                val isObjectBoxData = decodedContent.size >= 7 && 
                    decodedContent[0] == 0x55.toByte() && // U
                    decodedContent[1] == 0x45.toByte() && // E
                    decodedContent[2] == 0x73.toByte() && // s
                    decodedContent[3] == 0x44.toByte() && // D
                    decodedContent[4] == 0x42.toByte() && // B
                    decodedContent[5] == 0x42.toByte() && // B
                    decodedContent[6] == 0x51.toByte()    // Q
                
                if (isZipContent) { // via local

                    val tempZipFile = File(context.cacheDir, "temp_restore_from_base64.zip")
                    tempZipFile.writeBytes(decodedContent)
                    Log.d(TAG, "📦 Arquivo ZIP temporário criado: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                    
                    val extractDir = File(context.cacheDir, "temp_extract_from_base64")
                    if (extractDir.exists()) {
                        extractDir.deleteRecursively()
                    }
                    extractDir.mkdirs()
                    
                    extractZipFile(tempZipFile, extractDir)
                    val filesAfterExtraction = extractDir.listFiles()
                    if (filesAfterExtraction.isNullOrEmpty()) {
                        throw Exception("❌ Falha na extração: nenhum arquivo foi extraído do ZIP decodificado")
                    }
                    
                    var objectBoxSourceDir = findObjectBoxSourceDirectory(extractDir)
                    if (objectBoxSourceDir == null) {
                        objectBoxSourceDir = tryAlternativeObjectBoxDetection(extractDir)
                    }
                    if (objectBoxSourceDir == null) {
                        throw Exception("❌ Diretório ObjectBox não encontrado na extração")
                    }
                    
                    clearAllData()

                    restoreFromObjectBoxDirectory(objectBoxSourceDir)
                    

                    tempZipFile.delete()
                    extractDir.deleteRecursively()
                    
                } else if (isObjectBoxData) {

                    val objectBoxDataFile = File(context.cacheDir, "temp_objectbox_data")
                    objectBoxDataFile.writeBytes(decodedContent)

                    if (!objectBoxDataFile.exists() || objectBoxDataFile.length() == 0L) {
                        Log.e(TAG, "❌ ERRO: Arquivo ObjectBox não foi salvo corretamente!")
                        throw Exception("❌ Falha ao salvar dados ObjectBox temporários")
                    }
                    
                    val savedFirstBytes = objectBoxDataFile.readBytes().take(16)
                    val originalFirstBytes = decodedContent.take(16)
                    val isDataIntact = savedFirstBytes.toByteArray().contentEquals(originalFirstBytes.toByteArray())
                    
                    if (!isDataIntact) {
                        Log.e(TAG, "❌ ERRO: Dados ObjectBox corrompidos durante o salvamento!")
                        Log.e(TAG, "   - Original: ${originalFirstBytes.joinToString(" ") { "%02X".format(it) }}")
                        Log.e(TAG, "   - Salvo: ${savedFirstBytes.joinToString(" ") { "%02X".format(it) }}")
                        throw Exception("❌ Dados ObjectBox corrompidos durante o salvamento")
                    }

                    clearAllData()

                    // Para dados ObjectBox diretos, precisamos criar um diretório temporário
                    val tempObjectBoxDir = File(context.cacheDir, "temp_objectbox_dir")
                    if (tempObjectBoxDir.exists()) {
                        tempObjectBoxDir.deleteRecursively()
                    }
                    tempObjectBoxDir.mkdirs()
                    
                    // Copiar o arquivo de dados para o diretório temporário
                    val dataFile = File(tempObjectBoxDir, "data.mdb")
                    objectBoxDataFile.copyTo(dataFile, overwrite = true)
                    
                    // Não criar arquivo de metadados - deixar o ObjectBox criar sua própria estrutura
                    // O arquivo objectbox será criado automaticamente pelo ObjectBox quando necessário
                    
                    restoreFromObjectBoxDirectory(tempObjectBoxDir)
                    
                    // Limpar arquivos temporários
                    objectBoxDataFile.delete()
                    tempObjectBoxDir.deleteRecursively()
                    
                } else {
                    Log.d(TAG, "📄 CORREÇÃO URGENTE: Processando como JSON - dados não são ZIP nem ObjectBox diretos")
                    // Tentar processar como JSON
                    try {
                        val decodedString = String(decodedContent)
                        val backupData = JSONObject(decodedString)
                        Log.d(TAG, "📄 JSON decodificado processado com sucesso")
                        
                        // Verificar se tem seção "data"
                        if (backupData.has("data")) {
                            Log.d(TAG, "📄 JSON tem seção 'data' - importando dados...")
                            val data = backupData.getJSONObject("data")
                            importBackupDataFromJson(data)
                        } else {
                            Log.d(TAG, "📄 JSON não tem seção 'data' - importando diretamente...")
                            importBackupDataFromJson(backupData)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao processar conteúdo decodificado como JSON: ${e.message}")
                        throw Exception("❌ Conteúdo decodificado não é JSON válido: ${e.message}")
                    }
                }
            } else {
                if (jsonObject.has("data")) {
                    val data = jsonObject.getJSONObject("data")
                    importBackupDataFromJson(data)
                } else {
                    importBackupDataFromJson(jsonObject)
                }
            }
            
            Log.d(TAG, "✅ Backup JSON puro restaurado com sucesso!")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar backup JSON puro: ${e.message}")
            Result.failure(e)
        }
    }
    

    suspend fun restoreBackup(backupFilePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {

            val backupFile = File(backupFilePath)
            if (!backupFile.exists()) {
                throw Exception("Arquivo de backup não encontrado: $backupFilePath")
            }


            val firstLine = backupFile.bufferedReader().use { it.readLine() }
            if (firstLine?.trimStart()?.startsWith("{") == true) {
                return@withContext processJsonBackup(backupFile)
            }

            val validationResult = fileIntegrityManager.validateProtectedFile(backupFile)
            if (validationResult.isFailure) {
            } else {
                Log.d(TAG, "✅ Validação de integridade passou com sucesso")
            }


            val protectedData = if (backupFile.length() > 50 * 1024 * 1024) { // 50MB
                val result = ProtectedFileData.fromFileStreaming(backupFile)
                if (result.isFailure) {
                    throw Exception("Erro no streaming: ${result.exceptionOrNull()?.message}")
                }
                result.getOrThrow()
            } else {
                val jsonContent = readFileInChunks(backupFile)

                ProtectedFileData.fromJson(jsonContent)
            }

            val backupContent = if (protectedData.isBinary) {

                val tempZipFile = File(context.cacheDir, "temp_restore.zip")

                try {
                    if (protectedData.isBinary) {
                        if (protectedData.content == "FILE_STREAMING_PLACEHOLDER") {
                            // Extrair conteúdo diretamente do arquivo original
                            extractBinaryContentFromFile(backupFile, tempZipFile)
                        } else if (protectedData.content.length > 10_000_000) {
                            // Usar streaming para arquivos grandes
                            val result = ProtectedFileData.decodeBase64InStreaming(protectedData.content, tempZipFile)
                            if (result.isFailure) {
                                try {
                                    val binaryContent = Base64.getDecoder().decode(protectedData.content)
                                    tempZipFile.writeBytes(binaryContent)
                                } catch (e: Exception) {
                                    throw Exception("Falha no streaming e no fallback: ${result.exceptionOrNull()?.message}")
                                }
                            } else {
                                Log.d(TAG, "✅ Arquivo binário extraído via streaming: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                            }
                        } else {
                            var extractionSuccess = false

                            try {
                                val binaryContent = Base64.getDecoder().decode(protectedData.content)
                                tempZipFile.writeBytes(binaryContent)
                                extractionSuccess = true
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Estratégia 1 falhou: ${e.message}")
                            }

                            if (!extractionSuccess) {
                                try {
                                    val cleanedContent = protectedData.content
                                        .replace("\n", "")
                                        .replace("\r", "")
                                        .replace(" ", "")
                                        .trim()

                                    val binaryContent = Base64.getDecoder().decode(cleanedContent)
                                    tempZipFile.writeBytes(binaryContent)
                                    extractionSuccess = true
                                } catch (e: Exception) {
                                    Log.w(TAG, "⚠️ Estratégia 2 falhou: ${e.message}")
                                }
                            }

                            // Estratégia 3: Limpeza agressiva
                            if (!extractionSuccess) {
                                try {
                                    Log.d(TAG, "🔄 Estratégia 3: Limpeza agressiva do Base64...")
                                    val cleanedContent = protectedData.content
                                        .replace(Regex("[^A-Za-z0-9+/=]"), "") // Remove todos os caracteres não-Base64
                                        .trim()

                                    Log.d(TAG, "📦 Conteúdo limpo agressivamente: ${cleanedContent.length} caracteres")

                                    // Adicionar padding se necessário
                                    val paddingNeeded = (4 - (cleanedContent.length % 4)) % 4
                                    val paddedContent = cleanedContent + "=".repeat(paddingNeeded)

                                    Log.d(TAG, "📦 Conteúdo com padding: ${paddedContent.length} caracteres")

                                    val binaryContent = Base64.getDecoder().decode(paddedContent)
                                    tempZipFile.writeBytes(binaryContent)
                                    Log.d(TAG, "✅ Estratégia 3 sucesso: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                                    extractionSuccess = true
                                } catch (e: Exception) {
                                    Log.w(TAG, "⚠️ Estratégia 3 falhou: ${e.message}")
                                }
                            }

                            // Estratégia 4: Decodificação em chunks
                            if (!extractionSuccess) {
                                try {
                                    Log.d(TAG, "🔄 Estratégia 4: Decodificação em chunks...")
                                    val content = protectedData.content.replace(Regex("[^A-Za-z0-9+/=]"), "")
                                    val chunkSize = 8192 // 8KB chunks
                                    val output = mutableListOf<Byte>()

                                    for (i in content.indices step chunkSize) {
                                        val chunk = content.substring(i, minOf(i + chunkSize, content.length))
                                        val paddingNeeded = (4 - (chunk.length % 4)) % 4
                                        val paddedChunk = chunk + "=".repeat(paddingNeeded)

                                        try {
                                            val decodedChunk = Base64.getDecoder().decode(paddedChunk)
                                            output.addAll(decodedChunk.toList())
                                        } catch (e: Exception) {
                                            Log.w(TAG, "⚠️ Chunk $i falhou, pulando: ${e.message}")
                                        }
                                    }

                                    tempZipFile.writeBytes(output.toByteArray())
                                    Log.d(TAG, "✅ Estratégia 4 sucesso: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                                    extractionSuccess = true
                                } catch (e: Exception) {
                                    Log.w(TAG, "⚠️ Estratégia 4 falhou: ${e.message}")
                                }
                            }

                            // Estratégia 5: Tentar streaming como último recurso
                            if (!extractionSuccess) {
                                try {
                                    Log.d(TAG, "🔄 Estratégia 5: Tentando streaming como último recurso...")
                                    val result = ProtectedFileData.decodeBase64InStreaming(protectedData.content, tempZipFile)
                                    if (result.isSuccess) {
                                        Log.d(TAG, "✅ Estratégia 5 sucesso: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                                        extractionSuccess = true
                                    } else {
                                        Log.w(TAG, "⚠️ Estratégia 5 falhou: ${result.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "⚠️ Estratégia 5 falhou: ${e.message}")
                                }
                            }

                            if (!extractionSuccess) {
                                Log.e(TAG, "❌ Todas as estratégias de decodificação Base64 falharam")
                                Log.e(TAG, "📁 Informações do conteúdo Base64:")
                                Log.e(TAG, "   - Tamanho: ${protectedData.content.length} caracteres")
                                Log.e(TAG, "   - Primeiros 100 caracteres: ${protectedData.content.take(100)}")
                                Log.e(TAG, "   - Últimos 100 caracteres: ${protectedData.content.takeLast(100)}")
                                throw Exception("❌ Falha em todas as estratégias de decodificação Base64")
                            }
                        }
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
                Log.d(TAG, "📁 Arquivo ZIP temporário: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
                Log.d(TAG, "📁 Arquivo ZIP existe: ${tempZipFile.exists()}")
                Log.d(TAG, "📁 Arquivo ZIP pode ser lido: ${tempZipFile.canRead()}")

                extractZipFile(tempZipFile, tempExtractDir)

                // Verificar se a extração funcionou antes de deletar o ZIP
                val filesAfterExtraction = tempExtractDir.listFiles()
                Log.d(TAG, "📁 Arquivos após extração: ${filesAfterExtraction?.size ?: 0}")
                filesAfterExtraction?.forEach { file ->
                    Log.d(TAG, "   - ${file.name} (${if (file.isDirectory) "diretório" else "arquivo"}) - ${file.length()} bytes")
                }

                // Verificar se pelo menos um arquivo foi extraído
                if (filesAfterExtraction.isNullOrEmpty()) {
                    Log.e(TAG, "❌ Nenhum arquivo foi extraído do ZIP")
                    throw Exception("❌ Falha na extração: nenhum arquivo foi extraído do ZIP")
                }

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
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "❌ OutOfMemoryError ao extrair conteúdo JSON: ${e.message}")
                    throw Exception("❌ Arquivo JSON muito grande para processar. Tente um backup menor.")
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
                var objectBoxSourceDir = findObjectBoxSourceDirectory(tempExtractDir)
                if (objectBoxSourceDir == null) {
                    Log.e(TAG, "❌ Diretório ObjectBox não encontrado na extração")
                    Log.e(TAG, "🔄 Tentando estratégias alternativas...")

                    // Estratégia alternativa: verificar se o próprio diretório de extração contém dados ObjectBox
                    val alternativeDir = tryAlternativeObjectBoxDetection(tempExtractDir)
                    if (alternativeDir != null) {
                        Log.d(TAG, "✅ Diretório ObjectBox encontrado via estratégia alternativa: ${alternativeDir.absolutePath}")
                        objectBoxSourceDir = alternativeDir
                    } else {
                        throw Exception("❌ Diretório ObjectBox não encontrado na extração")
                    }
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

                        // Verificar se o conteúdo é muito grande para processar em memória
                        if (jsonContent.length > 50_000_000) { // 50MB
                            Log.w(TAG, "⚠️ Conteúdo JSON muito grande (${jsonContent.length} caracteres), processando em modo seguro...")
                            processLargeJsonContent(jsonContent)
                            return@withContext Result.success(Unit)
                        }

                        // Verificar memória disponível antes de processar
                        val runtime = Runtime.getRuntime()
                        val freeMemory = runtime.freeMemory()
                        val totalMemory = runtime.totalMemory()
                        val maxMemory = runtime.maxMemory()
                        val usedMemory = totalMemory - freeMemory

                        Log.d(TAG, "📊 Status da memória:")
                        Log.d(TAG, "   - Memória livre: ${freeMemory / 1024 / 1024}MB")
                        Log.d(TAG, "   - Memória usada: ${usedMemory / 1024 / 1024}MB")
                        Log.d(TAG, "   - Memória total: ${totalMemory / 1024 / 1024}MB")
                        Log.d(TAG, "   - Memória máxima: ${maxMemory / 1024 / 1024}MB")

                        // Se a memória livre é menor que 100MB, usar modo seguro
                        if (freeMemory < 100 * 1024 * 1024) { // 100MB
                            Log.w(TAG, "⚠️ Pouca memória disponível (${freeMemory / 1024 / 1024}MB), usando modo seguro...")
                            processLargeJsonContent(jsonContent)
                            return@withContext Result.success(Unit)
                        }

                        val backupData = try {
                            JSONObject(jsonContent)
                        } catch (e: OutOfMemoryError) {
                            Log.e(TAG, "❌ OutOfMemoryError ao criar JSONObject, usando modo seguro...")
                            processLargeJsonContent(jsonContent)
                            return@withContext Result.success(Unit)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao criar JSONObject: ${e.message}")
                            throw e
                        }

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
     * Restaura backup online a partir de dados JSON recebidos
     * @param backupData JSONObject contendo os dados do backup (content, hash, signature, etc.)
     * @return Result<Unit> indicando sucesso ou falha da operação
     */
    suspend fun restoreOnlineBackup(backupData: JSONObject): Result<Unit> = withContext(Dispatchers.IO) {
        restoreOnlineDb(backupData)
    }
    
    private suspend fun restoreOnlineDb(backupData: JSONObject): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Iniciando restauração de backup online...")
            
            // Extrair dados do JSON recebido
            val content = backupData.getString("content")
            val hash = backupData.getString("hash")
            val signature = backupData.getString("signature")
            val timestamp = backupData.getLong("timestamp")
            val version = backupData.getString("version")
            val isBinary = backupData.getBoolean("isBinary")
            val originalFileName = if (backupData.has("originalFileName") && !backupData.isNull("originalFileName")) {
                backupData.getString("originalFileName")
            } else null

            val protectedData = ProtectedFileData(
                content = content,
                hash = hash,
                signature = signature,
                timestamp = timestamp,
                version = version,
                isBinary = isBinary,
                originalFileName = originalFileName
            )
            
            // Validar integridade dos dados (opcional, mas recomendado)
            // TODO: Implementar validação de hash e signature se necessário
            
            // Processar o conteúdo baseado no tipo
            if (isBinary) {
                Log.d(TAG, "📦 Processando backup binário...")
                processBinaryBackupContent(protectedData)
            } else {
                Log.d(TAG, "📄 Processando backup JSON...")
                processJsonBackupContent(protectedData)
            }
            
            Log.d(TAG, "✅ Restauração de backup online concluída com sucesso")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na restauração de backup online: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Processa conteúdo de backup binário (ZIP)
     */
    private suspend fun processBinaryBackupContent(protectedData: ProtectedFileData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Processando conteúdo binário...")
            
            // Criar arquivo temporário para o ZIP
            val tempZipFile = File(context.cacheDir, protectedData.originalFileName ?: "temp_online_backup.zip")
            
            // Decodificar o conteúdo Base64
            if (protectedData.content.length > 10_000_000) { // 10MB
                // Usar streaming para arquivos grandes
                val result = ProtectedFileData.decodeBase64InStreaming(protectedData.content, tempZipFile)
                if (result.isFailure) {
                    throw Exception("Erro no streaming: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Decodificar diretamente para arquivos menores
                val binaryContent = Base64.getDecoder().decode(protectedData.content)
                tempZipFile.writeBytes(binaryContent)
            }
            
            Log.d(TAG, "📦 Arquivo ZIP temporário criado: ${tempZipFile.absolutePath} (${tempZipFile.length()} bytes)")
            
            // Extrair o ZIP
            val extractDir = File(context.cacheDir, "temp_extract_online")
            if (extractDir.exists()) {
                extractDir.deleteRecursively()
            }
            extractDir.mkdirs()
            
            extractZipFile(tempZipFile, extractDir)
            val filesAfterExtraction = extractDir.listFiles()
            if (filesAfterExtraction.isNullOrEmpty()) {
                throw Exception("❌ Falha na extração: nenhum arquivo foi extraído do ZIP")
            }
            
            // Encontrar diretório ObjectBox
            var objectBoxSourceDir = findObjectBoxSourceDirectory(extractDir)
            if (objectBoxSourceDir == null) {
                objectBoxSourceDir = tryAlternativeObjectBoxDetection(extractDir)
            }
            if (objectBoxSourceDir == null) {
                throw Exception("❌ Diretório ObjectBox não encontrado na extração")
            }
            
            Log.d(TAG, "📁 Diretório ObjectBox encontrado: ${objectBoxSourceDir.absolutePath}")
            
            // Restaurar dados do ObjectBox
            restoreFromObjectBoxDirectory(objectBoxSourceDir)
            
            // Limpar arquivos temporários
            tempZipFile.delete()
            extractDir.deleteRecursively()
            
            Log.d(TAG, "✅ Processamento de backup binário concluído")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento de backup binário: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Processa conteúdo de backup JSON
     */
    private suspend fun processJsonBackupContent(protectedData: ProtectedFileData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Processando conteúdo JSON...")
            
            // O conteúdo já está descriptografado, processar diretamente
            val jsonObject = JSONObject(protectedData.content)
            
            // Importar dados usando a função existente
            importBackupDataFromJson(jsonObject)
            
            Log.d(TAG, "✅ Processamento de backup JSON concluído")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento de backup JSON: ${e.message}", e)
            Result.failure(e)
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
                    put("tipoPonto", ponto.tipoPonto)
                    put("latitude", ponto.latitude)
                    put("longitude", ponto.longitude)
                    put("observacao", ponto.observacao)
                    put("fotoBase64", ponto.fotoBase64)
                    put("synced", ponto.synced)
                    put("entidadeId", ponto.entidadeId) 
                })
            }
        }
    }
    
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
                intervaloSincronizacao = json.getInt("intervaloSincronizacao")
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
                    tipoPonto = json.getString("tipoPonto"),
                    latitude = if (json.has("latitude") && !json.isNull("latitude")) json.getDouble("latitude") else null,
                    longitude = if (json.has("longitude") && !json.isNull("longitude")) json.getDouble("longitude") else null,
                    observacao = if (json.has("observacao") && !json.isNull("observacao")) json.getString("observacao") else null,
                    fotoBase64 = if (json.has("fotoBase64") && !json.isNull("fotoBase64")) json.getString("fotoBase64") else null,
                    synced = json.getBoolean("synced"),
                    entidadeId = if (json.has("entidadeId") && !json.isNull("entidadeId")) json.getString("entidadeId") else null // ✅ NOVO: Campo entidadeId
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
        Log.d(TAG, "📁 Diretório de busca: ${extractDir.absolutePath}")
        Log.d(TAG, "📁 Diretório existe: ${extractDir.exists()}")
        Log.d(TAG, "📁 É diretório: ${extractDir.isDirectory}")
        
        if (!extractDir.exists() || !extractDir.isDirectory) {
            Log.e(TAG, "❌ Diretório de extração não existe ou não é um diretório")
            return null
        }
        
        val files = extractDir.listFiles()
        Log.d(TAG, "📁 Arquivos no diretório raiz: ${files?.size ?: 0}")
        files?.forEach { file ->
            Log.d(TAG, "   - ${file.name} (${if (file.isDirectory) "diretório" else "arquivo"}) - ${file.length()} bytes")
        }
        
        // Procurar recursivamente por diretórios que contenham data.mdb
        fun searchForObjectBoxDir(dir: File, depth: Int = 0): File? {
            val indent = "  ".repeat(depth)
            Log.d(TAG, "${indent}🔍 Buscando em: ${dir.absolutePath}")
            
            if (!dir.exists() || !dir.isDirectory) {
                Log.d(TAG, "${indent}❌ Não existe ou não é diretório")
                return null
            }
            
            val files = dir.listFiles() ?: return null
            Log.d(TAG, "${indent}📁 ${files.size} arquivos encontrados")
            
            // Verificar se este diretório contém data.mdb
            val hasDataMdb = files.any { it.name == "data.mdb" }
            if (hasDataMdb) {
                Log.d(TAG, "${indent}✅ Diretório ObjectBox encontrado: ${dir.absolutePath}")
                return dir
            }
            
            // Verificar se contém outros arquivos ObjectBox típicos
            val hasObjectBoxFiles = files.any { 
                it.name == "data.mdb" || 
                it.name == "lock.mdb" || 
                it.name == "objectbox" ||
                it.name.endsWith(".mdb")
            }
            
            if (hasObjectBoxFiles) {
                Log.d(TAG, "${indent}✅ Possível diretório ObjectBox encontrado: ${dir.absolutePath}")
                return dir
            }
            
            // Procurar em subdiretórios (limitado a 3 níveis de profundidade)
            if (depth < 3) {
                for (file in files) {
                    if (file.isDirectory) {
                        val result = searchForObjectBoxDir(file, depth + 1)
                        if (result != null) return result
                    }
                }
            }
            
            return null
        }
        
        val result = searchForObjectBoxDir(extractDir)
        if (result == null) {
            Log.e(TAG, "❌ Nenhum diretório ObjectBox encontrado na extração")
            Log.e(TAG, "📁 Estrutura completa do diretório de extração:")
            printDirectoryStructure(extractDir, 0)
        }
        
        return result
    }
    
    /**
     * Imprime a estrutura completa de um diretório para debug
     */
    private fun printDirectoryStructure(dir: File, depth: Int) {
        val indent = "  ".repeat(depth)
        if (depth > 5) return // Limitar profundidade
        
        if (!dir.exists() || !dir.isDirectory) {
            Log.d(TAG, "${indent}❌ ${dir.name} (não existe ou não é diretório)")
            return
        }
        
        val files = dir.listFiles() ?: return
        Log.d(TAG, "${indent}📁 ${dir.name}/ (${files.size} itens)")
        
        for (file in files) {
            if (file.isDirectory) {
                printDirectoryStructure(file, depth + 1)
            } else {
                Log.d(TAG, "${indent}  📄 ${file.name} (${file.length()} bytes)")
            }
        }
    }
    
    /**
     * Tenta estratégias alternativas para encontrar dados ObjectBox
     */
    private fun tryAlternativeObjectBoxDetection(extractDir: File): File? {
        Log.d(TAG, "🔄 Tentando estratégias alternativas para encontrar ObjectBox...")
        
        // Estratégia 1: Verificar se o próprio diretório de extração contém dados ObjectBox
        val files = extractDir.listFiles() ?: return null
        val hasObjectBoxFiles = files.any { 
            it.name == "data.mdb" || 
            it.name == "lock.mdb" || 
            it.name == "objectbox" ||
            it.name.endsWith(".mdb")
        }
        
        if (hasObjectBoxFiles) {
            Log.d(TAG, "✅ Estratégia 1: Diretório de extração contém dados ObjectBox")
            return extractDir
        }
        
        // Estratégia 2: Procurar por qualquer diretório que contenha arquivos .mdb
        for (file in files) {
            if (file.isDirectory) {
                val subFiles = file.listFiles() ?: continue
                val hasMdbFiles = subFiles.any { it.name.endsWith(".mdb") }
                if (hasMdbFiles) {
                    Log.d(TAG, "✅ Estratégia 2: Diretório com arquivos .mdb encontrado: ${file.absolutePath}")
                    return file
                }
            }
        }
        
        // Estratégia 3: Procurar por diretórios com nomes que possam indicar ObjectBox
        for (file in files) {
            if (file.isDirectory) {
                val name = file.name.lowercase()
                if (name.contains("objectbox") || name.contains("database") || name.contains("db")) {
                    Log.d(TAG, "✅ Estratégia 3: Diretório com nome suspeito encontrado: ${file.absolutePath}")
                    return file
                }
            }
        }
        
        // Estratégia 4: Se há apenas um diretório, usar ele
        val directories = files.filter { it.isDirectory }
        if (directories.size == 1) {
            Log.d(TAG, "✅ Estratégia 4: Usando único diretório encontrado: ${directories[0].absolutePath}")
            return directories[0]
        }
        
        Log.d(TAG, "❌ Nenhuma estratégia alternativa funcionou")
        return null
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
        Log.d(TAG, "📁 ZIP existe: ${zipFile.exists()}, tamanho: ${zipFile.length()} bytes")
        Log.d(TAG, "📁 Diretório de extração existe: ${extractDir.exists()}")
        
        if (!zipFile.exists()) {
            throw Exception("❌ Arquivo ZIP não existe: ${zipFile.absolutePath}")
        }
        
        if (zipFile.length() == 0L) {
            throw Exception("❌ Arquivo ZIP está vazio: ${zipFile.absolutePath}")
        }
        
        // Validar se o arquivo é um ZIP válido
        if (!isValidZipFile(zipFile)) {
            Log.e(TAG, "❌ Arquivo não é um ZIP válido")
            
            // Tentar detectar o formato real do arquivo
            val fileFormat = detectFileFormat(zipFile)
            Log.e(TAG, "📁 Formato detectado: $fileFormat")
            
            // CORREÇÃO URGENTE: Se for ObjectBox DB mas o arquivo tem extensão .zip, forçar como ZIP
            if (fileFormat == "OBJECTBOX_DB" && zipFile.name.endsWith(".zip")) {
                Log.w(TAG, "🚨 CORREÇÃO URGENTE: Forçando detecção como ZIP para arquivo com extensão .zip")
                Log.w(TAG, "🚨 Tentando extrair como ZIP mesmo sem assinatura válida...")
                
                // Tentar extrair como ZIP mesmo sem assinatura válida
                try {
                    extractZipFileForce(zipFile, extractDir)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Falha ao extrair como ZIP forçado: ${e.message}")
                }
            }
            
            // Se não é ZIP, tentar tratar como arquivo direto
            if (fileFormat != "ZIP") {
                Log.d(TAG, "🔄 Arquivo não é ZIP, tentando tratar como arquivo direto...")
                return extractNonZipFile(zipFile, extractDir)
            }
            
            throw Exception("❌ Arquivo não é um ZIP válido ou está corrompido")
        }
        
        var entryCount = 0
        var totalSize = 0L
        
        try {
            FileInputStream(zipFile).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry: java.util.zip.ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        entryCount++
                        val entryFile = File(extractDir, entry.name)
                        
                        Log.d(TAG, "   📄 Processando entrada: ${entry.name} (${entry.size} bytes)")
                        
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                            Log.d(TAG, "   📁 Diretório criado: ${entry.name}")
                        } else {
                            entryFile.parentFile?.mkdirs()
                            FileOutputStream(entryFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            totalSize += entryFile.length()
                            Log.d(TAG, "   📄 Arquivo extraído: ${entry.name} -> ${entryFile.length()} bytes")
                        }
                        
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao extrair ZIP: ${e.message}")
            Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
            throw Exception("❌ Falha ao extrair arquivo ZIP: ${e.message}")
        }
        
        Log.d(TAG, "✅ ZIP extraído com sucesso: $entryCount entradas, $totalSize bytes totais")
        
        // Verificar se pelo menos uma entrada foi extraída
        if (entryCount == 0) {
            Log.e(TAG, "❌ Nenhuma entrada foi extraída do ZIP")
            Log.e(TAG, "📁 Verificando se o arquivo é realmente um ZIP...")
            
            // Tentar ler os primeiros bytes para verificar a assinatura do ZIP
            try {
                val firstBytes = zipFile.readBytes().take(10)
                Log.e(TAG, "📁 Primeiros 10 bytes do arquivo: ${firstBytes.joinToString(" ") { "%02X".format(it) }}")
                
                // Assinatura ZIP: 50 4B 03 04 ou 50 4B 05 06 ou 50 4B 07 08
                val isZipSignature = firstBytes.size >= 4 && 
                    firstBytes[0] == 0x50.toByte() && 
                    firstBytes[1] == 0x4B.toByte()
                
                Log.e(TAG, "📁 Tem assinatura ZIP: $isZipSignature")
                
                if (!isZipSignature) {
                    throw Exception("❌ Arquivo não tem assinatura ZIP válida. Possível corrupção na decodificação Base64.")
                } else {
                    throw Exception("❌ Arquivo tem assinatura ZIP mas não consegue extrair entradas. ZIP pode estar corrompido.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar assinatura ZIP: ${e.message}")
                throw Exception("❌ Nenhuma entrada foi extraída do ZIP - arquivo pode estar corrompido")
            }
        }
        
        // Verificar se o diretório de extração tem conteúdo
        val extractedFiles = extractDir.listFiles()
        Log.d(TAG, "📁 Arquivos extraídos: ${extractedFiles?.size ?: 0}")
        extractedFiles?.forEach { file ->
            Log.d(TAG, "   - ${file.name} (${if (file.isDirectory) "diretório" else "arquivo"}) - ${file.length()} bytes")
        }
    }
    
    /**
     * CORREÇÃO URGENTE: Tenta extrair como ZIP mesmo sem assinatura válida
     */
    private fun extractZipFileForce(zipFile: File, extractDir: File) {
        Log.w(TAG, "🚨 CORREÇÃO URGENTE: Tentando extrair ZIP forçado...")
        Log.w(TAG, "📁 Arquivo: ${zipFile.absolutePath}")
        Log.w(TAG, "📁 Destino: ${extractDir.absolutePath}")
        
        var entryCount = 0
        var totalSize = 0L
        
        try {
            FileInputStream(zipFile).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    
                    while (entry != null) {
                        entryCount++
                        val entryFile = File(extractDir, entry.name)
                        
                        // Criar diretórios necessários
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                            Log.d(TAG, "   📁 Diretório criado: ${entry.name}")
                        } else {
                            entryFile.parentFile?.mkdirs()
                            
                            // Copiar arquivo
                            FileOutputStream(entryFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            
                            totalSize += entryFile.length()
                            Log.d(TAG, "   📄 Arquivo extraído: ${entry.name} (${entryFile.length()} bytes)")
                        }
                        
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            
            Log.w(TAG, "✅ CORREÇÃO URGENTE: ZIP forçado extraído com sucesso!")
            Log.w(TAG, "📊 Entradas extraídas: $entryCount")
            Log.w(TAG, "📊 Tamanho total: $totalSize bytes")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na extração ZIP forçada: ${e.message}")
            throw Exception("Falha na extração ZIP forçada: ${e.message}")
        }
    }
    
    /**
     * Verifica se um arquivo é um ZIP válido
     */
    private fun isValidZipFile(file: File): Boolean {
        return try {
            Log.d(TAG, "🔍 Validando arquivo ZIP: ${file.absolutePath}")
            Log.d(TAG, "📁 Tamanho do arquivo: ${file.length()} bytes")
            
            // Verificar assinatura ZIP primeiro
            val firstBytes = file.readBytes().take(10)
            Log.d(TAG, "📁 Primeiros 10 bytes: ${firstBytes.joinToString(" ") { "%02X".format(it) }}")
            
            val isZipSignature = firstBytes.size >= 4 && 
                firstBytes[0] == 0x50.toByte() && 
                firstBytes[1] == 0x4B.toByte()
            
            Log.d(TAG, "📁 Tem assinatura ZIP: $isZipSignature")
            
            if (!isZipSignature) {
                Log.e(TAG, "❌ Arquivo não tem assinatura ZIP válida")
                return false
            }
            
            // Tentar abrir como ZIP
            FileInputStream(file).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    // Tentar ler a primeira entrada
                    val firstEntry = zis.nextEntry
                    Log.d(TAG, "📁 Primeira entrada ZIP: ${firstEntry?.name ?: "null"}")
                    firstEntry != null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Arquivo não é um ZIP válido: ${e.message}")
            Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
            false
        }
    }
    
    /**
     * Detecta o formato de um arquivo baseado na assinatura
     */
    private fun detectFileFormat(file: File): String {
        return try {
            val firstBytes = file.readBytes().take(16)
            Log.d(TAG, "🔍 Detectando formato do arquivo...")
            Log.d(TAG, "📁 Primeiros 16 bytes: ${firstBytes.joinToString(" ") { "%02X".format(it) }}")
            
            when {
                // ZIP signature
                firstBytes.size >= 4 && firstBytes[0] == 0x50.toByte() && firstBytes[1] == 0x4B.toByte() -> "ZIP"
                
                // RAR signature
                firstBytes.size >= 7 && firstBytes[0] == 0x52.toByte() && firstBytes[1] == 0x61.toByte() && firstBytes[2] == 0x72.toByte() -> "RAR"
                
                // 7Z signature
                firstBytes.size >= 6 && firstBytes[0] == 0x37.toByte() && firstBytes[1] == 0x7A.toByte() && firstBytes[2] == 0xBC.toByte() -> "7Z"
                
                // TAR signature
                firstBytes.size >= 262 && firstBytes[257] == 0x75.toByte() && firstBytes[258] == 0x73.toByte() && firstBytes[259] == 0x74.toByte() -> "TAR"
                
                // GZIP signature
                firstBytes.size >= 2 && firstBytes[0] == 0x1F.toByte() && firstBytes[1] == 0x8B.toByte() -> "GZIP"
                
                // BZIP2 signature
                firstBytes.size >= 3 && firstBytes[0] == 0x42.toByte() && firstBytes[1] == 0x5A.toByte() && firstBytes[2] == 0x68.toByte() -> "BZIP2"
                
                // PDF signature
                firstBytes.size >= 4 && firstBytes[0] == 0x25.toByte() && firstBytes[1] == 0x50.toByte() && firstBytes[2] == 0x44.toByte() && firstBytes[3] == 0x46.toByte() -> "PDF"
                
                // PNG signature
                firstBytes.size >= 8 && firstBytes[0] == 0x89.toByte() && firstBytes[1] == 0x50.toByte() && firstBytes[2] == 0x4E.toByte() && firstBytes[3] == 0x47.toByte() -> "PNG"
                
                // JPEG signature
                firstBytes.size >= 3 && firstBytes[0] == 0xFF.toByte() && firstBytes[1] == 0xD8.toByte() && firstBytes[2] == 0xFF.toByte() -> "JPEG"
                
                // ObjectBox database (LMDB) - pode começar com qualquer coisa
                firstBytes.size >= 4 && firstBytes[0] == 0x55.toByte() && firstBytes[1] == 0x45.toByte() -> "OBJECTBOX_DB"
                
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao detectar formato: ${e.message}")
            "UNKNOWN"
        }
    }
    
    /**
     * Extrai arquivos que não são ZIP (trata como arquivo direto)
     */
    private fun extractNonZipFile(sourceFile: File, extractDir: File) {
        Log.d(TAG, "🔄 Extraindo arquivo não-ZIP: ${sourceFile.absolutePath} -> ${extractDir.absolutePath}")
        
        try {
            // Criar um diretório com o nome do arquivo
            val fileName = sourceFile.nameWithoutExtension
            val targetDir = File(extractDir, fileName)
            targetDir.mkdirs()
            
            // Copiar o arquivo para o diretório de extração
            val targetFile = File(targetDir, sourceFile.name)
            sourceFile.copyTo(targetFile, overwrite = true)
            
            Log.d(TAG, "✅ Arquivo copiado: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            
            // Verificar se o diretório de extração tem conteúdo
            val extractedFiles = extractDir.listFiles()
            Log.d(TAG, "📁 Arquivos extraídos: ${extractedFiles?.size ?: 0}")
            extractedFiles?.forEach { file ->
                Log.d(TAG, "   - ${file.name} (${if (file.isDirectory) "diretório" else "arquivo"}) - ${file.length()} bytes")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao extrair arquivo não-ZIP: ${e.message}")
            throw Exception("❌ Falha ao extrair arquivo não-ZIP: ${e.message}")
        }
    }
    
    /**
     * Processa conteúdo JSON muito grande de forma segura para evitar OutOfMemoryError
     */
    private suspend fun processLargeJsonContent(jsonContent: String) {
        Log.d(TAG, "🔄 Processando JSON grande em modo seguro...")
        
        try {
            // Verificar se o conteúdo é Base64 codificado
            if (isBase64Content(jsonContent)) {
                Log.d(TAG, "📄 Conteúdo é Base64 codificado, decodificando primeiro...")
                
                // Tentar decodificar o Base64 em chunks
                try {
                    val decodedContent = decodeBase64InChunks(jsonContent)
                    Log.d(TAG, "✅ Base64 decodificado: ${decodedContent.length} caracteres")
                    
                    // Agora processar o conteúdo decodificado
                    processDecodedJsonContent(decodedContent)
                    
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Falha ao decodificar Base64: ${e.message}")
                    // Tentar processar como JSON direto
                    processDirectJsonContent(jsonContent)
                }
            } else {
                Log.d(TAG, "📄 Conteúdo não é Base64, processando como JSON direto...")
                processDirectJsonContent(jsonContent)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar JSON grande: ${e.message}")
            throw Exception("❌ Falha ao processar JSON grande: ${e.message}")
        }
    }
    
    /**
     * Verifica se o conteúdo é Base64 codificado
     */
    private fun isBase64Content(content: String): Boolean {
        // Verificar se contém apenas caracteres Base64 válidos
        val base64Pattern = Regex("^[A-Za-z0-9+/=]+$")
        val isValidBase64 = base64Pattern.matches(content) && content.length > 1000
        
        // Verificar se não contém caracteres JSON típicos
        val hasJsonChars = content.contains("{") || content.contains("}") || content.contains("\"")
        
        Log.d(TAG, "🔍 Verificação Base64:")
        Log.d(TAG, "   - Tamanho: ${content.length} caracteres")
        Log.d(TAG, "   - Padrão Base64: $isValidBase64")
        Log.d(TAG, "   - Contém JSON: $hasJsonChars")
        Log.d(TAG, "   - Primeiros 100 chars: ${content.take(100)}")
        
        return isValidBase64 && !hasJsonChars
    }
    
    /**
     * Decodifica Base64 de forma robusta para evitar OutOfMemoryError
     */
    private fun decodeBase64InChunks(base64Content: String): String {
        Log.d(TAG, "🔄 Decodificando Base64 de forma robusta...")
        
        try {
            // Limpar o conteúdo Base64 removendo caracteres inválidos
            val cleanedContent = base64Content.filter { char ->
                char.isLetterOrDigit() || char == '+' || char == '/' || char == '='
            }
            
            // Adicionar padding se necessário
            val paddingNeeded = (4 - (cleanedContent.length % 4)) % 4
            val paddedContent = cleanedContent + "=".repeat(paddingNeeded)
            
            Log.d(TAG, "📊 Conteúdo limpo: ${cleanedContent.length} -> ${paddedContent.length} caracteres")
            
            // Tentar decodificar diretamente
            val decodedBytes = Base64.getDecoder().decode(paddedContent)
            val result = String(decodedBytes)
            
            Log.d(TAG, "✅ Base64 decodificado com sucesso: ${decodedBytes.size} bytes -> ${result.length} caracteres")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao decodificar Base64: ${e.message}")
            throw Exception("Falha ao decodificar Base64: ${e.message}")
        }
    }
    
    /**
     * Processa conteúdo JSON decodificado
     */
    private suspend fun processDecodedJsonContent(decodedContent: String) {
        Log.d(TAG, "🔄 Processando conteúdo JSON decodificado...")
        
        // Verificar se contém seção data
        if (decodedContent.contains("\"data\"")) {
            Log.d(TAG, "✅ JSON decodificado contém seção 'data'")
            
            // Tentar encontrar o início da seção data
            val dataStart = decodedContent.indexOf("\"data\":")
            if (dataStart != -1) {
                Log.d(TAG, "📄 Seção 'data' encontrada na posição: $dataStart")
                
                // Processar apenas a seção data em chunks
                val dataSection = decodedContent.substring(dataStart)
                processJsonDataSection(dataSection)
            } else {
                Log.w(TAG, "⚠️ Seção 'data' não encontrada no JSON decodificado")
            }
        } else {
            Log.w(TAG, "⚠️ JSON decodificado não contém seção 'data'")
        }
    }
    
    /**
     * Processa conteúdo JSON direto (não Base64)
     */
    private suspend fun processDirectJsonContent(jsonContent: String) {
        Log.d(TAG, "🔄 Processando conteúdo JSON direto...")
        
        // Tentar processar apenas a estrutura básica primeiro
        val firstPart = jsonContent.take(1_000_000) // Primeiros 1MB
        Log.d(TAG, "📄 Processando primeira parte: ${firstPart.length} caracteres")
        
        // Verificar se é um JSON válido
        if (firstPart.contains("\"data\"")) {
            Log.d(TAG, "✅ JSON contém seção 'data', tentando processar...")
            
            // Tentar encontrar o início da seção data
            val dataStart = jsonContent.indexOf("\"data\":")
            if (dataStart != -1) {
                Log.d(TAG, "📄 Seção 'data' encontrada na posição: $dataStart")
                
                // Processar apenas a seção data em chunks
                val dataSection = jsonContent.substring(dataStart)
                processJsonDataSection(dataSection)
            } else {
                Log.w(TAG, "⚠️ Seção 'data' não encontrada no JSON")
            }
        } else {
            Log.w(TAG, "⚠️ JSON não contém seção 'data'")
        }
    }
    
    /**
     * Processa a seção de dados do JSON em chunks para evitar OutOfMemoryError
     */
    private suspend fun processJsonDataSection(dataSection: String) {
        Log.d(TAG, "🔄 Processando seção de dados em chunks...")
        
        try {
            val chunkSize = 10_000_000
            val chunk = if (dataSection.length > chunkSize) {
                dataSection.take(chunkSize) + "}"
            } else {
                dataSection
            }
            
            Log.d(TAG, "📄 Processando chunk: ${chunk.length} caracteres")
            
            val backupData = JSONObject("{\"data\":$chunk}")
            if (backupData.has("data")) {
                val data = backupData.getJSONObject("data")
                importBackupDataFromJson(data)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar chunk de dados: ${e.message}")
            throw Exception("❌ Falha ao processar chunk de dados: ${e.message}")
        }
    }
    
    /**
     * Importa dados de backup a partir de um JSONObject
     */
    private suspend fun importBackupDataFromJson(data: JSONObject) {
        Log.d(TAG, "🔄 Importando dados de backup do JSON...")
        
        try {
            // Importar funcionários
            val funcionarioIdMapping = if (data.has("funcionarios")) {
                Log.d(TAG, "🔄 Importando funcionários...")
                importFuncionarios(data.getJSONArray("funcionarios"))
            } else {
                Log.d(TAG, "⚠️ Nenhum funcionário encontrado no backup")
                emptyMap()
            }
            
            // Importar configurações
            if (data.has("configuracoes")) {
                Log.d(TAG, "🔄 Importando configurações...")
                importConfiguracoes(data.getJSONArray("configuracoes"))
            } else {
                Log.d(TAG, "⚠️ Nenhuma configuração encontrada no backup")
            }
            
            // Importar pessoas
            val personIdMapping = if (data.has("pessoas")) {
                Log.d(TAG, "🔄 Importando pessoas...")
                importPessoas(data.getJSONArray("pessoas"), funcionarioIdMapping)
            } else {
                Log.d(TAG, "⚠️ Nenhuma pessoa encontrada no backup")
                emptyMap()
            }
            
            // Importar imagens de face
            if (data.has("faceImages")) {
                Log.d(TAG, "🔄 Importando imagens de face...")
                importFaceImages(data.getJSONArray("faceImages"), personIdMapping)
            } else {
                Log.d(TAG, "⚠️ Nenhuma imagem de face encontrada no backup")
            }
            
            // Importar pontos genéricos
            if (data.has("pontosGenericos")) {
                Log.d(TAG, "🔄 Importando pontos genéricos...")
                importPontosGenericos(data.getJSONArray("pontosGenericos"))
            } else {
                Log.d(TAG, "⚠️ Nenhum ponto genérico encontrado no backup")
            }
            
            Log.d(TAG, "✅ Dados de backup importados com sucesso")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao importar dados de backup: ${e.message}")
            throw Exception("❌ Falha ao importar dados de backup: ${e.message}")
        }
    }
    
    private fun restoreContentJson(contentBox: JSONObject) {
        
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
                // Pular arquivo de metadados objectbox se for muito pequeno (provavelmente criado por nós)
                if (file.name == "objectbox" && file.length() < 100) {
                    Log.d(TAG, "   ⏭️ Pulando arquivo de metadados pequeno: ${file.name}")
                    continue
                }
                
                val destFile = File(destination, file.name)
                try {
                    // Remover arquivo de destino se existir para garantir sobrescrita
                    if (destFile.exists()) {
                        if (destFile.isDirectory) {
                            Log.d(TAG, "   🗑️ Removendo diretório de destino: ${file.name}")
                            destFile.deleteRecursively()
                        } else {
                            Log.d(TAG, "   🗑️ Arquivo de destino removido: ${file.name}")
                            destFile.delete()
                        }
                    }
                    
                    file.copyTo(destFile, overwrite = true)
                    Log.d(TAG, "   ✅ Copiado: ${file.name} (${file.length()} -> ${destFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Erro ao copiar ${file.name}: ${e.message}")
                    // Tentar uma abordagem alternativa para arquivos problemáticos
                    try {
                        Log.d(TAG, "   🔄 Tentando abordagem alternativa para ${file.name}...")
                        
                        // Garantir que o destino seja um arquivo, não diretório
                        if (destFile.exists() && destFile.isDirectory) {
                            destFile.deleteRecursively()
                        }
                        
                        val content = file.readBytes()
                        destFile.writeBytes(content)
                        Log.d(TAG, "   ✅ Copiado (alternativo): ${file.name} (${content.size} bytes)")
                    } catch (e2: Exception) {
                        Log.e(TAG, "   ❌ Falha também na abordagem alternativa para ${file.name}: ${e2.message}")
                        // Última tentativa: pular arquivos problemáticos
                        Log.w(TAG, "   ⚠️ Pulando arquivo problemático: ${file.name}")
                    }
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
    

    private fun readFileInChunks(file: File): String {
        // Verificar o tamanho do arquivo primeiro
        val fileSize = file.length()
        Log.d(TAG, "📁 Lendo arquivo: ${file.name} (${fileSize} bytes)")
        
        // Se o arquivo for muito grande (>50MB), usar uma abordagem diferente
        if (fileSize > 50 * 1024 * 1024) {
            Log.w(TAG, "⚠️ Arquivo muito grande (${fileSize} bytes), usando leitura otimizada")
            return readLargeFileOptimized(file)
        }
        
        val buffer = StringBuilder()
        val chunkSize = 8192 // 8KB por chunk
        
        try {
            file.inputStream().use { inputStream ->
                val byteArray = ByteArray(chunkSize)
                var bytesRead: Int
                
                while (inputStream.read(byteArray).also { bytesRead = it } != -1) {
                    buffer.append(String(byteArray, 0, bytesRead))
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "❌ OutOfMemoryError ao ler arquivo, tentando abordagem alternativa")
            return readLargeFileOptimized(file)
        }
        
        return buffer.toString()
    }
    

    private fun readLargeFileOptimized(file: File): String {
        return try {
            // Para arquivos muito grandes, ler apenas o necessário para o parse JSON
            val buffer = StringBuilder()
            val chunkSize = 1024 * 1024 // 1MB por chunk
            var totalRead = 0L
            val maxRead = 100 * 1024 * 1024 // Máximo 100MB
            
            file.inputStream().use { inputStream ->
                val byteArray = ByteArray(chunkSize)
                var bytesRead: Int
                
                while (inputStream.read(byteArray).also { bytesRead = it } != -1 && totalRead < maxRead) {
                    buffer.append(String(byteArray, 0, bytesRead))
                    totalRead += bytesRead
                }
            }
            
            Log.d(TAG, "📄 Arquivo grande lido parcialmente: ${totalRead} bytes de ${file.length()}")
            buffer.toString()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao ler arquivo grande: ${e.message}")
            throw e
        }
    }
    
    /**
     * Extrai conteúdo binário diretamente do arquivo protegido sem carregar tudo na memória
     */
    private fun extractBinaryContentFromFile(protectedFile: File, outputFile: File) {
        try {
            Log.d(TAG, "📦 Extraindo conteúdo binário diretamente do arquivo...")
            
            // Encontrar o início do conteúdo Base64
            val contentStartMarker = "\"content\":\""
            val buffer = ByteArray(1024 * 1024) // 1MB buffer
            var foundStart = false
            var contentStartPos = 0L
            
            // Primeiro, encontrar onde começa o conteúdo
            protectedFile.inputStream().use { inputStream ->
                val searchBuffer = ByteArray(1024)
                var bytesRead: Int
                var totalRead = 0L
                
                while (inputStream.read(searchBuffer).also { bytesRead = it } != -1) {
                    val chunk = String(searchBuffer, 0, bytesRead)
                    val startIndex = chunk.indexOf(contentStartMarker)
                    
                    if (startIndex != -1) {
                        contentStartPos = totalRead + startIndex + contentStartMarker.length
                        foundStart = true
                        break
                    }
                    
                    totalRead += bytesRead
                }
            }
            
            if (!foundStart) {
                throw Exception("Marcador de conteúdo não encontrado")
            }
            
            Log.d(TAG, "📍 Posição do conteúdo encontrada: $contentStartPos")
            
            // Agora extrair o conteúdo Base64 em streaming
            val decoder = Base64.getDecoder()
            val chunkSize = 1024 * 1024 // 1MB por chunk
            
            outputFile.outputStream().use { outputStream ->
                protectedFile.inputStream().use { inputStream ->
                    // Pular até a posição do conteúdo
                    inputStream.skip(contentStartPos)
                    
                    val base64Buffer = ByteArray(chunkSize)
                    var bytesRead: Int
                    
                    while (inputStream.read(base64Buffer).also { bytesRead = it } != -1) {
                        val chunk = String(base64Buffer, 0, bytesRead)
                        
                        // Remover aspas finais e caracteres de fechamento JSON
                        val cleanChunk = chunk.replace("\"", "").replace("}", "").replace("]", "").trim()
                        
                        if (cleanChunk.isNotEmpty()) {
                            try {
                                val decodedChunk = decoder.decode(cleanChunk)
                                outputStream.write(decodedChunk)
                            } catch (e: Exception) {
                                // Se o chunk não for válido, tentar com um chunk menor
                                val smallerChunk = cleanChunk.take(1024)
                                if (smallerChunk.isNotEmpty()) {
                                    try {
                                        val decodedChunk = decoder.decode(smallerChunk)
                                        outputStream.write(decodedChunk)
                                    } catch (e2: Exception) {
                                        // Ignorar chunks inválidos
                                        Log.w(TAG, "⚠️ Chunk inválido ignorado: ${e2.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "✅ Conteúdo binário extraído com sucesso: ${outputFile.length()} bytes")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao extrair conteúdo binário: ${e.message}")
            throw e
        }
    }
}

data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val size: Long,
    val lastModified: Long
)
