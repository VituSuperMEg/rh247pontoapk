package com.ml.shubham0204.facenet_android.service

import android.content.Context
import android.util.Log
import com.ml.shubham0204.facenet_android.data.PontosGenericosDao
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.ConfiguracoesEntity
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient
import com.ml.shubham0204.facenet_android.data.api.PontoSyncRequest
import com.ml.shubham0204.facenet_android.data.api.PontoSyncCompleteRequest
import com.ml.shubham0204.facenet_android.data.api.PontoSyncFlexibleResponse
import com.ml.shubham0204.facenet_android.utils.ErrorMessageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class SincronizacaoResult(
    val sucesso: Boolean,
    val quantidadePontos: Int,
    val duracaoSegundos: Long,
    val mensagem: String,
    val erroOriginal: String? = null
)

class PontoSincronizacaoService {
    
    companion object {
        private const val TAG = "SYNC_DEBUG"
    }

    // Obter quantidade de pontos pendentes
    suspend fun getQuantidadePontosPendentes(context: Context): Int {
        return withContext(Dispatchers.IO) {
            try {
                val pontosDao = PontosGenericosDao()
                val pontosPendentes = pontosDao.getNaoSincronizados()
                
                Log.d(TAG, "📊 Pontos pendentes: ${pontosPendentes.size}")
                pontosPendentes.size
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter quantidade de pontos pendentes: ${e.message}")
                0
            }
        }
    }

    // Sincronizar pontos pendentes (otimizado para evitar OutOfMemory)
    suspend fun sincronizarPontosPendentes(context: Context): SincronizacaoResult {
        return withContext(Dispatchers.IO) {
            val tempoInicio = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "🚀 === INICIANDO SINCRONIZAÇÃO OTIMIZADA ===")
                
                // Verificar configurações
                val configuracoesDao = ConfiguracoesDao()
                val configuracoes = configuracoesDao.getConfiguracoes()
                
                if (configuracoes == null) {
                    return@withContext SincronizacaoResult(false, 0, 0, "⚠️ Configurações não encontradas. Verifique as configurações do aplicativo.", null)
                }
                
                // Verificar se as configurações estão válidas
                if (configuracoes.entidadeId.isEmpty() || configuracoes.localizacaoId.isEmpty() || configuracoes.codigoSincronizacao.isEmpty()) {
                    return@withContext SincronizacaoResult(false, 0, 0, "⚠️ Configurações incompletas. Preencha todos os campos obrigatórios nas configurações.", null)
                }
                
                // Buscar pontos não sincronizados
                val pontosDao = PontosGenericosDao()
                
                // ✅ NOVO: Validar e corrigir pontos com campos vazios ou nulos
                val pontosValidados = pontosDao.validarECorrigirPontos()
                if (pontosValidados > 0) {
                    Log.d(TAG, "🔧 $pontosValidados pontos foram validados e corrigidos")
                }
                
                val pontosPendentes = pontosDao.getNaoSincronizados()
                
                if (pontosPendentes.isEmpty()) {
                    Log.d(TAG, "ℹ️ Nenhum ponto pendente para sincronização")
                    return@withContext SincronizacaoResult(true, 0, 0, "✅ Nenhum ponto pendente para sincronização", null)
                }
                
                Log.d(TAG, "📊 Total de pontos para sincronizar: ${pontosPendentes.size}")
                
                // ✅ NOVO: Processar em lotes para evitar OutOfMemory
                val BATCH_SIZE = 50 // Processar no máximo 50 pontos por vez
                val totalPontos = pontosPendentes.size
                var pontosSincronizados = 0
                
                if (totalPontos > BATCH_SIZE) {
                    Log.w(TAG, "⚠️ Muitos pontos ($totalPontos). Processando em lotes de $BATCH_SIZE")
                }
                
                // Dividir em lotes
                val lotes = pontosPendentes.chunked(BATCH_SIZE)
                
                for ((loteIndex, lote) in lotes.withIndex()) {
                    Log.d(TAG, "📦 === PROCESSANDO LOTE ${loteIndex + 1}/${lotes.size} (${lote.size} pontos) ===")
                    
                    try {
                        val pontosParaAPI = lote.map { ponto ->
                            PontoSyncRequest(
                                funcionarioId = ponto.funcionarioCpf,
                                funcionarioNome = ponto.funcionarioNome,
                                dataHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ponto.dataHora)),
                                tipoPonto = "PONTO",
                                latitude = ponto.latitude,
                                longitude = ponto.longitude,
                                fotoBase64 = ponto.fotoBase64,
                                observacao = ponto.observacao,
                                matriculaOrigem = ponto.matriculaOrigem // ✅ NOVO: Incluir matrícula de origem
                            )
                        }
                        
                        Log.d(TAG, "📊 Pontos com foto neste lote: ${pontosParaAPI.count { it.fotoBase64?.isNotEmpty() == true }}/${pontosParaAPI.size}")
                        
                        // Criar request para este lote
                        val requestLote = PontoSyncCompleteRequest(
                            localizacao_id = configuracoes.localizacaoId,
                            cod_sincroniza = configuracoes.codigoSincronizacao,
                            pontos = pontosParaAPI
                        )
                        
                        // Enviar lote para API
                        Log.d(TAG, "🚀 Enviando lote ${loteIndex + 1} com ${pontosParaAPI.size} pontos...")
                        val apiService = RetrofitClient.instance
                        val entidadeId = configuracoes.entidadeId
                        val response = apiService.sincronizarPontosCompleto(entidadeId, requestLote)
                        
                        if (response.isSuccessful) {
                            val responseBody = response.body() ?: ""
                            Log.d(TAG, "📡 Resposta da API (lote ${loteIndex + 1}): $responseBody")
                            
                            val isSuccess = responseBody.contains("Pontos Sincronizado com Sucesso") || 
                                           responseBody.contains("success") || 
                                           responseBody.contains("Sucesso")
                            
                            if (isSuccess) {
                                // Marcar pontos deste lote como sincronizados
                                lote.forEach { ponto ->
                                    pontosDao.marcarComoSincronizado(ponto.id)
                                }
                                pontosSincronizados += lote.size
                                Log.d(TAG, "✅ Lote ${loteIndex + 1} sincronizado com sucesso!")
                            } else {
                                Log.e(TAG, "❌ API retornou erro no lote ${loteIndex + 1}: $responseBody")
                                // Continuar com próximo lote
                            }
                        } else {
                            val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                            Log.e(TAG, "❌ Erro HTTP ${response.code()} no lote ${loteIndex + 1}: $errorBody")
                            // Continuar com próximo lote
                        }
                        
                        // ✅ CRÍTICO: Liberar memória entre lotes
                        if (loteIndex < lotes.size - 1) {
                            System.gc()
                            kotlinx.coroutines.delay(500) // Pequena pausa para GC
                            Log.d(TAG, "🧹 Memória liberada antes do próximo lote")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro no lote ${loteIndex + 1}: ${e.message}")
                        e.printStackTrace()
                        // Continuar com próximo lote
                    }
                }
                
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                
                if (pontosSincronizados == totalPontos) {
                    Log.d(TAG, "✅ Todos os pontos foram sincronizados com sucesso!")
                    SincronizacaoResult(
                        sucesso = true,
                        quantidadePontos = pontosSincronizados,
                        duracaoSegundos = duracaoSegundos,
                        mensagem = "✅ ${pontosSincronizados} pontos sincronizados com sucesso!",
                        erroOriginal = null
                    )
                } else if (pontosSincronizados > 0) {
                    Log.w(TAG, "⚠️ Sincronização parcial: $pontosSincronizados/$totalPontos pontos sincronizados")
                    SincronizacaoResult(
                        sucesso = false,
                        quantidadePontos = pontosSincronizados,
                        duracaoSegundos = duracaoSegundos,
                        mensagem = "⚠️ Sincronização parcial: $pontosSincronizados/$totalPontos pontos sincronizados",
                        erroOriginal = null
                    )
                } else {
                    Log.e(TAG, "❌ Nenhum ponto foi sincronizado")
                    SincronizacaoResult(
                        sucesso = false,
                        quantidadePontos = 0,
                        duracaoSegundos = duracaoSegundos,
                        mensagem = "❌ Falha na sincronização. Tente novamente mais tarde.",
                        erroOriginal = null
                    )
                }
                
            } catch (e: Exception) {
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                Log.e(TAG, "❌ Erro geral na sincronização: ${e.message}")
                e.printStackTrace()
                
                // Verificar se é erro de memória
                if (e is OutOfMemoryError || e.message?.contains("OutOfMemory", ignoreCase = true) == true) {
                    Log.e(TAG, "💥 ERRO DE MEMÓRIA DETECTADO! Tente sincronizar menos pontos por vez.")
                    SincronizacaoResult(
                        sucesso = false,
                        quantidadePontos = 0,
                        duracaoSegundos = duracaoSegundos,
                        mensagem = "❌ Erro de memória. Há muitos pontos para sincronizar. Aguarde alguns instantes e tente novamente.",
                        erroOriginal = e.stackTraceToString()
                    )
                } else {
                    SincronizacaoResult(
                        sucesso = false,
                        quantidadePontos = 0,
                        duracaoSegundos = duracaoSegundos,
                        mensagem = ErrorMessageHelper.getFriendlyErrorMessage(e),
                        erroOriginal = e.stackTraceToString()
                    )
                }
            }
        }
    }
} 