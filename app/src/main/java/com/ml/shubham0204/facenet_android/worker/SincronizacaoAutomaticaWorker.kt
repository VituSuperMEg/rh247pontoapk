package com.ml.shubham0204.facenet_android.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            
            // Executar sincronização
            val pontoSincronizacaoService = PontoSincronizacaoService()
            val resultado = pontoSincronizacaoService.sincronizarPontosPendentes(applicationContext)
            
            if (resultado.sucesso) {
                Log.d(TAG, "✅ Sincronização automática executada com sucesso!")
                Log.d(TAG, "📊 ${resultado.quantidadePontos} pontos sincronizados em ${resultado.duracaoSegundos} segundos")
                Result.success()
            } else {
                Log.e(TAG, "❌ Sincronização automática falhou: ${resultado.mensagem}")
                // Retornar sucesso para não tentar novamente imediatamente
                // O WorkManager tentará novamente no próximo intervalo
                Result.success()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na sincronização automática: ${e.message}")
            e.printStackTrace()
            // Retornar sucesso para não tentar novamente imediatamente
            Result.success()
        }
    }
} 