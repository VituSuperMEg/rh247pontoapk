package com.ml.shubham0204.facenet_android.presentation.screens.settings

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.ConfiguracoesEntity
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.data.config.ServerConfig
import com.ml.shubham0204.facenet_android.data.model.TabletVersionData
import com.ml.shubham0204.facenet_android.data.repository.TabletUpdateRepository
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoService
import com.ml.shubham0204.facenet_android.worker.SincronizacaoAutomaticaWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.android.annotation.KoinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@KoinViewModel
class SettingsViewModel : ViewModel(), KoinComponent {
    
    private val configuracoesDao = ConfiguracoesDao()
    private val context: Context by inject()
    private val tabletUpdateRepository: TabletUpdateRepository by inject()
    private val appPreferences: AppPreferences by inject()
    
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
    
    fun updateServerUrl(value: String) {
        _uiState.update { it.copy(serverUrl = value, serverUrlError = null) }
    }
    
    fun updateHoraSincronizacao(value: Int) {
        _uiState.update { it.copy(horaSincronizacao = value) }
    }
    
    fun updateMinutoSincronizacao(value: Int) {
        _uiState.update { it.copy(minutoSincronizacao = value) }
    }
    
    fun updateIntervaloSincronizacao(value: Int) {
        _uiState.update { it.copy(intervaloSincronizacao = value) }
    }
    
    fun updateDownloadProgress(progress: Int) {
        _uiState.update { it.copy(downloadProgress = progress) }
    }
    
    fun sincronizarAgora() {
        viewModelScope.launch {
            try {
                Toast.makeText(context, "🔄 Iniciando sincronização...", Toast.LENGTH_SHORT).show()
                
                val pontoSincronizacaoService = PontoSincronizacaoService()
                val resultado = pontoSincronizacaoService.sincronizarPontosPendentes(context)
                
                val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val historico = HistoricoSincronizacao(
                    dataHora = dataHora,
                    mensagem = if (resultado.sucesso) 
                        "Sincronização manual: ${resultado.quantidadePontos} pontos sincronizados" 
                    else 
                        "Sincronização manual falhou: ${resultado.mensagem}",
                    status = if (resultado.sucesso) "Sucesso" else "Erro"
                )
                
                _uiState.update { 
                    it.copy(
                        historicoSincronizacao = it.historicoSincronizacao + historico
                    )
                }
                
                if (resultado.sucesso) {
                    Toast.makeText(
                        context, 
                        "✅ ${resultado.quantidadePontos} pontos sincronizados com sucesso!", 
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context, 
                        "❌ Erro na sincronização: ${resultado.mensagem}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val historico = HistoricoSincronizacao(
                    dataHora = dataHora,
                    mensagem = "Erro na sincronização: ${e.message}",
                    status = "Erro"
                )
                
                _uiState.update { 
                    it.copy(
                        historicoSincronizacao = it.historicoSincronizacao + historico
                    )
                }
                
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
                
                if (currentState.serverUrl.isEmpty()) {
                    _uiState.update { it.copy(serverUrlError = "URL do Servidor é obrigatória") }
                    Toast.makeText(context, "❌ URL do Servidor é obrigatória", Toast.LENGTH_SHORT).show()
                    return
                }
                
                if (!currentState.serverUrl.startsWith("http://") && !currentState.serverUrl.startsWith("https://")) {
                    _uiState.update { it.copy(serverUrlError = "URL deve começar com http:// ou https://") }
                    Toast.makeText(context, "❌ URL deve começar com http:// ou https://", Toast.LENGTH_SHORT).show()
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
                    horaSincronizacao = currentState.horaSincronizacao,
                    minutoSincronizacao = currentState.minutoSincronizacao,
                    sincronizacaoAtiva = currentState.sincronizacaoAtiva,
                    intervaloSincronizacao = currentState.intervaloSincronizacao
                )
                
                // Salvar URL do servidor nas preferências
                appPreferences.serverUrl = currentState.serverUrl
                
                Log.d("SettingsViewModel", "💾 Salvando no banco de dados...")
                val resultado = configuracoesDao.salvarConfiguracoes(configuracoes)
                Log.d("SettingsViewModel", "✅ Resultado do salvamento: $resultado")
                
                // Verificar se foi salvo corretamente
                val configSalva = configuracoesDao.getConfiguracoes()
                Log.d("SettingsViewModel", "🔍 Configuração salva: $configSalva")
                
                // Agendar sincronização automática se estiver ativada
                if (currentState.sincronizacaoAtiva) {
                    agendarSincronizacaoAutomatica(
                        hora = currentState.horaSincronizacao,
                        minuto = currentState.minutoSincronizacao,
                        intervalo = currentState.intervaloSincronizacao
                    )
                } else {
                    cancelarSincronizacaoAutomatica()
                }
                
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
        Log.d("SettingsViewModel", "🚀 Iniciando verificação de atualizações...")
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCheckingUpdate = true, updateMessage = "🔍 Verificando atualizações..." as String?) }
                Log.d("SettingsViewModel", "🔄 Estado atualizado para 'verificando'")
                
                val result = tabletUpdateRepository.checkForUpdates()
                Log.d("SettingsViewModel", "📡 Resultado da verificação recebido: $result")
                
                result.fold(
                    onSuccess = { versionData ->
                        val currentVersion = tabletUpdateRepository.getCurrentAppVersion()
                        Log.d("SettingsViewModel", "🔍 Comparando versões:")
                        Log.d("SettingsViewModel", "   - Versão atual do app: '$currentVersion'")
                        Log.d("SettingsViewModel", "   - Versão disponível no servidor: '${versionData.version}'")
                        
                        val hasUpdate = tabletUpdateRepository.isUpdateAvailable(currentVersion, versionData.version)
                        Log.d("SettingsViewModel", "   - Há atualização disponível? $hasUpdate")
                        
                        if (hasUpdate) {
                            _uiState.update { 
                                it.copy(
                                    isCheckingUpdate = false,
                                    updateMessage = "✅ Nova versão ${versionData.version} disponível!",
                                    availableUpdate = versionData,
                                    hasUpdate = true
                                )
                            }
                            
                            val historico = HistoricoSincronizacao(
                                dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                                mensagem = "Nova versão ${versionData.version} encontrada",
                                status = "Sucesso"
                            )
                            
                            _uiState.update { 
                                it.copy(
                                    historicoSincronizacao = it.historicoSincronizacao + historico
                                )
                            }
                        } else {
                            _uiState.update { 
                                it.copy(
                                    isCheckingUpdate = false,
                                    updateMessage = "ℹ️ Você já está com a versão mais recente (${currentVersion})",
                                    hasUpdate = false
                                )
                            }
                        }
                    },
                    onFailure = { exception ->
                        val errorMessage = when (exception) {
                            is java.net.UnknownServiceException -> {
                                "🔒 Erro de segurança de rede. Verifique a configuração de segurança."
                            }
                            is java.net.ConnectException -> {
                                "🔌 Erro de conexão com o servidor."
                            }
                            is java.net.SocketTimeoutException -> {
                                "⏰ Timeout na conexão com o servidor."
                            }
                            is IllegalArgumentException -> {
                                "🔗 URL inválida para verificação."
                            }
                            else -> {
                                "❌ Erro ao verificar atualizações: ${exception.message}"
                            }
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isCheckingUpdate = false,
                                updateMessage = errorMessage,
                                hasUpdate = false
                            )
                        }
                        
                        val historico = HistoricoSincronizacao(
                            dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                            mensagem = "Erro na verificação: ${exception.message}",
                            status = "Erro"
                        )
                        
                        _uiState.update { 
                            it.copy(
                                historicoSincronizacao = it.historicoSincronizacao + historico
                            )
                        }
                        
                        // Log detalhado do erro
                        Log.e("SettingsViewModel", "❌ Erro detalhado na verificação", exception)
                    }
                )
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isCheckingUpdate = false,
                        updateMessage = "❌ Erro inesperado: ${e.message}",
                        hasUpdate = false
                    )
                }
            }
        }
    }
    
    fun downloadDiretoAtualizacaoComVersao(versao: String) {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isUpdating = true, 
                        updateMessage = "📥 Baixando atualização v$versao...",
                        downloadProgress = 0
                    ) 
                }
                
                // Rota fixa: 230440023/services/util/download-tablet-apk
                // Parâmetro dinâmico: versao=$versao.apk
                val downloadUrl = "https://api.rh247.com.br/${ServerConfig.DOWNLOAD_ENDPOINT}?versao=$versao.apk"
                val filename = "tablet_update_v$versao.apk"
                
                
                val downloadResult = tabletUpdateRepository.downloadDirectUpdate(
                    downloadUrl, 
                    filename,
                    onProgress = { progress ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                )
                
                downloadResult.fold(
                    onSuccess = { apkFile ->
                        _uiState.update { 
                            it.copy(
                                updateMessage = "🔧 Instalando atualização...",
                                downloadProgress = 100
                            ) 
                        }
                        
                        try {
                            tabletUpdateRepository.installUpdate(apkFile)
                            
                            _uiState.update { 
                                it.copy(
                                    isUpdating = false,
                                    updateMessage = "✅ Atualização v$versao baixada e pronta para instalar!",
                                    hasUpdate = false,
                                    availableUpdate = null,
                                    downloadProgress = 0
                                )
                            }
                            
                            val historico = HistoricoSincronizacao(
                                dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                                mensagem = "Atualização direta v$versao baixada com sucesso",
                                status = "Sucesso"
                            )
                            
                            _uiState.update { 
                                it.copy(
                                    historicoSincronizacao = it.historicoSincronizacao + historico
                                )
                            }
                            
                            Toast.makeText(context, "✅ Atualização v$versao baixada! Instale o APK quando solicitado.", Toast.LENGTH_LONG).show()
                            
                        } catch (e: Exception) {
                            _uiState.update { 
                                it.copy(
                                    isUpdating = false,
                                    updateMessage = "❌ Erro ao instalar: ${e.message}",
                                    hasUpdate = true,
                                    downloadProgress = 0
                                )
                            }
                            
                            Toast.makeText(context, "❌ Erro ao instalar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        
                    },
                    onFailure = { exception ->
                        val errorMessage = when (exception) {
                            is java.net.UnknownServiceException -> {
                                "🔒 Erro de segurança de rede. Verifique a configuração de segurança."
                            }
                            is java.net.ConnectException -> {
                                "🔌 Erro de conexão com o servidor."
                            }
                            is java.net.SocketTimeoutException -> {
                                "⏰ Timeout na conexão com o servidor."
                            }
                            is IllegalArgumentException -> {
                                "🔗 URL inválida para download."
                            }
                            else -> {
                                "❌ Erro ao baixar atualização: ${exception.message}"
                            }
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isUpdating = false,
                                updateMessage = errorMessage,
                                hasUpdate = true,
                                downloadProgress = 0
                            )
                        }
                        
                        val historico = HistoricoSincronizacao(
                            dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                            mensagem = "Erro no download direto v$versao: ${exception.message}",
                            status = "Erro"
                        )
                        
                        _uiState.update { 
                            it.copy(
                                historicoSincronizacao = it.historicoSincronizacao + historico
                            )
                        }
                        
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        
                        // Log detalhado do erro
                        Log.e("SettingsViewModel", "❌ Erro detalhado no download direto v$versao", exception)
                    }
                )
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isUpdating = false,
                        updateMessage = "❌ Erro inesperado: ${e.message}",
                        hasUpdate = true,
                        downloadProgress = 0
                    )
                }
            }
        }
    }

    fun downloadDiretoAtualizacao() {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isUpdating = true, 
                        updateMessage = "📥 Baixando atualização direta...",
                        downloadProgress = 0
                    ) 
                }
                
                // Construir URL usando o endpoint correto
                val downloadUrl = "https://api.rh247.com.br/${ServerConfig.DOWNLOAD_ENDPOINT}?versao=1.2.apk"
                val filename = "tablet_update_v1.2.apk"
                
                Log.d("SettingsViewModel", "🔗 URL de download: $downloadUrl")
                
                val downloadResult = tabletUpdateRepository.downloadDirectUpdate(
                    downloadUrl, 
                    filename,
                    onProgress = { progress ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                )
                
                downloadResult.fold(
                    onSuccess = { apkFile ->
                        _uiState.update { 
                            it.copy(
                                updateMessage = "🔧 Instalando atualização...",
                                downloadProgress = 100
                            ) 
                        }
                        
                        try {
                            tabletUpdateRepository.installUpdate(apkFile)
                            
                            _uiState.update { 
                                it.copy(
                                    isUpdating = false,
                                    updateMessage = "✅ Atualização baixada e pronta para instalar!",
                                    hasUpdate = false,
                                    availableUpdate = null,
                                    downloadProgress = 0
                                )
                            }
                            
                            val historico = HistoricoSincronizacao(
                                dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                                mensagem = "Atualização direta v1.2 baixada com sucesso",
                                status = "Sucesso"
                            )
                            
                            _uiState.update { 
                                it.copy(
                                    historicoSincronizacao = it.historicoSincronizacao + historico
                                )
                            }
                            
                            Toast.makeText(context, "✅ Atualização baixada! Instale o APK quando solicitado.", Toast.LENGTH_LONG).show()
                            
                        } catch (e: Exception) {
                            _uiState.update { 
                                it.copy(
                                    isUpdating = false,
                                    updateMessage = "❌ Erro ao instalar: ${e.message}",
                                    hasUpdate = true,
                                    downloadProgress = 0
                                )
                            }
                            
                            Toast.makeText(context, "❌ Erro ao instalar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        
                    },
                    onFailure = { exception ->
                        val errorMessage = when (exception) {
                            is java.net.UnknownServiceException -> {
                                "🔒 Erro de segurança de rede. Verifique a configuração de segurança."
                            }
                            is java.net.ConnectException -> {
                                "🔌 Erro de conexão com o servidor."
                            }
                            is java.net.SocketTimeoutException -> {
                                "⏰ Timeout na conexão com o servidor."
                            }
                            is IllegalArgumentException -> {
                                "🔗 URL inválida para download."
                            }
                            else -> {
                                "❌ Erro ao baixar atualização: ${exception.message}"
                            }
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isUpdating = false,
                                updateMessage = errorMessage,
                                hasUpdate = true,
                                downloadProgress = 0
                            )
                        }
                        
                        val historico = HistoricoSincronizacao(
                            dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                            mensagem = "Erro no download direto: ${exception.message}",
                            status = "Erro"
                        )
                        
                        _uiState.update { 
                            it.copy(
                                historicoSincronizacao = it.historicoSincronizacao + historico
                            )
                        }
                        
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        
                        // Log detalhado do erro
                        Log.e("SettingsViewModel", "❌ Erro detalhado no download direto", exception)
                    }
                )
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isUpdating = false,
                        updateMessage = "❌ Erro inesperado: ${e.message}",
                        hasUpdate = true,
                        downloadProgress = 0
                    )
                }
            }
        }
    }

    fun atualizarSistema() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val updateData = currentState.availableUpdate
                
                if (updateData == null) {
                    Toast.makeText(context, "❌ Nenhuma atualização disponível para instalar", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                _uiState.update { 
                    it.copy(
                        isUpdating = true, 
                        updateMessage = "📥 Baixando atualização...",
                        downloadProgress = 0
                    ) 
                }
                
                val downloadResult = tabletUpdateRepository.downloadUpdate(
                    updateData,
                    onProgress = { progress ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                )
                
                downloadResult.fold(
                    onSuccess = { apkFile ->
                        _uiState.update { 
                            it.copy(
                                updateMessage = "🔧 Instalando atualização...",
                                downloadProgress = 100
                            ) 
                        }
                        
                        try {
                            tabletUpdateRepository.installUpdate(apkFile)
                            
                            _uiState.update { 
                                it.copy(
                                    isUpdating = false,
                                    updateMessage = "✅ Atualização baixada e pronta para instalar!",
                                    hasUpdate = false,
                                    availableUpdate = null,
                                    downloadProgress = 0
                                )
                            }
                            
                            val historico = HistoricoSincronizacao(
                                dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                                mensagem = "Atualização ${updateData.version} baixada com sucesso",
                                status = "Sucesso"
                            )
                            
                            _uiState.update { 
                                it.copy(
                                    historicoSincronizacao = it.historicoSincronizacao + historico
                                )
                            }
                            
                            Toast.makeText(context, "✅ Atualização baixada! Instale o APK quando solicitado.", Toast.LENGTH_LONG).show()
                            
                        } catch (e: Exception) {
                            _uiState.update { 
                                it.copy(
                                    isUpdating = false,
                                    updateMessage = "❌ Erro ao instalar: ${e.message}",
                                    hasUpdate = true,
                                    downloadProgress = 0
                                )
                            }
                            
                            val historico = HistoricoSincronizacao(
                                dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                                mensagem = "Erro na instalação: ${e.message}",
                                status = "Erro"
                            )
                            
                            _uiState.update { 
                                it.copy(
                                    historicoSincronizacao = it.historicoSincronizacao + historico
                                )
                            }
                        }
                    },
                    onFailure = { exception ->
                        val errorMessage = when (exception) {
                            is java.net.UnknownServiceException -> {
                                "🔒 Erro de segurança de rede. Verifique a configuração de segurança."
                            }
                            is java.net.ConnectException -> {
                                "🔌 Erro de conexão com o servidor."
                            }
                            is java.net.SocketTimeoutException -> {
                                "⏰ Timeout na conexão com o servidor."
                            }
                            is IllegalArgumentException -> {
                                "🔗 URL inválida para download."
                            }
                            else -> {
                                "❌ Erro no download: ${exception.message}"
                            }
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isUpdating = false,
                                updateMessage = errorMessage,
                                hasUpdate = true,
                                downloadProgress = 0
                            )
                        }
                        
                        val historico = HistoricoSincronizacao(
                            dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                            mensagem = "Erro no download: ${exception.message}",
                            status = "Erro"
                        )
                        
                        _uiState.update { 
                            it.copy(
                                historicoSincronizacao = it.historicoSincronizacao + historico
                            )
                        }
                        
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        
                        // Log detalhado do erro
                        Log.e("SettingsViewModel", "❌ Erro detalhado no download", exception)
                    }
                )
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isUpdating = false,
                        updateMessage = "❌ Erro inesperado: ${e.message}",
                        hasUpdate = true,
                        downloadProgress = 0
                    )
                }
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
                            sincronizacaoAtiva = configuracoes.sincronizacaoAtiva,
                            horaSincronizacao = configuracoes.horaSincronizacao,
                            minutoSincronizacao = configuracoes.minutoSincronizacao,
                            intervaloSincronizacao = configuracoes.intervaloSincronizacao
                        )
                    }
                    
                    // Se a sincronização automática estiver ativada, agendar
                    if (configuracoes.sincronizacaoAtiva) {
                        agendarSincronizacaoAutomatica(
                            hora = configuracoes.horaSincronizacao,
                            minuto = configuracoes.minutoSincronizacao,
                            intervalo = configuracoes.intervaloSincronizacao
                        )
                    }
                }
                
                // Carregar URL do servidor das preferências
                _uiState.update { it.copy(serverUrl = appPreferences.serverUrl) }
            } catch (e: Exception) {
                // TODO: Tratar erro
            }
        }
    }
    
    private fun agendarSincronizacaoAutomatica(hora: Int, minuto: Int, intervalo: Int) {
        try {
            Log.d("SettingsViewModel", "🕐 Agendando sincronização automática: $hora:$minuto a cada $intervalo horas")
            
            val workManager = WorkManager.getInstance(context)
            
            // Cancelar trabalhos existentes
            workManager.cancelAllWorkByTag("sincronizacao_automatica")
            
            // Calcular delay inicial até o próximo horário
            val agora = LocalTime.now()
            val horarioAlvo = LocalTime.of(hora, minuto)
            
            var delayInicial = Duration.between(agora, horarioAlvo)
            if (delayInicial.isNegative) {
                // Se o horário já passou hoje, agendar para amanhã
                delayInicial = delayInicial.plusDays(1)
            }
            
            Log.d("SettingsViewModel", "⏰ Delay inicial: ${delayInicial.toMinutes()} minutos")
            
            // Criar dados para o worker
            val inputData = Data.Builder()
                .putInt("hora", hora)
                .putInt("minuto", minuto)
                .putInt("intervalo", intervalo)
                .build()
            
            // Criar trabalho periódico
            val sincronizacaoWork = PeriodicWorkRequestBuilder<SincronizacaoAutomaticaWorker>(
                intervalo.toLong(), TimeUnit.HOURS
            )
                .setInputData(inputData)
                .addTag("sincronizacao_automatica")
                .setInitialDelay(delayInicial.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            
            // Agendar o trabalho
            workManager.enqueue(sincronizacaoWork)
            
            Log.d("SettingsViewModel", "✅ Sincronização automática agendada com sucesso")
            Toast.makeText(
                context, 
                "✅ Sincronização automática agendada para $hora:${minuto.toString().padStart(2, '0')} a cada $intervalo horas", 
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "❌ Erro ao agendar sincronização automática: ${e.message}")
            Toast.makeText(
                context, 
                "❌ Erro ao agendar sincronização automática: ${e.message}", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun cancelarSincronizacaoAutomatica() {
        try {
            Log.d("SettingsViewModel", "❌ Cancelando sincronização automática")
            
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWorkByTag("sincronizacao_automatica")
            
            Log.d("SettingsViewModel", "✅ Sincronização automática cancelada")
            Toast.makeText(context, "✅ Sincronização automática cancelada", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "❌ Erro ao cancelar sincronização automática: ${e.message}")
        }
    }
    
    fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "0.1.0 (10)" // Versão padrão em caso de erro
        }
    }
}

data class SettingsUiState(
    val localizacaoId: String = "",
    val codigoSincronizacao: String = "",
    val entidadeId: String = "",
    val serverUrl: String = "",
    val sincronizacaoAtiva: Boolean = false,
    val horaSincronizacao: Int = 8,
    val minutoSincronizacao: Int = 0,
    val intervaloSincronizacao: Int = 24,
    val localizacaoIdError: String? = null,
    val codigoSincronizacaoError: String? = null,
    val entidadeIdError: String? = null,
    val serverUrlError: String? = null,
    val historicoSincronizacao: List<HistoricoSincronizacao> = emptyList(),
    val isCheckingUpdate: Boolean = false,
    val isUpdating: Boolean = false,
    val updateMessage: String? = null,
    val hasUpdate: Boolean = false,
    val availableUpdate: TabletVersionData? = null,
    val downloadProgress: Int = 0
)

data class HistoricoSincronizacao(
    val dataHora: String,
    val mensagem: String,
    val status: String
) 