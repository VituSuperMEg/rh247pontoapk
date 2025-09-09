package com.ml.shubham0204.facenet_android.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoService
import com.ml.shubham0204.facenet_android.utils.ErrorMessageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class SincronizacaoAutomaticaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "SincronizacaoAutomaticaWorker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🚀 === INICIANDO SINCRONIZAÇÃO AUTOMÁTICA ===")
            
            val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            Log.d(TAG, "⏰ Horário de execução: $dataHora")
            
            val isFrequente = inputData.getBoolean("isFrequente", false)
            val intervalo = inputData.getInt("intervalo", 15)
            
            // Executar sincronização
            val pontoSincronizacaoService = PontoSincronizacaoService()
            val resultado = pontoSincronizacaoService.sincronizarPontosPendentes(applicationContext)
            
            if (resultado.sucesso) {
                Log.d(TAG, "✅ Sincronização automática executada com sucesso!")
                Log.d(TAG, "📊 ${resultado.quantidadePontos} pontos sincronizados em ${resultado.duracaoSegundos} segundos")
                
                // Adicionar ao histórico
                adicionarAoHistorico(
                    dataHora = dataHora,
                    mensagem = "✅ Sincronização automática: ${resultado.quantidadePontos} pontos sincronizados",
                    status = "Sucesso"
                )
                
                // Se for sincronização frequente, agendar a próxima
                if (isFrequente) {
                    agendarProximaSincronizacao(intervalo)
                }
                
                Result.success()
            } else {
                Log.e(TAG, "❌ Sincronização automática falhou: ${resultado.mensagem}")
                
                // Adicionar erro ao histórico
                adicionarAoHistorico(
                    dataHora = dataHora,
                    mensagem = ErrorMessageHelper.getFriendlySyncMessage("Sincronização automática falhou: ${resultado.mensagem}", false),
                    status = "Erro"
                )
                
                // Se for sincronização frequente, agendar a próxima mesmo com erro
                if (isFrequente) {
                    agendarProximaSincronizacao(intervalo)
                }
                
                Result.success()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na sincronização automática: ${e.message}")
            e.printStackTrace()
            
            // Adicionar erro ao histórico
            val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            adicionarAoHistorico(
                dataHora = dataHora,
                mensagem = ErrorMessageHelper.getFriendlySyncMessage("Erro na sincronização automática: ${e.message}", false),
                status = "Erro"
            )
            
            // Se for sincronização frequente, agendar a próxima mesmo com erro
            val isFrequente = inputData.getBoolean("isFrequente", false)
            val intervalo = inputData.getInt("intervalo", 15)
            if (isFrequente) {
                agendarProximaSincronizacao(intervalo)
            }
            
            Result.success()
        }
    }
    
    private fun agendarProximaSincronizacao(intervalo: Int) {
        try {
            val workManager = WorkManager.getInstance(applicationContext)
            
            val inputData = Data.Builder()
                .putInt("intervalo", intervalo)
                .putBoolean("isFrequente", true)
                .build()
            
            val proximaSincronizacao = OneTimeWorkRequestBuilder<SincronizacaoAutomaticaWorker>()
                .setInputData(inputData)
                .addTag("sincronizacao_automatica")
                .setInitialDelay(intervalo.toLong(), TimeUnit.MINUTES)
                .build()
            
            workManager.enqueue(proximaSincronizacao)
            
            Log.d(TAG, "⏰ Próxima sincronização agendada para $intervalo minutos")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao agendar próxima sincronização: ${e.message}")
        }
    }
    
    private fun adicionarAoHistorico(dataHora: String, mensagem: String, status: String) {
        try {
            val prefs = applicationContext.getSharedPreferences("historico_sincronizacao", Context.MODE_PRIVATE)
            val historicoJson = prefs.getString("historico", "[]") ?: "[]"
            
            // Parse do JSON existente e adicionar nova entrada
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val historicoList = gson.fromJson<List<Map<String, String>>>(historicoJson, type).toMutableList()
            
            historicoList.add(mapOf(
                "dataHora" to dataHora,
                "mensagem" to mensagem,
                "status" to status
            ))
            
            // Manter apenas os últimos 50 registros
            if (historicoList.size > 50) {
                historicoList.removeAt(0)
            }
            
            val novoHistoricoJson = gson.toJson(historicoList)
            prefs.edit().putString("historico", novoHistoricoJson).apply()
            
            Log.d(TAG, "📝 Histórico atualizado: $mensagem")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao salvar histórico: ${e.message}")
        }
    }
} 