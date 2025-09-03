package com.ml.shubham0204.facenet_android.presentation.screens.detect_screen

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.data.PontosGenericosDao
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.data.RecognitionMetrics
import com.ml.shubham0204.facenet_android.presentation.components.FaceDetectionOverlay
import com.ml.shubham0204.facenet_android.domain.ImageVectorUseCase
import com.ml.shubham0204.facenet_android.domain.PersonUseCase
import com.ml.shubham0204.facenet_android.utils.BitmapUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class DetectScreenViewModel(
    val personUseCase: PersonUseCase,
    val imageVectorUseCase: ImageVectorUseCase,
    private val pontosGenericosDao: PontosGenericosDao,
    private val funcionariosDao: FuncionariosDao
) : ViewModel() {
    val faceDetectionMetricsState = mutableStateOf<RecognitionMetrics?>(null)
    val isProcessingRecognition = mutableStateOf(false)
    val currentFaceBitmap = mutableStateOf<Bitmap?>(null)
    val recognizedPerson = mutableStateOf<FuncionariosEntity?>(null)
    val showSuccessScreen = mutableStateOf(false)
    val savedPonto = mutableStateOf<PontosGenericosEntity?>(null)
    val lastRecognizedPersonName = mutableStateOf<String?>(null)
    
    // ✅ NOVO: Job para controlar o reconhecimento
    private var recognitionJob: kotlinx.coroutines.Job? = null

    fun getNumPeople(): Long = personUseCase.getCount()
    
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
        currentFaceBitmap.value = bitmap
    }
    
    fun setLastRecognizedPersonName(name: String?) {
        lastRecognizedPersonName.value = name
    }
    
    fun processFaceRecognition() {
        // ✅ CORRIGIDO: Cancelar job anterior se existir
        recognitionJob?.cancel()
        
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
                
                // Aguardar até que uma pessoa seja reconhecida
                var attempts = 0
                val maxAttempts = 20 // 10 segundos (20 * 500ms)
                
                while (attempts < maxAttempts && !showSuccessScreen.value && isActive) {
                    delay(500)
                    attempts++
                    
                    val recognizedPersonName = lastRecognizedPersonName.value
                    Log.d("DetectScreenViewModel", "🔍 Tentativa $attempts - Pessoa reconhecida: $recognizedPersonName")
                    
                    if (recognizedPersonName != null && recognizedPersonName != "Not recognized" && recognizedPersonName != "Não Encontrado") {
                        Log.d("DetectScreenViewModel", "✅ Pessoa reconhecida! Processando...")
                        
                        // Aguardar um pouco mais para garantir que a informação está estável
                        delay(1000)
                        
                        // Buscar funcionários reconhecidos
                        val funcionario = findRecognizedEmployee()
                        
                        if (funcionario != null) {
                            Log.d("DetectScreenViewModel", "✅ Funcionário reconhecido: ${funcionario.nome}")
                            recognizedPerson.value = funcionario
                            
                            // Registrar ponto
                            val ponto = registerPonto(funcionario)
                            if (ponto != null) {
                                savedPonto.value = ponto
                                showSuccessScreen.value = true
                                Log.d("DetectScreenViewModel", "✅ Ponto registrado com sucesso")
                                break // ✅ CORRIGIDO: Sair do loop após sucesso
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
                        Log.w("DetectScreenViewModel", "⚠️ Funcionário encontrado mas está INATIVO: ${funcionarioInativo.nome}")
                        Log.w("DetectScreenViewModel", "⚠️ Ponto não autorizado para funcionários inativos")
                    }
                    
                    // ✅ NOVO: Log detalhado para debug
                    Log.w("DetectScreenViewModel", "🔍 === DEBUG DE COMPARAÇÃO ===")
                    Log.w("DetectScreenViewModel", "🔍 Nome reconhecido: '$recognizedPersonName'")
                    Log.w("DetectScreenViewModel", "🔍 Tamanho do nome reconhecido: ${recognizedPersonName.length}")
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
            return null
        }
    }
    

    
    private fun registerPonto(funcionario: FuncionariosEntity): PontosGenericosEntity? {
        return try {
            Log.d("DetectScreenViewModel", "💾 Registrando ponto para: ${funcionario.nome}")
            
            val horarioAtual = System.currentTimeMillis()
            
            // ✅ NOVO: Capturar foto do momento do registro
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
            
            // Criar ponto com foto
            val ponto = PontosGenericosEntity(
                funcionarioId = funcionario.id.toString(),
                funcionarioNome = funcionario.nome,
                funcionarioMatricula = funcionario.matricula,
                funcionarioCpf = funcionario.cpf,
                funcionarioCargo = funcionario.cargo,
                funcionarioSecretaria = funcionario.secretaria,
                funcionarioLotacao = funcionario.lotacao,
                tipoPonto = "PONTO",
                dataHora = horarioAtual,
                latitude = -6.377917793252374, // Simular coordenadas
                longitude = -39.316891286420876,
                fotoBase64 = fotoBase64, // ✅ NOVO: Incluir foto base64
                synced = false
            )
            
            // Salvar no banco
            val pontoId = pontosGenericosDao.insert(ponto)
            Log.d("DetectScreenViewModel", "✅ Ponto salvo com ID: $pontoId")
            if (fotoBase64 != null) {
                Log.d("DetectScreenViewModel", "✅ Foto base64 salva com sucesso")
            }
            
            ponto
        } catch (e: Exception) {
            Log.e("DetectScreenViewModel", "❌ Erro ao registrar ponto: ${e.message}")
            null
        }
    }
    
    fun resetRecognition() {
        // ✅ CORRIGIDO: Cancelar job de reconhecimento
        recognitionJob?.cancel()
        recognitionJob = null
        
        isProcessingRecognition.value = false
        currentFaceBitmap.value = null
        recognizedPerson.value = null
        showSuccessScreen.value = false
        savedPonto.value = null
        lastRecognizedPersonName.value = null // ✅ CORRIGIDO: Resetar o nome da pessoa reconhecida
        Log.d("DetectScreenViewModel", "🔄 Estados resetados para nova captura")
    }
}
