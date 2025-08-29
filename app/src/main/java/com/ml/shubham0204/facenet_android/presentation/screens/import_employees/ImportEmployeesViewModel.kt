package com.ml.shubham0204.facenet_android.presentation.screens.import_employees

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.data.api.FuncionariosModel
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ImportEmployeesViewModel : ViewModel(), KoinComponent {
    
    private val funcionariosDao = FuncionariosDao()
    private val configuracoesDao = ConfiguracoesDao()
    private val apiService = RetrofitClient.instance
    private val context: Context by inject()
    
    private val _uiState = MutableStateFlow(ImportEmployeesUiState())
    val uiState: StateFlow<ImportEmployeesUiState> = _uiState.asStateFlow()
    
    private var currentPage = 1
    private var isLoading = false
    private var isLoadingMore = false
    private var hasMorePages = true
    private var searchJob: kotlinx.coroutines.Job? = null
    
    private fun getEntidadeId(): String {
        return try {
            val configuracoes = configuracoesDao.getConfiguracoes()
            val entidadeId = configuracoes?.entidadeId ?: ""
            Log.d("ImportEmployeesViewModel", "🏢 Entidade ID obtida: '$entidadeId'")
            entidadeId
        } catch (e: Exception) {
            Log.e("ImportEmployeesViewModel", "❌ Erro ao obter entidade ID", e)
            ""
        }
    }
    
    init {
        loadFuncionarios()
        loadImportedIds()
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filtrarFuncionarios(query)
    }
    
    fun importFuncionario(funcionario: FuncionariosModel) {
        _uiState.update { 
            it.copy(
                selectedFuncionario = funcionario,
                showImportDialog = true
            )
        }
    }
    
    fun hideImportDialog() {
        _uiState.update { 
            it.copy(
                showImportDialog = false,
                selectedFuncionario = null
            )
        }
    }
    
    fun confirmImport() {
        val funcionario = _uiState.value.selectedFuncionario ?: return
        
        Log.d("ImportEmployeesViewModel", "🚀 Iniciando importação do funcionário: ${funcionario.nome}")
        
        viewModelScope.launch {
            try {
                val cpfLimpo = funcionario.numero_cpf.replace(Regex("[^0-9]"), "")
                
                Log.d("ImportEmployeesViewModel", "📝 Dados do funcionário:")
                Log.d("ImportEmployeesViewModel", "   - ID: ${funcionario.id}")
                Log.d("ImportEmployeesViewModel", "   - Nome: ${funcionario.nome}")
                Log.d("ImportEmployeesViewModel", "   - CPF Original: ${funcionario.numero_cpf}")
                Log.d("ImportEmployeesViewModel", "   - CPF Limpo: $cpfLimpo")
                Log.d("ImportEmployeesViewModel", "   - Matrícula: ${funcionario.matricula}")
                Log.d("ImportEmployeesViewModel", "   - Cargo: ${funcionario.cargo_descricao}")
                
                // Verificar se já existe por ID da API
                val funcionarioExistente = funcionariosDao.getByApiId(funcionario.id.toLong())
                if (funcionarioExistente != null) {
                    Log.w("ImportEmployeesViewModel", "⚠️ Funcionário já existe: ${funcionario.nome}")
                    Toast.makeText(
                        context,
                        "⚠️ ${funcionario.nome} já foi importado!",
                        Toast.LENGTH_SHORT
                    ).show()
                    hideImportDialog()
                    return@launch
                }
                
                val funcionarioEntity = FuncionariosEntity(
                    id = 0, // ObjectBox vai gerar automaticamente
                    codigo = cpfLimpo,
                    nome = funcionario.nome,
                    ativo = 1,
                    matricula = funcionario.matricula,
                    cpf = cpfLimpo,
                    cargo = funcionario.cargo_descricao,
                    secretaria = funcionario.orgao_descricao ?: "N/A",
                    lotacao = funcionario.setor_descricao ?: "N/A",
                    apiId = funcionario.id.toLong() // ID original da API
                )
                
                Log.d("ImportEmployeesViewModel", "💾 Salvando no banco de dados...")
                funcionariosDao.insert(funcionarioEntity)
                Log.d("ImportEmployeesViewModel", "✅ Funcionário salvo com sucesso!")
                
                // Atualizar lista de IDs importados
                loadImportedIds()
                
                hideImportDialog()
                
                // Mostrar Toast de sucesso
                Toast.makeText(
                    context,
                    "✅ ${funcionario.nome} importado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e("ImportEmployeesViewModel", "❌ Erro ao importar funcionário", e)
                
                // Mostrar Toast de erro
                Toast.makeText(
                    context,
                    "❌ Erro ao importar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    fun loadMoreFuncionarios() {
        Log.d("ImportEmployeesViewModel", "🔄 loadMoreFuncionarios chamado")
        Log.d("ImportEmployeesViewModel", "   - isLoadingMore: $isLoadingMore")
        Log.d("ImportEmployeesViewModel", "   - hasMorePages: $hasMorePages")
        Log.d("ImportEmployeesViewModel", "   - searchQuery: '${_uiState.value.searchQuery}'")
        
        if (isLoadingMore || !hasMorePages) {
            Log.d("ImportEmployeesViewModel", "❌ Condição não atendida para carregar mais")
            return
        }
        
        Log.d("ImportEmployeesViewModel", "✅ Carregando mais funcionários...")
        loadMoreFuncionariosInternal()
    }
    
    private fun loadFuncionarios() {
        if (isLoading) return
        
        isLoading = true
        currentPage = 1
        hasMorePages = true
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                val entidadeId = getEntidadeId()
                
                if (entidadeId.isEmpty()) {
                    Log.w("ImportEmployeesViewModel", "⚠️ Entidade ID não configurada")
                    _uiState.update { 
                        it.copy(
                            funcionarios = emptyList(),
                            isLoading = false
                        )
                    }
                    return@launch
                }
                
                Log.d("ImportEmployeesViewModel", "📡 Carregando funcionários para entidade: $entidadeId")
                
                val response = apiService.getFuncionarios(entidadeId, currentPage)
                val funcionarios = response.data ?: emptyList()
                
                if (funcionarios.isNotEmpty()) {
                    _uiState.update { 
                        it.copy(
                            funcionarios = funcionarios,
                            isLoading = false,
                            hasMorePages = funcionarios.size >= 20 // Assumindo 20 por página
                        )
                    }
                    currentPage++
                    hasMorePages = funcionarios.size >= 20
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            hasMorePages = false
                        )
                    }
                    hasMorePages = false
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                // TODO: Mostrar erro
            } finally {
                isLoading = false
            }
        }
    }
    
    private fun loadMoreFuncionariosInternal() {
        if (isLoadingMore) return
        
        isLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        
        viewModelScope.launch {
            try {
                val entidadeId = getEntidadeId()
                
                if (entidadeId.isEmpty()) {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    return@launch
                }
                
                Log.d("ImportEmployeesViewModel", "📡 Carregando mais funcionários - página $currentPage")
                
                val response = apiService.getFuncionarios(entidadeId, currentPage)
                val funcionarios = response.data ?: emptyList()
                
                if (funcionarios.isNotEmpty()) {
                    val currentList = _uiState.value.funcionarios.toMutableList()
                    currentList.addAll(funcionarios)
                    
                    _uiState.update { 
                        it.copy(
                            funcionarios = currentList,
                            isLoadingMore = false,
                            hasMorePages = funcionarios.size >= 20
                        )
                    }
                    currentPage++
                    hasMorePages = funcionarios.size >= 20
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoadingMore = false,
                            hasMorePages = false
                        )
                    }
                    hasMorePages = false
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
                // TODO: Mostrar erro
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    private fun loadImportedIds() {
        viewModelScope.launch {
            try {
                val funcionariosImportados = funcionariosDao.getAll()
                val idsImportados = funcionariosImportados.map { it.apiId }.toSet()
                
                _uiState.update { it.copy(importedIds = idsImportados) }
            } catch (e: Exception) {
                // TODO: Tratar erro
            }
        }
    }
    
    private fun filtrarFuncionarios(query: String) {
        searchJob?.cancel()
        
        val descricao = query.trim()
        
        if (descricao.isEmpty()) {
            // Resetar para lista completa
            currentPage = 1
            _uiState.update { it.copy(funcionarios = emptyList()) }
            loadFuncionarios()
        } else {
            // Debounce: aguardar 500ms antes de fazer a busca
            searchJob = viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                
                try {
                    val entidadeId = getEntidadeId()
                    
                    if (entidadeId.isEmpty()) {
                        _uiState.update { 
                            it.copy(funcionarios = emptyList())
                        }
                        return@launch
                    }
                    
                    val response = apiService.getFuncionarios(entidadeId, 1, descricao)
                    val funcionarios = response.data ?: emptyList()
                    
                    // Filtrar resultados precisos
                    val funcionariosFiltrados = filtrarResultadosPrecisos(funcionarios, descricao)
                    
                    _uiState.update { 
                        it.copy(funcionarios = funcionariosFiltrados)
                    }
                    
                } catch (e: Exception) {
                    // TODO: Mostrar erro
                }
            }
        }
    }
    
    private fun filtrarResultadosPrecisos(
        funcionarios: List<FuncionariosModel>, 
        descricao: String
    ): List<FuncionariosModel> {
        if (descricao.isEmpty()) return funcionarios
        
        val descricaoUpper = descricao.uppercase()
        
        return funcionarios.filter { funcionario ->
            val nome = funcionario.nome.uppercase()
            val cargo = funcionario.cargo_descricao.uppercase()
            val orgao = funcionario.orgao_descricao?.uppercase() ?: ""
            val setor = funcionario.setor_descricao?.uppercase() ?: ""
            val matricula = funcionario.matricula.uppercase()
            val cpf = funcionario.numero_cpf.replace(Regex("[^0-9]"), "")
            val cpfBusca = descricao.replace(Regex("[^0-9]"), "")
            
            nome.contains(descricaoUpper) ||
            cargo.contains(descricaoUpper) ||
            orgao.contains(descricaoUpper) ||
            setor.contains(descricaoUpper) ||
            matricula.contains(descricaoUpper) ||
            (cpfBusca.isNotEmpty() && cpf.contains(cpfBusca))
        }
    }
}

data class ImportEmployeesUiState(
    val funcionarios: List<FuncionariosModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val importedIds: Set<Long> = emptySet(),
    val showImportDialog: Boolean = false,
    val selectedFuncionario: FuncionariosModel? = null
) 