package com.ml.shubham0204.facenet_android.utils

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
            // Parse simples do JSON (em produção, use uma biblioteca JSON)
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
            
            // Decodificar o conteúdo Base64
            val encodedContent = contentMatch.groupValues[1]
            val content = String(Base64.getDecoder().decode(encodedContent))
            
            return ProtectedFileData(
                content = content,
                hash = hashMatch.groupValues[1],
                signature = signatureMatch.groupValues[1],
                timestamp = timestampMatch.groupValues[1].toLong(),
                version = versionMatch.groupValues[1],
                isBinary = isBinaryMatch?.groupValues?.get(1)?.toBoolean() ?: false,
                originalFileName = originalFileNameMatch?.groupValues?.get(1)
            )
        }
    }
}
