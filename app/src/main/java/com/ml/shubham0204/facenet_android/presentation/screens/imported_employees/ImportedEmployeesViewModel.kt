package com.ml.shubham0204.facenet_android.presentation.screens.imported_employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.domain.PersonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinViewModel
class ImportedEmployeesViewModel : ViewModel(), KoinComponent {
    
    private val funcionariosDao = FuncionariosDao()
    private val personUseCase: PersonUseCase by inject()
    
    private val _uiState = MutableStateFlow(ImportedEmployeesUiState())
    val uiState: StateFlow<ImportedEmployeesUiState> = _uiState.asStateFlow()
    
    init {
        loadImportedEmployees()
    }
    
    fun loadImportedEmployees() {
        viewModelScope.launch {
            try {
                android.util.Log.d("ImportedEmployeesViewModel", "🔄 Carregando funcionários importados...")
                
                // ✅ NOVO: Por padrão, mostrar apenas funcionários ativos
                val funcionarios = funcionariosDao.getActiveFuncionarios()
                android.util.Log.d("ImportedEmployeesViewModel", "📊 Funcionários ativos encontrados: ${funcionarios.size}")
                
                // ✅ NOVO: Ordenar funcionários alfabeticamente por nome
                val funcionariosOrdenados = funcionarios.sortedBy { it.nome }
                android.util.Log.d("ImportedEmployeesViewModel", "📋 Funcionários ordenados: ${funcionariosOrdenados.size}")
                
                // ✅ NOVO: Verificar quais funcionários têm facial cadastrada
                val funcionariosComFacial = funcionariosOrdenados.map { funcionario ->
                    val temFacial = personUseCase.getPersonByFuncionarioId(funcionario.id) != null
                    android.util.Log.d("ImportedEmployeesViewModel", "👤 ${funcionario.nome}: facial = $temFacial")
                    FuncionarioComFacial(funcionario, temFacial)
                }
                
                android.util.Log.d("ImportedEmployeesViewModel", "✅ Total de funcionários com facial: ${funcionariosComFacial.size}")
                
                _uiState.update { 
                    it.copy(
                        funcionarios = funcionariosComFacial,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ImportedEmployeesViewModel", "❌ Erro ao carregar funcionários: ${e.message}")
                _uiState.update { 
                    it.copy(
                        funcionarios = emptyList(),
                        isLoading = false,
                        error = e.message ?: "Erro ao carregar funcionários"
                    )
                }
            }
        }
    }
    
    // ✅ NOVO: Função para carregar todos os funcionários (ativos + inativos)
    fun loadAllFuncionarios() {
        viewModelScope.launch {
            try {
                val funcionarios = funcionariosDao.getAll()
                val funcionariosOrdenados = funcionarios.sortedBy { it.nome }
                
                val funcionariosComFacial = funcionariosOrdenados.map { funcionario ->
                    val temFacial = personUseCase.getPersonByFuncionarioId(funcionario.id) != null
                    FuncionarioComFacial(funcionario, temFacial)
                }
                
                _uiState.update { 
                    it.copy(
                        funcionarios = funcionariosComFacial,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        funcionarios = emptyList(),
                        isLoading = false,
                        error = e.message ?: "Erro ao carregar funcionários"
                    )
                }
            }
        }
    }
    
    // ✅ NOVO: Função para alternar entre mostrar apenas ativos ou todos
    fun toggleShowInactiveFuncionarios() {
        if (uiState.value.showInactiveFuncionarios) {
            loadImportedEmployees() // Mostrar apenas ativos
        } else {
            loadAllFuncionarios() // Mostrar todos
        }
        
        _uiState.update { 
            it.copy(showInactiveFuncionarios = !it.showInactiveFuncionarios)
        }
    }
    
    fun deleteFuncionario(funcionario: FuncionariosEntity) {
        viewModelScope.launch {
            try {
                funcionariosDao.deleteById(funcionario.id)
                loadImportedEmployees() // Recarregar lista
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Erro ao deletar funcionário")
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    
    // ✅ NOVO: Função de debug para verificar funcionários no banco
    fun debugFuncionarios() {
        viewModelScope.launch {
            try {
                android.util.Log.d("ImportedEmployeesViewModel", "🔍 === DEBUG FUNCIONÁRIOS ===")
                
                val todosFuncionarios = funcionariosDao.getAll()
                android.util.Log.d("ImportedEmployeesViewModel", "📊 Total no banco: ${todosFuncionarios.size}")
                
                val funcionariosAtivos = funcionariosDao.getActiveFuncionarios()
                android.util.Log.d("ImportedEmployeesViewModel", "✅ Ativos: ${funcionariosAtivos.size}")
                
                val funcionariosInativos = funcionariosDao.getInactiveFuncionarios()
                android.util.Log.d("ImportedEmployeesViewModel", "❌ Inativos: ${funcionariosInativos.size}")
                
                // Mostrar detalhes de cada funcionário
                todosFuncionarios.forEach { funcionario ->
                    android.util.Log.d("ImportedEmployeesViewModel", "👤 ${funcionario.nome} - Ativo: ${funcionario.ativo} - Entidade: '${funcionario.entidadeId ?: "null"}'")
                }
                
                android.util.Log.d("ImportedEmployeesViewModel", "🔍 === FIM DEBUG ===")
                
            } catch (e: Exception) {
                android.util.Log.e("ImportedEmployeesViewModel", "❌ Erro no debug: ${e.message}")
            }
        }
    }
    
    // ✅ NOVO: Função para recarregar funcionários (útil quando retornar da tela de facial)
    fun refreshFuncionarios() {
        loadImportedEmployees()
    }
    
    // ✅ NOVO: Funções para ativação/desativação de funcionários
    fun activateFuncionario(funcionarioId: Long) {
        viewModelScope.launch {
            try {
                funcionariosDao.activateFuncionario(funcionarioId)
                loadImportedEmployees() // Recarregar lista
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Erro ao ativar funcionário")
                }
            }
        }
    }
    
    fun deactivateFuncionario(funcionarioId: Long) {
        viewModelScope.launch {
            try {
                funcionariosDao.deactivateFuncionario(funcionarioId)
                loadImportedEmployees() // Recarregar lista
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Erro ao desativar funcionário")
                }
            }
        }
    }
    
    // ✅ NOVO: Função para alternar status do funcionário
    fun toggleFuncionarioStatus(funcionarioId: Long) {
        val funcionario = uiState.value.funcionarios.find { 
            it.funcionario.id == funcionarioId 
        }
        
        funcionario?.let {
            if (it.funcionario.ativo == 1) {
                deactivateFuncionario(funcionarioId)
            } else {
                activateFuncionario(funcionarioId)
            }
        }
    }
}

// ✅ NOVO: Data class para funcionário com informação de facial
data class FuncionarioComFacial(
    val funcionario: FuncionariosEntity,
    val temFacial: Boolean
)

data class ImportedEmployeesUiState(
    val funcionarios: List<FuncionarioComFacial> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showInactiveFuncionarios: Boolean = false
) 