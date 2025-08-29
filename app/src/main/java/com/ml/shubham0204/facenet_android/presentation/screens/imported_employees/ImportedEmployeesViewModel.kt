package com.ml.shubham0204.facenet_android.presentation.screens.imported_employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ImportedEmployeesViewModel : ViewModel() {
    
    private val funcionariosDao = FuncionariosDao()
    
    private val _uiState = MutableStateFlow(ImportedEmployeesUiState())
    val uiState: StateFlow<ImportedEmployeesUiState> = _uiState.asStateFlow()
    
    init {
        loadImportedEmployees()
    }
    
    fun loadImportedEmployees() {
        viewModelScope.launch {
            try {
                val funcionarios = funcionariosDao.getAll()
                _uiState.update { 
                    it.copy(
                        funcionarios = funcionarios,
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
}

data class ImportedEmployeesUiState(
    val funcionarios: List<FuncionariosEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) 