package com.ml.shubham0204.facenet_android.presentation.screens.add_face

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ml.shubham0204.facenet_android.domain.AppException
import com.ml.shubham0204.facenet_android.domain.ImageVectorUseCase
import com.ml.shubham0204.facenet_android.domain.PersonUseCase
import com.ml.shubham0204.facenet_android.presentation.components.setProgressDialogText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class AddFaceScreenViewModel(
    private val personUseCase: PersonUseCase,
    private val imageVectorUseCase: ImageVectorUseCase,
) : ViewModel() {
    val personNameState: MutableState<String> = mutableStateOf("")
    val selectedImageURIs: MutableState<List<Uri>> = mutableStateOf(emptyList())

    val isProcessingImages: MutableState<Boolean> = mutableStateOf(false)
    val numImagesProcessed: MutableState<Int> = mutableIntStateOf(0)
    val showSuccessScreen: MutableState<Boolean> = mutableStateOf(false)
    
    // ✅ NOVO: Estados para controle da exclusão
    val isDeletingUser: MutableState<Boolean> = mutableStateOf(false)
    val showDeleteConfirmation: MutableState<Boolean> = mutableStateOf(false)
    
    // ✅ NOVO: Estados para validação de face duplicada
    val showDuplicateFaceDialog: MutableState<Boolean> = mutableStateOf(false)
    val duplicateFaceInfo: MutableState<DuplicateFaceInfo?> = mutableStateOf(null)
    
    // ✅ NOVO: Adicionar funcionarioId para conectar com o banco de funcionários
    var funcionarioId: Long = 0
    
    // ✅ NOVO: Função para atualizar status da detecção
    fun setFaceDetectionStatus(status: String) {
        android.util.Log.d("AddFaceScreenViewModel", "📱 Status: $status")
    }
    
    // ✅ NOVO: Função para adicionar URI de imagem
    fun addSelectedImageURI(uri: Uri) {
        val currentList = selectedImageURIs.value.toMutableList()
        currentList.add(uri)
        selectedImageURIs.value = currentList
        android.util.Log.d("AddFaceScreenViewModel", "📸 URI adicionada: $uri")
        android.util.Log.d("AddFaceScreenViewModel", "📊 Total de URIs: ${selectedImageURIs.value.size}")
    }
    
    // ✅ NOVO: Função para limpar URIs selecionadas
    fun clearSelectedImageURIs() {
        selectedImageURIs.value = emptyList()
        android.util.Log.d("AddFaceScreenViewModel", "🗑️ URIs limpas")
    }
    
    // ✅ NOVO: Função para atualizar nome da pessoa
    fun updatePersonName(name: String) {
        personNameState.value = name
        android.util.Log.d("AddFaceScreenViewModel", "📝 Nome atualizado: $name")
    }
    
    // ✅ NOVO: Função para verificar se pode gerenciar facial
    suspend fun canManageFacial(): Boolean {
        if (funcionarioId <= 0) {
            android.util.Log.w("AddFaceScreenViewModel", "⚠️ FuncionarioId inválido: $funcionarioId")
            return false
        }
        return personUseCase.canManageFacial(funcionarioId)
    }
    
    // ✅ NOVO: Função para verificar se uma face já existe no sistema
    suspend fun validateFaceNotDuplicate(imageUri: Uri, currentPersonID: Long? = null): Boolean {
        return try {
            android.util.Log.d("AddFaceScreenViewModel", "🔍 Validando se face já existe no sistema...")
            
            val result = imageVectorUseCase.checkIfFaceAlreadyExists(imageUri, currentPersonID)
            
            if (result.isSuccess) {
                val faceCheckResult = result.getOrNull()!!
                
                if (faceCheckResult.exists) {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Face já existe no sistema!")
                    android.util.Log.w("AddFaceScreenViewModel", "   - Pessoa existente: ${faceCheckResult.existingFace?.personName}")
                    android.util.Log.w("AddFaceScreenViewModel", "   - Similaridade: ${faceCheckResult.similarity}")
                    
                    // Mostrar diálogo de face duplicada
                    duplicateFaceInfo.value = DuplicateFaceInfo(
                        existingPersonName = faceCheckResult.existingFace?.personName ?: "Desconhecido",
                        similarity = faceCheckResult.similarity
                    )
                    showDuplicateFaceDialog.value = true
                    
                    return false
                } else {
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Face é única - pode cadastrar")
                    return true
                }
            } else {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao verificar face duplicada: ${result.exceptionOrNull()?.message}")
                return true // Em caso de erro, permitir cadastro
            }
        } catch (e: Exception) {
            android.util.Log.e("AddFaceScreenViewModel", "❌ Erro na validação de face duplicada: ${e.message}")
            e.printStackTrace()
            return true // Em caso de erro, permitir cadastro
        }
    }
    
    // ✅ NOVO: Função para confirmar cadastro mesmo com face duplicada
    fun confirmDuplicateFaceRegistration() {
        showDuplicateFaceDialog.value = false
        duplicateFaceInfo.value = null
        // Continuar com o cadastro
        saveFacesInternal()
    }
    
    // ✅ NOVO: Função para cancelar cadastro por face duplicada
    fun cancelDuplicateFaceRegistration() {
        showDuplicateFaceDialog.value = false
        duplicateFaceInfo.value = null
        isProcessingImages.value = false
    }
    
    // ✅ NOVO: Função para sincronizar com o servidor
    private fun syncWithServer(funcionarioId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                
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
                // ✅ NOVO: Verificar se funcionário está ativo antes de permitir operações de facial
                if (!canManageFacial()) {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Funcionário inativo - operação de facial não permitida")
                    setProgressDialogText("Funcionário inativo - operação não permitida")
                    delay(2000) // Mostrar mensagem por 2 segundos
                    isProcessingImages.value = false
                    return@launch
                }
                
                // ✅ NOVO: Validar se as faces não são duplicadas
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
    
    // ✅ NOVO: Função para confirmar exclusão
    fun confirmDeleteUser() {
        showDeleteConfirmation.value = false
        isDeletingUser.value = true
        
        CoroutineScope(Dispatchers.Default).launch {
            try {
                android.util.Log.d("AddFaceScreenViewModel", "🗑️ Iniciando exclusão do usuário...")
                
                val existingPerson = personUseCase.getPersonByFuncionarioId(funcionarioId)
                
                if (existingPerson != null) {
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo faces do banco...")
                    imageVectorUseCase.removeImages(existingPerson.personID)
                    
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo pessoa do banco...")
                    personUseCase.removePerson(existingPerson.personID)
                    
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Usuário excluído com sucesso!")
                    
                    // Limpar URIs
                    clearSelectedImageURIs()
                    
                    showSuccessScreen.value = true
                } else {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Nenhuma pessoa encontrada para exclusão")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro ao excluir usuário: ${e.message}")
                e.printStackTrace()
            } finally {
                isDeletingUser.value = false
            }
        }
    }
    
    // ✅ NOVO: Função para cancelar exclusão
    fun cancelDeleteUser() {
        showDeleteConfirmation.value = false
    }
}

// ✅ NOVO: Classe para informações de face duplicada
data class DuplicateFaceInfo(
    val existingPersonName: String,
    val similarity: Float
)
