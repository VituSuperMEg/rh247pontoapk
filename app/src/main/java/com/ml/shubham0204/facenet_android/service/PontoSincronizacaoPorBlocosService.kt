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

data class SincronizacaoPorBlocosResult(
    val sucesso: Boolean,
    val totalPontos: Int,
    val pontosSincronizados: Int,
    val entidadesProcessadas: Int,
    val duracaoSegundos: Long,
    val mensagem: String,
    val detalhesPorEntidade: List<EntidadeSyncResult>,
    val erroOriginal: String? = null
)

data class EntidadeSyncResult(
    val entidadeId: String,
    val sucesso: Boolean,
    val quantidadePontos: Int,
    val mensagem: String,
    val erroOriginal: String? = null
)

class PontoSincronizacaoPorBlocosService {
    
    companion object {
        private const val TAG = "SYNC_BLOCOS_DEBUG"
    }

    // Sincronizar pontos pendentes por blocos de entidade
    suspend fun sincronizarPontosPorBlocos(context: Context): SincronizacaoPorBlocosResult {
        return withContext(Dispatchers.IO) {
            val tempoInicio = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "🚀 === INICIANDO SINCRONIZAÇÃO POR BLOCOS DE ENTIDADE ===")
                
                // Verificar configurações
                val configuracoesDao = ConfiguracoesDao()
                val configuracoes = configuracoesDao.getConfiguracoes()
                
                if (configuracoes == null) {
                    return@withContext SincronizacaoPorBlocosResult(
                        sucesso = false,
                        totalPontos = 0,
                        pontosSincronizados = 0,
                        entidadesProcessadas = 0,
                        duracaoSegundos = 0,
                        mensagem = "⚠️ Configurações não encontradas. Verifique as configurações do aplicativo.",
                        detalhesPorEntidade = emptyList()
                    )
                }
                
                // Verificar se as configurações estão válidas
                if (configuracoes.entidadeId.isEmpty() || configuracoes.localizacaoId.isEmpty() || configuracoes.codigoSincronizacao.isEmpty()) {
                    return@withContext SincronizacaoPorBlocosResult(
                        sucesso = false,
                        totalPontos = 0,
                        pontosSincronizados = 0,
                        entidadesProcessadas = 0,
                        duracaoSegundos = 0,
                        mensagem = "⚠️ Configurações incompletas. Preencha todos os campos obrigatórios nas configurações.",
                        detalhesPorEntidade = emptyList()
                    )
                }
                
                // Buscar pontos não sincronizados agrupados por entidade
                val pontosDao = PontosGenericosDao()
                
                // ✅ NOVO: Corrigir pontos antigos que não têm entidadeId
                val pontosCorrigidos = pontosDao.corrigirPontosSemEntidade()
                if (pontosCorrigidos > 0) {
                    Log.d(TAG, "🔧 $pontosCorrigidos pontos antigos foram corrigidos com entidadeId")
                }
                
                val pontosPorEntidade = pontosDao.getNaoSincronizadosPorEntidade()
                
                if (pontosPorEntidade.isEmpty()) {
                    Log.d(TAG, "ℹ️ Nenhum ponto pendente para sincronização")
                    return@withContext SincronizacaoPorBlocosResult(
                        sucesso = true,
                        totalPontos = 0,
                        pontosSincronizados = 0,
                        entidadesProcessadas = 0,
                        duracaoSegundos = 0,
                        mensagem = "✅ Nenhum ponto pendente para sincronização",
                        detalhesPorEntidade = emptyList()
                    )
                }
                
                val totalPontos = pontosPorEntidade.values.sumOf { it.size }
                Log.d(TAG, "📊 Total de pontos para sincronizar: $totalPontos")
                Log.d(TAG, "🏢 Entidades encontradas: ${pontosPorEntidade.keys}")
                
                val resultadosPorEntidade = mutableListOf<EntidadeSyncResult>()
                var pontosSincronizadosTotal = 0
                var entidadesProcessadas = 0
                
                // Processar cada entidade separadamente
                for ((entidadeId, pontosEntidade) in pontosPorEntidade) {
                    Log.d(TAG, "🔄 === PROCESSANDO ENTIDADE: $entidadeId ===")
                    Log.d(TAG, "📊 Pontos da entidade $entidadeId: ${pontosEntidade.size}")
                    
                    try {
                        val resultadoEntidade = sincronizarPontosDaEntidade(
                            entidadeId = entidadeId,
                            pontos = pontosEntidade,
                            configuracoes = configuracoes
                        )
                        
                        resultadosPorEntidade.add(resultadoEntidade)
                        entidadesProcessadas++
                        
                        if (resultadoEntidade.sucesso) {
                            pontosSincronizadosTotal += resultadoEntidade.quantidadePontos
                            
                            // Marcar pontos como sincronizados
                            pontosEntidade.forEach { ponto ->
                                pontosDao.marcarComoSincronizado(ponto.id)
                            }
                            
                            Log.d(TAG, "✅ Entidade $entidadeId sincronizada com sucesso: ${resultadoEntidade.quantidadePontos} pontos")
                        } else {
                            Log.e(TAG, "❌ Erro na entidade $entidadeId: ${resultadoEntidade.mensagem}")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao processar entidade $entidadeId: ${e.message}")
                        resultadosPorEntidade.add(
                            EntidadeSyncResult(
                                entidadeId = entidadeId,
                                sucesso = false,
                                quantidadePontos = pontosEntidade.size,
                                mensagem = "Erro interno: ${e.message}",
                                erroOriginal = e.stackTraceToString()
                            )
                        )
                        entidadesProcessadas++
                    }
                }
                
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                val sucessoGeral = resultadosPorEntidade.all { it.sucesso }
                
                val mensagemFinal = if (sucessoGeral) {
                    "✅ Sincronização por blocos concluída com sucesso! $pontosSincronizadosTotal pontos sincronizados em $entidadesProcessadas entidades."
                } else {
                    val entidadesComErro = resultadosPorEntidade.count { !it.sucesso }
                    "⚠️ Sincronização parcial: $pontosSincronizadosTotal pontos sincronizados, $entidadesComErro entidades com erro."
                }
                
                Log.d(TAG, "🏁 === SINCRONIZAÇÃO POR BLOCOS FINALIZADA ===")
                Log.d(TAG, "📊 Resultado: $mensagemFinal")
                
                SincronizacaoPorBlocosResult(
                    sucesso = sucessoGeral,
                    totalPontos = totalPontos,
                    pontosSincronizados = pontosSincronizadosTotal,
                    entidadesProcessadas = entidadesProcessadas,
                    duracaoSegundos = duracaoSegundos,
                    mensagem = mensagemFinal,
                    detalhesPorEntidade = resultadosPorEntidade
                )
                
            } catch (e: Exception) {
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                Log.e(TAG, "❌ Erro geral na sincronização por blocos: ${e.message}")
                e.printStackTrace()
                
                SincronizacaoPorBlocosResult(
                    sucesso = false,
                    totalPontos = 0,
                    pontosSincronizados = 0,
                    entidadesProcessadas = 0,
                    duracaoSegundos = duracaoSegundos,
                    mensagem = ErrorMessageHelper.getFriendlyErrorMessage(e),
                    detalhesPorEntidade = emptyList(),
                    erroOriginal = e.stackTraceToString()
                )
            }
        }
    }
    
    // Sincronizar pontos de uma entidade específica
    private suspend fun sincronizarPontosDaEntidade(
        entidadeId: String,
        pontos: List<PontosGenericosEntity>,
        configuracoes: ConfiguracoesEntity
    ): EntidadeSyncResult {
        return try {
            Log.d(TAG, "🔄 Sincronizando ${pontos.size} pontos da entidade: $entidadeId")
            
            // Converter pontos para formato da API
            val pontosParaAPI = pontos.map { ponto ->
                PontoSyncRequest(
                    funcionarioId = ponto.funcionarioCpf,
                    funcionarioNome = ponto.funcionarioNome,
                    dataHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ponto.dataHora)),
                    tipoPonto = ponto.tipoPonto.uppercase(),
                    latitude = ponto.latitude,
                    longitude = ponto.longitude,
                    fotoBase64 = ponto.fotoBase64,
                    observacao = ponto.observacao
                )
            }
            
            // Criar request para esta entidade
            val requestEntidade = PontoSyncCompleteRequest(
                localizacao_id = configuracoes.localizacaoId,
                cod_sincroniza = configuracoes.codigoSincronizacao,
                pontos = pontosParaAPI
            )
            
            Log.d(TAG, "📡 Enviando ${pontosParaAPI.size} pontos da entidade $entidadeId para API...")
            
            // Fazer chamada para API usando a entidade específica
            val apiService = RetrofitClient.instance
            val response = apiService.sincronizarPontosCompleto(entidadeId, requestEntidade)
            
            if (response.isSuccessful) {
                val responseBody = response.body() ?: ""
                Log.d(TAG, "📡 Resposta da API para entidade $entidadeId: $responseBody")
                
                val isSuccess = responseBody.contains("Pontos Sincronizado com Sucesso") || 
                               responseBody.contains("success") || 
                               responseBody.contains("Sucesso")
                
                if (isSuccess) {
                    Log.d(TAG, "✅ Entidade $entidadeId sincronizada com sucesso!")
                    EntidadeSyncResult(
                        entidadeId = entidadeId,
                        sucesso = true,
                        quantidadePontos = pontos.size,
                        mensagem = "Pontos sincronizados com sucesso"
                    )
                } else {
                    Log.e(TAG, "❌ API retornou erro para entidade $entidadeId: $responseBody")
                    EntidadeSyncResult(
                        entidadeId = entidadeId,
                        sucesso = false,
                        quantidadePontos = pontos.size,
                        mensagem = "Erro na API: $responseBody",
                        erroOriginal = responseBody
                    )
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                Log.e(TAG, "❌ Erro HTTP ${response.code()} para entidade $entidadeId: $errorBody")
                EntidadeSyncResult(
                    entidadeId = entidadeId,
                    sucesso = false,
                    quantidadePontos = pontos.size,
                    mensagem = "Erro HTTP ${response.code()}: $errorBody",
                    erroOriginal = "Erro HTTP ${response.code()}: $errorBody"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao sincronizar entidade $entidadeId: ${e.message}")
            EntidadeSyncResult(
                entidadeId = entidadeId,
                sucesso = false,
                quantidadePontos = pontos.size,
                mensagem = ErrorMessageHelper.getFriendlyErrorMessage(e),
                erroOriginal = e.stackTraceToString()
            )
        }
    }
    
    // Obter quantidade de pontos pendentes por entidade
    suspend fun getQuantidadePontosPendentesPorEntidade(context: Context): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val pontosDao = PontosGenericosDao()
                val pontosPorEntidade = pontosDao.getNaoSincronizadosPorEntidade()
                
                val quantidadePorEntidade = pontosPorEntidade.mapValues { it.value.size }
                
                Log.d(TAG, "📊 Pontos pendentes por entidade: $quantidadePorEntidade")
                quantidadePorEntidade
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter quantidade de pontos pendentes por entidade: ${e.message}")
                emptyMap()
            }
        }
    }
}
