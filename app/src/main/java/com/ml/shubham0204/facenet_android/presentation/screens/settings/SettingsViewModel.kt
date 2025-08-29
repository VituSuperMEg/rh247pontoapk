package com.ml.shubham0204.facenet_android.presentation.screens.settings

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.ConfiguracoesEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@KoinViewModel
class SettingsViewModel : ViewModel(), KoinComponent {
    
    private val configuracoesDao = ConfiguracoesDao()
    private val context: Context by inject()
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        carregarConfiguracoes()
    }
    
    fun updateLocalizacaoId(value: String) {
        _uiState.update { it.copy(localizacaoId = value, localizacaoIdError = null) }
    }
    
    fun updateCodigoSincronizacao(value: String) {
        _uiState.update { it.copy(codigoSincronizacao = value, codigoSincronizacaoError = null) }
    }
    
    fun updateEntidadeId(value: String) {
        _uiState.update { it.copy(entidadeId = value, entidadeIdError = null) }
    }
    
    fun updateSincronizacaoAtiva(value: Boolean) {
        _uiState.update { it.copy(sincronizacaoAtiva = value) }
    }
    
    fun sincronizarAgora() {
        viewModelScope.launch {
            try {
                Toast.makeText(context, "🔄 Iniciando sincronização...", Toast.LENGTH_SHORT).show()
                
                // TODO: Implementar sincronização real
                val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val historico = HistoricoSincronizacao(
                    dataHora = dataHora,
                    mensagem = "Sincronização manual executada",
                    status = "Sucesso"
                )
                
                _uiState.update { 
                    it.copy(
                        historicoSincronizacao = it.historicoSincronizacao + historico
                    )
                }
                
                Toast.makeText(context, "✅ Sincronização executada com sucesso!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Erro na sincronização: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    fun salvarConfiguracoes() {
        Log.d("SettingsViewModel", "🔄 Iniciando salvamento de configurações")
        val currentState = _uiState.value
        
        Log.d("SettingsViewModel", "📊 Dados para salvar:")
        Log.d("SettingsViewModel", "   - Localização ID: '${currentState.localizacaoId}'")
        Log.d("SettingsViewModel", "   - Código Sincronização: '${currentState.codigoSincronizacao}'")
        Log.d("SettingsViewModel", "   - Entidade ID: '${currentState.entidadeId}'")
        Log.d("SettingsViewModel", "   - Sincronização Ativa: ${currentState.sincronizacaoAtiva}")
        
        // Validações
        if (currentState.localizacaoId.isEmpty()) {
            Log.e("SettingsViewModel", "❌ Validação falhou: ID da Localização vazio")
            _uiState.update { it.copy(localizacaoIdError = "ID da Localização é obrigatório") }
            Toast.makeText(context, "❌ ID da Localização é obrigatório", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentState.codigoSincronizacao.isEmpty()) {
            _uiState.update { it.copy(codigoSincronizacaoError = "Código de sincronização é obrigatório") }
            Toast.makeText(context, "❌ Código de sincronização é obrigatório", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentState.entidadeId.isEmpty()) {
            _uiState.update { it.copy(entidadeIdError = "Código da Entidade é obrigatório") }
            Toast.makeText(context, "❌ Código da Entidade é obrigatório", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentState.entidadeId.length != 9) {
            _uiState.update { it.copy(entidadeIdError = "Código da Entidade deve ter 9 dígitos") }
            Toast.makeText(context, "❌ Código da Entidade deve ter 9 dígitos", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("SettingsViewModel", "🔄 Criando entidade de configurações")
                
                val configuracoes = ConfiguracoesEntity(
                    id = 0, // Usar 0 para deixar o ObjectBox gerar o ID automaticamente
                    entidadeId = currentState.entidadeId,
                    localizacaoId = currentState.localizacaoId,
                    codigoSincronizacao = currentState.codigoSincronizacao,
                    horaSincronizacao = 8,
                    minutoSincronizacao = 0,
                    sincronizacaoAtiva = currentState.sincronizacaoAtiva,
                    intervaloSincronizacao = 24
                )
                
                Log.d("SettingsViewModel", "💾 Salvando no banco de dados...")
                val resultado = configuracoesDao.salvarConfiguracoes(configuracoes)
                Log.d("SettingsViewModel", "✅ Resultado do salvamento: $resultado")
                
                // Verificar se foi salvo corretamente
                val configSalva = configuracoesDao.getConfiguracoes()
                Log.d("SettingsViewModel", "🔍 Configuração salva: $configSalva")
                
                // Adicionar ao histórico
                val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val historico = HistoricoSincronizacao(
                    dataHora = dataHora,
                    mensagem = "Configurações salvas com sucesso",
                    status = "Sucesso"
                )
                
                _uiState.update { 
                    it.copy(
                        historicoSincronizacao = it.historicoSincronizacao + historico
                    )
                }
                
                Log.d("SettingsViewModel", "✅ Salvamento concluído com sucesso")
                Toast.makeText(context, "✅ Configurações salvas com sucesso!", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "❌ Erro ao salvar configurações", e)
                Toast.makeText(context, "❌ Erro ao salvar configurações: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    fun verificarAtualizacao() {
        viewModelScope.launch {
            try {
                // TODO: Implementar verificação de atualização real
                val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val historico = HistoricoSincronizacao(
                    dataHora = dataHora,
                    mensagem = "Verificação de atualização executada",
                    status = "Sucesso"
                )
                
                _uiState.update { 
                    it.copy(
                        historicoSincronizacao = it.historicoSincronizacao + historico
                    )
                }
            } catch (e: Exception) {
                // TODO: Tratar erro
            }
        }
    }
    
    fun atualizarSistema() {
        viewModelScope.launch {
            try {
                // TODO: Implementar atualização real
                val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val historico = HistoricoSincronizacao(
                    dataHora = dataHora,
                    mensagem = "Atualização do sistema executada",
                    status = "Sucesso"
                )
                
                _uiState.update { 
                    it.copy(
                        historicoSincronizacao = it.historicoSincronizacao + historico
                    )
                }
            } catch (e: Exception) {
                // TODO: Tratar erro
            }
        }
    }
    
    private fun carregarConfiguracoes() {
        viewModelScope.launch {
            try {
                val configuracoes = configuracoesDao.getConfiguracoes()
                
                if (configuracoes != null) {
                    _uiState.update {
                        it.copy(
                            localizacaoId = configuracoes.localizacaoId,
                            codigoSincronizacao = configuracoes.codigoSincronizacao,
                            entidadeId = configuracoes.entidadeId,
                            sincronizacaoAtiva = configuracoes.sincronizacaoAtiva
                        )
                    }
                }
            } catch (e: Exception) {
                // TODO: Tratar erro
            }
        }
    }
}

data class SettingsUiState(
    val localizacaoId: String = "",
    val codigoSincronizacao: String = "",
    val entidadeId: String = "",
    val sincronizacaoAtiva: Boolean = false,
    val localizacaoIdError: String? = null,
    val codigoSincronizacaoError: String? = null,
    val entidadeIdError: String? = null,
    val historicoSincronizacao: List<HistoricoSincronizacao> = emptyList()
)

data class HistoricoSincronizacao(
    val dataHora: String,
    val mensagem: String,
    val status: String
) 