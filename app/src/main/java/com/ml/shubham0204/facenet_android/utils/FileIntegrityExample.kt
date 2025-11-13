package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.util.Log
import com.ml.shubham0204.facenet_android.data.ObjectBoxStore
import java.io.File

/**
 * Exemplo de uso do sistema de proteção de integridade de arquivos
 * 
 * Este arquivo demonstra como usar o FileIntegrityManager para:
 * 1. Criar arquivos protegidos contra alterações
 * 2. Validar a integridade de arquivos na importação
 * 3. Detectar tentativas de modificação
 */
class FileIntegrityExample(private val context: Context) {
    
    companion object {
        private const val TAG = "FileIntegrityExample"
    }
    
    private val fileIntegrityManager = FileIntegrityManager()
    
    /**
     * Demonstra a criação de um arquivo protegido
     */
    suspend fun demonstrateProtectedFileCreation() {
        try {
            Log.d(TAG, "🔒 === DEMONSTRAÇÃO: Criação de Arquivo Protegido ===")
            
            // Conteúdo de exemplo (dados sensíveis)
            val sensitiveData = """
            {
                "funcionarios": [
                    {
                        "id": 1,
                        "nome": "João Silva",
                        "cpf": "12345678901",
                        "cargo": "Desenvolvedor"
                    }
                ],
                "configuracoes": {
                    "entidade": "Empresa XYZ",
                    "localizacao": "São Paulo"
                }
            }
            """.trimIndent()
            
            // Criar arquivo protegido
            val protectedFile = File(context.filesDir, "exemplo_protegido.json")
            val result = fileIntegrityManager.createProtectedFile(sensitiveData, protectedFile)
            
            if (result.isSuccess) {
                val integrityInfo = result.getOrThrow()
                Log.d(TAG, "✅ Arquivo protegido criado com sucesso!")
                Log.d(TAG, "📁 Arquivo: ${integrityInfo.file.name}")
                Log.d(TAG, "🔐 Hash: ${integrityInfo.hash.take(16)}...")
                Log.d(TAG, "⏰ Timestamp: ${integrityInfo.timestamp}")
                
                // Demonstrar validação
                demonstrateFileValidation(protectedFile)
                
            } else {
                Log.e(TAG, "❌ Falha ao criar arquivo protegido: ${result.exceptionOrNull()?.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na demonstração", e)
        }
    }
    
    /**
     * Demonstra a validação de integridade de um arquivo
     */
    private suspend fun demonstrateFileValidation(file: File) {
        try {
            Log.d(TAG, "🔍 === DEMONSTRAÇÃO: Validação de Integridade ===")
            
            // Validar arquivo original (deve passar)
            val validationResult = fileIntegrityManager.validateProtectedFile(file)
            if (validationResult.isSuccess) {
                Log.d(TAG, "✅ Validação bem-sucedida - arquivo íntegro!")
            } else {
                Log.e(TAG, "❌ Validação falhou: ${validationResult.exceptionOrNull()?.message}")
            }
            
            // Simular tentativa de alteração
            demonstrateTamperingDetection(file)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação", e)
        }
    }
    
    /**
     * Demonstra a detecção de tentativas de alteração
     */
    private suspend fun demonstrateTamperingDetection(originalFile: File) {
        try {
            Log.d(TAG, "⚠️ === DEMONSTRAÇÃO: Detecção de Alteração ===")
            
            // Criar uma cópia do arquivo
            val tamperedFile = File(context.filesDir, "exemplo_alterado.json")
            originalFile.copyTo(tamperedFile, overwrite = true)
            
            // Alterar o conteúdo do arquivo (simular tentativa de modificação)
            val currentContent = tamperedFile.readText()
            val tamperedContent = currentContent.replace("João Silva", "Maria Santos")
            tamperedFile.writeText(tamperedContent)
            
            Log.d(TAG, "🔧 Arquivo foi alterado (João Silva → Maria Santos)")
            
            // Tentar validar o arquivo alterado
            val validationResult = fileIntegrityManager.validateProtectedFile(tamperedFile)
            if (validationResult.isFailure) {
                Log.d(TAG, "🚨 ALTERAÇÃO DETECTADA! Validação falhou:")
                Log.d(TAG, "   Motivo: ${validationResult.exceptionOrNull()?.message}")
                Log.d(TAG, "   ✅ Sistema de proteção funcionando corretamente!")
            } else {
                Log.e(TAG, "❌ ERRO: Sistema não detectou a alteração!")
            }
            
            // Limpar arquivo de teste
            tamperedFile.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na demonstração de detecção", e)
        }
    }
    
    /**
     * Demonstra a extração de conteúdo de arquivo protegido
     */
    suspend fun demonstrateContentExtraction() {
        try {
            Log.d(TAG, "📤 === DEMONSTRAÇÃO: Extração de Conteúdo ===")
            
            // Criar arquivo protegido de exemplo
            val exampleContent = """
            {
                "dados_importantes": {
                    "usuario": "admin",
                    "senha": "senha123",
                    "configuracoes": {
                        "tema": "escuro",
                        "idioma": "pt-BR"
                    }
                }
            }
            """.trimIndent()
            
            val protectedFile = File(context.filesDir, "exemplo_extracao.json")
            fileIntegrityManager.createProtectedFile(exampleContent, protectedFile)
            
            // Extrair conteúdo original
            val extractionResult = fileIntegrityManager.extractOriginalContent(protectedFile)
            if (extractionResult.isSuccess) {
                val extractedContent = extractionResult.getOrThrow()
                Log.d(TAG, "✅ Conteúdo extraído com sucesso!")
                Log.d(TAG, "📄 Conteúdo: ${extractedContent.take(100)}...")
            } else {
                Log.e(TAG, "❌ Falha na extração: ${extractionResult.exceptionOrNull()?.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na demonstração de extração", e)
        }
    }
    
    /**
     * Executa todas as demonstrações
     */
    suspend fun runAllDemonstrations() {
        Log.d(TAG, "🚀 Iniciando demonstrações do sistema de proteção de integridade...")
        
        demonstrateProtectedFileCreation()
        demonstrateContentExtraction()
        
        Log.d(TAG, "✅ Todas as demonstrações concluídas!")
    }
}

/**
 * Exemplo de uso prático no contexto do aplicativo
 */
class BackupIntegrityExample(private val context: Context) {
    
    companion object {
        private const val TAG = "BackupIntegrityExample"
    }
    
  
    suspend fun demonstrateBackupProtection() {
        try {
            
            val backupService = com.ml.shubham0204.facenet_android.data.BackupService(context, ObjectBoxStore.store)
            

            val backupResult = backupService.createBackup()
            if (backupResult.isSuccess) {
                val backupPath = backupResult.getOrThrow()
                Log.d(TAG, "✅ Backup protegido criado: $backupPath")
                
                val restoreResult = backupService.restoreBackup(backupPath)
                if (restoreResult.isSuccess) {
                    Log.d(TAG, "✅ Backup restaurado com sucesso - integridade validada!")
                } else {
                    Log.e(TAG, "❌ Falha na restauração: ${restoreResult.exceptionOrNull()?.message}")
                }
            } else {
                Log.e(TAG, "❌ Falha ao criar backup: ${backupResult.exceptionOrNull()?.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na demonstração de backup", e)
        }
    }
}
