package com.ml.shubham0204.facenet_android.utils

import android.util.Log
import com.ml.shubham0204.facenet_android.data.PontosGenericosDao

object ClearAdeiltonPointsUtil {
    
    fun clearAdeiltonPoints() {
        try {
            val pontosDao = PontosGenericosDao()
            val pontosRemovidos = pontosDao.deleteByFuncionarioNome("ADEILTON CAITANO DA SILVA")
            
            Log.d("ClearAdeiltonPointsUtil", "🗑️ Removidos $pontosRemovidos pontos do ADEILTON")
            
            if (pontosRemovidos > 0) {
                Log.d("ClearAdeiltonPointsUtil", "✅ Limpeza concluída com sucesso")
            } else {
                Log.d("ClearAdeiltonPointsUtil", "ℹ️ Nenhum ponto do ADEILTON encontrado para remover")
            }
            
        } catch (e: Exception) {
            Log.e("ClearAdeiltonPointsUtil", "❌ Erro ao limpar pontos do ADEILTON: ${e.message}")
        }
    }
} 