package com.ml.shubham0204.facenet_android.data

import android.util.Log
import com.ml.shubham0204.facenet_android.data.ObjectBoxStore.store
import io.objectbox.Box

class PontosGenericosDao {
    private val box: Box<PontosGenericosEntity> = store.boxFor(PontosGenericosEntity::class.java)
    
    fun insert(ponto: PontosGenericosEntity): Long {
        return try {
            val id = box.put(ponto)
            Log.d("PontosGenericosDao", "✅ Ponto salvo com ID: $id")
            id
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao salvar ponto: ${e.message}")
            throw e
        }
    }
    
    fun getAll(): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all
            Log.d("PontosGenericosDao", "📋 Total de pontos: ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos: ${e.message}")
            emptyList()
        }
    }
    
    fun getById(id: Long): PontosGenericosEntity? {
        return try {
            val ponto = box.get(id)
            if (ponto != null) {
                Log.d("PontosGenericosDao", "✅ Ponto encontrado: ${ponto.funcionarioNome}")
            } else {
                Log.w("PontosGenericosDao", "⚠️ Ponto não encontrado com ID: $id")
            }
            ponto
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar ponto por ID: ${e.message}")
            null
        }
    }
    
    fun getByFuncionarioId(funcionarioId: String): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all.filter { it.funcionarioCpf == funcionarioId } // Usar CPF em vez do ID interno
            Log.d("PontosGenericosDao", "📋 Pontos do funcionário $funcionarioId: ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos do funcionário: ${e.message}")
            emptyList()
        }
    }
    
    fun getNaoSincronizados(): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all.filter { !it.synced }
            Log.d("PontosGenericosDao", "📋 Pontos não sincronizados: ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos não sincronizados: ${e.message}")
            emptyList()
        }
    }
    
    fun marcarComoSincronizado(id: Long) {
        try {
            val ponto = box.get(id)
            if (ponto != null) {
                ponto.synced = true
                box.put(ponto)
                Log.d("PontosGenericosDao", "✅ Ponto $id marcado como sincronizado")
            }
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao marcar ponto como sincronizado: ${e.message}")
        }
    }
    
    fun delete(id: Long) {
        try {
            box.remove(id)
            Log.d("PontosGenericosDao", "✅ Ponto $id removido")
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao remover ponto: ${e.message}")
        }
    }
    
    fun deleteAll() {
        try {
            box.removeAll()
            Log.d("PontosGenericosDao", "✅ Todos os pontos removidos")
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao remover todos os pontos: ${e.message}")
        }
    }
    
    fun deleteByFuncionarioNome(funcionarioNome: String): Int {
        return try {
            val pontosParaRemover = box.all.filter { it.funcionarioNome == funcionarioNome }
            val idsParaRemover = pontosParaRemover.map { it.id }
            
            if (idsParaRemover.isNotEmpty()) {
                box.remove(*idsParaRemover.toLongArray()) // Usar spread operator
                Log.d("PontosGenericosDao", "✅ ${idsParaRemover.size} pontos do funcionário '$funcionarioNome' removidos")
                idsParaRemover.size
            } else {
                Log.d("PontosGenericosDao", "ℹ️ Nenhum ponto encontrado para o funcionário '$funcionarioNome'")
                0
            }
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao remover pontos do funcionário '$funcionarioNome': ${e.message}")
            0
        }
    }
} 