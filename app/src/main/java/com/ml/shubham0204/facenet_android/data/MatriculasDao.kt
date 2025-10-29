package com.ml.shubham0204.facenet_android.data

import io.objectbox.Box
import com.ml.shubham0204.facenet_android.data.ObjectBoxStore.store

class MatriculasDao {

    private val box: Box<MatriculasEntity> = store.boxFor(MatriculasEntity::class.java)


    fun insert(matriculas: MatriculasEntity): Unit {
        return try {
            val id = box.put(matriculas)
        }catch (e: Exception) {
            throw  e
        }
    }

    fun getByCpf(cpf: String): MatriculasEntity? {
        return box.all.find { it.funcionarioCpf == cpf }
    }

    fun getAll(): List<MatriculasEntity> {
        return box.all
    }

    fun getMatriculasCompletasByCpf(cpf: String): List<MatriculaCompleta> {
        val entity = getByCpf(cpf) ?: return emptyList()
        
        val matriculas = entity.matricula.filter { it.isNotEmpty() }
        val cargos = entity.cargoDescricao?.filter { it.isNotEmpty() } ?: emptyList()
        val ativos = entity.ativo?.filter { it.isNotEmpty() } ?: emptyList()
        val setores = entity.setorDescricao?.filter { it.isNotEmpty() } ?: emptyList()
        val orgaos = entity.orgaoDescricao?.filter { it.isNotEmpty() } ?: emptyList()
        
        android.util.Log.d("MatriculasDao", "📋 Recuperando matrículas para CPF: $cpf")
        android.util.Log.d("MatriculasDao", "   Matrículas encontradas: ${matriculas.size}")
        android.util.Log.d("MatriculasDao", "   Cargos encontrados: ${cargos.size}")
        android.util.Log.d("MatriculasDao", "   Setores encontrados: ${setores.size}")
        android.util.Log.d("MatriculasDao", "   Órgãos encontrados: ${orgaos.size}")
        
        return matriculas.mapIndexed { index, matricula ->
            val cargo = cargos.getOrElse(index) { "" }
            val setor = setores.getOrElse(index) { "" }
            val orgao = orgaos.getOrElse(index) { "" }
            
            android.util.Log.d("MatriculasDao", "   Matrícula $index: $matricula")
            android.util.Log.d("MatriculasDao", "     Cargo: '$cargo'")
            android.util.Log.d("MatriculasDao", "     Setor: '$setor'")
            android.util.Log.d("MatriculasDao", "     Órgão: '$orgao'")
            
            MatriculaCompleta(
                matricula = matricula,
                cargoDescricao = cargo,
                ativo = ativos.getOrElse(index) { "0" }.toIntOrNull() ?: 0,
                setorDescricao = setor,
                orgaoDescricao = orgao
            )
        }
    }

    // ✅ NOVO: Método para excluir matrícula
    fun delete(matricula: MatriculasEntity) {
        box.remove(matricula)
    }

    // ✅ NOVO: Método para limpar todos os dados (para resolver problemas de compatibilidade)
    fun clearAll() {
        box.removeAll()
    }
    
    // ✅ NOVO: Método para excluir matrículas por CPF do funcionário
    fun deleteByCpf(cpf: String) {
        val matriculas = box.all.filter { it.funcionarioCpf == cpf }
        android.util.Log.d("MatriculasDao", "🗑️ Excluindo ${matriculas.size} matrículas para CPF: $cpf")
        matriculas.forEach { matricula ->
            box.remove(matricula)
            android.util.Log.d("MatriculasDao", "🗑️ Matrícula excluída: ID ${matricula.id}, Matrículas: ${matricula.matricula}")
        }
    }
    
    // ✅ NOVO: Método para excluir matrículas por ID do funcionário
    fun deleteByFuncionarioId(funcionarioId: String) {
        val matriculas = box.all.filter { it.funcionarioId == funcionarioId }
        android.util.Log.d("MatriculasDao", "🗑️ Excluindo ${matriculas.size} matrículas para Funcionário ID: $funcionarioId")
        matriculas.forEach { matricula ->
            box.remove(matricula)
            android.util.Log.d("MatriculasDao", "🗑️ Matrícula excluída: ID ${matricula.id}, Matrículas: ${matricula.matricula}")
        }
    }
}