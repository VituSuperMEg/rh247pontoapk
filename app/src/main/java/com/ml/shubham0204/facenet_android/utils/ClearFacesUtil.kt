package com.ml.shubham0204.facenet_android.utils

import android.util.Log
import com.ml.shubham0204.facenet_android.data.ClearFacesDao

object ClearFacesUtil {
    
    fun clearAllFaces() {
        try {
            val clearFacesDao = ClearFacesDao()
            val facesCount = clearFacesDao.getFacesCount()
            
            Log.d("ClearFacesUtil", "🗑️ Limpando $facesCount faces cadastradas...")
            
            clearFacesDao.clearAllFaces()
            
            Log.d("ClearFacesUtil", "✅ Todas as faces foram removidas com sucesso!")
            
        } catch (e: Exception) {
            Log.e("ClearFacesUtil", "❌ Erro ao limpar faces", e)
        }
    }
} 