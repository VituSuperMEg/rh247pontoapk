package com.ml.shubham0204.facenet_android.presentation.screens.detect_screen

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.data.PontosGenericosDao
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.data.RecognitionMetrics
import com.ml.shubham0204.facenet_android.presentation.components.FaceDetectionOverlay
import com.ml.shubham0204.facenet_android.domain.ImageVectorUseCase
import com.ml.shubham0204.facenet_android.domain.PersonUseCase
import com.ml.shubham0204.facenet_android.utils.BitmapUtils
import com.ml.shubham0204.facenet_android.utils.LocationUtils
import com.ml.shubham0204.facenet_android.utils.LocationResult
import com.ml.shubham0204.facenet_android.utils.ConnectivityUtils
import com.ml.shubham0204.facenet_android.utils.SoundUtils
import com.ml.shubham0204.facenet_android.utils.PerformanceConfig
import com.ml.shubham0204.facenet_android.utils.CrashReporter
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoService
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoPorBlocosService
import com.ml.shubham0204.facenet_android.data.api.ApiService
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@KoinViewModel
class DetectScreenViewModel(
    val personUseCase: PersonUseCase,
    val imageVectorUseCase: ImageVectorUseCase,
    private val pontosGenericosDao: PontosGenericosDao,
    private val funcionariosDao: FuncionariosDao,
    private val pontoSincronizacaoService: PontoSincronizacaoService,
    private val pontoSincronizacaoPorBlocosService: PontoSincronizacaoPorBlocosService // ✅ NOVO: Serviço por blocos
) : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val apiService: ApiService = RetrofitClient.instance
    private val locationUtils = LocationUtils(context)
    val faceDetectionMetricsState = mutableStateOf<RecognitionMetrics?>(null)
    val isProcessingRecognition = mutableStateOf(false)
    val currentFaceBitmap = mutableStateOf<Bitmap?>(null)
    val recognizedPerson = mutableStateOf<FuncionariosEntity?>(null)
    val showSuccessScreen = mutableStateOf(false)
    val savedPonto = mutableStateOf<PontosGenericosEntity?>(null)
    val lastRecognizedPersonName = mutableStateOf<String?>(null)
    
    // ✅ NOVO: Estados para seleção de matrícula
    val showMatriculaSelectionModal = mutableStateOf(false)
    val availableMatriculas = mutableStateOf<List<String>>(emptyList())
    val selectedMatricula = mutableStateOf<String?>(null)
    val pendingFuncionario = mutableStateOf<FuncionariosEntity?>(null)
    
    // ✅ NOVO: Job para controlar o reconhecimento
    private var recognitionJob: kotlinx.coroutines.Job? = null
    
    // ✅ NOVO: Controle de throttling para evitar ANR
    private var lastRecognitionTime: Long = 0
    
    // ✅ NOVO: Controle de duplicação de registros
    private var lastRegisteredPerson: String? = null
    private var lastRegistrationTime: Long = 0
    
    // ✅ NOVO: Controle de foto única para cada ponto
    private var lastPhotoTimestamp: Long = 0
    private var lastPhotoHash: String? = null

    fun getNumPeople(): Long = personUseCase.getCount()
    
    // ✅ NOVO: Funções para seleção de matrícula
    fun selectMatricula(matricula: String) {
        selectedMatricula.value = matricula
        showMatriculaSelectionModal.value = false
        
        // Processar o ponto com a matrícula selecionada
        pendingFuncionario.value?.let { funcionario ->
            viewModelScope.launch {
                processPontoWithSelectedMatricula(funcionario, matricula)
            }
        }
    }
    
    fun cancelMatriculaSelection() {
        showMatriculaSelectionModal.value = false
        availableMatriculas.value = emptyList()
        selectedMatricula.value = null
        pendingFuncionario.value = null
    }
    
    private suspend fun processPontoWithSelectedMatricula(funcionario: FuncionariosEntity, matricula: String) {
        Log.d("DetectScreenViewModel", "🔄 Processando ponto com matrícula selecionada: $matricula")
        // ✅ NOVO: Processar o ponto com a matrícula selecionada
        val ponto = registerPontoWithMatricula(funcionario, matricula)
        if (ponto != null) {
            Log.d("DetectScreenViewModel", "✅ Ponto criado com sucesso, iniciando sincronização...")
        } else {
            Log.e("DetectScreenViewModel", "❌ Falha ao criar ponto com matrícula selecionada")
        }
    }
    
    private suspend fun registerPontoWithMatricula(funcionario: FuncionariosEntity, matriculaSelecionada: String): PontosGenericosEntity? {
        return try {
            Log.d("DetectScreenViewModel", "💾 Registrando ponto para: ${funcionario.nome} com matrícula: $matriculaSelecionada")
            
            val horarioAtual = System.currentTimeMillis()
            
            val locationResult = try {
                val geolocEnabled = try { com.ml.shubham0204.facenet_android.data.ConfiguracoesDao().getConfiguracoes()?.geolocalizacaoHabilitada ?: true } catch (_: Exception) { true }
                if (geolocEnabled) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        locationUtils.getCurrentLocation(PerformanceConfig.LOCATION_TIMEOUT_MS)
                    }
                } else null
            } catch (e: Exception) {
                Log.w("DetectScreenViewModel", "⚠️ Erro ao obter localização: ${e.message}")
                null
            }
            
            val latitude: Double?
            val longitude: Double?
            
            val entidadeId = if (!funcionario.entidadeId.isNullOrEmpty()) {
                funcionario.entidadeId
            } else {
                val configuracoesDao = ConfiguracoesDao()
                val configuracoes = configuracoesDao.getConfiguracoes()
                configuracoes?.entidadeId ?: "ENTIDADE_PADRAO"
            }
            
            // Preferir coordenadas fixas das configurações
            val configuracoes = try { com.ml.shubham0204.facenet_android.data.ConfiguracoesDao().getConfiguracoes() } catch (_: Exception) { null }
            if (configuracoes?.latitudeFixa != null && configuracoes.longitudeFixa != null) {
                latitude = configuracoes.latitudeFixa
                longitude = configuracoes.longitudeFixa
            } else if (locationResult != null) {
                latitude = locationResult.latitude
                longitude = locationResult.longitude
            } else {
                latitude = null
                longitude = null
            }
            
            val fotoBase64 = currentFaceBitmap.value?.let { bitmap ->
                if (BitmapUtils.isValidBitmap(bitmap)) {
                    val base64 = BitmapUtils.bitmapToBase64(bitmap, 80)
                    Log.d("DetectScreenViewModel", "📸 Foto capturada e convertida para base64 (${base64.length} chars)")
                    base64
                } else {
                    Log.w("DetectScreenViewModel", "⚠️ Bitmap inválido para conversão")
                    null
                }
            } ?: run {
                Log.w("DetectScreenViewModel", "⚠️ Nenhuma foto disponível para captura")
                null
            }
            
            Log.d("DetectScreenViewModel", "🏢 Criando ponto para entidade: $entidadeId")
            
            val ponto = PontosGenericosEntity(
                funcionarioId = funcionario.id.toString(),
                funcionarioNome = funcionario.nome,
                funcionarioMatricula = funcionario.matricula,
                funcionarioCpf = funcionario.cpf,
                funcionarioCargo = funcionario.cargo,
                funcionarioSecretaria = funcionario.secretaria,
                funcionarioLotacao = funcionario.lotacao,
                dataHora = horarioAtual,
                latitude = latitude,
                longitude = longitude,
                fotoBase64 = fotoBase64,
                synced = false,
                entidadeId = entidadeId,
                matriculaReal = matriculaSelecionada // ✅ NOVO: Matrícula selecionada no modal
            )
            
            pontosGenericosDao.insert(ponto)
            savedPonto.value = ponto
            showSuccessScreen.value = true
            
            // ✅ NOVO: Sincronização automática como na função original
            Log.d("DetectScreenViewModel", "🔄 Iniciando sincronização automática para ponto com matrícula: $matriculaSelecionada")
            attemptAutoSync()
            
            Log.d("DetectScreenViewModel", "✅ Ponto registrado com sucesso para ${funcionario.nome} com matrícula: $matriculaSelecionada")
            ponto
            
        } catch (e: Exception) {
            Log.e("DetectScreenViewModel", "❌ Erro ao registrar ponto: ${e.message}", e)
            CrashReporter.logException(context, e, "registerPontoWithMatricula")
            null
        }
    }
    
    // ✅ NOVO: Função para verificar e limpar o banco se necessário
    fun checkAndClearDatabase() {
        val totalPessoas = personUseCase.getCount()
        Log.d("DetectScreenViewModel", "🔍 Verificando banco de dados...")
        Log.d("DetectScreenViewModel", "📊 Total de pessoas no banco: $totalPessoas")
        
        if (totalPessoas > 0) {
            Log.d("DetectScreenViewModel", "✅ Banco de dados OK - $totalPessoas pessoa(s) cadastrada(s)")
            
            // ✅ NOVO: Listar todas as pessoas cadastradas
            try {
                val pessoas = personUseCase.getAll()
                Log.d("DetectScreenViewModel", "📋 Pessoas cadastradas:")
                // Como é um Flow, vamos apenas logar que existe
                Log.d("DetectScreenViewModel", "📋 Flow de pessoas disponível")
            } catch (e: Exception) {
                Log.e("DetectScreenViewModel", "❌ Erro ao listar pessoas: ${e.message}")
            }
        } else {
            Log.w("DetectScreenViewModel", "⚠️ Banco de dados vazio - nenhuma pessoa cadastrada")
        }
    }
    
    fun setCurrentFaceBitmap(bitmap: Bitmap?) {
        // ✅ NOVO: Validar se a foto é nova e única
        if (bitmap != null) {
            val currentTime = System.currentTimeMillis()
            val photoHash = generatePhotoHash(bitmap)
            
            // ✅ NOVO: Verificar se é a mesma foto usada recentemente
            if (photoHash == lastPhotoHash && (currentTime - lastPhotoTimestamp) < 5000) { // 5 segundos
                Log.d("DetectScreenViewModel", "⚠️ Foto duplicada detectada - ignorando captura")
                return
            }
            
            // ✅ NOVO: Atualizar controles de foto única
            lastPhotoTimestamp = currentTime
            lastPhotoHash = photoHash
            
            Log.d("DetectScreenViewModel", "📸 Nova foto capturada - timestamp: $currentTime, hash: ${photoHash.take(8)}...")
        }
        
        currentFaceBitmap.value = bitmap
    }
    
    // ✅ NOVO: Função para gerar hash único da foto
    private fun generatePhotoHash(bitmap: Bitmap): String {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val sampleSize = 8 // Amostrar apenas alguns pixels para performance
            
            val hash = StringBuilder()
            for (y in 0 until height step sampleSize) {
                for (x in 0 until width step sampleSize) {
                    val pixel = bitmap.getPixel(x, y)
                    hash.append(pixel.toString(16))
                }
            }
            hash.toString().hashCode().toString()
        } catch (e: Exception) {
            Log.e("DetectScreenViewModel", "❌ Erro ao gerar hash da foto: ${e.message}")
            System.currentTimeMillis().toString() // Fallback para timestamp
        }
    }
    
    fun setLastRecognizedPersonName(name: String?) {
        lastRecognizedPersonName.value = name
    }
    
    fun processFaceRecognition() {
        // ✅ NOVO: Throttling para evitar ANR
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRecognitionTime < PerformanceConfig.MIN_RECOGNITION_INTERVAL_MS) {
            Log.d("DetectScreenViewModel", "⚠️ Throttling ativo - aguardando ${PerformanceConfig.MIN_RECOGNITION_INTERVAL_MS - (currentTime - lastRecognitionTime)}ms")
            return
        }
        lastRecognitionTime = currentTime
        
        // ✅ NOVO: Verificar se já registrou ponto para a mesma pessoa recentemente
        val recognizedPersonName = lastRecognizedPersonName.value
        if (recognizedPersonName != null && 
            recognizedPersonName == lastRegisteredPerson && 
            currentTime - lastRegistrationTime < 10000) { // 10 segundos
            Log.d("DetectScreenViewModel", "⚠️ Ponto já registrado para $recognizedPersonName recentemente (há ${currentTime - lastRegistrationTime}ms)")
            return
        }
        
        // ✅ OTIMIZADO: Cancelar job anterior se existir
        try {
            recognitionJob?.cancel()
        } catch (e: Exception) {
            Log.w("DetectScreenViewModel", "⚠️ Erro ao cancelar job anterior: ${e.message}")
        }
        
        if (isProcessingRecognition.value) {
            Log.d("DetectScreenViewModel", "⚠️ Reconhecimento já em andamento, ignorando...")
            return
        }
        
        // ✅ NOVO: Verificar quantas pessoas estão cadastradas
        val totalPessoas = personUseCase.getCount()
        Log.d("DetectScreenViewModel", "📊 Total de pessoas cadastradas no FaceNet: $totalPessoas")
        
        // ✅ NOVO: Se não há pessoas cadastradas, não tentar reconhecer
        if (totalPessoas == 0L) {
            Log.w("DetectScreenViewModel", "⚠️ NENHUMA PESSOA CADASTRADA NO BANCO! Cadastre faces primeiro.")
            return
        }
        
        recognitionJob = viewModelScope.launch {
            try {
                isProcessingRecognition.value = true
                Log.d("DetectScreenViewModel", "🔄 Iniciando reconhecimento facial...")
                
                // Log de início do processo
                CrashReporter.logEvent(context, "Iniciando reconhecimento facial", "INFO")
                
                // ✅ OTIMIZADO: Aguardar até que uma pessoa seja reconhecida
                var attempts = 0
                val maxAttempts = PerformanceConfig.MAX_RECOGNITION_ATTEMPTS
                
                while (attempts < maxAttempts && !showSuccessScreen.value && isActive) {
                    delay(PerformanceConfig.RECOGNITION_DELAY_MS)
                    attempts++
                    
                    val recognizedPersonName = lastRecognizedPersonName.value
                    Log.d("DetectScreenViewModel", "🔍 Tentativa $attempts - Pessoa reconhecida: $recognizedPersonName")
                    
                    if (recognizedPersonName != null && recognizedPersonName != "Not recognized" && recognizedPersonName != "Não Encontrado") {
                        // ✅ NOVO: Verificar se foi detectado spoofing
                        if (recognizedPersonName == "SPOOF_DETECTED") {
                            Log.w("DetectScreenViewModel", "🚫 SPOOF DETECTADO! Bloqueando registro de ponto")
                            mostrarMensagemSpoofDetectado()
                            return@launch // Sair sem processar
                        }
                        
                        Log.d("DetectScreenViewModel", "✅ Pessoa reconhecida! Processando...")
                        Log.d("DetectScreenViewModel", "🔍 Nome reconhecido: '$recognizedPersonName'")
                        
                        // ✅ OTIMIZADO: Aguardar menos tempo para processamento mais rápido
                        delay(PerformanceConfig.RECOGNITION_DELAY_MS)
                        
                        // Buscar funcionários reconhecidos
                        Log.d("DetectScreenViewModel", "🔍 Chamando findRecognizedEmployee()...")
                        val funcionario = findRecognizedEmployee()
                        Log.d("DetectScreenViewModel", "🔍 Resultado findRecognizedEmployee: ${funcionario?.nome ?: "null"}")

                        if (funcionario != null) {
                            Log.d("DetectScreenViewModel", "✅ Funcionário reconhecido: ${funcionario.nome}")
                            recognizedPerson.value = funcionario
                            
                            // ✅ NOVO: Verificar POOF antes de registrar ponto
                            if (verificarPOOF(funcionario)) {
                                Log.d("DetectScreenViewModel", "✅ POOF válido para: ${funcionario.nome}")
                                
                                // Registrar ponto
                                val ponto = registerPonto(funcionario)
                                if (ponto != null) {
                                    savedPonto.value = ponto
                                    showSuccessScreen.value = true
                                    
                                    // ✅ NOVO: Marcar que registrou ponto para esta pessoa
                                    lastRegisteredPerson = funcionario.nome
                                    lastRegistrationTime = System.currentTimeMillis()
                                    
                                    Log.d("DetectScreenViewModel", "✅ Ponto registrado com sucesso para: ${funcionario.nome}")
                                    break
                                }
                            } else {
                                Log.w("DetectScreenViewModel", "❌ POOF inválido - Registro negado para: ${funcionario.nome}")
                                mostrarMensagemPOOFInvalido(funcionario)
                                // Não registra o ponto e continua o loop
                            }
                        } else {
                            Log.w("DetectScreenViewModel", "⚠️ Nenhum funcionário reconhecido")
                        }
                    }
                }
                
                if (!showSuccessScreen.value && isActive) {
                    Log.w("DetectScreenViewModel", "⚠️ Timeout - Nenhuma pessoa reconhecida após $maxAttempts tentativas")
                }
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("DetectScreenViewModel", "🔄 Reconhecimento cancelado: ${e.message}")
                // Não é um erro, apenas cancelamento normal
            } catch (e: Exception) {
                Log.e("DetectScreenViewModel", "❌ Erro no reconhecimento: ${e.message}")
                // Log do erro para crash reporting
                CrashReporter.logException(context, e, "processFaceRecognition")
            } finally {
                isProcessingRecognition.value = false
                recognitionJob = null
            }
        }
    }
    
    private fun findRecognizedEmployee(): FuncionariosEntity? {
        return try {
            Log.d("DetectScreenViewModel", "🔍 Buscando pessoa reconhecida...")
            
            // Obter a pessoa que está sendo reconhecida
            val recognizedPersonName = lastRecognizedPersonName.value
            Log.d("DetectScreenViewModel", "🔍 Nome da pessoa reconhecida: $recognizedPersonName")
            
            if (recognizedPersonName != null && recognizedPersonName != "Not recognized" && recognizedPersonName != "Não Encontrado") {
                Log.d("DetectScreenViewModel", "✅ Pessoa reconhecida: $recognizedPersonName")
                
                // ✅ NOVO: Buscar apenas funcionários ATIVOS no banco
                val funcionarios = funcionariosDao.getActiveFuncionarios()
                Log.d("DetectScreenViewModel", "📊 Total de funcionários ATIVOS no banco: ${funcionarios.size}")
                
                // Listar todos os funcionários ativos para debug
                funcionarios.forEach { funcionario ->
                    Log.d("DetectScreenViewModel", "📋 Funcionário ATIVO no banco: ${funcionario.nome}")
                }
                
                // ✅ MELHORADO: Buscar o funcionário correspondente no banco com comparação mais flexível
                val funcionario = funcionarios.find { funcionario ->
                    // Comparação exata
                    funcionario.nome == recognizedPersonName ||
                    // Comparação ignorando case
                    funcionario.nome.equals(recognizedPersonName, ignoreCase = true) ||
                    // Comparação removendo espaços extras
                    funcionario.nome.trim() == recognizedPersonName.trim()
                }
                
                if (funcionario != null) {
                    Log.d("DetectScreenViewModel", "✅ Funcionário ATIVO encontrado no banco: ${funcionario.nome}")
                    Log.d("DetectScreenViewModel", "✅ ID do funcionário: ${funcionario.id}")
                    Log.d("DetectScreenViewModel", "✅ CPF do funcionário: ${funcionario.cpf}")
                    Log.d("DetectScreenViewModel", "✅ Status do funcionário: ${if (funcionario.ativo == 1) "ATIVO" else "INATIVO"}")
                    return funcionario
                } else {
                    Log.w("DetectScreenViewModel", "⚠️ Pessoa reconhecida mas não encontrada entre funcionários ATIVOS: $recognizedPersonName")
                    Log.w("DetectScreenViewModel", "⚠️ Funcionários ATIVOS disponíveis: ${funcionarios.map { it.nome }}")
                    
                    // ✅ NOVO: Verificar se existe entre funcionários inativos
                    val funcionariosInativos = funcionariosDao.getInactiveFuncionarios()
                    val funcionarioInativo = funcionariosInativos.find { funcionario ->
                        funcionario.nome == recognizedPersonName ||
                        funcionario.nome.equals(recognizedPersonName, ignoreCase = true) ||
                        funcionario.nome.trim() == recognizedPersonName.trim()
                    }
                    
                    if (funcionarioInativo != null) {
                        Toast.makeText(context, "⚠️ Funcionário encontrado mas está INATIVO: ${funcionarioInativo.nome}", Toast.LENGTH_LONG).show()
                    }
                    
                 
                    funcionarios.forEach { func ->
                        Log.w("DetectScreenViewModel", "🔍 Comparando com ATIVO: '${func.nome}' (tamanho: ${func.nome.length})")
                        Log.w("DetectScreenViewModel", "🔍 Igual exato: ${func.nome == recognizedPersonName}")
                        Log.w("DetectScreenViewModel", "🔍 Igual ignore case: ${func.nome.equals(recognizedPersonName, ignoreCase = true)}")
                        Log.w("DetectScreenViewModel", "🔍 Igual trim: ${func.nome.trim() == recognizedPersonName.trim()}")
                    }
                    
                    return null
                }
            } else {
                Log.w("DetectScreenViewModel", "⚠️ Nenhuma pessoa reconhecida")
                return null
            }
            
        } catch (e: Exception) {
            Log.e("DetectScreenViewModel", "❌ Erro ao buscar pessoa reconhecida: ${e.message}")
            // Log do erro para crash reporting
            CrashReporter.logException(context, e, "findRecognizedEmployee")
            return null
        }
    }
    

    
    private suspend fun registerPonto(funcionario: FuncionariosEntity): PontosGenericosEntity? {
        return try {
            Log.d("DetectScreenViewModel", "💾 Registrando ponto para: ${funcionario.nome}")
            
            val horarioAtual = System.currentTimeMillis()
            

            val locationResult = try {
                val geolocEnabled = try { com.ml.shubham0204.facenet_android.data.ConfiguracoesDao().getConfiguracoes()?.geolocalizacaoHabilitada ?: true } catch (_: Exception) { true }
                if (geolocEnabled) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        locationUtils.getCurrentLocation(PerformanceConfig.LOCATION_TIMEOUT_MS)
                    }
                } else null
            } catch (e: Exception) {
                Log.w("DetectScreenViewModel", "⚠️ Erro ao obter localização: ${e.message}")
                null
            }
            
            val latitude: Double?
            val longitude: Double?
            
            val entidadeIdForMatricula = funcionario.entidadeId ?: return null
            val matriculas = apiService.obterVariasMatricula(entidadeIdForMatricula, funcionario.cpf)
            if (matriculas.isSuccessful && matriculas.body()?.is_open_modal == true) {
                val matriculasList = matriculas.body()?.matriculas?.mapNotNull { matricula ->
                    when (matricula) {
                        is Map<*, *> -> matricula["matricula"]?.toString()
                        else -> matricula.toString()
                    }
                }?.filter { it.isNotBlank() } ?: emptyList()
                
                availableMatriculas.value = matriculasList
                pendingFuncionario.value = funcionario
                showMatriculaSelectionModal.value = true
                return null
            }
            
            // Preferir coordenadas fixas das configurações
            val configuracoes = try { com.ml.shubham0204.facenet_android.data.ConfiguracoesDao().getConfiguracoes() } catch (_: Exception) { null }
            if (configuracoes?.latitudeFixa != null && configuracoes.longitudeFixa != null) {
                latitude = configuracoes.latitudeFixa
                longitude = configuracoes.longitudeFixa
            } else if (locationResult != null) {
                latitude = locationResult.latitude
                longitude = locationResult.longitude
            } else {
                latitude = null
                longitude = null
            }
            
            val fotoBase64 = currentFaceBitmap.value?.let { bitmap ->
                if (BitmapUtils.isValidBitmap(bitmap)) {
                    val base64 = BitmapUtils.bitmapToBase64(bitmap, 80)
                    Log.d("DetectScreenViewModel", "📸 Foto capturada e convertida para base64 (${base64.length} chars)")
                    base64
                } else {
                    Log.w("DetectScreenViewModel", "⚠️ Bitmap inválido para conversão")
                    null
                }
            } ?: run {
                Log.w("DetectScreenViewModel", "⚠️ Nenhuma foto disponível para captura")
                null
            }
            
            val entidadeId = if (!funcionario.entidadeId.isNullOrEmpty()) {
                funcionario.entidadeId
            } else {
                val configuracoesDao = ConfiguracoesDao()
                val configuracoes = configuracoesDao.getConfiguracoes()
                configuracoes?.entidadeId ?: "ENTIDADE_PADRAO"
            }
            
            Log.d("DetectScreenViewModel", "🏢 Criando ponto para entidade: $entidadeId")
            
            val ponto = PontosGenericosEntity(
                funcionarioId = funcionario.id.toString(),
                funcionarioNome = funcionario.nome,
                funcionarioMatricula = funcionario.matricula,
                funcionarioCpf = funcionario.cpf,
                funcionarioCargo = funcionario.cargo,
                funcionarioSecretaria = funcionario.secretaria,
                funcionarioLotacao = funcionario.lotacao,
                dataHora = horarioAtual,
                latitude = latitude,
                longitude = longitude,
                fotoBase64 = fotoBase64,
                synced = false,
                entidadeId = entidadeId
            )
            
            val pontoId = pontosGenericosDao.insert(ponto)

            if (fotoBase64 != null) {
                Log.d("DetectScreenViewModel", "✅ Foto base64 salva com sucesso")
            }
            
            try {
                SoundUtils.playBeepSound(context)
            } catch (e: Exception) {
                Log.w("DetectScreenViewModel", "⚠️ Erro ao reproduzir som: ${e.message}")
            }
            
            attemptAutoSync()
            
            ponto
        } catch (e: Exception) {
            Log.e("DetectScreenViewModel", "❌ Erro ao registrar ponto: ${e.message}")
            // Log do erro para crash reporting
            CrashReporter.logException(context, e, "registerPonto")
            null
        }
    }
    
    fun resetRecognition() {
        try {
            recognitionJob?.cancel()
            recognitionJob = null
            
            isProcessingRecognition.value = false
            currentFaceBitmap.value = null
            recognizedPerson.value = null
            showSuccessScreen.value = false
            savedPonto.value = null
            lastRecognizedPersonName.value = null
            
            lastPhotoTimestamp = 0
            lastPhotoHash = null
            
            Log.d("DetectScreenViewModel", "🔄 Reconhecimento resetado com sucesso - controles de foto limpos")
        } catch (e: Exception) {
            Log.e("DetectScreenViewModel", "❌ Erro ao resetar reconhecimento: ${e.message}")
        }
    }

    private fun verificarPOOF(funcionario: FuncionariosEntity): Boolean {
        return try {

            val poofValido = funcionario.ativo == 1
            
            poofValido
        } catch (e: Exception) {
            false
        }
    }

    private fun mostrarMensagemPOOFInvalido(funcionario: FuncionariosEntity) {
        viewModelScope.launch {
            try {
                val mensagem = when {
                    funcionario.ativo == 0 -> "❌ ACESSO NEGADO\n\n${funcionario.nome}\n\nFuncionário INATIVO no sistema.\nProcure o RH para regularizar sua situação."
                    else -> "❌ ACESSO NEGADO\n\n${funcionario.nome}\n\nPOOF (Proof of Employment) inválido.\nProcure o RH para validação."
                }
                
                Toast.makeText(
                    context,
                    mensagem,
                    Toast.LENGTH_LONG
                ).show()
                
                Log.w("DetectScreenViewModel", "🚫 Acesso negado - POOF inválido para: ${funcionario.nome}")
                
            } catch (e: Exception) {
                Log.e("DetectScreenViewModel", "❌ Erro ao mostrar mensagem de POOF inválido: ${e.message}")
            }
        }
    }

    // ✅ NOVO: Função para mostrar mensagem de spoofing detectado
    private fun mostrarMensagemSpoofDetectado() {
        viewModelScope.launch {
            try {
                val mensagem = " ACESSO NEGADO\n\nFOTO DETECTADA!\n\nO sistema detectou que você está usando uma foto.\nUse seu rosto real para registrar o ponto."
                
                Toast.makeText(
                    context,
                    mensagem,
                    Toast.LENGTH_LONG
                ).show()
                
                Log.w("DetectScreenViewModel", " Acesso negado - Spoofing detectado")
                
            } catch (e: Exception) {
                Log.e("DetectScreenViewModel", "❌ Erro ao mostrar mensagem de spoofing: ${e.message}")
            }
        }
    }
    
    // ✅ NOVO: Função para tentar sincronização automática
    private fun attemptAutoSync() {
        viewModelScope.launch {
            try {
                Log.d("DetectScreenViewModel", "🔄 Verificando conectividade para sincronização automática...")
                
                // Verificar se há internet disponível
                val hasInternet = ConnectivityUtils.isInternetAvailableWithTimeout(context, 3000)
                
                if (hasInternet) {
                    Log.d("DetectScreenViewModel", "🌐 Internet disponível - Iniciando sincronização automática...")
                    
                    // Verificar se há pontos pendentes para sincronizar
                    val pontosPendentes = pontoSincronizacaoService.getQuantidadePontosPendentes(context)
                    
                    if (pontosPendentes > 0) {
                        Log.d("DetectScreenViewModel", "📊 Encontrados $pontosPendentes pontos pendentes para sincronização")
                        
                        // ✅ NOVO: Executar sincronização por blocos
                        val resultado = pontoSincronizacaoPorBlocosService.sincronizarPontosPorBlocos(context)
                        
                        if (resultado.sucesso) {
                            Log.d("DetectScreenViewModel", "✅ Sincronização automática por blocos bem-sucedida: ${resultado.pontosSincronizados} pontos sincronizados em ${resultado.entidadesProcessadas} entidades")
                            val mensagemToast = if (resultado.entidadesProcessadas > 1) {
                                "✅ ${resultado.pontosSincronizados} ponto(s) sincronizado(s) automaticamente em ${resultado.entidadesProcessadas} entidades!"
                            } else {
                                "✅ ${resultado.pontosSincronizados} ponto(s) sincronizado(s) automaticamente!"
                            }
                            Toast.makeText(context, mensagemToast, Toast.LENGTH_SHORT).show()
                        } else {
                            Log.w("DetectScreenViewModel", "⚠️ Falha na sincronização automática: ${resultado.mensagem}")
                            // Não mostrar toast de erro para não incomodar o usuário
                        }
                    } else {
                        Log.d("DetectScreenViewModel", "ℹ️ Nenhum ponto pendente para sincronização")
                    }
                } else {
                    Log.d("DetectScreenViewModel", "📵 Sem internet - Ponto salvo localmente para sincronização posterior")
                    // Não mostrar toast, pois é comportamento normal
                }
                
            } catch (e: Exception) {
                Log.e("DetectScreenViewModel", "❌ Erro na sincronização automática: ${e.message}")
                // Não mostrar toast de erro para não incomodar o usuário
            }
        }
    }
}
