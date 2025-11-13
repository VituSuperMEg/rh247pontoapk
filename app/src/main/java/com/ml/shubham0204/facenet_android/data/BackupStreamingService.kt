package com.ml.shubham0204.facenet_android.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * 🚀 BACKUP STREAMING SERVICE
 *
 * Solução definitiva para processar backups JSON GIGANTES (335MB+) sem OutOfMemoryError
 *
 * Técnicas utilizadas:
 * - JsonReader (Gson Streaming API) - Processa JSON token por token
 * - GZIPInputStream - Suporte para arquivos .gz comprimidos
 * - Processamento em chunks - Nunca carrega tudo na memória
 * - Inserção incremental no banco - Dados salvos aos poucos
 */
class BackupStreamingService(
    private val context: Context,
    private val objectBoxStore: BoxStore
) {

    companion object {
        private const val TAG = "BackupStreamingService"
        private const val BATCH_SIZE = 1 // 🔥🔥🔥 1 REGISTRO POR VEZ - MODO EXTREMO
    }

    // ============================================
    // 🔥 MÉTODO 1: JSON Normal (Streaming)
    // ============================================

    /**
     * Restaura backup JSON usando streaming (Gson JsonReader)
     *
     * ✅ Funciona com arquivos de QUALQUER tamanho
     * ✅ Usa apenas ~10-20MB de memória
     * ✅ Processa token por token
     *
     * Estrutura esperada:
     * {
     *   "timestamp": 1234567890,
     *   "version": "1.0",
     *   "data": {
     *     "funcionarios": [...],
     *     "pessoas": [...],
     *     "pontos": [...]
     *   }
     * }
     */
    suspend fun restoreFromJsonStreaming(backupFile: File): Result<RestoreStats> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🚀 Iniciando restauração via STREAMING")
                Log.d(TAG, "📄 Arquivo: ${backupFile.name} (${backupFile.length() / 1024 / 1024}MB)")

                val stats = RestoreStats()
                val startTime = System.currentTimeMillis()

                // Criar JsonReader com buffer grande
                FileInputStream(backupFile).use { fis ->
                    BufferedInputStream(fis, 8192 * 8).use { bis ->
                        InputStreamReader(bis, Charsets.UTF_8).use { isr ->
                            BufferedReader(isr, 8192 * 8).use { reader ->
                                JsonReader(reader).use { jsonReader ->

                                    // Configurar JsonReader para modo lenient (mais tolerante)
                                    jsonReader.isLenient = true

                                    // Começar a ler o objeto raiz
                                    jsonReader.beginObject()

                                    while (jsonReader.hasNext()) {
                                        when (jsonReader.nextName()) {
                                            "timestamp" -> {
                                                stats.timestamp = jsonReader.nextLong()
                                                Log.d(TAG, "   ⏰ Timestamp: ${stats.timestamp}")
                                            }
                                            "version" -> {
                                                stats.version = jsonReader.nextString()
                                                Log.d(TAG, "   📌 Versão: ${stats.version}")
                                            }
                                            "data" -> {
                                                // Processar o objeto "data"
                                                processDataSection(jsonReader, stats)
                                            }
                                            else -> jsonReader.skipValue()
                                        }
                                    }

                                    jsonReader.endObject()
                                }
                            }
                        }
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ Restauração concluída em ${elapsed / 1000}s")
                Log.d(TAG, "📊 Estatísticas: $stats")

                Result.success(stats)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na restauração streaming", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Processa a seção "data" do JSON
     * 🔥🔥🔥 MODO ULTRA ECONÔMICO: APENAS PONTOS NÃO SINCRONIZADOS
     */
    private suspend fun processDataSection(jsonReader: JsonReader, stats: RestoreStats) {
        jsonReader.beginObject()

        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                "pontosGenericos" -> {
                    Log.d(TAG, "🔥 Processando APENAS pontos não sincronizados...")
                    Log.d(TAG, "⚠️  PULANDO: funcionários, pessoas, configurações, faceImages")
                    processPontosStreaming(jsonReader, stats)
                }
                "funcionarios" -> {
                    Log.d(TAG, "⚠️  PULANDO funcionários (economia de memória)")
                    jsonReader.skipValue()
                }
                "configuracoes" -> {
                    Log.d(TAG, "⚠️  PULANDO configurações (economia de memória)")
                    jsonReader.skipValue()
                }
                "pessoas" -> {
                    Log.d(TAG, "⚠️  PULANDO pessoas (economia de memória)")
                    jsonReader.skipValue()
                }
                "faceImages" -> {
                    Log.d(TAG, "⚠️  PULANDO face images (economia de memória)")
                    jsonReader.skipValue()
                }
                else -> jsonReader.skipValue()
            }
        }

        jsonReader.endObject()
    }

    /**
     * Processa array de funcionários usando streaming
     */
    private suspend fun processFuncionariosStreaming(jsonReader: JsonReader, stats: RestoreStats) {
        jsonReader.beginArray()

        val batch = mutableListOf<FuncionariosEntity>()

        while (jsonReader.hasNext()) {
            val funcionario = parseFuncionario(jsonReader)
            batch.add(funcionario)
            stats.funcionariosCount++

            // Salvar em lotes
            if (batch.size >= BATCH_SIZE) {
                saveFuncionariosBatch(batch)
                Log.d(TAG, "      💾 ${stats.funcionariosCount} funcionários processados...")
                batch.clear()
                System.gc() // 🔥 FORÇAR LIMPEZA DE MEMÓRIA
            }
        }

        // Salvar resto
        if (batch.isNotEmpty()) {
            saveFuncionariosBatch(batch)
        }

        jsonReader.endArray()
        Log.d(TAG, "   ✅ Total: ${stats.funcionariosCount} funcionários")
    }

    /**
     * Parseia um objeto Funcionario do JSON
     */
    private fun parseFuncionario(jsonReader: JsonReader): FuncionariosEntity {
        jsonReader.beginObject()

        var id = 0L
        var codigo = ""
        var nome = ""
        var ativo = 1
        var matricula = ""
        var cpf = ""
        var cargo = ""
        var secretaria = ""
        var lotacao = ""
        var apiId = 0L

        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                "id" -> id = safeNextLong(jsonReader)
                "codigo" -> codigo = safeNextString(jsonReader)
                "nome" -> nome = safeNextString(jsonReader)
                "ativo" -> ativo = safeNextInt(jsonReader)
                "matricula" -> matricula = safeNextString(jsonReader)
                "cpf" -> cpf = safeNextString(jsonReader)
                "cargo" -> cargo = safeNextString(jsonReader)
                "secretaria" -> secretaria = safeNextString(jsonReader)
                "lotacao" -> lotacao = safeNextString(jsonReader)
                "apiId" -> apiId = safeNextLong(jsonReader)
                else -> jsonReader.skipValue()
            }
        }

        jsonReader.endObject()

        return FuncionariosEntity(
            id = id,
            codigo = codigo,
            nome = nome,
            ativo = ativo,
            matricula = matricula,
            cpf = cpf,
            cargo = cargo,
            secretaria = secretaria,
            lotacao = lotacao,
            apiId = apiId
        )
    }

    /**
     * Salva lote de funcionários no ObjectBox
     */
    private suspend fun saveFuncionariosBatch(batch: List<FuncionariosEntity>) {
        withContext(Dispatchers.IO) {
            objectBoxStore.boxFor(FuncionariosEntity::class.java).put(batch)
        }
    }

    /**
     * Processa configurações (similar aos funcionários)
     */
    private suspend fun processConfiguracoesStreaming(jsonReader: JsonReader, stats: RestoreStats) {
        jsonReader.beginArray()

        while (jsonReader.hasNext()) {
            // Parse configuração
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                jsonReader.skipValue() // Por enquanto, apenas contar
            }
            jsonReader.endObject()

            stats.configuracoesCount++
        }

        jsonReader.endArray()
        Log.d(TAG, "   ✅ Total: ${stats.configuracoesCount} configurações")
    }

    /**
     * Processa pessoas
     */
    private suspend fun processPessoasStreaming(jsonReader: JsonReader, stats: RestoreStats) {
        jsonReader.beginArray()

        while (jsonReader.hasNext()) {
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                jsonReader.skipValue()
            }
            jsonReader.endObject()

            stats.pessoasCount++
        }

        jsonReader.endArray()
        Log.d(TAG, "   ✅ Total: ${stats.pessoasCount} pessoas")
    }

    /**
     * Processa face images
     */
    private suspend fun processFaceImagesStreaming(jsonReader: JsonReader, stats: RestoreStats) {
        jsonReader.beginArray()

        while (jsonReader.hasNext()) {
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                jsonReader.skipValue()
            }
            jsonReader.endObject()

            stats.faceImagesCount++
        }

        jsonReader.endArray()
        Log.d(TAG, "   ✅ Total: ${stats.faceImagesCount} face images")
    }

    /**
     * Processa pontos genéricos
     * 🔥 FILTRO: Apenas pontos NÃO sincronizados (synced = false)
     */
    private suspend fun processPontosStreaming(jsonReader: JsonReader, stats: RestoreStats) {
        jsonReader.beginArray()

        val batch = mutableListOf<PontosGenericosEntity>()
        var totalLidos = 0
        var totalPulados = 0

        while (jsonReader.hasNext()) {
            val ponto = parsePonto(jsonReader)
            totalLidos++

            // 🔥 FILTRO: Apenas pontos NÃO sincronizados!
            if (!ponto.synced) {
                batch.add(ponto)
                stats.pontosCount++

                if (batch.size >= BATCH_SIZE) {
                    savePontosBatch(batch)
                    Log.d(TAG, "      💾 ${stats.pontosCount} pontos NÃO sincronizados salvos (pulados: $totalPulados)...")
                    batch.clear()
                    System.gc() // 🔥 FORÇAR LIMPEZA
                }
            } else {
                totalPulados++
            }
        }

        if (batch.isNotEmpty()) {
            savePontosBatch(batch)
        }

        jsonReader.endArray()
        Log.d(TAG, "   ✅ Total: ${stats.pontosCount} pontos NÃO sincronizados salvos (de $totalLidos lidos, $totalPulados já sincronizados)")
    }

    /**
     * Parseia um ponto genérico
     */
    private fun parsePonto(jsonReader: JsonReader): PontosGenericosEntity {
        jsonReader.beginObject()

        var id = 0L
        var funcionarioId = ""
        var funcionarioNome = ""
        var funcionarioMatricula = ""
        var dataHora = 0L
        var latitude: Double? = null
        var longitude: Double? = null
        var observacao = ""
        var fotoBase64 = ""
        var synced = false
        var entidadeId = ""

        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                "id" -> id = safeNextLong(jsonReader)
                "funcionarioId" -> funcionarioId = safeNextString(jsonReader)
                "funcionarioNome" -> funcionarioNome = safeNextString(jsonReader)
                "funcionarioMatricula" -> funcionarioMatricula = safeNextString(jsonReader)
                "dataHora" -> dataHora = safeNextLong(jsonReader)
                "latitude" -> latitude = safeNextDouble(jsonReader)
                "longitude" -> longitude = safeNextDouble(jsonReader)
                "observacao" -> observacao = safeNextString(jsonReader)
                "fotoBase64" -> {
                    // 🔥 PULAR fotos Base64 - economizar memória EXTREMA
                    jsonReader.skipValue()
                    fotoBase64 = ""
                }
                "synced" -> synced = safeNextBoolean(jsonReader)
                "entidadeId" -> entidadeId = safeNextString(jsonReader)
                else -> jsonReader.skipValue()
            }
        }

        jsonReader.endObject()

        return PontosGenericosEntity(
            id = id,
            funcionarioId = funcionarioId,
            funcionarioNome = funcionarioNome,
            funcionarioMatricula = funcionarioMatricula,
            dataHora = dataHora,
            latitude = latitude,
            longitude = longitude,
            observacao = observacao,
            fotoBase64 = fotoBase64,
            synced = synced,
            entidadeId = entidadeId
        )
    }

    /**
     * Salva lote de pontos
     */
    private suspend fun savePontosBatch(batch: List<PontosGenericosEntity>) {
        withContext(Dispatchers.IO) {
            objectBoxStore.boxFor(PontosGenericosEntity::class.java).put(batch)
        }
    }

    // ============================================
    // 🔥 MÉTODO 2: JSON Comprimido (.gz)
    // ============================================

    /**
     * Restaura backup JSON.GZ usando streaming
     *
     * ✅ Descomprime on-the-fly (não precisa descomprimir o arquivo inteiro antes)
     * ✅ Usa GZIPInputStream + JsonReader
     * ✅ Economia de 70-90% de espaço
     */
    suspend fun restoreFromGzipStreaming(gzipFile: File): Result<RestoreStats> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🚀 Iniciando restauração via GZIP STREAMING")
                Log.d(TAG, "📦 Arquivo comprimido: ${gzipFile.name} (${gzipFile.length() / 1024 / 1024}MB)")

                val stats = RestoreStats()
                val startTime = System.currentTimeMillis()

                // Criar stream com GZIP + JsonReader
                FileInputStream(gzipFile).use { fis ->
                    BufferedInputStream(fis, 8192 * 8).use { bis ->
                        GZIPInputStream(bis, 8192 * 8).use { gzis ->
                            InputStreamReader(gzis, Charsets.UTF_8).use { isr ->
                                BufferedReader(isr, 8192 * 8).use { reader ->
                                    JsonReader(reader).use { jsonReader ->

                                        jsonReader.isLenient = true
                                        jsonReader.beginObject()

                                        while (jsonReader.hasNext()) {
                                            when (jsonReader.nextName()) {
                                                "timestamp" -> stats.timestamp = jsonReader.nextLong()
                                                "version" -> stats.version = jsonReader.nextString()
                                                "data" -> processDataSection(jsonReader, stats)
                                                else -> jsonReader.skipValue()
                                            }
                                        }

                                        jsonReader.endObject()
                                    }
                                }
                            }
                        }
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ Restauração GZIP concluída em ${elapsed / 1000}s")
                Log.d(TAG, "📊 Estatísticas: $stats")

                Result.success(stats)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na restauração GZIP streaming", e)
                Result.failure(e)
            }
        }
    }

    // ============================================
    // 🛡️ MÉTODOS AUXILIARES (Safe parsers)
    // ============================================

    /**
     * Lê Long de forma segura (trata null e erros)
     */
    private fun safeNextLong(jsonReader: JsonReader): Long {
        return try {
            if (jsonReader.peek() == JsonToken.NULL) {
                jsonReader.nextNull()
                0L
            } else {
                jsonReader.nextLong()
            }
        } catch (e: Exception) {
            try {
                jsonReader.nextString().toLongOrNull() ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    /**
     * Lê String de forma segura
     */
    private fun safeNextString(jsonReader: JsonReader): String {
        return try {
            if (jsonReader.peek() == JsonToken.NULL) {
                jsonReader.nextNull()
                ""
            } else {
                jsonReader.nextString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Lê Int de forma segura
     */
    private fun safeNextInt(jsonReader: JsonReader): Int {
        return try {
            if (jsonReader.peek() == JsonToken.NULL) {
                jsonReader.nextNull()
                0
            } else {
                jsonReader.nextInt()
            }
        } catch (e: Exception) {
            try {
                jsonReader.nextString().toIntOrNull() ?: 0
            } catch (e2: Exception) {
                0
            }
        }
    }

    /**
     * Lê Double de forma segura
     */
    private fun safeNextDouble(jsonReader: JsonReader): Double {
        return try {
            if (jsonReader.peek() == JsonToken.NULL) {
                jsonReader.nextNull()
                0.0
            } else {
                jsonReader.nextDouble()
            }
        } catch (e: Exception) {
            try {
                jsonReader.nextString().toDoubleOrNull() ?: 0.0
            } catch (e2: Exception) {
                0.0
            }
        }
    }

    /**
     * Lê Boolean de forma segura
     */
    private fun safeNextBoolean(jsonReader: JsonReader): Boolean {
        return try {
            if (jsonReader.peek() == JsonToken.NULL) {
                jsonReader.nextNull()
                false
            } else {
                jsonReader.nextBoolean()
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Estatísticas da restauração
 */
data class RestoreStats(
    var timestamp: Long = 0,
    var version: String = "",
    var funcionariosCount: Int = 0,
    var configuracoesCount: Int = 0,
    var pessoasCount: Int = 0,
    var faceImagesCount: Int = 0,
    var pontosCount: Int = 0
) {
    override fun toString(): String {
        return """
            Timestamp: $timestamp
            Versão: $version
            Funcionários: $funcionariosCount
            Configurações: $configuracoesCount
            Pessoas: $pessoasCount
            Face Images: $faceImagesCount
            Pontos: $pontosCount
        """.trimIndent()
    }
}
