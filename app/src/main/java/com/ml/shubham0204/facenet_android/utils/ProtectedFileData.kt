package com.ml.shubham0204.facenet_android.utils

import java.io.File
import java.util.Base64

/**
 * Dados de um arquivo protegido
 */
data class ProtectedFileData(
    val content: String,
    val hash: String,
    val signature: String,
    val timestamp: Long,
    val version: String,
    val isBinary: Boolean = false,
    val originalFileName: String? = null
) {
    fun toJson(): String {
        // Codificar o conteúdo em Base64 para evitar problemas de escape
        val encodedContent = Base64.getEncoder().encodeToString(content.toByteArray())
        
        return """
        {
            "content": "$encodedContent",
            "hash": "$hash",
            "signature": "$signature",
            "timestamp": $timestamp,
            "version": "$version",
            "isBinary": $isBinary,
            "originalFileName": ${originalFileName?.let { "\"$it\"" } ?: "null"}
        }
        """.trimIndent()
    }
    
    companion object {
        fun fromJson(json: String): ProtectedFileData {
            return try {
                // Para arquivos muito grandes, usar parser em streaming
                if (json.length > 20_000_000) { // 20MB
                    return fromJsonStreaming(json)
                }
                
                // Parse simples do JSON para arquivos menores
                val contentMatch = Regex("\"content\":\\s*\"([^\"]*(?:\\\\.[^\"]*)*)\"").find(json)
                val hashMatch = Regex("\"hash\":\\s*\"([^\"]+)\"").find(json)
                val signatureMatch = Regex("\"signature\":\\s*\"([^\"]+)\"").find(json)
                val timestampMatch = Regex("\"timestamp\":\\s*(\\d+)").find(json)
                val versionMatch = Regex("\"version\":\\s*\"([^\"]+)\"").find(json)
                val isBinaryMatch = Regex("\"isBinary\":\\s*(true|false)").find(json)
                val originalFileNameMatch = Regex("\"originalFileName\":\\s*(?:\"([^\"]+)\"|null)").find(json)
                
                if (contentMatch == null || hashMatch == null || signatureMatch == null || 
                    timestampMatch == null || versionMatch == null) {
                    throw Exception("Formato JSON inválido")
                }
                
                // Decodificar o conteúdo Base64 de forma segura
                val encodedContent = contentMatch.groupValues[1]
                val isBinary = isBinaryMatch?.groupValues?.get(1)?.toBoolean() ?: false
                
                // Para arquivos binários grandes, não decodificar o conteúdo aqui
                val content = if (isBinary && encodedContent.length > 10_000_000) { // 10MB
                    encodedContent
                } else {
                    try {
                        String(Base64.getDecoder().decode(encodedContent))
                    } catch (e: OutOfMemoryError) {
                        encodedContent
                    }
                }
                
                ProtectedFileData(
                    content = content,
                    hash = hashMatch.groupValues[1],
                    signature = signatureMatch.groupValues[1],
                    timestamp = timestampMatch.groupValues[1].toLong(),
                    version = versionMatch.groupValues[1],
                    isBinary = isBinary,
                    originalFileName = originalFileNameMatch?.groupValues?.get(1)
                )
            } catch (e: OutOfMemoryError) {
                // Se der OOM no parsing normal, tentar streaming
                fromJsonStreaming(json)
            }
        }
        
        /**
         * Parser JSON em streaming para arquivos muito grandes
         */
        private fun fromJsonStreaming(json: String): ProtectedFileData {
            try {
                android.util.Log.d("ProtectedFileData", "🔍 Iniciando parsing streaming de JSON (${json.length} caracteres)")
                android.util.Log.d("ProtectedFileData", "📄 Primeiros 200 caracteres: ${json.take(200)}")
                
                // Primeiro, extrair o campo content que é o mais importante
                // Tentar diferentes variações do campo content
                var contentStart = json.indexOf("\"content\":\"")
                android.util.Log.d("ProtectedFileData", "🔍 Posição do campo content (\"content\":\"): $contentStart")
                
                if (contentStart == -1) {
                    // Tentar sem as aspas duplas
                    contentStart = json.indexOf("content\":\"")
                    android.util.Log.d("ProtectedFileData", "🔍 Posição do campo content (content\":\"): $contentStart")
                }
                
                if (contentStart == -1) {
                    // Tentar apenas "content"
                    contentStart = json.indexOf("content")
                    android.util.Log.d("ProtectedFileData", "🔍 Posição do campo content (content): $contentStart")
                    
                    if (contentStart != -1) {
                        // Verificar se é realmente o campo content
                        val context = json.substring(maxOf(0, contentStart - 10), minOf(json.length, contentStart + 20))
                        android.util.Log.d("ProtectedFileData", "📄 Contexto ao redor de 'content': '$context'")
                        
                        // Ajustar para incluir as aspas
                        if (json.substring(contentStart - 1, contentStart + 8) == "\"content\"") {
                            contentStart = contentStart - 1
                            android.util.Log.d("ProtectedFileData", "🔍 Posição ajustada para incluir aspas: $contentStart")
                        }
                    }
                }
                
                if (contentStart == -1) {
                    android.util.Log.e("ProtectedFileData", "❌ Campo 'content' não encontrado no JSON")
                    android.util.Log.e("ProtectedFileData", "📄 JSON contém 'content': ${json.contains("content")}")
                    android.util.Log.e("ProtectedFileData", "📄 JSON contém '\"content\"': ${json.contains("\"content\"")}")
                    android.util.Log.e("ProtectedFileData", "📄 JSON contém 'content\":': ${json.contains("content\":")}")
                    
                    // Verificar se há caracteres especiais
                    val firstChar = json.firstOrNull()
                    val firstChars = json.take(10).map { it.code }.joinToString(", ")
                    android.util.Log.e("ProtectedFileData", "📄 Primeiro caractere: '$firstChar' (código: ${firstChar?.code})")
                    android.util.Log.e("ProtectedFileData", "📄 Primeiros 10 códigos de caracteres: [$firstChars]")
                    
                    // Mostrar mais contexto do JSON
                    android.util.Log.e("ProtectedFileData", "📄 Primeiros 500 caracteres: ${json.take(500)}")
                    
                    // Tentar uma abordagem alternativa - procurar por padrões
                    val patterns = listOf(
                        "\"content\"",
                        "content",
                        "Content",
                        "CONTENT"
                    )
                    
                    for (pattern in patterns) {
                        val pos = json.indexOf(pattern)
                        if (pos != -1) {
                            android.util.Log.e("ProtectedFileData", "📄 Padrão '$pattern' encontrado na posição: $pos")
                            val context = json.substring(maxOf(0, pos - 5), minOf(json.length, pos + 15))
                            android.util.Log.e("ProtectedFileData", "📄 Contexto: '$context'")
                        }
                    }
                    
                    throw Exception("Campo 'content' não encontrado")
                }
                
                val contentStartPos = contentStart + 11 // Tamanho de "\"content\":\""
                val contentEnd = json.lastIndexOf("\"", json.length - 2) // Última aspas antes do }
                
                if (contentEnd == -1 || contentEnd <= contentStartPos) {
                    throw Exception("Formato de conteúdo inválido")
                }
                
                val encodedContent = json.substring(contentStartPos, contentEnd)
                android.util.Log.d("ProtectedFileData", "✅ Content extraído: ${encodedContent.length} caracteres")
                
                // Extrair metadados sem usar Regex pesado
                val hash = extractValue(json, "\"hash\":", "\"")
                android.util.Log.d("ProtectedFileData", "✅ Hash extraído: ${hash.take(20)}...")
                
                val signature = extractValue(json, "\"signature\":", "\"")
                android.util.Log.d("ProtectedFileData", "✅ Signature extraído: ${signature.take(20)}...")
                
                val timestamp = extractValue(json, "\"timestamp\":", ",", "}").toLong()
                android.util.Log.d("ProtectedFileData", "✅ Timestamp extraído: $timestamp")
                
                val version = extractValue(json, "\"version\":", "\"")
                android.util.Log.d("ProtectedFileData", "✅ Version extraído: $version")
                
                val isBinary = extractValue(json, "\"isBinary\":", ",", "}").toBoolean()
                android.util.Log.d("ProtectedFileData", "✅ IsBinary extraído: $isBinary")
                
                val originalFileName = try {
                    extractValue(json, "\"originalFileName\":", ",", "}").let { 
                        if (it == "null") null else it.removeSurrounding("\"")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ProtectedFileData", "⚠️ Campo originalFileName não encontrado ou inválido: ${e.message}")
                    // Campo opcional, pode não existir
                    null
                }
                android.util.Log.d("ProtectedFileData", "✅ OriginalFileName extraído: $originalFileName")
                
                // Para arquivos binários grandes, retornar o conteúdo codificado
                val content = if (isBinary && encodedContent.length > 10_000_000) {
                    encodedContent
                } else {
                    try {
                        String(Base64.getDecoder().decode(encodedContent))
                    } catch (e: OutOfMemoryError) {
                        encodedContent
                    }
                }
                
                return ProtectedFileData(
                    content = content,
                    hash = hash,
                    signature = signature,
                    timestamp = timestamp,
                    version = version,
                    isBinary = isBinary,
                    originalFileName = originalFileName
                )
            } catch (e: Exception) {
                throw Exception("Erro no parsing streaming: ${e.message}")
            }
        }
        
        /**
         * Extrai valor de um campo JSON de forma simples
         */
        private fun extractValue(json: String, field: String, vararg terminators: String): String {
            val start = json.indexOf(field)
            if (start == -1) {
                throw Exception("Campo '$field' não encontrado")
            }
            
            val valueStart = start + field.length
            var end = -1
            var foundTerminator = ""
            
            // Procurar pelo primeiro terminador encontrado
            for (terminator in terminators) {
                val terminatorPos = json.indexOf(terminator, valueStart)
                if (terminatorPos != -1 && (end == -1 || terminatorPos < end)) {
                    end = terminatorPos
                    foundTerminator = terminator
                }
            }
            
            if (end == -1) {
                throw Exception("Nenhum terminador ${terminators.joinToString(", ")} encontrado para campo '$field'")
            }
            
            return json.substring(valueStart, end).trim()
        }
        
        /**
         * Decodifica conteúdo Base64 em streaming para evitar OutOfMemoryError
         */
        fun decodeBase64InStreaming(encodedContent: String, outputFile: File): Result<File> {
            return try {
                android.util.Log.d("ProtectedFileData", "🔧 Iniciando decodificação Base64 robusta...")
                android.util.Log.d("ProtectedFileData", "📊 Tamanho do conteúdo: ${encodedContent.length} caracteres")
                
                val decoder = Base64.getDecoder()
                val chunkSize = 1024 * 1024 // 1MB por chunk
                
                outputFile.outputStream().use { outputStream ->
                    var index = 0
                    val buffer = StringBuilder()
                    
                    while (index < encodedContent.length) {
                        val endIndex = minOf(index + chunkSize, encodedContent.length)
                        val chunk = encodedContent.substring(index, endIndex)
                        
                        // Limpar o chunk atual
                        val cleanedChunk = cleanBase64Chunk(chunk)
                        buffer.append(cleanedChunk)
                        
                        // Tentar decodificar o buffer quando ele tiver tamanho suficiente
                        if (buffer.length >= 1024 * 4) { // 4KB mínimo para Base64
                            val bufferStr = buffer.toString()
                            val remainder = bufferStr.length % 4
                            val toDecode = if (remainder == 0) {
                                bufferStr
                            } else {
                                bufferStr.substring(0, bufferStr.length - remainder)
                            }
                            
                            if (toDecode.isNotEmpty()) {
                                try {
                                    val decodedChunk = decoder.decode(toDecode)
                                    outputStream.write(decodedChunk)
                                    android.util.Log.d("ProtectedFileData", "✅ Chunk decodificado: ${decodedChunk.size} bytes")
                                    
                                    // Manter o resto no buffer
                                    buffer.clear()
                                    if (remainder > 0) {
                                        buffer.append(bufferStr.substring(bufferStr.length - remainder))
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("ProtectedFileData", "⚠️ Erro no chunk: ${e.message}")
                                    
                                    // Tentar com chunk menor
                                    val smallerSize = 1024
                                    val smallerToDecode = toDecode.take(smallerSize)
                                    if (smallerToDecode.isNotEmpty()) {
                                        try {
                                            val decodedChunk = decoder.decode(smallerToDecode)
                                            outputStream.write(decodedChunk)
                                            android.util.Log.d("ProtectedFileData", "✅ Chunk menor decodificado: ${decodedChunk.size} bytes")
                                            
                                            // Ajustar o buffer
                                            buffer.clear()
                                            buffer.append(toDecode.substring(smallerSize))
                                        } catch (e2: Exception) {
                                            android.util.Log.e("ProtectedFileData", "❌ Erro mesmo com chunk menor: ${e2.message}")
                                            throw Exception("Falha no streaming: ${e2.message}")
                                        }
                                    }
                                }
                            }
                        }
                        
                        index = endIndex
                    }
                    
                    // Processar o resto do buffer
                    if (buffer.isNotEmpty()) {
                        val bufferStr = buffer.toString()
                        val remainder = bufferStr.length % 4
                        val toDecode = if (remainder == 0) {
                            bufferStr
                        } else {
                            bufferStr + "=".repeat(4 - remainder)
                        }
                        
                        if (toDecode.isNotEmpty()) {
                            try {
                                val decodedChunk = decoder.decode(toDecode)
                                outputStream.write(decodedChunk)
                                android.util.Log.d("ProtectedFileData", "✅ Chunk final decodificado: ${decodedChunk.size} bytes")
                            } catch (e: Exception) {
                                android.util.Log.w("ProtectedFileData", "⚠️ Erro no chunk final: ${e.message}")
                            }
                        }
                    }
                }
                
                android.util.Log.d("ProtectedFileData", "✅ Decodificação Base64 concluída com sucesso")
                Result.success(outputFile)
            } catch (e: Exception) {
                android.util.Log.e("ProtectedFileData", "❌ Erro na decodificação Base64: ${e.message}")
                Result.failure(e)
            }
        }
        
        /**
         * Limpa um chunk de conteúdo Base64 removendo caracteres inválidos
         */
        private fun cleanBase64Chunk(chunk: String): String {
            return chunk.filter { char ->
                // Manter apenas caracteres válidos para Base64
                char.isLetterOrDigit() || char == '+' || char == '/' || char == '='
            }
        }
        
        /**
         * Lê e processa um arquivo protegido diretamente do disco sem carregar tudo na memória
         */
        fun fromFileStreaming(protectedFile: File): Result<ProtectedFileData> {
            return try {
                val fileSize = protectedFile.length()
                if (fileSize > 50 * 1024 * 1024) { // 50MB
                    return fromFileDirectStreaming(protectedFile)
                }
                
                // Para arquivos menores, usar o método normal
                val jsonContent = protectedFile.readText()
                val result = fromJson(jsonContent)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        
        /**
         * Processa arquivos muito grandes diretamente do disco
         */
        private fun fromFileDirectStreaming(protectedFile: File): Result<ProtectedFileData> {
            return try {
                // Ler apenas os metadados do início do arquivo
                val headerSize = 1024 * 1024 // 1MB para ler os metadados
                val header = protectedFile.inputStream().use { inputStream ->
                    val buffer = ByteArray(headerSize)
                    val bytesRead = inputStream.read(buffer)
                    String(buffer, 0, bytesRead)
                }
                
                // Primeiro, verificar se o campo content está no header
                val contentInHeader = header.contains("\"content\":\"")
                android.util.Log.d("ProtectedFileData", "📄 Content no header: $contentInHeader")
                
                // Extrair metadados do header
                val hash = extractValue(header, "\"hash\":", "\"")
                val signature = extractValue(header, "\"signature\":", "\"")
                val timestamp = extractValue(header, "\"timestamp\":", ",", "}").toLong()
                val version = extractValue(header, "\"version\":", "\"")
                val isBinary = extractValue(header, "\"isBinary\":", ",", "}").toBoolean()
                val originalFileName = try {
                    extractValue(header, "\"originalFileName\":", ",", "}").let { 
                        if (it == "null") null else it.removeSurrounding("\"")
                    }
                } catch (e: Exception) {
                    // Campo opcional, pode não existir
                    null
                }
                
                // Para arquivos binários grandes, não carregar o conteúdo na memória
                val content = if (isBinary) {
                    // Retornar um placeholder que indica que o conteúdo está no arquivo
                    "FILE_STREAMING_PLACEHOLDER"
                } else {
                    // Para JSON, tentar ler o conteúdo
                    try {
                        val fullContent = protectedFile.readText()
                        val contentStart = fullContent.indexOf("\"content\":\"")
                        if (contentStart != -1) {
                            val contentStartPos = contentStart + 11
                            val contentEnd = fullContent.lastIndexOf("\"", fullContent.length - 2)
                            if (contentEnd != -1 && contentEnd > contentStartPos) {
                                val encodedContent = fullContent.substring(contentStartPos, contentEnd)
                                String(Base64.getDecoder().decode(encodedContent))
                            } else {
                                "JSON_CONTENT_ERROR"
                            }
                        } else {
                            "JSON_CONTENT_ERROR"
                        }
                    } catch (e: Exception) {
                        "JSON_CONTENT_ERROR"
                    }
                }
                
                val result = ProtectedFileData(
                    content = content,
                    hash = hash,
                    signature = signature,
                    timestamp = timestamp,
                    version = version,
                    isBinary = isBinary,
                    originalFileName = originalFileName
                )
                
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
