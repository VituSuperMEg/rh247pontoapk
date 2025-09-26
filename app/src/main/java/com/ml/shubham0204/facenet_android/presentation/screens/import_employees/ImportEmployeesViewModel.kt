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
import com.ml.shubham0204.facenet_android.data.api.LocalizacaoModel
import com.ml.shubham0204.facenet_android.data.api.OrgaoModel
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient
import com.ml.shubham0204.facenet_android.utils.ErrorMessageHelper
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
    
    private fun getEntidadeId(): String? {
        return try {
            val selectedEntidade = _uiState.value.selectedEntidade
            if (selectedEntidade != null) {
                return selectedEntidade
            }
            
            val configuracoes = configuracoesDao.getConfiguracoes()
            val entidadeId = configuracoes?.entidadeId ?: ""
            if (entidadeId.isNullOrEmpty()) null else entidadeId
        } catch (e: Exception) {
            Log.e("ImportEmployeesViewModel", "❌ Erro ao obter entidade ID", e)
            null
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

        viewModelScope.launch {
            try {
                val cpfLimpo = funcionario.numero_cpf.replace(Regex("[^0-9]"), "")

                val funcionarioExistente = funcionariosDao.getByApiId(funcionario.id.toLong())
                if (funcionarioExistente != null) {
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
                    apiId = funcionario.id.toLong(),
                    dataImportacao = System.currentTimeMillis(),
                    entidadeId = getEntidadeId()
                )
                
                funcionariosDao.insert(funcionarioEntity)

                loadImportedIds()
                
                hideImportDialog()
                
                Toast.makeText(
                    context,
                    "✅ ${funcionario.nome} importado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "❌ Erro ao importar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    fun addToImportQueue(funcionario: FuncionariosModel) {
        val currentQueue = _uiState.value.funcionariosParaImportar.toMutableList()
        if (!currentQueue.any { it.id == funcionario.id }) {
            currentQueue.add(funcionario)
            _uiState.update { 
                it.copy(funcionariosParaImportar = currentQueue)
            }
            Toast.makeText(
                context,
                "✅ ${funcionario.nome} adicionado à lista de importação!",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                "⚠️ ${funcionario.nome} já está na lista de importação!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    fun removeFromImportQueue(funcionario: FuncionariosModel) {
        val currentQueue = _uiState.value.funcionariosParaImportar.toMutableList()
        currentQueue.removeAll { it.id == funcionario.id }
        _uiState.update { 
            it.copy(funcionariosParaImportar = currentQueue)
        }
    }
    
    fun importAllFromQueue() {
        val funcionariosParaImportar = _uiState.value.funcionariosParaImportar
        if (funcionariosParaImportar.isEmpty()) {
            Toast.makeText(context, "⚠️ Nenhum funcionário na lista de importação!", Toast.LENGTH_SHORT).show()
            return
        }
        
        _uiState.update { it.copy(isImportingBatch = true) }
        
        viewModelScope.launch {
            var sucessos = 0
            var erros = 0
            
            funcionariosParaImportar.forEach { funcionario ->
                try {
                    val funcionarioExistente = funcionariosDao.getByApiId(funcionario.id.toLong())
                    if (funcionarioExistente != null) {
                        erros++
                        return@forEach
                    }
                    
                    val cpfLimpo = funcionario.numero_cpf.replace(Regex("[^0-9]"), "")
                    val funcionarioEntity = FuncionariosEntity(
                        id = 0,
                        codigo = cpfLimpo,
                        nome = funcionario.nome,
                        ativo = 1,
                        matricula = funcionario.matricula,
                        cpf = cpfLimpo,
                        cargo = funcionario.cargo_descricao,
                        secretaria = funcionario.orgao_descricao ?: "N/A",
                        lotacao = funcionario.setor_descricao ?: "N/A",
                        apiId = funcionario.id.toLong(),
                        dataImportacao = System.currentTimeMillis(),
                        entidadeId = getEntidadeId()
                    )
                    
                    funcionariosDao.insert(funcionarioEntity)
                    sucessos++
                } catch (e: Exception) {
                    Log.e("ImportEmployeesViewModel", "Erro ao importar ${funcionario.nome}", e)
                    erros++
                }
            }
            
            _uiState.update {
                it.copy(
                    funcionariosParaImportar = emptyList(),
                    isImportingBatch = false
                )
            }
            
            loadImportedIds()
            
            val mensagem = if (erros == 0) {
                "✅ $sucessos funcionários importados com sucesso!"
            } else {
                "⚠️ $sucessos importados, $erros erros/já existentes"
            }
            
            Toast.makeText(context, mensagem, Toast.LENGTH_LONG).show()
        }
    }
    
    fun clearImportQueue() {
        _uiState.update { it.copy(funcionariosParaImportar = emptyList()) }
    }
    
    fun updateSelectedTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTabIndex = tabIndex) }
    }
    
    fun setSelectedOrgao(orgao: OrgaoModel?) {
        _uiState.update { it.copy(selectedOrgao = orgao) }
        loadFuncionarios()
    }

    fun setSelectedLocalizacao(localizacao: LocalizacaoModel?) {
        _uiState.update { it.copy(selectedLocalizacao = localizacao) }
        loadFuncionarios()
    }
    
    fun clearOrgaoFilter() {
        _uiState.update { it.copy(selectedOrgao = null) }
        loadFuncionarios()
    }
    
    fun clearLocalizacaoFilter() {
        _uiState.update { it.copy(selectedLocalizacao = null) }
        loadFuncionarios()
    }
    
    fun setSelectedEntidade(entidade: String?) {
        _uiState.update { it.copy(selectedEntidade = entidade) }
        // Recarregar funcionários com o novo filtro de entidade
        loadFuncionarios()
    }
    
    fun clearEntidadeFilter() {
        _uiState.update { it.copy(selectedEntidade = null) }
        // Recarregar funcionários sem filtro de entidade
        loadFuncionarios()
    }
    
    fun testEntidadeAndLoad(entidadeCode: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("ImportEmployeesViewModel", "🔍 Testando entidade: $entidadeCode")
                
                // Fazer uma busca de teste para verificar se a entidade existe
                val response = apiService.getFuncionarios(entidadeCode, 1, null, null, null)
                val funcionarios = response.data ?: emptyList()
                
                Log.d("ImportEmployeesViewModel", "📊 Entidade testada - funcionários encontrados: ${funcionarios.size}")
                
                // Se chegou até aqui, a entidade existe
                callback(true, null)
                
            } catch (e: Exception) {
                Log.e("ImportEmployeesViewModel", "❌ Erro ao testar entidade", e)
                
                val errorMessage = when {
                    e.message?.contains("Código do cliente errado", ignoreCase = true) == true -> {
                        "❌ Código da entidade incorreto ou não encontrado"
                    }
                    e.message?.contains("access-control-allow-origin", ignoreCase = true) == true -> {
                        "🌐 Problema de configuração do servidor"
                    }
                    else -> {
                        "❌ Erro ao verificar entidade: ${e.message ?: "Erro desconhecido"}"
                    }
                }
                
                callback(false, errorMessage)
            }
        }
    }
    
    fun showError(errorMessage: String) {
        val friendlyMessage = when {
            errorMessage.contains("Código do cliente errado ou não configurado", ignoreCase = true) -> {
                "🔐 Código do cliente incorreto ou não configurado. Verifique as configurações de sincronização."
            }
            errorMessage.contains("access-control-allow-origin", ignoreCase = true) -> {
                "🌐 Problema de configuração do servidor. Entre em contato com o suporte técnico."
            }
            else -> {
                ErrorMessageHelper.getFriendlyErrorMessage(errorMessage)
            }
        }
        
        _uiState.update { 
            it.copy(
                errorMessage = friendlyMessage,
                showErrorDialog = true
            )
        }
    }
    
    fun hideError() {
        _uiState.update { 
            it.copy(
                errorMessage = null,
                showErrorDialog = false
            )
        }
    }
    
    // Função para testar o erro específico mencionado
    fun testSpecificError() {
        val testErrorMessage = "access-control-allow-origin: *\n2025-09-09 16:22:45.197 22531-30521 okhttp.OkHttpClient     com.ml.shubham0204.facenet_android   I  {\"message\":\"Código do cliente errado ou não configurado.\"}"
        showError(testErrorMessage)
    }
    
    suspend fun loadOrgaos(): List<OrgaoModel> {
        return try {
            val entidadeId = getEntidadeId()
            
            if (entidadeId.isNullOrEmpty()) {
                Log.w("ImportEmployeesViewModel", "⚠️ Entidade ID não configurada para carregar órgãos")
                return emptyList()
            }
            
            Log.d("ImportEmployeesViewModel", "📡 Carregando órgãos para entidade: $entidadeId")
            
            // Fazer a chamada assíncrona para a API
            val response = apiService.getOrgaos(entidadeId)
            
            val orgaos = response.data ?: emptyList()
            Log.d("ImportEmployeesViewModel", "📊 Órgãos carregados: ${orgaos.size}")
            
            // Ordenar órgãos alfabeticamente por descrição
            val orgaosOrdenados = orgaos.sortedBy { it.descricao }
            Log.d("ImportEmployeesViewModel", "📊 Órgãos ordenados alfabeticamente")
            
            orgaosOrdenados
        } catch (e: Exception) {
            Log.e("ImportEmployeesViewModel", "❌ Erro ao carregar órgãos", e)
            
            // Mostrar erro amigável
            val errorMessage = e.message ?: "Erro desconhecido ao carregar órgãos"
            showError(errorMessage)
            
            emptyList()
        }
    }
    
    suspend fun loadLocalizacoes(): List<LocalizacaoModel> {
        return try {
            val entidadeId = getEntidadeId()
            
            if (entidadeId.isNullOrEmpty()) {
                Log.w("ImportEmployeesViewModel", "⚠️ Entidade ID não configurada para carregar localizações")
                return emptyList()
            }
            
            Log.d("ImportEmployeesViewModel", "📡 Carregando localizações para entidade: $entidadeId")
            
            // Fazer a chamada assíncrona para a API
            val response = apiService.getLocalizacao(entidadeId)
            
            val localizacoes = response.data ?: emptyList()
            Log.d("ImportEmployeesViewModel", "📊 Localizações carregadas: ${localizacoes.size}")
            
            // Ordenar localizações alfabeticamente por descrição
            val localizacoesOrdenadas = localizacoes.sortedBy { it.descricao }
            Log.d("ImportEmployeesViewModel", "📊 Localizações ordenadas alfabeticamente")
            
            localizacoesOrdenadas
        } catch (e: Exception) {
            Log.e("ImportEmployeesViewModel", "❌ Erro ao carregar localizações", e)
            
            // Mostrar erro amigável
            val errorMessage = e.message ?: "Erro desconhecido ao carregar localizações"
            showError(errorMessage)
            
            emptyList()
        }
    }
    
    fun loadMoreFuncionarios() {
        Log.d("ImportEmployeesViewModel", "🔄 loadMoreFuncionarios chamado")
        Log.d("ImportEmployeesViewModel", "   - isLoadingMore: $isLoadingMore")
        Log.d("ImportEmployeesViewModel", "   - hasMorePages: $hasMorePages")
        Log.d("ImportEmployeesViewModel", "   - currentPage: $currentPage")
        Log.d("ImportEmployeesViewModel", "   - searchQuery: '${_uiState.value.searchQuery}'")
        
        if (isLoadingMore || !hasMorePages) {
            Log.d("ImportEmployeesViewModel", "❌ Condição não atendida para carregar mais")
            Log.d("ImportEmployeesViewModel", "   - isLoadingMore: $isLoadingMore")
            Log.d("ImportEmployeesViewModel", "   - hasMorePages: $hasMorePages")
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
                
                if (entidadeId.isNullOrEmpty()) {
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
                
                // Usar o código do órgão selecionado como filtro
                val orgaoCodigo = _uiState.value.selectedOrgao?.codigo
                val localizacaoId = _uiState.value.selectedLocalizacao?.id
                Log.d("ImportEmployeesViewModel", "🏢 Filtro de órgão: $orgaoCodigo")
                Log.d("ImportEmployeesViewModel", "📍 Filtro de localização: $localizacaoId")
                
                val response = apiService.getFuncionarios(entidadeId, currentPage, null, orgaoCodigo, localizacaoId?.toString())
                val funcionarios = response.data ?: emptyList()
                
                if (funcionarios.isNotEmpty()) {
                    // ✅ NOVO: Ordenar funcionários alfabeticamente por nome
                    val funcionariosOrdenados = funcionarios.sortedBy { it.nome }
                    
                    _uiState.update { 
                        it.copy(
                            funcionarios = funcionariosOrdenados,
                            isLoading = false,
                            hasMorePages = funcionarios.size >= 10 // Assumindo 10 por página
                        )
                    }
                    currentPage++
                    hasMorePages = funcionarios.size >= 10
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
                Log.e("ImportEmployeesViewModel", "❌ Erro ao carregar funcionários", e)
                _uiState.update { it.copy(isLoading = false) }
                
                // Mostrar erro amigável
                val errorMessage = e.message ?: "Erro desconhecido ao carregar funcionários"
                showError(errorMessage)
            } finally {
                isLoading = false
            }
        }
    }
    
    private fun loadMoreFuncionariosInternal() {
        if (isLoadingMore) return
        
        isLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        
        Log.d("ImportEmployeesViewModel", "🔄 loadMoreFuncionariosInternal - página $currentPage")
        
        viewModelScope.launch {
            try {
                val entidadeId = getEntidadeId()
                
                if (entidadeId.isNullOrEmpty()) {
                    Log.w("ImportEmployeesViewModel", "⚠️ Entidade ID vazia")
                    _uiState.update { it.copy(isLoadingMore = false) }
                    return@launch
                }
                
                Log.d("ImportEmployeesViewModel", "📡 Fazendo requisição para página $currentPage")
                
                // Usar o código do órgão selecionado como filtro
                val orgaoCodigo = _uiState.value.selectedOrgao?.codigo
                val localizacaoId = _uiState.value.selectedLocalizacao?.id
                Log.d("ImportEmployeesViewModel", "🏢 Filtro de órgão (loadMore): $orgaoCodigo")
                Log.d("ImportEmployeesViewModel", "📍 Filtro de localização (loadMore): $localizacaoId")
                
                val response = apiService.getFuncionarios(entidadeId, currentPage, null, orgaoCodigo, localizacaoId?.toString())
                val funcionarios = response.data ?: emptyList()
                
                Log.d("ImportEmployeesViewModel", "📊 Funcionários recebidos: ${funcionarios.size}")
                
                if (funcionarios.isNotEmpty()) {
                    val currentList = _uiState.value.funcionarios.toMutableList()
                    currentList.addAll(funcionarios)
                    
                    // ✅ NOVO: Ordenar a lista completa alfabeticamente por nome
                    val listaOrdenada = currentList.sortedBy { it.nome }
                    
                    // ✅ NOVO: Verificar se há mais páginas baseado no tamanho da resposta
                    // A API retorna 10 funcionários por página (per_page: 10)
                    val hasMore = funcionarios.size >= 10 // Corrigido de 20 para 10
                    
                    Log.d("ImportEmployeesViewModel", "✅ Adicionando ${funcionarios.size} funcionários")
                    Log.d("ImportEmployeesViewModel", "📊 Total agora: ${listaOrdenada.size}")
                    Log.d("ImportEmployeesViewModel", "🔄 Há mais páginas: $hasMore")
                    
                    _uiState.update { 
                        it.copy(
                            funcionarios = listaOrdenada,
                            isLoadingMore = false,
                            hasMorePages = hasMore
                        )
                    }
                    currentPage++
                    hasMorePages = hasMore
                    
                    Log.d("ImportEmployeesViewModel", "📄 Página incrementada para: $currentPage")
                } else {
                    Log.d("ImportEmployeesViewModel", "❌ Nenhum funcionário recebido - fim das páginas")
                    _uiState.update { 
                        it.copy(
                            isLoadingMore = false,
                            hasMorePages = false
                        )
                    }
                    hasMorePages = false
                }
                
            } catch (e: Exception) {
                Log.e("ImportEmployeesViewModel", "❌ Erro ao carregar mais funcionários", e)
                _uiState.update { it.copy(isLoadingMore = false) }
                
                // Mostrar erro amigável
                val errorMessage = e.message ?: "Erro desconhecido ao carregar mais funcionários"
                showError(errorMessage)
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
        
        Log.d("ImportEmployeesViewModel", "🔍 Filtrando funcionários: '$descricao'")
        
        if (descricao.isEmpty()) {
            // Resetar para lista completa
            Log.d("ImportEmployeesViewModel", "🔄 Resetando para lista completa")
            currentPage = 1
            hasMorePages = true
            isLoadingMore = false
            _uiState.update { 
                it.copy(
                    funcionarios = emptyList(),
                    hasMorePages = true,
                    isLoadingMore = false
                )
            }
            loadFuncionarios()
        } else {
            // Debounce: aguardar 500ms antes de fazer a busca
            searchJob = viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                
                try {
                    Log.d("ImportEmployeesViewModel", "🔍 Executando busca por: '$descricao'")
                    
                    val entidadeId = getEntidadeId()
                    
                    if (entidadeId.isNullOrEmpty()) {
                        Log.w("ImportEmployeesViewModel", "⚠️ Entidade ID vazia na busca")
                        _uiState.update { 
                            it.copy(funcionarios = emptyList())
                        }
                        return@launch
                    }
                    
                    // Usar o código do órgão selecionado como filtro
                    val orgaoCodigo = _uiState.value.selectedOrgao?.codigo
                    val localizacaoId = _uiState.value.selectedLocalizacao?.id
                    Log.d("ImportEmployeesViewModel", "🏢 Filtro de órgão (busca): $orgaoCodigo")
                    Log.d("ImportEmployeesViewModel", "📍 Filtro de localização (busca): $localizacaoId")
                    
                    val response = apiService.getFuncionarios(entidadeId, 1, descricao, orgaoCodigo, localizacaoId?.toString())
                    val funcionarios = response.data ?: emptyList()
                    
                    Log.d("ImportEmployeesViewModel", "📊 Resultados da busca: ${funcionarios.size}")
                    
                    // Filtrar resultados precisos
                    val funcionariosFiltrados = filtrarResultadosPrecisos(funcionarios, descricao)
                    
                    Log.d("ImportEmployeesViewModel", "✅ Funcionários filtrados: ${funcionariosFiltrados.size}")
                    
                    _uiState.update { 
                        it.copy(
                            funcionarios = funcionariosFiltrados,
                            hasMorePages = false // Busca não suporta paginação
                        )
                    }
                    
                } catch (e: Exception) {
                    Log.e("ImportEmployeesViewModel", "❌ Erro na busca", e)
                    
                    // Mostrar erro amigável
                    val errorMessage = e.message ?: "Erro desconhecido na busca"
                    showError(errorMessage)
                }
            }
        }
    }
    
    private fun filtrarResultadosPrecisos(
        funcionarios: List<FuncionariosModel>, 
        descricao: String
    ): List<FuncionariosModel> {
        if (descricao.isEmpty()) return funcionarios.sortedBy { it.nome } // ✅ NOVO: Ordenar mesmo quando não há filtro
        
        val descricaoUpper = descricao.uppercase()
        
        val funcionariosFiltrados = funcionarios.filter { funcionario ->
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
        
        // ✅ NOVO: Ordenar resultados filtrados alfabeticamente por nome
        return funcionariosFiltrados.sortedBy { it.nome }
    }
}

data class ImportEmployeesUiState(
    val funcionarios: List<FuncionariosModel> = emptyList(),
    val funcionariosParaImportar: List<FuncionariosModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isImportingBatch: Boolean = false,
    val hasMorePages: Boolean = true,
    val importedIds: Set<Long> = emptySet(),
    val showImportDialog: Boolean = false,
    val selectedFuncionario: FuncionariosModel? = null,
    val selectedTabIndex: Int = 0,
    val selectedOrgao: OrgaoModel? = null,
    val selectedLocalizacao: LocalizacaoModel? = null,
    val selectedEntidade: String? = null,
    val errorMessage: String? = null,
    val showErrorDialog: Boolean = false
) 