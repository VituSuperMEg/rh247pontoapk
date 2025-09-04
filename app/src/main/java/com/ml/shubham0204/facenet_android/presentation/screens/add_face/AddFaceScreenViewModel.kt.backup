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
    
    // ✅ NOVO: Função para limpar URIs das imagens
    fun clearSelectedImageURIs() {
        selectedImageURIs.value = emptyList()
        android.util.Log.d("AddFaceScreenViewModel", "🧹 URIs das imagens limpas")
    }
    
    // Variável para armazenar o arquivo temporário da foto atual
    private var currentPhotoFile: File? = null
    private var currentFaceBitmap: Bitmap? = null
    private var faceDetectionOverlay: Any? = null // Será o FaceDetectionOverlay
    
    fun updatePersonName(name: String) {
        personNameState.value = name
    }
    
    fun setCurrentPhotoFile(file: File) {
        currentPhotoFile = file
    }
    
    fun addCapturedImage() {
        currentPhotoFile?.let { file ->
            val uri = Uri.fromFile(file)
            val currentList = selectedImageURIs.value.toMutableList()
            currentList.add(uri)
            selectedImageURIs.value = currentList
            currentPhotoFile = null
        }
    }
    
    // Nova função para capturar foto da câmera integrada
    fun capturePhotoFromCamera() {
        // Capturar o frame atual da câmera
        currentFaceBitmap?.let { bitmap ->
            // Converter bitmap para URI temporário
            val tempFile = createTempFileFromBitmap(bitmap)
            if (tempFile != null) {
                val uri = Uri.fromFile(tempFile)
                val currentList = selectedImageURIs.value.toMutableList()
                currentList.add(uri)
                selectedImageURIs.value = currentList
            }
        }
    }
    
    // Função para definir o FaceDetectionOverlay
    fun setFaceDetectionOverlay(overlay: Any) {
        faceDetectionOverlay = overlay
    }
    
    // Função para capturar o frame atual
    fun captureCurrentFrame() {
        // Usar reflection para acessar o método getCurrentFrameBitmap
        try {
            val overlay = faceDetectionOverlay
            if (overlay != null) {
                val method = overlay.javaClass.getMethod("getCurrentFrameBitmap")
                val bitmap = method.invoke(overlay) as? Bitmap
                currentFaceBitmap = bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun createTempFileFromBitmap(bitmap: Bitmap): File? {
        return try {
            val tempFile = File.createTempFile("capture_", ".jpg")
            val outputStream = tempFile.outputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ✅ NOVO: Função para verificar se funcionário está ativo
    suspend fun isFuncionarioActive(): Boolean {
        return personUseCase.isFuncionarioActive(funcionarioId)
    }
    
    // ✅ NOVO: Função para verificar se pode gerenciar facial
    suspend fun canManageFacial(): Boolean {
        return personUseCase.canManageFacial(funcionarioId)
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
                    
                    android.util.Log.d("AddFaceScreenViewModel", "🎉 === RECADASTRO CONCLUÍDO ===")
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
                    
                } else {
                    android.util.Log.d("AddFaceScreenViewModel", "🆕 === PRIMEIRO CADASTRO ===")
                    android.util.Log.d("AddFaceScreenViewModel", "🆕 Nenhuma pessoa encontrada para este funcionário")
                    
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
                    
                    // ✅ NOVO: Salvar cada imagem
                    selectedImageURIs.value.forEachIndexed { index, uri ->
                        android.util.Log.d("AddFaceScreenViewModel", "📸 Processando foto ${index + 1}: $uri")
                        
                        try {
                            imageVectorUseCase
                                .addImage(personId, personNameState.value, uri)
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
                    
                    android.util.Log.d("AddFaceScreenViewModel", "🎉 === PRIMEIRO CADASTRO CONCLUÍDO ===")
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
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro geral no salvamento: ${e.message}")
                e.printStackTrace()
            } finally {
                isProcessingImages.value = false
            }
        }
    }
    
    // ✅ NOVO: Função para excluir usuário e suas faces
    fun deleteUserAndFaces() {
        android.util.Log.d("AddFaceScreenViewModel", "🗑️ === INICIANDO EXCLUSÃO DE USUÁRIO ===")
        android.util.Log.d("AddFaceScreenViewModel", "🆔 FuncionarioId: $funcionarioId")
        android.util.Log.d("AddFaceScreenViewModel", "📝 Nome: ${personNameState.value}")
        
        isDeletingUser.value = true
        
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Buscar a pessoa no banco
                val existingPerson = personUseCase.getPersonByFuncionarioId(funcionarioId)
                
                if (existingPerson != null) {
                    android.util.Log.d("AddFaceScreenViewModel", "✅ === PESSOA ENCONTRADA PARA EXCLUSÃO ===")
                    android.util.Log.d("AddFaceScreenViewModel", "   - Person ID: ${existingPerson.personID}")
                    android.util.Log.d("AddFaceScreenViewModel", "   - Nome: ${existingPerson.personName}")
                    android.util.Log.d("AddFaceScreenViewModel", "   - Faces: ${existingPerson.numImages}")
                    
                    // Buscar todas as faces da pessoa
                    val faceImages = imageVectorUseCase.getImagesByPersonID(existingPerson.personID)
                    android.util.Log.d("AddFaceScreenViewModel", "📸 Total de faces encontradas: ${faceImages.size}")
                    
                    // Remover todas as faces
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo faces...")
                    imageVectorUseCase.removeImages(existingPerson.personID)
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Faces removidas")
                    
                    // Remover a pessoa
                    android.util.Log.d("AddFaceScreenViewModel", "🗑️ Removendo pessoa...")
                    personUseCase.removePerson(existingPerson.personID)
                    android.util.Log.d("AddFaceScreenViewModel", "✅ Pessoa removida")
                    
                    // Verificar se foi removida
                    val personAfterDeletion = personUseCase.getPersonByFuncionarioId(funcionarioId)
                    if (personAfterDeletion == null) {
                        android.util.Log.d("AddFaceScreenViewModel", "✅ === EXCLUSÃO CONCLUÍDA COM SUCESSO ===")
                        android.util.Log.d("AddFaceScreenViewModel", "📊 Total de pessoas no banco: ${personUseCase.getCount()}")
                        
                        // Limpar dados locais
                        clearSelectedImageURIs()
                        personNameState.value = ""
                        
                        // Mostrar tela de sucesso da exclusão
                        showSuccessScreen.value = true
                    } else {
                        android.util.Log.e("AddFaceScreenViewModel", "❌ ERRO: Pessoa ainda existe após exclusão!")
                    }
                    
                } else {
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ Nenhuma pessoa encontrada para exclusão")
                    android.util.Log.w("AddFaceScreenViewModel", "⚠️ FuncionarioId: $funcionarioId")
                    
                    // Mesmo sem pessoa no banco, limpar dados locais
                    clearSelectedImageURIs()
                    personNameState.value = ""
                    
                    // Mostrar tela de sucesso da exclusão
                    showSuccessScreen.value = true
                }
                
                // ✅ NOVO: Resetar o estado de exclusão após mostrar a tela de sucesso
                isDeletingUser.value = false
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro na exclusão: ${e.message}")
                e.printStackTrace()
            } finally {
                // ✅ CORRIGIDO: Não resetar isDeletingUser aqui, pois pode estar mostrando a tela de sucesso
                showDeleteConfirmation.value = false
            }
        }
    }
    
    // ✅ NOVO: Função para mostrar diálogo de confirmação
    fun showDeleteConfirmationDialog() {
        showDeleteConfirmation.value = true
    }
    
    // ✅ NOVO: Função para cancelar exclusão
    fun cancelDelete() {
        showDeleteConfirmation.value = false
    }
}
