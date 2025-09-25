package com.ml.shubham0204.facenet_android.data

import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

@Single
class PersonDB {
    private val personBox = ObjectBoxStore.store.boxFor(PersonRecord::class.java)

    fun addPerson(person: PersonRecord): Long = personBox.put(person)

    fun removePerson(personID: Long) {
        personBox.removeByIds(listOf(personID))
    }

    // Returns the number of records present in the collection
    fun getCount(): Long = personBox.count()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAll(): Flow<MutableList<PersonRecord>> =
        personBox
            .query(PersonRecord_.personID.notNull())
            .order(PersonRecord_.personName) // ✅ NOVO: Ordenar alfabeticamente por nome
            .build()
            .flow()
            .flowOn(Dispatchers.IO)
            
    suspend fun getPersonByFuncionarioId(funcionarioId: Long): PersonRecord? {
        android.util.Log.d("PersonDB", "🔍 Buscando pessoa com funcionarioId: $funcionarioId")
        
        val result = personBox
            .query(PersonRecord_.funcionarioId.equal(funcionarioId))
            .build()
            .findFirst()
            
        if (result != null) {
            android.util.Log.d("PersonDB", "✅ Pessoa encontrada: ${result.personName} (personID: ${result.personID}, funcionarioId: ${result.funcionarioId})")
        } else {
            android.util.Log.d("PersonDB", "❌ Nenhuma pessoa encontrada com funcionarioId: $funcionarioId")
            
            val allPersons = personBox.all
            android.util.Log.d("PersonDB", "📋 Todas as pessoas no banco:")
            allPersons.forEach { person ->
                android.util.Log.d("PersonDB", "   - ${person.personName} (personID: ${person.personID}, funcionarioId: ${person.funcionarioId})")
            }
        }
        
        return result
    }
    
    // ✅ NOVO: Função para obter todas as pessoas ordenadas por tempo de cadastro
    fun getAllPersonsSortedByTime(): List<PersonRecord> {
        return personBox.all.sortedBy { it.addTime }
    }
}
