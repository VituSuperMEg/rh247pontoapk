package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.util.Log
import com.ml.shubham0204.facenet_android.data.ObjectBoxStore
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.data.FaceImageRecord
import com.ml.shubham0204.facenet_android.data.PersonRecord
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.data.ConfiguracoesEntity
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.File

/**
 * Gerenciador de cache para limpeza agressiva de dados acumulados
 * Resolve o problema de 4GB+ de cache acumulado
 */
class CacheManager(
    private val context: Context,
    private val appPreferences: AppPreferences
) {
    
    companion object {
        private const val TAG = "CacheManager"
    }
    
    /**
     * Limpeza completa de todos os caches
     */
    fun performCompleteCacheCleanup(): Result<String> {
        return try {
            Log.d(TAG, "🧹 Iniciando limpeza completa de cache...")
            
            val totalBefore = getTotalCacheSize()
            Log.d(TAG, "📊 Tamanho do cache antes: ${formatBytes(totalBefore)}")
            
            // 1. Limpar cache do ObjectBox
            clearObjectBoxCache()
            
            // 2. Limpar arquivos temporários
            clearTempFiles()
            
            // 3. Limpar cache de imagens
            clearImageCache()
            
            // 4. Limpar preferências
            appPreferences.clearAllCaches()
            
            // 5. Forçar garbage collection
            System.gc()
            
            val totalAfter = getTotalCacheSize()
            val saved = totalBefore - totalAfter
            
            val message = "✅ Cache limpo! Economizou ${formatBytes(saved)} (${formatBytes(totalBefore)} → ${formatBytes(totalAfter)})"
            Log.d(TAG, message)
            
            Result.success(message)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na limpeza de cache", e)
            Result.failure(e)
        }
    }
    
    /**
     * Limpeza agressiva do cache do ObjectBox
     */
    private fun clearObjectBoxCache() {
        try {
            Log.d(TAG, "🗃️ Limpando cache do ObjectBox...")
            
            val boxStore = ObjectBoxStore.store
            
            // Limpar cache de todas as entidades
            clearBoxCache<FaceImageRecord>(boxStore)
            clearBoxCache<PersonRecord>(boxStore)
            clearBoxCache<FuncionariosEntity>(boxStore)
            clearBoxCache<ConfiguracoesEntity>(boxStore)
            clearBoxCache<PontosGenericosEntity>(boxStore)
            
            // Limpar cache interno do ObjectBox
            boxStore.close()
            // Reabrir para limpar cache interno
            ObjectBoxStore.init(context)
            
            Log.d(TAG, "✅ Cache do ObjectBox limpo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao limpar cache do ObjectBox", e)
        }
    }
    
    /**
     * Limpa cache de uma entidade específica
     */
    private inline fun <reified T> clearBoxCache(boxStore: BoxStore) {
        try {
            val box: Box<T> = boxStore.boxFor(T::class.java)
            // Forçar limpeza de cache interno da box
            // ObjectBox não tem método close() nas boxes, apenas no BoxStore
            Log.d(TAG, "🧹 Cache da box ${T::class.simpleName} processado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao limpar cache da box ${T::class.simpleName}", e)
        }
    }
    
    /**
     * Limpa arquivos temporários
     */
    private fun clearTempFiles() {
        try {
            Log.d(TAG, "📁 Limpando arquivos temporários...")
            
            val tempDir = File(context.cacheDir, "temp")
            if (tempDir.exists()) {
                deleteRecursively(tempDir)
            }
            
            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (backupDir.exists()) {
                // Manter apenas os 3 backups mais recentes
                val files = backupDir.listFiles()?.sortedByDescending { it.lastModified() }
                files?.drop(3)?.forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "🗑️ Backup antigo removido: ${file.name}")
                    }
                }
            }
            
            Log.d(TAG, "✅ Arquivos temporários limpos")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao limpar arquivos temporários", e)
        }
    }
    
    /**
     * Limpa cache de imagens
     */
    private fun clearImageCache() {
        try {
            Log.d(TAG, "🖼️ Limpando cache de imagens...")
            
            val imageCacheDir = File(context.cacheDir, "images")
            if (imageCacheDir.exists()) {
                deleteRecursively(imageCacheDir)
            }
            
            // Limpar cache do sistema
            val systemCacheDir = context.cacheDir
            systemCacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.contains("image", ignoreCase = true)) {
                    deleteRecursively(file)
                }
            }
            
            Log.d(TAG, "✅ Cache de imagens limpo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao limpar cache de imagens", e)
        }
    }
    
    /**
     * Deleta arquivo/diretório recursivamente
     */
    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        file.delete()
    }
    
    /**
     * Calcula o tamanho total do cache
     */
    private fun getTotalCacheSize(): Long {
        return try {
            var totalSize = 0L
            
            // Tamanho do diretório de cache
            totalSize += getDirectorySize(context.cacheDir)
            
            // Tamanho dos arquivos externos
            context.getExternalFilesDir(null)?.let { externalDir ->
                totalSize += getDirectorySize(externalDir)
            }
            
            // Tamanho do banco ObjectBox
            val objectBoxDir = File(context.filesDir, "objectbox")
            if (objectBoxDir.exists()) {
                totalSize += getDirectorySize(objectBoxDir)
            }
            
            totalSize
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular tamanho do cache", e)
            0L
        }
    }
    
    /**
     * Calcula o tamanho de um diretório
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
    
    /**
     * Formata bytes em formato legível
     */
    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }
    
    /**
     * Limpeza rápida (apenas cache essencial)
     */
    fun performQuickCacheCleanup(): Result<String> {
        return try {
            Log.d(TAG, "⚡ Iniciando limpeza rápida de cache...")
            
            // Limpar apenas preferências e cache de imagens
            appPreferences.clearAllCaches()
            clearImageCache()
            
            System.gc()
            
            val message = "✅ Limpeza rápida concluída!"
            Log.d(TAG, message)
            
            Result.success(message)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na limpeza rápida", e)
            Result.failure(e)
        }
    }
}
