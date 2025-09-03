package com.ml.shubham0204.facenet_android.data

import android.util.Log
import io.objectbox.Box

class FuncionariosDao {
    private val box: Box<FuncionariosEntity> = ObjectBoxStore.store.boxFor(FuncionariosEntity::class.java)

    fun insert(funcionario: FuncionariosEntity): Long {
        Log.d("FuncionariosDao", "💾 Inserindo funcionário: ${funcionario.nome}")
        Log.d("FuncionariosDao", "   - ID Original: ${funcionario.id}")
        Log.d("FuncionariosDao", "   - CPF: ${funcionario.cpf}")
        Log.d("FuncionariosDao", "   - Matrícula: ${funcionario.matricula}")
        
        // Verificar se já existe um funcionário com o mesmo ID da API
        val funcionarioExistente = getByApiId(funcionario.apiId)
        if (funcionarioExistente != null) {
            Log.w("FuncionariosDao", "⚠️ Funcionário já existe no banco: ${funcionario.nome}")
            return funcionarioExistente.id
        }
        
        // Criar nova entidade com ID 0 para ObjectBox gerar automaticamente
        val novaEntidade = funcionario.copy(id = 0)
        val result = box.put(novaEntidade)
        
        Log.d("FuncionariosDao", "✅ Funcionário inserido com ID gerado: $result")
        return result
    }

    fun getAll(): List<FuncionariosEntity> {
        val result = box.all
        Log.d("FuncionariosDao", "📋 Total de funcionários no banco: ${result.size}")
        return result
    }

    fun getById(id: Long): FuncionariosEntity? {
        return box.get(id)
    }
    
    fun getByApiId(apiId: Long): FuncionariosEntity? {
        return getAll().find { it.apiId == apiId }
    }

    fun update(funcionario: FuncionariosEntity) {
        box.put(funcionario)
    }

    fun delete(funcionario: FuncionariosEntity) {
        box.remove(funcionario)
    }

    fun deleteById(id: Long) {
        box.remove(id)
    }

    fun getByCpf(cpf: String): FuncionariosEntity? {
        return getAll().find { it.cpf == cpf }
    }

    fun getByMatricula(matricula: String): FuncionariosEntity? {
        return getAll().find { it.matricula == matricula }
    }

    fun searchByName(name: String): List<FuncionariosEntity> {
        val searchTerm = name.uppercase()
        val funcionariosFiltrados = getAll().filter { 
            it.nome.uppercase().contains(searchTerm) 
        }
        // ✅ NOVO: Ordenar resultados alfabeticamente por nome
        return funcionariosFiltrados.sortedBy { it.nome }
    }
    
    // ✅ NOVO: Métodos para ativação/desativação de funcionários
    fun activateFuncionario(funcionarioId: Long) {
        val funcionario = getById(funcionarioId)
        funcionario?.let {
            val funcionarioAtivado = it.copy(ativo = 1)
            update(funcionarioAtivado)
        }
    }
    
    fun deactivateFuncionario(funcionarioId: Long) {
        val funcionario = getById(funcionarioId)
        funcionario?.let {
            val funcionarioDesativado = it.copy(ativo = 0)
            update(funcionarioDesativado)
        }
    }
    
    // ✅ NOVO: Obter apenas funcionários ativos
    fun getActiveFuncionarios(): List<FuncionariosEntity> {
        return getAll().filter { it.ativo == 1 }
    }
    
    // ✅ NOVO: Obter apenas funcionários inativos
    fun getInactiveFuncionarios(): List<FuncionariosEntity> {
        return getAll().filter { it.ativo == 0 }
    }
    
    // ✅ NOVO: Verificar se funcionário está ativo
    fun isFuncionarioActive(funcionarioId: Long): Boolean {
        val funcionario = getById(funcionarioId)
        return funcionario?.ativo == 1
    }
} 