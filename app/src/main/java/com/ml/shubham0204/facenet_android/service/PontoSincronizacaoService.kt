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
                    return@withContext SincronizacaoResult(false, 0, 0, "⚠️ Configurações não encontradas. Verifique as configurações do aplicativo.", null)
                }
                
                // Verificar se as configurações estão válidas
                if (configuracoes.entidadeId.isEmpty() || configuracoes.localizacaoId.isEmpty() || configuracoes.codigoSincronizacao.isEmpty()) {
                    return@withContext SincronizacaoResult(false, 0, 0, "⚠️ Configurações incompletas. Preencha todos os campos obrigatórios nas configurações.", null)
                }
                
                // Buscar pontos não sincronizados
                val pontosDao = PontosGenericosDao()
                val pontosPendentes = pontosDao.getNaoSincronizados()
                
                if (pontosPendentes.isEmpty()) {
                    Log.d(TAG, "ℹ️ Nenhum ponto pendente para sincronização")
                    return@withContext SincronizacaoResult(true, 0, 0, "✅ Nenhum ponto pendente para sincronização", null)
                }
                
                Log.d(TAG, "📊 Total de pontos para sincronizar: ${pontosPendentes.size}")
                pontosPendentes.forEachIndexed { index, ponto ->
                    Log.d(TAG, "  🔹 [$index] ${ponto.funcionarioNome} - PONTO - ${Date(ponto.dataHora)}")
                }
                
                
                val pontosParaAPI = pontosPendentes.map { ponto ->
                    PontoSyncRequest(
                        funcionarioId = ponto.funcionarioCpf, // Usar CPF em vez do ID interno
                        funcionarioNome = ponto.funcionarioNome,
                        dataHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ponto.dataHora)),
                        tipoPonto = "PONTO", // ✅ CORRIGIDO: Adicionar tipoPonto obrigatório
                        latitude = ponto.latitude,
                        longitude = ponto.longitude,
                        fotoBase64 = ponto.fotoBase64, // ✅ NOVO: Incluir foto base64
                        observacao = ponto.observacao,
                        matriculaReal = ponto.matriculaReal // ✅ NOVO: Incluir matrícula selecionada
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
                    Log.d(TAG, "  📋 Ponto $index:")
                    Log.d(TAG, "    - funcionarioId: ${pontoAPI.funcionarioId}")
                    Log.d(TAG, "    - funcionarioNome: ${pontoAPI.funcionarioNome}")
                    Log.d(TAG, "    - dataHora: ${pontoAPI.dataHora}")
                    Log.d(TAG, "    - tipoPonto: ${pontoAPI.tipoPonto}")
                    Log.d(TAG, "    - latitude: ${pontoAPI.latitude}")
                    Log.d(TAG, "    - longitude: ${pontoAPI.longitude}")
                    Log.d(TAG, "    - fotoBase64: ${if (pontoAPI.fotoBase64?.isNotEmpty() == true) "SIM (${pontoAPI.fotoBase64.length} chars)" else "NÃO"}")
                    Log.d(TAG, "    - observacao: ${pontoAPI.observacao}")
                    Log.d(TAG, "    - matriculaReal: ${pontoAPI.matriculaReal ?: "NULL"}") // ✅ NOVO: Log da matrícula real
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
                        // ✅ CORRIGIDO: Mostrar início da foto com prefixo
                        Log.d(TAG, "     Início da foto: ${pontoAPI.fotoBase64.take(80)}...")
                        // Verificar se tem o prefixo correto
                        if (pontoAPI.fotoBase64.startsWith("data:image/jpeg;base64,")) {
                            Log.d(TAG, "    ✅ Prefixo correto detectado: data:image/jpeg;base64,")
                        } else {
                            Log.w(TAG, "    ⚠️ Prefixo não encontrado - pode causar erro no servidor")
                        }
                    }
                    Log.d(TAG, "  ---")
                }
                
                // Fazer chamada para API
                Log.d(TAG, "🚀 Enviando ${pontosParaAPI.size} pontos para API...")
                val apiService = RetrofitClient.instance
                val entidadeId = configuracoes.entidadeId
                val response = apiService.sincronizarPontosCompleto(entidadeId, requestCompleto)
                
                // ✅ CORRIGIDO: Tratar resposta como string, não como JSON
                if (response.isSuccessful) {
                    val responseBody = response.body() ?: ""
                    
                    Log.d(TAG, "📡 Resposta da API: $responseBody")
                    
                    // Verificar se contém mensagem de sucesso
                    val isSuccess = responseBody.contains("Pontos Sincronizado com Sucesso") || 
                                   responseBody.contains("success") || 
                                   responseBody.contains("Sucesso")
                    
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
                            mensagem = "✅ Pontos sincronizados com sucesso!",
                            erroOriginal = null
                        )
                    } else {
                        Log.e(TAG, "❌ API retornou erro: $responseBody")
                        val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                        SincronizacaoResult(
                            sucesso = false,
                            quantidadePontos = 0,
                            duracaoSegundos = duracaoSegundos,
                            mensagem = ErrorMessageHelper.getFriendlyErrorMessage("Erro na API: $responseBody"),
                            erroOriginal = responseBody
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
                        mensagem = ErrorMessageHelper.getFriendlyErrorMessage("Erro HTTP ${response.code()}: $errorBody"),
                        erroOriginal = "Erro HTTP ${response.code()}: $errorBody"
                    )
                }
                
            } catch (e: Exception) {
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                Log.e(TAG, "❌ Erro na sincronização: ${e.message}")
                e.printStackTrace()
                Log.d(TAG, "🚀 === SINCRONIZAÇÃO COM ERRO ===")
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