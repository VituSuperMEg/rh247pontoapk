package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.util.Log
import com.ml.shubham0204.facenet_android.data.BackupService
import java.io.File

/**
 * Helper para testar o sistema de backup e identificar problemas
 */
class BackupTestHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "BackupTestHelper"
    }
    
    private val backupService = BackupService(context)
    private val fileIntegrityManager = FileIntegrityManager()
    
    /**
     * Testa a criação e restauração de backup
     */
    suspend fun testBackupSystem() {
        try {
            Log.d(TAG, "🧪 === INICIANDO TESTE DO SISTEMA DE BACKUP ===")
            
            // 1. Criar backup
            Log.d(TAG, "1️⃣ Criando backup...")
            val createResult = backupService.createBackup()
            if (createResult.isFailure) {
                Log.e(TAG, "❌ Falha ao criar backup: ${createResult.exceptionOrNull()?.message}")
                return
            }
            
            val backupPath = createResult.getOrThrow()
            Log.d(TAG, "✅ Backup criado: $backupPath")
            
            // 2. Verificar arquivo
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                Log.e(TAG, "❌ Arquivo de backup não existe: $backupPath")
                return
            }
            
            Log.d(TAG, "📁 Arquivo encontrado: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            
            // 3. Verificar se é arquivo protegido
            val content = backupFile.readText()
            Log.d(TAG, "📄 Conteúdo do arquivo: ${content.length} caracteres")
            Log.d(TAG, "🔍 Primeiros 200 caracteres: ${content.take(200)}")
            
            // 4. Tentar parsear como ProtectedFileData
            try {
                val protectedData = ProtectedFileData.fromJson(content)
                Log.d(TAG, "✅ Arquivo protegido parseado com sucesso:")
                Log.d(TAG, "   - isBinary: ${protectedData.isBinary}")
                Log.d(TAG, "   - originalFileName: ${protectedData.originalFileName}")
                Log.d(TAG, "   - timestamp: ${protectedData.timestamp}")
                Log.d(TAG, "   - version: ${protectedData.version}")
                Log.d(TAG, "   - hash: ${protectedData.hash.take(16)}...")
                Log.d(TAG, "   - signature: ${protectedData.signature.take(16)}...")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao parsear arquivo protegido: ${e.message}")
                return
            }
            
            // 5. Testar validação de integridade
            Log.d(TAG, "2️⃣ Testando validação de integridade...")
            val validationResult = fileIntegrityManager.validateProtectedFile(backupFile)
            if (validationResult.isFailure) {
                Log.e(TAG, "❌ Falha na validação: ${validationResult.exceptionOrNull()?.message}")
                return
            }
            
            Log.d(TAG, "✅ Validação de integridade passou")
            
            // 6. Testar restauração
            Log.d(TAG, "3️⃣ Testando restauração...")
            val restoreResult = backupService.restoreBackup(backupPath)
            if (restoreResult.isFailure) {
                Log.e(TAG, "❌ Falha na restauração: ${restoreResult.exceptionOrNull()?.message}")
                return
            }
            
            Log.d(TAG, "✅ Restauração bem-sucedida")
            
            Log.d(TAG, "🎉 === TESTE CONCLUÍDO COM SUCESSO ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro durante o teste: ${e.message}", e)
        }
    }
    
    /**
     * Testa apenas a validação de um arquivo específico
     */
    suspend fun testFileValidation(filePath: String) {
        try {
            Log.d(TAG, "🔍 === TESTANDO VALIDAÇÃO DE ARQUIVO ===")
            Log.d(TAG, "📁 Arquivo: $filePath")
            
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "❌ Arquivo não existe: $filePath")
                return
            }
            
            Log.d(TAG, "📊 Tamanho: ${file.length()} bytes")
            
            // Tentar ler conteúdo
            val content = file.readText()
            Log.d(TAG, "📄 Conteúdo: ${content.length} caracteres")
            Log.d(TAG, "🔍 Primeiros 200 caracteres: ${content.take(200)}")
            
            // Tentar parsear
            try {
                val protectedData = ProtectedFileData.fromJson(content)
                Log.d(TAG, "✅ Parse bem-sucedido:")
                Log.d(TAG, "   - isBinary: ${protectedData.isBinary}")
                Log.d(TAG, "   - originalFileName: ${protectedData.originalFileName}")
                Log.d(TAG, "   - timestamp: ${protectedData.timestamp}")
                Log.d(TAG, "   - version: ${protectedData.version}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no parse: ${e.message}")
                return
            }
            
            // Testar validação
            val validationResult = fileIntegrityManager.validateProtectedFile(file)
            if (validationResult.isFailure) {
                Log.e(TAG, "❌ Validação falhou: ${validationResult.exceptionOrNull()?.message}")
            } else {
                Log.d(TAG, "✅ Validação passou")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro durante teste de validação: ${e.message}", e)
        }
    }
}
