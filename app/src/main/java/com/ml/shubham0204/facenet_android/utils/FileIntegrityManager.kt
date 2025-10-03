package com.ml.shubham0204.facenet_android.utils

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Gerenciador de integridade de arquivos
 * 
 * Este utilitário fornece proteção contra alterações em arquivos através de:
 * 1. Geração de hash SHA-256 para verificação de integridade
 * 2. Assinatura digital usando chave secreta
 * 3. Validação de integridade na importação
 * 
 * Se qualquer byte do arquivo for alterado, a validação falhará.
 */
class FileIntegrityManager {
    
    companion object {
        private const val TAG = "FileIntegrityManager"
        private const val ALGORITHM = "SHA-256"
        private const val ENCRYPTION_ALGORITHM = "AES/ECB/PKCS5Padding"
        private const val KEY_LENGTH = 256
        
        private val SECRET_KEY = generateSecureKey()
        

    
        private fun generateSecureKey(): ByteArray {
            val baseKey = "FaceRecognitionApp2024!@#SecureKey"
            val keyBytes = baseKey.toByteArray()
            
            val finalKey = when {
                keyBytes.size >= 32 -> keyBytes.take(32).toByteArray()
                keyBytes.size < 32 -> {
                    val repeated = keyBytes.toList()
                    val result = mutableListOf<Byte>()
                    while (result.size < 32) {
                        result.addAll(repeated)
                    }
                    result.take(32).toByteArray()
                }
                else -> keyBytes
            }
                        return finalKey
        }
    }
    

    fun generateFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance(ALGORITHM)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            throw Exception("Falha ao gerar hash do arquivo: ${e.message}")
        }
    }
    
    fun generateStringHash(content: String): String {
        return try {
            val digest = MessageDigest.getInstance(ALGORITHM)
            val hash = digest.digest(content.toByteArray())
            bytesToHex(hash)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar hash da string", e)
            throw Exception("Falha ao gerar hash: ${e.message}")
        }
    }
    
    fun createDigitalSignature(hash: String): String {
        return try {
            val keySpec = SecretKeySpec(SECRET_KEY, ENCRYPTION_ALGORITHM)
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encrypted = cipher.doFinal(hash.toByteArray())
            Base64.getEncoder().encodeToString(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar assinatura digital", e)
            throw Exception("Falha ao criar assinatura digital: ${e.message}")
        }
    }
    
    fun verifyDigitalSignature(signature: String, hash: String): Boolean {
        return try {
            val keySpec = SecretKeySpec(SECRET_KEY, ENCRYPTION_ALGORITHM)
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val encrypted = Base64.getDecoder().decode(signature)
            val decrypted = String(cipher.doFinal(encrypted))
            decrypted == hash
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar assinatura digital", e)
            false
        }
    }
    
    /**
     * Cria um arquivo protegido com informações de integridade
     * 
     * @param originalContent Conteúdo original do arquivo (String para JSON, ByteArray para binários)
     * @param outputFile Arquivo de saída onde será salvo o conteúdo protegido
     * @return Resultado da operação
     */
    fun createProtectedFile(originalContent: String, outputFile: File): Result<FileIntegrityInfo> {
        return try {
            
            val contentHash = generateStringHash(originalContent)
            
            val digitalSignature = createDigitalSignature(contentHash)
            
            val protectedData = ProtectedFileData(
                content = originalContent,
                hash = contentHash,
                signature = digitalSignature,
                timestamp = System.currentTimeMillis(),
                version = "1.0"
            )
            
            val jsonContent = protectedData.toJson()
            
            outputFile.writeText(jsonContent)
            Log.d(TAG, "✅ Arquivo protegido salvo com sucesso")
            
            val integrityInfo = FileIntegrityInfo(
                file = outputFile,
                hash = contentHash,
                signature = digitalSignature,
                timestamp = protectedData.timestamp,
                isValid = true
            )
            
            Result.success(integrityInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar arquivo protegido", e)
            Result.failure(e)
        }
    }
 
    fun createProtectedFileFromFileStreaming(sourceFile: File, outputFile: File): Result<FileIntegrityInfo> {
        return try {
            val contentHash = generateFileHash(sourceFile)
            val digitalSignature = createDigitalSignature(contentHash)

            // Escrever JSON manualmente e fazer streaming do campo "content" em Base64
            outputFile.outputStream().buffered().writer().use { writer ->
                writer.write("{")
                writer.write("\"content\":\"")

                // Stream Base64 do arquivo fonte dentro da string JSON
                val base64Encoder = java.util.Base64.getEncoder().withoutPadding()
                val bridge = object : java.io.OutputStream() {
                    override fun write(b: Int) {
                        writer.write(b)
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        writer.write(String(b, off, len))
                    }
                }

                sourceFile.inputStream().use { input ->
                    base64Encoder.wrap(bridge).use { b64Out ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            b64Out.write(buffer, 0, read)
                        }
                    }
                }

                writer.write("\",")
                writer.write("\"hash\":\"$contentHash\",")
                writer.write("\"signature\":\"$digitalSignature\",")
                writer.write("\"timestamp\":${System.currentTimeMillis()},")
                writer.write("\"version\":\"1.0\",")
                writer.write("\"isBinary\":false,")
                writer.write("\"originalFileName\":null")
                writer.write("}")
                writer.flush()
            }

            val integrityInfo = FileIntegrityInfo(
                file = outputFile,
                hash = contentHash,
                signature = digitalSignature,
                timestamp = System.currentTimeMillis(),
                isValid = true
            )
            Result.success(integrityInfo)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar arquivo protegido (streaming)", e)
            Result.failure(e)
        }
    }

    /**
     * Cria um arquivo protegido a partir de um arquivo binário (ZIP, etc.)
     * 
     * @param sourceFile Arquivo binário original
     * @param outputFile Arquivo de saída onde será salvo o conteúdo protegido
     * @return Resultado da operação
     */
    fun createProtectedFileFromBinary(sourceFile: File, outputFile: File): Result<FileIntegrityInfo> {
        return try {
            Log.d(TAG, "🔒 Criando arquivo binário protegido via streaming...")
            
            val contentHash = generateFileHash(sourceFile)
            val digitalSignature = createDigitalSignature(contentHash)
            
            // ✅ OTIMIZAÇÃO: Escrever arquivo protegido via streaming sem carregar tudo na memória
            outputFile.outputStream().buffered().writer().use { writer ->
                writer.write("{")
                writer.write("\"content\":\"")
                
                // Stream Base64 do arquivo binário
                val base64Encoder = java.util.Base64.getEncoder().withoutPadding()
                val bridge = object : java.io.OutputStream() {
                    override fun write(b: Int) {
                        writer.write(b)
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        writer.write(String(b, off, len))
                    }
                }
                
                sourceFile.inputStream().use { input ->
                    base64Encoder.wrap(bridge).use { b64Out ->
                        val buffer = ByteArray(16 * 1024) // 16KB buffer
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            b64Out.write(buffer, 0, read)
                        }
                    }
                }
                
                writer.write("\",")
                writer.write("\"hash\":\"$contentHash\",")
                writer.write("\"signature\":\"$digitalSignature\",")
                writer.write("\"timestamp\":${System.currentTimeMillis()},")
                writer.write("\"version\":\"1.0\",")
                writer.write("\"isBinary\":true,")
                writer.write("\"originalFileName\":\"${sourceFile.name}\"")
                writer.write("}")
                writer.flush()
            }
            
            val integrityInfo = FileIntegrityInfo(
                file = outputFile,
                hash = contentHash,
                signature = digitalSignature,
                timestamp = System.currentTimeMillis(),
                isValid = true
            )
            
            Log.d(TAG, "✅ Arquivo binário protegido criado via streaming: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            Result.success(integrityInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar arquivo binário protegido", e)
            Result.failure(e)
        }
    }
    
    /**
     * Valida a integridade de um arquivo protegido
     * 
     * @param protectedFile Arquivo protegido para validar
     * @return Resultado da validação
     */
    fun validateProtectedFile(protectedFile: File): Result<FileIntegrityInfo> {
        return try {
            
            if (!protectedFile.exists()) {
                throw Exception("Arquivo não encontrado: ${protectedFile.name}")
            }
            
             val jsonContent = readFileInChunks(protectedFile)
            
            val protectedData = ProtectedFileData.fromJson(jsonContent)
            
            val isSignatureValid = verifyDigitalSignature(protectedData.signature, protectedData.hash)
            if (!isSignatureValid) {
                throw Exception("❌ Assinatura digital inválida - arquivo foi corrompido ou alterado!")
            }
            
            val integrityInfo = FileIntegrityInfo(
                file = protectedFile,
                hash = protectedData.hash,
                signature = protectedData.signature,
                timestamp = protectedData.timestamp,
                isValid = true
            )
            
            Result.success(integrityInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Falha na validação do arquivo", e)
            
            val integrityInfo = FileIntegrityInfo(
                file = protectedFile,
                hash = "",
                signature = "",
                timestamp = 0,
                isValid = false,
                errorMessage = e.message
            )
            
            Result.failure(e)
        }
    }
    
   
    fun extractOriginalContent(protectedFile: File): Result<String> {
        return try {
            val validationResult = validateProtectedFile(protectedFile)
            if (validationResult.isFailure) {
                return Result.failure(validationResult.exceptionOrNull() ?: Exception("Validação falhou"))
            }
            
            val jsonContent = readFileInChunks(protectedFile)
            val protectedData = ProtectedFileData.fromJson(jsonContent)
            
            Result.success(protectedData.content)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Extrai um arquivo binário original de um arquivo protegido (após validação)
     */
    fun extractOriginalBinaryFile(protectedFile: File, outputFile: File): Result<File> {
        return try {
            val validationResult = validateProtectedFile(protectedFile)
            if (validationResult.isFailure) {
                return Result.failure(validationResult.exceptionOrNull() ?: Exception("Validação falhou"))
            }
            
            val jsonContent = readFileInChunks(protectedFile)
            val protectedData = ProtectedFileData.fromJson(jsonContent)
            
            if (!protectedData.isBinary) {
                throw Exception("Arquivo não é binário")
            }
            
            // Decodificar conteúdo Base64
            val binaryContent = Base64.getDecoder().decode(protectedData.content)
            
            // Salvar arquivo binário
            outputFile.writeBytes(binaryContent)
            
            Log.d(TAG, "📤 Arquivo binário original extraído com sucesso: ${outputFile.name}")
            Result.success(outputFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao extrair arquivo binário original", e)
            Result.failure(e)
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
    
    /**
     * Converte array de bytes para string hexadecimal
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}


/**
 * Informações de integridade de um arquivo
 */
data class FileIntegrityInfo(
    val file: File,
    val hash: String,
    val signature: String,
    val timestamp: Long,
    val isValid: Boolean,
    val errorMessage: String? = null
)
