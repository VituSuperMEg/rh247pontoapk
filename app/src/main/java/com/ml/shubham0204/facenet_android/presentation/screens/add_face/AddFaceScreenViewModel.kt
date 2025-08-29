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
    
    fun addImages() {
        android.util.Log.d("AddFaceScreenViewModel", "🚀 === INICIANDO SALVAMENTO DE FACES ===")
        android.util.Log.d("AddFaceScreenViewModel", "📝 Nome da pessoa: ${personNameState.value}")
        android.util.Log.d("AddFaceScreenViewModel", "🆔 FuncionarioId: $funcionarioId")
        android.util.Log.d("AddFaceScreenViewModel", "📸 Total de fotos: ${selectedImageURIs.value.size}")
        
        // ✅ NOVO: Verificar se há fotos para salvar
        if (selectedImageURIs.value.isEmpty()) {
            android.util.Log.e("AddFaceScreenViewModel", "❌ NENHUMA FOTO PARA SALVAR!")
            return
        }
        
        // ✅ NOVO: Verificar se o nome está preenchido
        if (personNameState.value.isEmpty()) {
            android.util.Log.e("AddFaceScreenViewModel", "❌ NOME DA PESSOA NÃO PREENCHIDO!")
            return
        }
        
        isProcessingImages.value = true
        showSuccessScreen.value = false
        numImagesProcessed.value = 0 // ✅ NOVO: Resetar contador
        
        CoroutineScope(Dispatchers.Default).launch {
            try {
                android.util.Log.d("AddFaceScreenViewModel", "🔄 Iniciando salvamento no banco...")
                
                // ✅ NOVO: Verificar se o personUseCase está funcionando
                android.util.Log.d("AddFaceScreenViewModel", "🔍 Verificando personUseCase...")
                
                // ✅ NOVO: Salvar pessoa no banco FaceNet
                val personId = personUseCase.addPerson(
                    personNameState.value,
                    selectedImageURIs.value.size.toLong(),
                    funcionarioId // ✅ NOVO: Passar o funcionarioId
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
                
                android.util.Log.d("AddFaceScreenViewModel", "🎉 === SALVAMENTO CONCLUÍDO ===")
                android.util.Log.d("AddFaceScreenViewModel", "📊 Total de fotos processadas: ${numImagesProcessed.value}")
                
                // ✅ NOVO: Verificar se todas as fotos foram processadas
                if (numImagesProcessed.value == selectedImageURIs.value.size) {
                    android.util.Log.d("AddFaceScreenViewModel", "✅ TODAS AS FOTOS FORAM SALVAS COM SUCESSO!")
                    
                    // ✅ NOVO: Verificar novamente o total de pessoas
                    val totalFinal = personUseCase.getCount()
                    android.util.Log.d("AddFaceScreenViewModel", "📊 Total final de pessoas no banco: $totalFinal")
                    
                    showSuccessScreen.value = true
                } else {
                    android.util.Log.e("AddFaceScreenViewModel", "❌ ERRO: Nem todas as fotos foram processadas!")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AddFaceScreenViewModel", "❌ Erro geral no salvamento: ${e.message}")
                e.printStackTrace()
            } finally {
                isProcessingImages.value = false
            }
        }
    }
}
