package com.ml.shubham0204.facenet_android.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.ml.shubham0204.facenet_android.data.api.TabletVersionApi
import com.ml.shubham0204.facenet_android.data.config.ServerConfig
import com.ml.shubham0204.facenet_android.data.model.TabletVersionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class TabletUpdateRepository(
    private val api: TabletVersionApi,
    private val context: Context
) {
    
    companion object {
        private const val TAG = "TabletUpdateRepository"
        private const val DOWNLOAD_DIR = "tablet_updates"
    }
    
    suspend fun checkForUpdates(): Result<TabletVersionData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Verificando atualizações em: ${ServerConfig.BASE_URL}")
            
            val response = api.checkTabletVersion()
            Log.d(TAG, "📡 Resposta da API recebida: $response")
            
            if (response.available) {
                Log.d(TAG, "✅ Nova versão encontrada: ${response.version}")
                Log.d(TAG, "📱 Dados da versão: filename=${response.filename}, size=${response.fileSizeFormatted}")
                Result.success(response)
            } else {
                Log.d(TAG, "ℹ️ Nenhuma atualização disponível")
                Log.d(TAG, "❌ Response available: ${response.available}")
                Result.failure(Exception("Nenhuma atualização disponível"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar atualizações", e)
            Result.failure(e)
        }
    }
    
    suspend fun downloadUpdate(
        versionData: TabletVersionData, 
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📥 Iniciando download da versão ${versionData.version}")
            Log.d(TAG, "🔗 URL de download original da API: ${versionData.downloadUrl}")
            
            // Construir URL correta usando o endpoint correto
            val downloadUrl = "https://api.rh247.com.br/${ServerConfig.DOWNLOAD_ENDPOINT}?versao=${versionData.version}.apk"
            
            Log.d(TAG, "🔗 URL corrigida para download: $downloadUrl")
            Log.d(TAG, "🔗 ServerConfig.BASE_URL: ${ServerConfig.BASE_URL}")
            Log.d(TAG, "🔗 ServerConfig.DOWNLOAD_ENDPOINT: ${ServerConfig.DOWNLOAD_ENDPOINT}")
            
            // Validar se a URL é válida
            try {
                java.net.URL(downloadUrl)
                Log.d(TAG, "✅ URL válida construída")
            } catch (e: Exception) {
                Log.e(TAG, "❌ URL inválida: $downloadUrl", e)
                throw IllegalArgumentException("URL de download inválida: $downloadUrl")
            }
            
            // Criar diretório de download se não existir
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val apkFile = File(downloadDir, versionData.filename)
            
            // Download do arquivo
            val client = OkHttpClient.Builder()
                .connectTimeout(ServerConfig.CONNECT_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(ServerConfig.READ_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(ServerConfig.WRITE_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                
            val request = Request.Builder()
                .url(downloadUrl)
                .build()
            
            Log.d(TAG, "🌐 Fazendo requisição para: $downloadUrl")
            
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "📡 Resposta recebida: ${response.code} ${response.message}")
                
                if (!response.isSuccessful) {
                    throw IOException("Erro no download: ${response.code} - ${response.message}")
                }
                
                val body = response.body
                if (body == null) {
                    throw IOException("Corpo da resposta vazio")
                }
                
                val contentLength = body.contentLength()
                Log.d(TAG, "📊 Tamanho do arquivo: $contentLength bytes")
                
                if (contentLength > 0) {
                    // Download com progresso
                    var bytesRead = 0L
                    val buffer = ByteArray(8192)
                    
                    FileOutputStream(apkFile).use { fos ->
                        body.byteStream().use { input ->
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } != -1) {
                                fos.write(buffer, 0, bytes)
                                bytesRead += bytes
                                
                                // Calcular e reportar progresso
                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                onProgress(progress)
                                
                                Log.d(TAG, "📥 Progresso: $progress% ($bytesRead/$contentLength bytes)")
                            }
                        }
                    }
                } else {
                    // Download sem progresso (tamanho desconhecido)
                    Log.d(TAG, "⚠️ Tamanho do arquivo desconhecido, download sem progresso")
                    onProgress(0)
                    
                    FileOutputStream(apkFile).use { fos ->
                        body.byteStream().use { input ->
                            input.copyTo(fos)
                        }
                    }
                    
                    onProgress(100)
                }
            }
            
            Log.d(TAG, "✅ Download concluído: ${apkFile.absolutePath}")
            Result.success(apkFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no download", e)
            
            // Log específico para erros de rede
            when (e) {
                is java.net.UnknownServiceException -> {
                    Log.e(TAG, "🔒 Erro de segurança de rede: ${e.message}")
                    Log.e(TAG, "💡 Verifique se o domínio está configurado no network_security_config.xml")
                }
                is java.net.ConnectException -> {
                    Log.e(TAG, "🔌 Erro de conexão: ${e.message}")
                }
                is java.net.SocketTimeoutException -> {
                    Log.e(TAG, "⏰ Timeout na conexão: ${e.message}")
                }
                else -> {
                    Log.e(TAG, "❓ Outro tipo de erro: ${e.javaClass.simpleName} - ${e.message}")
                }
            }
            
            Result.failure(e)
        }
    }
    
    suspend fun downloadDirectUpdate(
        downloadUrl: String, 
        filename: String = "tablet_update.apk",
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📥 Iniciando download direto da URL: $downloadUrl")
            
            // Validar se a URL é válida
            try {
                java.net.URL(downloadUrl)
                Log.d(TAG, "✅ URL válida: $downloadUrl")
            } catch (e: Exception) {
                Log.e(TAG, "❌ URL inválida: $downloadUrl", e)
                throw IllegalArgumentException("URL de download inválida: $downloadUrl")
            }
            
            // Criar diretório de download se não existir
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val apkFile = File(downloadDir, filename)
            
            // Download do arquivo
            val client = OkHttpClient.Builder()
                .connectTimeout(ServerConfig.CONNECT_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(ServerConfig.READ_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(ServerConfig.WRITE_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                
            val request = Request.Builder()
                .url(downloadUrl)
                .build()
            
            Log.d(TAG, "🌐 Fazendo requisição para: $downloadUrl")
            
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "📡 Resposta recebida: ${response.code} ${response.message}")
                
                if (!response.isSuccessful) {
                    throw IOException("Erro no download: ${response.code} - ${response.message}")
                }
                
                val body = response.body
                if (body == null) {
                    throw IOException("Corpo da resposta vazio")
                }
                
                val contentLength = body.contentLength()
                Log.d(TAG, "📊 Tamanho do arquivo: $contentLength bytes")
                
                if (contentLength > 0) {
                    // Download com progresso
                    var bytesRead = 0L
                    val buffer = ByteArray(8192)
                    
                    FileOutputStream(apkFile).use { fos ->
                        body.byteStream().use { input ->
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } != -1) {
                                fos.write(buffer, 0, bytes)
                                bytesRead += bytes
                                
                                // Calcular e reportar progresso
                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                onProgress(progress)
                                
                                Log.d(TAG, "📥 Progresso: $progress% ($bytesRead/$contentLength bytes)")
                            }
                        }
                    }
                } else {
                    // Download sem progresso (tamanho desconhecido)
                    Log.d(TAG, "⚠️ Tamanho do arquivo desconhecido, download sem progresso")
                    onProgress(0)
                    
                    FileOutputStream(apkFile).use { fos ->
                        body.byteStream().use { input ->
                            input.copyTo(fos)
                        }
                    }
                    
                    onProgress(100)
                }
            }
            
            Log.d(TAG, "✅ Download direto concluído: ${apkFile.absolutePath}")
            Result.success(apkFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no download direto", e)
            
            // Log específico para erros de rede
            when (e) {
                is java.net.UnknownServiceException -> {
                    Log.e(TAG, "🔒 Erro de segurança de rede: ${e.message}")
                    Log.e(TAG, "💡 Verifique se o domínio está configurado no network_security_config.xml")
                }
                is java.net.ConnectException -> {
                    Log.e(TAG, "🔌 Erro de conexão: ${e.message}")
                }
                is java.net.SocketTimeoutException -> {
                    Log.e(TAG, "⏰ Timeout na conexão: ${e.message}")
                }
                else -> {
                    Log.e(TAG, "❓ Outro tipo de erro: ${e.javaClass.simpleName} - ${e.message}")
                }
            }
            
            Result.failure(e)
        }
    }
    
    fun installUpdate(apkFile: File) {
        try {
            Log.d(TAG, "🔧 Instalando atualização: ${apkFile.name}")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    ),
                    "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao instalar atualização", e)
            throw e
        }
    }
    
    fun getCurrentAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao obter versão atual", e)
            "0.0.0"
        }
    }
    
    fun isUpdateAvailable(currentVersion: String, newVersion: String): Boolean {
        return try {
            Log.d(TAG, "🔍 Comparando versões:")
            Log.d(TAG, "   - Versão atual: '$currentVersion'")
            Log.d(TAG, "   - Versão nova: '$newVersion'")
            
            val current = currentVersion.split(".").map { it.toInt() }
            val new = newVersion.split(".").map { it.toInt() }
            
            Log.d(TAG, "   - Versão atual parseada: $current")
            Log.d(TAG, "   - Versão nova parseada: $new")
            
            // Comparar versões
            for (i in 0 until minOf(current.size, new.size)) {
                Log.d(TAG, "   - Comparando posição $i: ${new[i]} vs ${current[i]}")
                if (new[i] > current[i]) {
                    Log.d(TAG, "   ✅ Nova versão é maior na posição $i")
                    return true
                }
                if (new[i] < current[i]) {
                    Log.d(TAG, "   ❌ Nova versão é menor na posição $i")
                    return false
                }
                Log.d(TAG, "   ⚖️ Versões são iguais na posição $i")
            }
            
            // Se chegou aqui, verificar se a nova versão tem mais componentes
            val hasMoreComponents = new.size > current.size
            Log.d(TAG, "   - Nova versão tem mais componentes? $hasMoreComponents")
            
            hasMoreComponents
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao comparar versões", e)
            false
        }
    }
} 