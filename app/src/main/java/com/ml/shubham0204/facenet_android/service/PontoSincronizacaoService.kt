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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class SincronizacaoResult(
    val sucesso: Boolean,
    val quantidadePontos: Int,
    val duracaoSegundos: Long,
    val mensagem: String
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

    // Sincronizar pontos pendentes
    suspend fun sincronizarPontosPendentes(context: Context): SincronizacaoResult {
        return withContext(Dispatchers.IO) {
            val tempoInicio = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "🚀 === INICIANDO SINCRONIZAÇÃO REAL ===")
                
                // Verificar configurações
                val configuracoesDao = ConfiguracoesDao()
                val configuracoes = configuracoesDao.getConfiguracoes()
                
                if (configuracoes == null) {
                    Log.e(TAG, "❌ Configurações não encontradas")
                    return@withContext SincronizacaoResult(false, 0, 0, "Configurações não encontradas")
                }
                
                // Verificar se as configurações estão válidas
                if (configuracoes.entidadeId.isEmpty() || configuracoes.localizacaoId.isEmpty() || configuracoes.codigoSincronizacao.isEmpty()) {
                    Log.e(TAG, "❌ Configurações inválidas!")
                    return@withContext SincronizacaoResult(false, 0, 0, "Configurações de entidade/localização/código não preenchidas")
                }
                
                Log.d(TAG, "✅ Configurações encontradas:")
                Log.d(TAG, "  🆔 Entidade ID: '${configuracoes.entidadeId}'")
                Log.d(TAG, "  📍 Localização ID: '${configuracoes.localizacaoId}'")
                Log.d(TAG, "  🔑 Código: '${configuracoes.codigoSincronizacao}'")
                
                // Buscar pontos não sincronizados
                val pontosDao = PontosGenericosDao()
                val pontosPendentes = pontosDao.getNaoSincronizados()
                
                if (pontosPendentes.isEmpty()) {
                    Log.d(TAG, "ℹ️ Nenhum ponto pendente para sincronização")
                    return@withContext SincronizacaoResult(true, 0, 0, "Nenhum ponto pendente para sincronização")
                }
                
                Log.d(TAG, "📊 Total de pontos para sincronizar: ${pontosPendentes.size}")
                pontosPendentes.forEachIndexed { index, ponto ->
                    Log.d(TAG, "  🔹 [$index] ${ponto.funcionarioNome} - ${ponto.tipoPonto} - ${Date(ponto.dataHora)}")
                }
                
                Log.d(TAG, "🔄 Iniciando sincronização de ${pontosPendentes.size} pontos...")
                
                // Converter pontos para formato da API
                val pontosParaAPI = pontosPendentes.map { ponto ->
                    PontoSyncRequest(
                        funcionarioId = ponto.funcionarioCpf, // Usar CPF em vez do ID interno
                        funcionarioNome = ponto.funcionarioNome,
                        dataHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ponto.dataHora)),
                        tipoPonto = ponto.tipoPonto.uppercase(),
                        latitude = ponto.latitude,
                        longitude = ponto.longitude,
                        fotoBase64 = ponto.fotoBase64, // ✅ NOVO: Incluir foto base64
                        observacao = ponto.observacao
                    )
                }
                
                Log.d(TAG, "📊 Pontos com foto: ${pontosParaAPI.count { it.fotoBase64?.isNotEmpty() == true }}/${pontosParaAPI.size}")
                
                // Criar request completo
                val requestCompleto = PontoSyncCompleteRequest(
                    localizacao_id = configuracoes.localizacaoId,
                    cod_sincroniza = configuracoes.codigoSincronizacao,
                    pontos = pontosParaAPI
                )
                
                // Mostrar formato completo para API
                Log.d(TAG, "📋 === FORMATO COMPLETO PARA API ===")
                Log.d(TAG, "  localizacao_id: '${requestCompleto.localizacao_id}'")
                Log.d(TAG, "  cod_sincroniza: '${requestCompleto.cod_sincroniza}'")
                Log.d(TAG, "  pontos: ${requestCompleto.pontos.size} pontos")
                
                // ✅ NOVO: Mostrar detalhes de cada ponto individualmente
                Log.d(TAG, "🔍 === DETALHES DE CADA PONTO ===")
                requestCompleto.pontos.forEachIndexed { index, pontoAPI ->
                    Log.d(TAG, "Ponto API #${index + 1}:")
                    Log.d(TAG, "  funcionarioId (CPF): '${pontoAPI.funcionarioId}'")
                    Log.d(TAG, "  funcionarioNome: '${pontoAPI.funcionarioNome}'")
                    Log.d(TAG, "  dataHora: '${pontoAPI.dataHora}'")
                    Log.d(TAG, "  tipoPonto: '${pontoAPI.tipoPonto}'")
                    Log.d(TAG, "  latitude: ${pontoAPI.latitude}")
                    Log.d(TAG, "  longitude: ${pontoAPI.longitude}")
                    Log.d(TAG, "  observacao: '${pontoAPI.observacao}'")
                    Log.d(TAG, "  fotoBase64: ${if (pontoAPI.fotoBase64?.isNotEmpty() == true) "✅ Presente (${pontoAPI.fotoBase64.length} chars)" else "❌ Ausente"}")
                    if (pontoAPI.fotoBase64?.isNotEmpty() == true) {
                        Log.d(TAG, "    📸 Início da foto: ${pontoAPI.fotoBase64.take(50)}...")
                    }
                    Log.d(TAG, "  ---")
                }
                
                // Fazer chamada para API
                Log.d(TAG, "🚀 Enviando ${pontosParaAPI.size} pontos para API...")
                val apiService = RetrofitClient.instance
                val entidadeId = configuracoes.entidadeId
                val response = apiService.sincronizarPontosCompleto(entidadeId, requestCompleto)
                
                // ✅ NOVO: Tratar resposta como string, não como JSON
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val rawResponse = response.raw().body?.string() ?: ""
                    
                    Log.d(TAG, "📡 Resposta da API (raw): $rawResponse")
                    
                    // Verificar se contém mensagem de sucesso
                    val isSuccess = rawResponse.contains("Pontos Sincronizado com Sucesso") || 
                                   rawResponse.contains("success") || 
                                   rawResponse.contains("Sucesso")
                    
                    if (isSuccess) {
                        Log.d(TAG, "✅ Sincronização realizada com sucesso!")
                        
                        // Marcar pontos como sincronizados
                        pontosPendentes.forEach { ponto ->
                            pontosDao.marcarComoSincronizado(ponto.id)
                        }
                        
                        val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                        SincronizacaoResult(
                            sucesso = true,
                            quantidadePontos = pontosPendentes.size,
                            duracaoSegundos = duracaoSegundos,
                            mensagem = "Pontos sincronizados com sucesso!"
                        )
                    } else {
                        Log.e(TAG, "❌ API retornou erro: $rawResponse")
                        val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                        SincronizacaoResult(
                            sucesso = false,
                            quantidadePontos = 0,
                            duracaoSegundos = duracaoSegundos,
                            mensagem = "Erro na API: $rawResponse"
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                    Log.e(TAG, "❌ Erro HTTP ${response.code()}: $errorBody")
                    val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                    SincronizacaoResult(
                        sucesso = false,
                        quantidadePontos = 0,
                        duracaoSegundos = duracaoSegundos,
                        mensagem = "Erro HTTP ${response.code()}: $errorBody"
                    )
                }
                
            } catch (e: Exception) {
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                Log.e(TAG, "❌ Erro na sincronização: ${e.message}")
                e.printStackTrace()
                Log.d(TAG, "🚀 === SINCRONIZAÇÃO COM ERRO ===")
                SincronizacaoResult(false, 0, duracaoSegundos, "Erro na sincronização: ${e.message}")
            }
        }
    }
} 