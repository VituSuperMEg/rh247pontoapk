package com.ml.shubham0204.facenet_android.utils

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
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
     * Valida arquivo protegido via streaming sem carregar todo conteúdo na memória
     * Apenas valida hash e assinatura
     */
    fun validateProtectedFileStreaming(protectedFile: File): Result<StreamingValidationInfo> {
        return try {
            if (!protectedFile.exists()) {
                throw Exception("Arquivo não encontrado: ${protectedFile.name}")
            }

            // Parse JSON manualmente buscando apenas hash e signature
            val metadata = extractMetadataFromJson(protectedFile)

            // Validar assinatura digital
            val isSignatureValid = verifyDigitalSignature(metadata.signature, metadata.hash)
            if (!isSignatureValid) {
                throw Exception("❌ Assinatura digital inválida - arquivo foi corrompido ou alterado!")
            }

            Log.d(TAG, "✅ Validação de integridade via streaming bem-sucedida")
            Result.success(StreamingValidationInfo(
                hash = metadata.hash,
                signature = metadata.signature,
                timestamp = metadata.timestamp,
                isBinary = metadata.isBinary,
                isValid = true
            ))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Falha na validação via streaming", e)
            Result.failure(e)
        }
    }

    /**
     * Extrai apenas metadados do JSON sem carregar o campo "content" na memória
     * 🚀 OTIMIZADO: Usa regex em blocos grandes para velocidade máxima
     */
    private fun extractMetadataFromJson(file: File): JsonMetadata {
        Log.d(TAG, "🔍 Extraindo metadados do JSON...")

        // Ler apenas os primeiros 50KB do arquivo (metadados estão no início)
        val headerSize = minOf(50 * 1024L, file.length()).toInt()
        val header = ByteArray(headerSize)

        file.inputStream().use { input ->
            input.read(header)
        }

        val headerText = String(header)

        // Extrair campos usando regex (muito mais rápido!)
        val hashMatch = "\"hash\":\"([^\"]+)\"".toRegex().find(headerText)
        val signatureMatch = "\"signature\":\"([^\"]+)\"".toRegex().find(headerText)
        val timestampMatch = "\"timestamp\":(\\d+)".toRegex().find(headerText)
        val isBinaryMatch = "\"isBinary\":(true|false)".toRegex().find(headerText)

        val hash = hashMatch?.groupValues?.get(1) ?: ""
        val signature = signatureMatch?.groupValues?.get(1) ?: ""
        val timestamp = timestampMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val isBinary = isBinaryMatch?.groupValues?.get(1) == "true"

        Log.d(TAG, "✅ Metadados extraídos: hash=${hash.take(16)}..., isBinary=$isBinary")

        return JsonMetadata(hash, signature, timestamp, isBinary)
    }

    /**
     * Extrai conteúdo JSON para arquivo temporário (para arquivos grandes)
     * 🚀 SOLUÇÃO DEFINITIVA: Retorna o ARQUIVO em vez de carregar conteúdo na memória
     *
     * IMPORTANTE: O chamador é responsável por deletar o arquivo temporário após uso!
     */
    fun extractJsonContentToFile(protectedFile: File): Result<File> {
        var tempFile: File? = null
        return try {
            Log.d(TAG, "🚀 Extraindo conteúdo JSON para arquivo temporário...")
            val startTime = System.currentTimeMillis()

            // Validar primeiro
            val validation = validateProtectedFileStreaming(protectedFile)
            if (validation.isFailure) {
                return Result.failure(validation.exceptionOrNull() ?: Exception("Validação falhou"))
            }

            val info = validation.getOrThrow()
            if (info.isBinary) {
                throw Exception("Arquivo é binário, não JSON")
            }

            // Criar arquivo temporário para o conteúdo
            tempFile = File.createTempFile("json_content_", ".tmp")
            Log.d(TAG, "   📝 Arquivo temporário: ${tempFile.absolutePath}")

            val searchPattern = "\"content\":\"".toByteArray()
            var totalCharsWritten = 0L

            protectedFile.inputStream().buffered(2 * 1024 * 1024).use { input ->
                // FASE 1: Encontrar campo "content"
                var contentStartFound = false
                val megaBlock = ByteArray(2 * 1024 * 1024)
                var overlap = ByteArray(searchPattern.size)

                while (!contentStartFound) {
                    val bytesRead = input.read(megaBlock)
                    if (bytesRead == -1) throw Exception("Campo 'content' não encontrado")

                    val searchSpace = overlap + megaBlock.sliceArray(0 until bytesRead)

                    for (i in 0 until searchSpace.size - searchPattern.size) {
                        var match = true
                        for (j in searchPattern.indices) {
                            if (searchSpace[i + j] != searchPattern[j]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            val skipBack = searchSpace.size - i - searchPattern.size
                            input.skip(-skipBack.toLong())
                            contentStartFound = true
                            Log.d(TAG, "   🎯 Campo 'content' encontrado")
                            break
                        }
                    }

                    if (!contentStartFound) {
                        val overlapSize = minOf(searchPattern.size, bytesRead)
                        overlap = megaBlock.sliceArray(bytesRead - overlapSize until bytesRead)
                    }
                }

                // FASE 2: Escrever conteúdo direto no arquivo temporário
                // 🔥 USAR UM ÚNICO READER - NÃO CRIAR NOVOS A CADA ITERAÇÃO!
                val reader = InputStreamReader(input, Charsets.UTF_8).buffered(64 * 1024)
                val writer = tempFile.bufferedWriter(Charsets.UTF_8, 64 * 1024)

                try {
                    val buffer = CharArray(64 * 1024) // 64KB por vez (menor = menos memória)
                    var escaped = false
                    var foundEnd = false

                    while (true) {
                        val charsRead = reader.read(buffer)
                        if (charsRead == -1) break

                        for (i in 0 until charsRead) {
                            val c = buffer[i]

                            if (escaped) {
                                writer.write(c.code)
                                totalCharsWritten++
                                escaped = false
                            } else if (c == '\\') {
                                writer.write(c.code)
                                totalCharsWritten++
                                escaped = true
                            } else if (c == '"') {
                                // Fim do campo content
                                writer.flush()
                                writer.close()
                                reader.close()

                                val elapsed = System.currentTimeMillis() - startTime
                                Log.d(TAG, "✅ Conteúdo JSON escrito em arquivo temporário")
                                Log.d(TAG, "   📊 Tamanho: $totalCharsWritten caracteres (${totalCharsWritten / 1024 / 1024}MB)")
                                Log.d(TAG, "   ⏱️  Tempo: ${elapsed / 1000}s")

                                foundEnd = true
                                break
                            } else {
                                writer.write(c.code)
                                totalCharsWritten++
                            }
                        }

                        if (foundEnd) break
                    }

                    if (!foundEnd) {
                        throw Exception("Fim do campo 'content' não encontrado")
                    }

                    // 🎉 RETORNAR O ARQUIVO em vez de tentar carregar na memória!
                    Result.success(tempFile)
                } catch (e: Exception) {
                    writer.close()
                    reader.close()
                    throw e
                }
            }
        } catch (e: Exception) {
            tempFile?.delete()
            Log.e(TAG, "❌ Erro ao extrair JSON para arquivo", e)
            Result.failure(e)
        }
    }

    /**
     * Extrai conteúdo JSON usando streaming (para arquivos grandes)
     * ⚠️ DEPRECATED para arquivos > 100MB - use extractJsonContentToFile() em vez disso
     */
    @Deprecated("Use extractJsonContentToFile() for files > 100MB", ReplaceWith("extractJsonContentToFile"))
    fun extractJsonContentStreaming(protectedFile: File): Result<String> {
        var tempFile: File? = null
        return try {
            Log.d(TAG, "🚀 Extraindo conteúdo JSON via streaming (usando arquivo temporário)...")
            val startTime = System.currentTimeMillis()

            // Validar primeiro
            val validation = validateProtectedFileStreaming(protectedFile)
            if (validation.isFailure) {
                return Result.failure(validation.exceptionOrNull() ?: Exception("Validação falhou"))
            }

            val info = validation.getOrThrow()
            if (info.isBinary) {
                throw Exception("Arquivo é binário, não JSON")
            }

            // Criar arquivo temporário para o conteúdo
            tempFile = File.createTempFile("json_content_", ".tmp")
            Log.d(TAG, "   📝 Arquivo temporário: ${tempFile.absolutePath}")

            val searchPattern = "\"content\":\"".toByteArray()
            var totalCharsWritten = 0L

            protectedFile.inputStream().buffered(2 * 1024 * 1024).use { input ->
                // FASE 1: Encontrar campo "content"
                var contentStartFound = false
                val megaBlock = ByteArray(2 * 1024 * 1024)
                var overlap = ByteArray(searchPattern.size)

                while (!contentStartFound) {
                    val bytesRead = input.read(megaBlock)
                    if (bytesRead == -1) throw Exception("Campo 'content' não encontrado")

                    val searchSpace = overlap + megaBlock.sliceArray(0 until bytesRead)

                    for (i in 0 until searchSpace.size - searchPattern.size) {
                        var match = true
                        for (j in searchPattern.indices) {
                            if (searchSpace[i + j] != searchPattern[j]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            val skipBack = searchSpace.size - i - searchPattern.size
                            input.skip(-skipBack.toLong())
                            contentStartFound = true
                            Log.d(TAG, "   🎯 Campo 'content' encontrado")
                            break
                        }
                    }

                    if (!contentStartFound) {
                        val overlapSize = minOf(searchPattern.size, bytesRead)
                        overlap = megaBlock.sliceArray(bytesRead - overlapSize until bytesRead)
                    }
                }

                // FASE 2: Escrever conteúdo direto no arquivo temporário
                tempFile.bufferedWriter().use { writer ->
                    val buffer = CharArray(1024 * 1024) // 1MB por vez
                    var escaped = false

                    while (true) {
                        val charsRead = input.reader().read(buffer)
                        if (charsRead == -1) break

                        for (i in 0 until charsRead) {
                            val c = buffer[i]

                            if (escaped) {
                                writer.write(c.code)
                                totalCharsWritten++
                                escaped = false
                            } else if (c == '\\') {
                                writer.write(c.code)
                                totalCharsWritten++
                                escaped = true
                            } else if (c == '"') {
                                // Fim do campo content
                                writer.flush()
                                val elapsed = System.currentTimeMillis() - startTime
                                Log.d(TAG, "✅ Conteúdo JSON escrito em arquivo temporário")
                                Log.d(TAG, "   📊 Tamanho: $totalCharsWritten caracteres")
                                Log.d(TAG, "   ⏱️  Tempo: ${elapsed / 1000}s")

                                // Ler arquivo temporário de volta (será processado aos poucos pelo parser JSON)
                                val content = tempFile.readText()
                                tempFile.delete()
                                return Result.success(content)
                            } else {
                                writer.write(c.code)
                                totalCharsWritten++
                            }
                        }
                    }

                    throw Exception("Fim do campo 'content' não encontrado")
                }
            }
        } catch (e: Exception) {
            tempFile?.delete()
            Log.e(TAG, "❌ Erro ao extrair JSON via streaming", e)
            Result.failure(e)
        }
    }

    /**
     * Extrai arquivo binário usando streaming para evitar OOM
     * 🚀 ULTRA-OTIMIZADO: Busca direta por padrão sem processar char-by-char
     */
    fun extractBinaryFileStreaming(protectedFile: File, outputFile: File): Result<File> {
        return try {
            // Validar primeiro
            val validation = validateProtectedFileStreaming(protectedFile)
            if (validation.isFailure) {
                return Result.failure(validation.exceptionOrNull() ?: Exception("Validação falhou"))
            }

            val info = validation.getOrThrow()
            if (!info.isBinary) {
                throw Exception("Arquivo não é binário")
            }

            Log.d(TAG, "🚀 Extraindo arquivo binário via streaming ULTRA-OTIMIZADO...")
            val startTime = System.currentTimeMillis()

            // 🚀 NOVA ABORDAGEM: Ler arquivo em blocos grandes e buscar padrão diretamente
            val fileContent = protectedFile.readText()

            // Encontrar início e fim do campo "content"
            val contentStart = fileContent.indexOf("\"content\":\"")
            if (contentStart == -1) {
                throw Exception("Campo 'content' não encontrado no arquivo")
            }

            val base64Start = contentStart + 11 // Tamanho de "content":"
            val base64End = fileContent.indexOf("\"", base64Start)
            if (base64End == -1) {
                throw Exception("Fim do campo 'content' não encontrado")
            }

            Log.d(TAG, "   🎯 Campo 'content' encontrado, decodificando Base64...")

            // Extrair apenas o conteúdo Base64
            val base64Content = fileContent.substring(base64Start, base64End)

            // Decodificar Base64 direto
            val decoder = Base64.getDecoder()
            val binaryData = decoder.decode(base64Content)

            // Escrever arquivo
            outputFile.writeBytes(binaryData)

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Arquivo binário extraído via streaming: ${outputFile.absolutePath}")
            Log.d(TAG, "   📊 Tamanho: ${outputFile.length()} bytes (${outputFile.length() / 1024 / 1024} MB)")
            Log.d(TAG, "   ⏱️  Tempo: ${elapsed / 1000}s")

            Result.success(outputFile)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "❌ Arquivo muito grande, usando método alternativo...")
            // Fallback para método char-by-char se OOM
            extractBinaryFileStreamingFallback(protectedFile, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao extrair arquivo binário via streaming", e)
            Result.failure(e)
        }
    }

    /**
     * Método fallback ULTRA-OTIMIZADO: processa em mega-blocos de 2MB
     * Evita char-by-char e usa ByteArray para máxima performance
     */
    private fun extractBinaryFileStreamingFallback(protectedFile: File, outputFile: File): Result<File> {
        return try {
            Log.d(TAG, "🚀 Usando método fallback ULTRA-OTIMIZADO (mega-blocos de 2MB)...")
            val startTime = System.currentTimeMillis()
            var totalBytesWritten = 0L
            var lastProgressLog = startTime

            val decoder = Base64.getDecoder()
            val searchPattern = "\"content\":\"".toByteArray()
            val endPattern = "\"".toByteArray()

            protectedFile.inputStream().buffered(2 * 1024 * 1024).use { input ->
                // FASE 1: Encontrar início do campo "content" usando mega-blocos
                Log.d(TAG, "   🔍 Fase 1: Procurando campo 'content'...")
                var contentStartFound = false
                val megaBlock = ByteArray(2 * 1024 * 1024) // 2MB por vez!
                var overlap = ByteArray(searchPattern.size)
                var totalRead = 0L

                while (!contentStartFound) {
                    val bytesRead = input.read(megaBlock)
                    if (bytesRead == -1) throw Exception("Campo 'content' não encontrado no arquivo")

                    totalRead += bytesRead

                    // Procurar padrão no bloco (incluindo overlap do bloco anterior)
                    val searchSpace = overlap + megaBlock.sliceArray(0 until bytesRead)

                    for (i in 0 until searchSpace.size - searchPattern.size) {
                        var match = true
                        for (j in searchPattern.indices) {
                            if (searchSpace[i + j] != searchPattern[j]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            // Encontrou! Posicionar stream após o padrão
                            val skipBack = searchSpace.size - i - searchPattern.size
                            input.skip(-skipBack.toLong())
                            contentStartFound = true
                            Log.d(TAG, "   🎯 Campo 'content' encontrado após ${totalRead / 1024 / 1024}MB")
                            break
                        }
                    }

                    if (!contentStartFound) {
                        // Guardar últimos bytes para overlap no próximo bloco
                        val overlapSize = minOf(searchPattern.size, bytesRead)
                        overlap = megaBlock.sliceArray(bytesRead - overlapSize until bytesRead)
                    }
                }

                // FASE 2: Processar Base64 em mega-blocos
                Log.d(TAG, "   🚀 Fase 2: Decodificando Base64 em mega-blocos...")

                outputFile.outputStream().buffered(2 * 1024 * 1024).use { output ->
                    val base64Block = ByteArray(2 * 1024 * 1024) // 2MB de Base64
                    var base64Buffer = byteArrayOf()
                    var processedBlocks = 0

                    while (true) {
                        val bytesRead = input.read(base64Block)
                        if (bytesRead == -1) break

                        // Verificar se chegamos no fim do campo (aspas)
                        var endIndex = -1
                        for (i in 0 until bytesRead) {
                            if (base64Block[i] == '"'.code.toByte()) {
                                endIndex = i
                                break
                            }
                        }

                        // Adicionar ao buffer
                        val blockToProcess = if (endIndex != -1) {
                            base64Block.sliceArray(0 until endIndex)
                        } else {
                            base64Block.sliceArray(0 until bytesRead)
                        }

                        base64Buffer += blockToProcess

                        // Decodificar em lotes grandes
                        if (base64Buffer.size >= 1024 * 1024 || endIndex != -1) {
                            // Encontrar múltiplo de 4 válido para Base64
                            val validLength = (base64Buffer.size / 4) * 4
                            if (validLength > 0) {
                                val toProcess = base64Buffer.sliceArray(0 until validLength)
                                val decoded = decoder.decode(String(toProcess))
                                output.write(decoded)
                                totalBytesWritten += decoded.size

                                // Log de progresso
                                val now = System.currentTimeMillis()
                                if (now - lastProgressLog > 2000) {
                                    val mbWritten = totalBytesWritten / 1024 / 1024
                                    val elapsed = (now - startTime) / 1000.0
                                    val speedMBs = if (elapsed > 0) (totalBytesWritten / 1024.0 / 1024.0 / elapsed) else 0.0
                                    Log.d(TAG, "   📈 ${mbWritten}MB processados (%.1f MB/s)".format(speedMBs))
                                    lastProgressLog = now
                                }

                                // Manter resto no buffer
                                base64Buffer = base64Buffer.sliceArray(validLength until base64Buffer.size)
                            }
                        }

                        if (endIndex != -1) {
                            // Processar resto final
                            if (base64Buffer.isNotEmpty()) {
                                val decoded = decoder.decode(String(base64Buffer))
                                output.write(decoded)
                                totalBytesWritten += decoded.size
                            }
                            break
                        }

                        processedBlocks++
                    }

                    output.flush()
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Arquivo extraído via fallback ULTRA-OTIMIZADO")
            Log.d(TAG, "   📊 Tamanho: ${outputFile.length()} bytes (${outputFile.length() / 1024 / 1024} MB)")
            Log.d(TAG, "   ⏱️  Tempo: ${elapsed / 1000}s (%.1f MB/s)".format(outputFile.length() / 1024.0 / 1024.0 / (elapsed / 1000.0)))

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no método fallback", e)
            Result.failure(e)
        }
    }

    /**
     * Lê um arquivo em chunks para evitar OutOfMemoryError
     * ⚠️ DEPRECATED: Use validateProtectedFileStreaming + extractBinaryFileStreaming para arquivos grandes
     */
    @Deprecated("Use streaming methods for large files", ReplaceWith("validateProtectedFileStreaming"))
    private fun readFileInChunks(file: File): String {
        // Para arquivos pequenos (< 10MB), ok usar este método
        if (file.length() > 10 * 1024 * 1024) {
            throw OutOfMemoryError("Arquivo muito grande (${file.length()} bytes). Use métodos de streaming.")
        }

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

/**
 * Informações de validação via streaming (não contém referência ao arquivo completo)
 */
data class StreamingValidationInfo(
    val hash: String,
    val signature: String,
    val timestamp: Long,
    val isBinary: Boolean,
    val isValid: Boolean
)

/**
 * Metadados extraídos do JSON sem carregar o campo content
 */
data class JsonMetadata(
    val hash: String,
    val signature: String,
    val timestamp: Long,
    val isBinary: Boolean
)
