package com.ml.shubham0204.facenet_android.domain

import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.PersonDB
import com.ml.shubham0204.facenet_android.data.PersonRecord
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class PersonUseCase(
    private val personDB: PersonDB,
    private val funcionariosDao: FuncionariosDao
) {
    fun addPerson(
        name: String,
        numImages: Long,
        funcionarioId: Long = 0, // ✅ NOVO: Parâmetro opcional
    ): Long =
        personDB.addPerson(
            PersonRecord(
                personName = name,
                numImages = numImages,
                addTime = System.currentTimeMillis(),
                funcionarioId = funcionarioId, // ✅ NOVO: Salvar o funcionarioId
            ),
        )

    fun removePerson(personID: Long) {
        personDB.removePerson(personID)
    }

    fun getAll(): Flow<List<PersonRecord>> = personDB.getAll()

    fun getCount(): Long = personDB.getCount()
    
    // ✅ NOVO: Função para buscar pessoa por funcionarioId
    suspend fun getPersonByFuncionarioId(funcionarioId: Long): PersonRecord? {
        return personDB.getPersonByFuncionarioId(funcionarioId)
    }
    
    // ✅ NOVO: Função para verificar se funcionário está ativo antes de operações de facial
    suspend fun isFuncionarioActive(funcionarioId: Long): Boolean {
        return funcionariosDao.isFuncionarioActive(funcionarioId)
    }
    
    // ✅ NOVO: Função para verificar se pode cadastrar/editar facial
    suspend fun canManageFacial(funcionarioId: Long): Boolean {
        return funcionariosDao.isFuncionarioActive(funcionarioId)
    }
}
