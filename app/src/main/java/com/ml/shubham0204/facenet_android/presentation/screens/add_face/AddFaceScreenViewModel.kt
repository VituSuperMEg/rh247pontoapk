package com.ml.shubham0204.facenet_android.presentation.screens.add_face

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.data.api.ApiService
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient
import com.ml.shubham0204.facenet_android.data.config.ServerConfig
import android.content.Context
import com.ml.shubham0204.facenet_android.domain.AppException
import com.ml.shubham0204.facenet_android.domain.ImageVectorUseCase
import com.ml.shubham0204.facenet_android.domain.PersonUseCase
import com.ml.shubham0204.facenet_android.presentation.components.setProgressDialogText
import com.ml.shubham0204.facenet_android.utils.ConnectivityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.android.annotation.KoinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

@KoinViewModel
class AddFaceScreenViewModel(
    private val personUseCase: PersonUseCase,
    private val imageVectorUseCase: ImageVectorUseCase,
) : ViewModel(), KoinComponent {
    private val context: Context by inject()
    
    val personNameState: MutableState<String> = mutableStateOf("")
    val selectedImageURIs: MutableState<List<Uri>> = mutableStateOf(emptyList())

    val isProcessingImages: MutableState<Boolean> = mutableStateOf(false)
    val numImagesProcessed: MutableState<Int> = mutableIntStateOf(0)
    val showSuccessScreen: MutableState<Boolean> = mutableStateOf(false)
    
    val isDeletingUser: MutableState<Boolean> = mutableStateOf(false)
    val showDeleteConfirmation: MutableState<Boolean> = mutableStateOf(false)
    val wasUserDeleted: MutableState<Boolean> = mutableStateOf(false) // ✅ NOVO: Controla se foi uma exclusão
    var onUserDeleted: (() -> Unit)? = null // ✅ NOVO: Callback para navegação
    
    // ✅ NOVO: Estados para fotos capturadas
    val capturedImagesUrls: MutableState<List<String>> = mutableStateOf(emptyList())
    val isLoadingImages: MutableState<Boolean> = mutableStateOf(false)
    
    val showDuplicateFaceDialog: MutableState<Boolean> = mutableStateOf(false)
    val duplicateFaceInfo: MutableState<DuplicateFaceInfo?> = mutableStateOf(null)

    private val funcionariosList: MutableState<List<FuncionariosEntity>> = mutableStateOf(emptyList())
    private val configuracoesDao = ConfiguracoesDao()
    private val apiService = RetrofitClient.instance

    var funcionarioId: Long = 0
    
    private fun getEntidadeId(): String? {
        return try {
            val configuracoes = configuracoesDao.getConfiguracoes()
            val entidadeId = configuracoes?.entidadeId ?: ""
            if (entidadeId.isNullOrEmpty()) null else entidadeId
        } catch (e: Exception) {
            android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao obter entidade ID", e)
            null
        }
    }
    
    fun setFaceDetectionStatus(status: String) {
        android.util.Log.d("AddFaceScreenViewModel", "📱 Status: $status")
    }
    
    fun addSelectedImageURI(uri: Uri) {
        val currentList = selectedImageURIs.value.toMutableList()
        currentList.add(uri)
        selectedImageURIs.value = currentList
        android.util.Log.d("AddFaceScreenViewModel", "📸 URI adicionada: $uri")
        android.util.Log.d("AddFaceScreenViewModel", "📊 Total de URIs: ${selectedImageURIs.value.size}")
    }
    
    fun clearSelectedImageURIs() {
        selectedImageURIs.value = emptyList()
        android.util.Log.d("AddFaceScreenViewModel", "🗑️ URIs limpas")
    }
    
    fun updatePersonName(name: String) {
        personNameState.value = name
    }
    
    suspend fun canManageFacial(): Boolean {
        if (funcionarioId <= 0) {
            android.util.Log.w("AddFaceScreenViewModel", "⚠️ FuncionarioId inválido: $funcionarioId")
            return false
        }
        return personUseCase.canManageFacial(funcionarioId)
    }
    
    suspend fun validateFaceNotDuplicate(imageUri: Uri, currentPersonID: Long? = null): Boolean {
        return try {
            val result = imageVectorUseCase.checkIfFaceAlreadyExists(imageUri, currentPersonID)
            
            if (result.isSuccess) {
                val faceCheckResult = result.getOrNull()!!
                
                if (faceCheckResult.exists) {
                    duplicateFaceInfo.value = DuplicateFaceInfo(
                        existingPersonName = faceCheckResult.existingFace?.personName ?: "Desconhecido",
                        similarity = faceCheckResult.similarity
                    )
                    showDuplicateFaceDialog.value = true
                    
                    return false
                } else {
                    return true
                }
            } else {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return true
        }
    }
    
    fun confirmDuplicateFaceRegistration() {
        showDuplicateFaceDialog.value = false
        duplicateFaceInfo.value = null
        saveFacesInternal()
    }
    
    fun cancelDuplicateFaceRegistration() {
        showDuplicateFaceDialog.value = false
        duplicateFaceInfo.value = null
        isProcessingImages.value = false
    }
    
    private fun syncWithServer(funcionarioId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Vai pegar as fotos, o embedding da face e sincronizar com o servidor
                val context = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
                val tabletDataSyncUtil = com.ml.shubham0204.facenet_android.utils.TabletDataSyncUtil(context)
                
                val syncResult = tabletDataSyncUtil.syncSingleFuncionario(funcionarioId)
                
                if (syncResult.success) {
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Sincronização concluída com sucesso!")
                    android.util.Log.d("AddFaceScreenViewModel", "📊 Sucessos: ${syncResult.successCount}, Erros: ${syncResult.errorCount}")
                } else {
                    android.util.Log.e("AddFaceScreenViewModel", "❌ Erro na sincronização:")
                    syncResult.errors.forEach { error ->
                        android.util.Log.e("AddFaceScreenViewModel", "❌ $error")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao sincronizar: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun saveFaces() {
        if (selectedImageURIs.value.isEmpty()) {
            android.util.Log.w("AddFaceScreenViewModel", "⚠️ Nenhuma imagem selecionada")
            return
        }

        if (personNameState.value.isBlank()) {
            android.util.Log.w("AddFaceScreenViewModel", "⚠️ Nome da pessoa não informado")
            return
        }

        isProcessingImages.value = true
        numImagesProcessed.value = 0

        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (!canManageFacial()) {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Funcionário inativo - operação de facial não permitida")
                    setProgressDialogText("Funcionário inativo - operação não permitida")
                    delay(2000) // Mostrar mensagem por 2 segundos
                    isProcessingImages.value = false
                    return@launch
                }
                
                android.util.Log.d("AddFaceScreenViewModel", "🔍 Validando faces para duplicação...")
                
                val existingPerson = personUseCase.getPersonByFuncionarioId(funcionarioId)
                val currentPersonID = existingPerson?.personID
                
                var allFacesValid = true
                var duplicateFound = false
                
                for ((index, uri) in selectedImageURIs.value.withIndex()) {
                    android.util.Log.d("AddFaceScreenViewModel", "🔍 Validando face ${index + 1}/${selectedImageURIs.value.size}")
                    
                    val isValid = validateFaceNotDuplicate(uri, currentPersonID)
                    if (!isValid) {
                        allFacesValid = false
                        duplicateFound = true
                        break // Parar na primeira face duplicada
                    }
                }
                
                if (duplicateFound) {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Face duplicada encontrada - aguardando confirmação do usuário")
                    return@launch // Aguardar confirmação do usuário
                }
                
                if (allFacesValid) {
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Todas as faces são válidas - prosseguindo com cadastro")
                    saveFacesInternal()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao validar faces: ${e.message}")
                e.printStackTrace()
                isProcessingImages.value = false
            }
        }
    }
    
    // ✅ NOVO: Função interna para salvar faces (após validação)
    private fun saveFacesInternal() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                android.util.Log.d("AddFaceScreenViewModel", "🔄 Iniciando salvamento no banco...")
                
                val existingPerson = personUseCase.getPersonByFuncionarioId(funcionarioId)
                
                if (existingPerson != null) {
                    android.util.Log.d("AddFaceScreenViewModel", "🔄 === RECADASTRO DE FACES ===")
                    android.util.Log.d("AddFaceScreenViewModel", "🔄 Pessoa existente encontrada:")
                    android.util.Log.d("AddFaceScreenViewModel", "   - Person ID: ${existingPerson.personID}")
                    android.util.Log.d("AddFaceScreenViewModel", "   - Nome: ${existingPerson.personName}")
                    android.util.Log.d("AddFaceScreenViewModel", "   - Faces antigas: ${existingPerson.numImages}")
                    
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Apagando faces antigas...")
                    imageVectorUseCase.removeImages(existingPerson.personID)
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Faces antigas removidas")
                    
                    val updatedPerson = existingPerson.copy(
                        personName = personNameState.value,
                        numImages = selectedImageURIs.value.size.toLong(),
                        addTime = System.currentTimeMillis()
                    )
                    
                    personUseCase.removePerson(existingPerson.personID)
                    val newPersonId = personUseCase.addPerson(
                        updatedPerson.personName,
                        updatedPerson.numImages,
                        updatedPerson.funcionarioId
                    )
                    
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Pessoa atualizada com novo ID: $newPersonId")
                    
                    selectedImageURIs.value.forEachIndexed { index, uri ->
                        android.util.Log.d("AddFaceScreenViewModel", "📸 Processando foto ${index + 1}: $uri")
                        
                        try {
                            imageVectorUseCase
                                .addImage(newPersonId, personNameState.value, uri)
                                .onFailure { error ->
                                    val errorMessage = (error as AppException).errorCode.message
                                    android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao processar foto ${index + 1}: $errorMessage")
                                    setProgressDialogText(errorMessage)
                                }.onSuccess {
                                    numImagesProcessed.value += 1
                                    android.util.Log.d("AddFaceScreenViewModel", "✅ Foto ${index + 1} processada com sucesso")
                                    setProgressDialogText("Processed ${numImagesProcessed.value} image(s)")
                                }
                        } catch (e: Exception) {
                            android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao processar foto ${index + 1}: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    
                    android.util.Log.d("AddFaceScreenViewModel", "�� === RECADASTRO CONCLUÍDO ===")
                    android.util.Log.d("AddFaceScreenViewModel", "�� Total de fotos processadas: ${numImagesProcessed.value}")
                    
                    if (numImagesProcessed.value == selectedImageURIs.value.size) {
                        android.util.Log.d("AddFaceScreenViewModel", "✅ TODAS AS FOTOS FORAM SALVAS COM SUCESSO!")
                        
                        val totalFinal = personUseCase.getCount()
                        android.util.Log.d("AddFaceScreenViewModel", "📊 Total final de pessoas no banco: $totalFinal")
                        
                        // ✅ NOVO: Limpar URIs após salvamento bem-sucedido
                        clearSelectedImageURIs()
                        
                        showSuccessScreen.value = true
                    } else {
                        android.util.Log.e("AddFaceScreenViewModel", "❌ ERRO: Nem todas as fotos foram processadas!")
                    }
                    
                } else {
                    android.util.Log.d("AddFaceScreenViewModel", "🆕 === PRIMEIRO CADASTRO ===")
                    android.util.Log.d("AddFaceScreenViewModel", "�� Nenhuma pessoa encontrada para este funcionário")
                    
                    // ✅ NOVO: Salvar pessoa no banco FaceNet
                    val personId = personUseCase.addPerson(
                        personNameState.value,
                        selectedImageURIs.value.size.toLong(),
                        funcionarioId
                    )
                    
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Pessoa salva com ID: $personId")
                    
                    // ✅ NOVO: Verificar se a pessoa foi salva
                    val totalPessoas = personUseCase.getCount()
                    android.util.Log.d("AddFaceScreenViewModel", "📊 Total de pessoas no banco após salvar: $totalPessoas")
                    
                    // ✅ NOVO: Salvar todas as imagens de uma vez
                    android.util.Log.d("AddFaceScreenViewModel", "📸 Salvando ${selectedImageURIs.value.size} imagens...")
                    
                    val result = imageVectorUseCase.addMultipleImages(
                        personId, 
                        personNameState.value, 
                        selectedImageURIs.value
                    )
                    
                    if (result.isSuccess) {
                        numImagesProcessed.value = selectedImageURIs.value.size
                        android.util.Log.d("AddFaceScreenViewModel", "✅ Todas as imagens salvas com sucesso")
                        setProgressDialogText("Todas as imagens processadas com sucesso")
                        
                        // ✅ NOVO: Sincronizar automaticamente com o servidor
                        android.util.Log.d("AddFaceScreenViewModel", "🔄 Iniciando sincronização automática...")
                        syncWithServer(funcionarioId)
                        
                    } else {
                        android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao salvar imagens: ${result.exceptionOrNull()?.message}")
                        setProgressDialogText("Erro ao processar imagens: ${result.exceptionOrNull()?.message}")
                    }
                    
                    android.util.Log.d("AddFaceScreenViewModel", "�� === PRIMEIRO CADASTRO CONCLUÍDO ===")
                    android.util.Log.d("AddFaceScreenViewModel", "📊 Total de fotos processadas: ${numImagesProcessed.value}")
                    
                    if (numImagesProcessed.value == selectedImageURIs.value.size) {
                        android.util.Log.d("AddFaceScreenViewModel", "✅ TODAS AS FOTOS FORAM SALVAS COM SUCESSO!")
                        
                        val totalFinal = personUseCase.getCount()
                        android.util.Log.d("AddFaceScreenViewModel", "📊 Total final de pessoas no banco: $totalFinal")
                        
                        // ✅ NOVO: Limpar URIs após salvamento bem-sucedido
                        clearSelectedImageURIs()
                        
                        showSuccessScreen.value = true
                    } else {
                        android.util.Log.e("AddFaceScreenViewModel", "❌ ERRO: Nem todas as fotos foram processadas!")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao salvar faces: ${e.message}")
                e.printStackTrace()
            } finally {
                isProcessingImages.value = false
            }
        }
    }
    
    // ✅ NOVO: Função para mostrar diálogo de confirmação de exclusão
    fun showDeleteConfirmationDialog() {
        android.util.Log.d("AddFaceScreenViewModel", "🔘 showDeleteConfirmationDialog() chamada")
        android.util.Log.d("AddFaceScreenViewModel", "🔘 funcionarioId atual: $funcionarioId")
        showDeleteConfirmation.value = true
        android.util.Log.d("AddFaceScreenViewModel", "🔘 showDeleteConfirmation.value = ${showDeleteConfirmation.value}")
    }
    
    // ✅ NOVO: Função para mostrar diálogo de confirmação de exclusão de funcionário completo
    fun showDeleteFuncionarioConfirmationDialog() {
        android.util.Log.d("AddFaceScreenViewModel", "🔘 showDeleteFuncionarioConfirmationDialog() chamada")
        android.util.Log.d("AddFaceScreenViewModel", "🔘 funcionarioId atual: $funcionarioId")
        android.util.Log.d("AddFaceScreenViewModel", "🔘 showDeleteConfirmation ANTES: ${showDeleteConfirmation.value}")
        showDeleteConfirmation.value = true
        android.util.Log.d("AddFaceScreenViewModel", "🔘 showDeleteConfirmation DEPOIS: ${showDeleteConfirmation.value}")
        android.util.Log.d("AddFaceScreenViewModel", "🔘 isDeletingUser: ${isDeletingUser.value}")
    }
    
    // ✅ NOVO: Função para excluir apenas a face (não o funcionário completo)
    fun confirmDeleteFace() {
        android.util.Log.d("AddFaceScreenViewModel", "🔘 confirmDeleteFace() chamada - Excluir apenas facial")
        android.util.Log.d("AddFaceScreenViewModel", "🔘 funcionarioId: $funcionarioId")

        showDeleteConfirmation.value = false
        isDeletingUser.value = true

        CoroutineScope(Dispatchers.Default).launch {
            try {
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ Iniciando exclusão de FACES apenas...")
                
                // Buscar funcionário para obter CPF
                val funcionariosDao = com.ml.shubham0204.facenet_android.data.FuncionariosDao()
                var funcionario = funcionariosDao.getById(funcionarioId)
                
                if (funcionario == null) {
                    funcionario = funcionariosDao.getByApiId(funcionarioId)
                }
                
                if (funcionario != null) {
                    // 1. Excluir faces do banco local
                    val existingPerson = personUseCase.getPersonByFuncionarioId(funcionarioId)
                    if (existingPerson != null) {
                        android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo faces do banco local...")
                        imageVectorUseCase.removeImages(existingPerson.personID)
                        android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo pessoa do banco local...")
                        personUseCase.removePerson(existingPerson.personID)
                        android.util.Log.d("AddFaceScreenViewModel", "✅ Faces excluídas do banco local com sucesso!")
                    } else {
                        android.util.Log.w("AddFaceScreenViewModel", "⚠️ Nenhuma face encontrada para excluir")
                    }
                    
                    // 2. Excluir faces do servidor (se houver internet)
                    val entidadeId = getEntidadeId()
                    if (entidadeId != null && funcionario.cpf.isNotEmpty()) {
                        android.util.Log.d("AddFaceScreenViewModel", "🌐 Tentando excluir faces do servidor...")
                        deleteFacesFromServer(funcionario.cpf, entidadeId)
                    } else {
                        android.util.Log.w("AddFaceScreenViewModel", "⚠️ EntidadeId ou CPF vazio - não excluindo do servidor")
                    }
                } else {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Funcionário não encontrado")
                }

                clearSelectedImageURIs()

                android.util.Log.d("AddFaceScreenViewModel", "✅ Exclusão de faces concluída!")
                withContext(Dispatchers.Main) {
                    showSuccessScreen.value = true
                }

            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao excluir faces: ${e.message}")
                e.printStackTrace()
            } finally {
                isDeletingUser.value = false
            }
        }
    }
    
    fun confirmDeleteUser() {
        android.util.Log.d("AddFaceScreenViewModel", "🔘 confirmDeleteUser() chamada - Excluir funcionário completo")
        android.util.Log.d("AddFaceScreenViewModel", "🔘 funcionarioId: $funcionarioId")

        showDeleteConfirmation.value = false
        isDeletingUser.value = true

        CoroutineScope(Dispatchers.Default).launch {
            // ✅ NOVO: Marcar como exclusão desde o início
            wasUserDeleted.value = true
            android.util.Log.d("AddFaceScreenViewModel", "🔘 wasUserDeleted definido como true no início da exclusão")
            
            try {
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ Iniciando exclusão COMPLETA do funcionário...")

                // ✅ NOVO: Excluir funcionário completo do banco
                val funcionariosDao = com.ml.shubham0204.facenet_android.data.FuncionariosDao()
                val matriculasDao = com.ml.shubham0204.facenet_android.data.MatriculasDao()
                val pontosDao = com.ml.shubham0204.facenet_android.data.PontosGenericosDao()

                // 1. Buscar funcionário primeiro
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ Buscando funcionário no banco...")
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ funcionarioId para busca: $funcionarioId (tipo: ${funcionarioId::class.simpleName})")
                
                // Tentar buscar por ID primeiro
                var funcionario = funcionariosDao.getById(funcionarioId)
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ Busca por ID: ${if (funcionario != null) "ENCONTRADO" else "NÃO ENCONTRADO"}")
                
                // Se não encontrou por ID, tentar por API ID
                if (funcionario == null) {
                    funcionario = funcionariosDao.getByApiId(funcionarioId)
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Busca por API ID: ${if (funcionario != null) "ENCONTRADO" else "NÃO ENCONTRADO"}")
                }
                
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ Resultado final: ${if (funcionario != null) "ENCONTRADO" else "NÃO ENCONTRADO"}")
                if (funcionario != null) {
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Funcionário encontrado: ${funcionario.nome}")
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ ID do funcionário: ${funcionario.id}")
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ API ID do funcionário: ${funcionario.apiId}")
                    
                    // 2. Excluir faces e pessoa
                    val existingPerson = personUseCase.getPersonByFuncionarioId(funcionarioId)
                    if (existingPerson != null) {
                        android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo faces do banco...")
                        imageVectorUseCase.removeImages(existingPerson.personID)

                        android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo pessoa do banco...")
                        personUseCase.removePerson(existingPerson.personID)
                    }
                    
                    // ✅ NOVO: Deletar fotos do servidor (se houver internet)
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Tentando deletar fotos do servidor...")
                    try {
                        // Buscar entidadeId das configurações
                        val configuracoesDao = com.ml.shubham0204.facenet_android.data.ConfiguracoesDao()
                        val entidadeId = configuracoesDao.getConfiguracoes()?.entidadeId ?: ""
                        
                        if (entidadeId.isNotEmpty()) {
                            deleteFacesFromServer(funcionario.cpf, entidadeId)
                            android.util.Log.d("AddFaceScreenViewModel", "✅ Chamada para deletar fotos do servidor enviada")
                        } else {
                            android.util.Log.w("AddFaceScreenViewModel", "⚠️ EntidadeId não encontrado - não deletando fotos do servidor")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao deletar fotos do servidor: ${e.message}")
                    }
                    
                    // 3. Excluir matrículas do funcionário
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo matrículas do banco...")
                    try {
                        // Excluir por ID do funcionário
                        matriculasDao.deleteByFuncionarioId(funcionario.id.toString())
                        
                        // Excluir por CPF do funcionário (para garantir que todas sejam removidas)
                        matriculasDao.deleteByCpf(funcionario.cpf)
                        
                        android.util.Log.d("AddFaceScreenViewModel", "✅ Matrículas excluídas com sucesso!")
                        
                    } catch (e: Exception) {
                        android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao excluir matrículas: ${e.message}")
                        e.printStackTrace()
                        
                        // Fallback: tentar limpar todas as matrículas se houver erro
                        try {
                            android.util.Log.w("AddFaceScreenViewModel", "⚠️ Tentando limpar todas as matrículas como fallback...")
                            matriculasDao.clearAll()
                            android.util.Log.d("AddFaceScreenViewModel", "✅ Fallback: Todas as matrículas foram limpas")
                        } catch (e2: Exception) {
                            android.util.Log.e("AddFaceScreenViewModel", "❌ Erro no fallback de limpeza: ${e2.message}")
                        }
                    }
                    
                    // 4. Excluir pontos do funcionário
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo pontos do banco...")
                    pontosDao.deleteByFuncionarioNome(funcionario.nome)
                    
                    // 5. Excluir funcionário do banco
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo funcionário do banco...")
                    funcionariosDao.delete(funcionario)
                    
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Funcionário COMPLETO excluído com sucesso!")
                } else {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Funcionário não encontrado no banco")
                }

                clearSelectedImageURIs()

                android.util.Log.d("AddFaceScreenViewModel", "🔘 Definindo showSuccessScreen = true")
                showSuccessScreen.value = true
                android.util.Log.d("AddFaceScreenViewModel", "🔘 showSuccessScreen.value = ${showSuccessScreen.value}")
                android.util.Log.d("AddFaceScreenViewModel", "🔘 wasUserDeleted.value = ${wasUserDeleted.value}")
                android.util.Log.d("AddFaceScreenViewModel", "🔘 === ESTADOS DEFINIDOS - CHAMANDO CALLBACK ===")
                
                // ✅ NOVO: Chamar callback para navegação imediata
                onUserDeleted?.invoke()

            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao excluir funcionário: ${e.message}")
                e.printStackTrace()
                
                // Mesmo com erro, marcar como exclusão para mostrar feedback
                wasUserDeleted.value = true
                showSuccessScreen.value = true
                android.util.Log.d("AddFaceScreenViewModel", "🔘 Erro - mas definindo wasUserDeleted = true")
            } finally {
                isDeletingUser.value = false
                android.util.Log.d("AddFaceScreenViewModel", "🔘 Estado final - wasUserDeleted: ${wasUserDeleted.value}, showSuccessScreen: ${showSuccessScreen.value}")
            }
        }
    }

    fun sincronizarFaceComServidor() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val funcionariosDao = com.ml.shubham0204.facenet_android.data.FuncionariosDao()
                val funcionario = funcionariosDao.getById(funcionarioId)
                
                if (funcionario != null) {
                    val cpf = funcionario.cpf
                    val entidadeId = getEntidadeId()

                    if (entidadeId != null) {
                        android.util.Log.d("AddFaceScreenViewModel", "🔄 Iniciando sincronização com servidor...")
                        android.util.Log.d("AddFaceScreenViewModel", "🌐 URL: https://api.rh247.com.br/$entidadeId/ponto/funcionarios/foto-tablet")
                        android.util.Log.d("AddFaceScreenViewModel", "👤 CPF: $cpf")
                        
                        val response = apiService.obterFaceOnline(
                            entidade = entidadeId,
                            numero_cpf = cpf
                        )
                        
                        if (response.isSuccessful) {
                            val faceDataList = response.body()
                            if (faceDataList != null && faceDataList.isNotEmpty()) {
                                val faceData = faceDataList.first() // Pega o primeiro item da lista
                                android.util.Log.d("AddFaceScreenViewModel", "✅ Sincronização bem-sucedida!")
                                android.util.Log.d("AddFaceScreenViewModel", "📊 Face ID: ${faceData.id}")
                                android.util.Log.d("AddFaceScreenViewModel", "👤 Funcionário ID: ${faceData.funcionario_id}")
                                android.util.Log.d("AddFaceScreenViewModel", "🖼️ Imagem 1: ${faceData.imagem_1}")
                                android.util.Log.d("AddFaceScreenViewModel", "🖼️ Imagem 2: ${faceData.imagem_2}")
                                android.util.Log.d("AddFaceScreenViewModel", "🖼️ Imagem 3: ${faceData.imagem_3}")
                                android.util.Log.d("AddFaceScreenViewModel", "🧠 Embedding: ${faceData.embedding.take(50)}...")
                                

                            } else {
                                android.util.Log.w("AddFaceScreenViewModel", "⚠️ Nenhuma face encontrada no servidor para este CPF")
                            }
                        } else {
                            android.util.Log.w("AddFaceScreenViewModel", "⚠️ Erro HTTP: ${response.code()} - ${response.message()}")
                        }
                    } else {
                        android.util.Log.w("AddFaceScreenViewModel", "⚠️ Entidade ID não configurada para sincronização")
                    }
                    android.util.Log.d("AddFaceScreenViewModel", "🔄 Sincronizando face do funcionário")
                    android.util.Log.d("AddFaceScreenViewModel", "👤 Nome: ${funcionario.nome}")
                    android.util.Log.d("AddFaceScreenViewModel", "🆔 CPF: $cpf")
                    android.util.Log.d("AddFaceScreenViewModel", "🆔 Funcionário ID: $funcionarioId")
                    

                } else {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Funcionário não encontrado para ID: $funcionarioId")
                }
                
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("AddFaceScreenViewModel", "🌐 ERRO DE CONECTIVIDADE:")
                android.util.Log.e("AddFaceScreenViewModel", "   - Verifique sua conexão com a internet")
                android.util.Log.e("AddFaceScreenViewModel", "   - Verifique se o servidor api.rh247.com.br está online")
                android.util.Log.e("AddFaceScreenViewModel", "   - Verifique configurações de DNS/proxy")
                android.util.Log.e("AddFaceScreenViewModel", "   - Erro: ${e.message}")
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("AddFaceScreenViewModel", "⏰ TIMEOUT DE CONEXÃO:")
                android.util.Log.e("AddFaceScreenViewModel", "   - Servidor demorou muito para responder")
                android.util.Log.e("AddFaceScreenViewModel", "   - Verifique a qualidade da conexão")
                android.util.Log.e("AddFaceScreenViewModel", "   - Erro: ${e.message}")
            } catch (e: java.net.ConnectException) {
                android.util.Log.e("AddFaceScreenViewModel", "🔌 ERRO DE CONEXÃO:")
                android.util.Log.e("AddFaceScreenViewModel", "   - Não foi possível conectar ao servidor")
                android.util.Log.e("AddFaceScreenViewModel", "   - Verifique se o servidor está online")
                android.util.Log.e("AddFaceScreenViewModel", "   - Erro: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro inesperado ao sincronizar face: ${e.message}")
                android.util.Log.e("AddFaceScreenViewModel", "   - Tipo: ${e.javaClass.simpleName}")
                e.printStackTrace()
            }
        }
    }

    fun cancelDeleteUser() {
        showDeleteConfirmation.value = false
    }
    
    // ✅ NOVO: Função para resetar o estado de exclusão
    fun resetDeletionState() {
        wasUserDeleted.value = false
        showSuccessScreen.value = false
        isDeletingUser.value = false
    }
    
    // ✅ NOVO: Função para deletar fotos do servidor
    fun deleteFacesFromServer(cpf: String, entidadeId: String) {
        if (cpf.isEmpty() || entidadeId.isEmpty()) {
            android.util.Log.w("AddFaceScreenViewModel", "⚠️ CPF ou EntidadeId vazio - não deletando fotos do servidor")
            return
        }
        
        // Verificar se há internet antes de chamar a API
        val hasInternet = ConnectivityUtils.isInternetAvailable(context)
        if (!hasInternet) {
            android.util.Log.w("AddFaceScreenViewModel", "⚠️ Sem conexão com internet - não deletando fotos do servidor")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ Deletando fotos do servidor para CPF: $cpf")
                android.util.Log.d("AddFaceScreenViewModel", "🌐 Chamando API: DELETE /$entidadeId/tablet/funcionarios/deletar-face?numero_cpf=$cpf")
                
                val response = apiService.deletarFace(entidadeId, cpf)
                
                if (response.isSuccessful) {
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Fotos deletadas do servidor com sucesso")
                } else {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Erro ao deletar fotos do servidor: ${response.code()}")
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Mensagem: ${response.message()}")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao deletar fotos do servidor: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // ✅ NOVO: Função para buscar fotos capturadas do servidor
    fun loadCapturedImages(cpf: String, entidadeId: String) {
        if (cpf.isEmpty() || entidadeId.isEmpty()) {
            android.util.Log.w("AddFaceScreenViewModel", "⚠️ CPF ou EntidadeId vazio - não carregando fotos")
            return
        }
        
        isLoadingImages.value = true
        capturedImagesUrls.value = emptyList()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("AddFaceScreenViewModel", "📸 Carregando fotos capturadas para CPF: $cpf")
                
                val response = apiService.obterImagesFaces(entidadeId, cpf)
                
                if (response.isSuccessful && response.body()?.fotos != null) {
                    val imagesData = response.body()!!.fotos!!
                    android.util.Log.d("AddFaceScreenViewModel", "📸 Dados de fotos recebidos - ID: ${imagesData.id}, Funcionário: ${imagesData.funcionario_id}")
                    
                    val imageUrls = mutableListOf<String>()
                    
                    // Adicionar as 3 fotos se existirem
                    if (imagesData.image_1.isNotEmpty()) {
                        imageUrls.add(imagesData.image_1)
                        android.util.Log.d("AddFaceScreenViewModel", "📸 Foto 1: ${imagesData.image_1}")
                    }
                    if (imagesData.image_2.isNotEmpty()) {
                        imageUrls.add(imagesData.image_2)
                        android.util.Log.d("AddFaceScreenViewModel", "📸 Foto 2: ${imagesData.image_2}")
                    }
                    if (imagesData.image_3.isNotEmpty()) {
                        imageUrls.add(imagesData.image_3)
                        android.util.Log.d("AddFaceScreenViewModel", "📸 Foto 3: ${imagesData.image_3}")
                    }
                    
                    withContext(Dispatchers.Main) {
                        capturedImagesUrls.value = imageUrls
                        android.util.Log.d("AddFaceScreenViewModel", "✅ ${imageUrls.size} fotos carregadas com sucesso")
                    }
                } else {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Resposta da API não foi bem-sucedida: ${response.code()}")
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Status: ${response.body()?.status}")
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Fotos: ${response.body()?.fotos}")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao carregar fotos: ${e.message}")
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isLoadingImages.value = false
                }
            }
        }
    }
}

data class DuplicateFaceInfo(
    val existingPersonName: String,
    val similarity: Float
)
