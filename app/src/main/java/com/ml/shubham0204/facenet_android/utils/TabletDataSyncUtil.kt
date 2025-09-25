package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.ImagesVectorDB
import com.ml.shubham0204.facenet_android.data.PersonDB
import com.ml.shubham0204.facenet_android.data.config.ServerConfig
import com.ml.shubham0204.facenet_android.data.api.ApiService
import com.ml.shubham0204.facenet_android.data.api.TabletDataRequest
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.ArrayDeque

/**
 * Utilitário para sincronizar dados dos funcionários e faces com o backend
 */
class TabletDataSyncUtil(private val context: Context) {
    
    private val funcionariosDao = FuncionariosDao()
    private val personDB = PersonDB()
    private val imagesVectorDB = ImagesVectorDB()
    private val appPreferences = AppPreferences(context)
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(ServerConfig.BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val apiService = retrofit.create(ApiService::class.java)
    
    /**
     * Sincroniza dados de um funcionário específico com o backend
     */
    suspend fun syncSingleFuncionario(funcionarioId: Long): SyncResult = withContext(Dispatchers.IO) {
        try {
            Log.d("TabletDataSyncUtil", "🚀 Sincronizando funcionário específico: $funcionarioId")
            
            // Buscar o funcionário específico
            val funcionario = funcionariosDao.getById(funcionarioId)
            if (funcionario == null) {
                Log.w("TabletDataSyncUtil", "⚠️ Funcionário não encontrado: $funcionarioId")
                return@withContext SyncResult(
                    success = false,
                    successCount = 0,
                    errorCount = 1,
                    errors = listOf("Funcionário não encontrado: $funcionarioId")
                )
            }
            
            Log.d("TabletDataSyncUtil", "🔄 Sincronizando: ${funcionario.nome} (ID: ${funcionario.id})")
            
            // Buscar pessoa associada ao funcionário
            val person = personDB.getPersonByFuncionarioId(funcionario.id)
            if (person == null) {
                Log.w("TabletDataSyncUtil", "⚠️ Nenhuma face cadastrada para: ${funcionario.nome}")
                return@withContext SyncResult(
                    success = false,
                    successCount = 0,
                    errorCount = 1,
                    errors = listOf("Nenhuma face cadastrada para: ${funcionario.nome}")
                )
            }
            
            Log.d("TabletDataSyncUtil", "✅ Pessoa encontrada: ${person.personName} (personID: ${person.personID})")
            
            // Buscar faces da pessoa
            val faceImages = imagesVectorDB.getFaceImagesByPersonID(person.personID)
            if (faceImages.isEmpty()) {
                Log.w("TabletDataSyncUtil", "⚠️ Nenhuma face encontrada para: ${funcionario.nome}")
                return@withContext SyncResult(
                    success = false,
                    successCount = 0,
                    errorCount = 1,
                    errors = listOf("Nenhuma face encontrada para: ${funcionario.nome}")
                )
            }
            
            Log.d("TabletDataSyncUtil", "📸 Faces encontradas para ${funcionario.nome}: ${faceImages.size}")
            faceImages.forEachIndexed { faceIndex, faceImage ->
                Log.d("TabletDataSyncUtil", "📸 Face $faceIndex: ${faceImage.personName} (caminho: ${faceImage.originalImagePath})")
            }
            
            // Sincronizar dados do funcionário
            val result = syncFuncionarioData(funcionario, faceImages)
            if (result.success) {
                Log.d("TabletDataSyncUtil", "✅ Sincronizado com sucesso: ${funcionario.nome}")
                SyncResult(success = true, successCount = 1, errorCount = 0, errors = emptyList())
            } else {
                val errorMsg = result.errors.firstOrNull() ?: "Erro desconhecido"
                Log.e("TabletDataSyncUtil", "❌ Erro ao sincronizar ${funcionario.nome}: $errorMsg")
                SyncResult(success = false, successCount = 0, errorCount = 1, errors = listOf("${funcionario.nome}: $errorMsg"))
            }
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro ao sincronizar funcionário $funcionarioId: ${e.message}")
            SyncResult(
                success = false,
                successCount = 0,
                errorCount = 1,
                errors = listOf("Erro ao sincronizar: ${e.message}")
            )
        }
    }
    
    /**
     * Sincroniza todos os dados dos funcionários e faces com o backend
     */
    suspend fun syncAllDataWithBackend(): SyncResult = withContext(Dispatchers.IO) {
        try {
            Log.d("TabletDataSyncUtil", "🚀 Iniciando sincronização de dados com backend...")
            
            // Buscar todos os funcionários
            val funcionarios = funcionariosDao.getAll()
            Log.d("TabletDataSyncUtil", "📊 Total de funcionários encontrados: ${funcionarios.size}")
            
            var successCount = 0
            var errorCount = 0
            val errors = mutableListOf<String>()
            
            // Processar cada funcionário
            funcionarios.forEachIndexed { index, funcionario ->
                try {
                    Log.d("TabletDataSyncUtil", "🔄 === PROCESSANDO FUNCIONÁRIO ${index + 1}/${funcionarios.size} ===")
                    Log.d("TabletDataSyncUtil", "🔄 Funcionário: ${funcionario.nome} (ID: ${funcionario.id})")
                    
                    // Buscar pessoa associada ao funcionário
                    val person = personDB.getPersonByFuncionarioId(funcionario.id)
                    if (person == null) {
                        Log.w("TabletDataSyncUtil", "⚠️ Nenhuma face cadastrada para: ${funcionario.nome}")
                        return@forEachIndexed
                    }
                    
                    Log.d("TabletDataSyncUtil", "✅ Pessoa encontrada: ${person.personName} (personID: ${person.personID})")
                    
                    // Buscar faces da pessoa
                    val faceImages = imagesVectorDB.getFaceImagesByPersonID(person.personID)
                    if (faceImages.isEmpty()) {
                        Log.w("TabletDataSyncUtil", "⚠️ Nenhuma face encontrada para: ${funcionario.nome}")
                        return@forEachIndexed
                    }
                    
                    Log.d("TabletDataSyncUtil", "📸 Faces encontradas para ${funcionario.nome}: ${faceImages.size}")
                    faceImages.forEachIndexed { faceIndex, faceImage ->
                        Log.d("TabletDataSyncUtil", "📸 Face $faceIndex: ${faceImage.personName} (caminho: ${faceImage.originalImagePath})")
                    }
                    
                    // Sincronizar dados do funcionário
                    val result = syncFuncionarioData(funcionario, faceImages)
                    if (result.success) {
                        successCount++
                        Log.d("TabletDataSyncUtil", "✅ Sincronizado com sucesso: ${funcionario.nome}")
                    } else {
                        errorCount++
                        val errorMsg = result.errors.firstOrNull() ?: "Erro desconhecido"
                        errors.add("${funcionario.nome}: $errorMsg")
                        Log.e("TabletDataSyncUtil", "❌ Erro ao sincronizar ${funcionario.nome}: $errorMsg")
                    }
                    
                } catch (e: Exception) {
                    errorCount++
                    errors.add("${funcionario.nome}: ${e.message}")
                    Log.e("TabletDataSyncUtil", "❌ Erro ao processar ${funcionario.nome}: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            Log.d("TabletDataSyncUtil", "🎉 Sincronização concluída: $successCount sucessos, $errorCount erros")
            
            SyncResult(
                success = errorCount == 0,
                successCount = successCount,
                errorCount = errorCount,
                errors = errors
            )
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro geral na sincronização: ${e.message}")
            SyncResult(
                success = false,
                successCount = 0,
                errorCount = 1,
                errors = listOf("Erro geral: ${e.message}")
            )
        }
    }
    
    /**
     * Sincroniza dados de um funcionário específico
     */
    private suspend fun syncFuncionarioData(
        funcionario: com.ml.shubham0204.facenet_android.data.FuncionariosEntity,
        faceImages: List<com.ml.shubham0204.facenet_android.data.FaceImageRecord>
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            // Preparar dados para envio
            val requestData = prepareSyncData(funcionario, faceImages)
            
            // Fazer requisição para o backend
            val response = sendDataToBackend(requestData)
            
            if (response.isSuccessful) {
                Log.d("TabletDataSyncUtil", "✅ Dados enviados com sucesso para: ${funcionario.nome}")
                SyncResult(success = true, successCount = 1, errorCount = 0, errors = emptyList())
            } else {
                // Capturar o corpo da resposta de erro
                val errorBody = response.errorBody()?.string() ?: "Sem detalhes do erro"
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                val fullErrorMsg = "$errorMsg - Detalhes: $errorBody"
                
                Log.e("TabletDataSyncUtil", "❌ Erro HTTP ao enviar dados: $errorMsg")
                Log.e("TabletDataSyncUtil", "❌ Corpo do erro: $errorBody")
                
                SyncResult(success = false, successCount = 0, errorCount = 1, errors = listOf(fullErrorMsg))
            }
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro ao sincronizar dados: ${e.message}")
            SyncResult(success = false, successCount = 0, errorCount = 1, errors = listOf(e.message ?: "Erro desconhecido"))
        }
    }
    
    /**
     * Prepara os dados para sincronização
     */
    private suspend fun prepareSyncData(
        funcionario: com.ml.shubham0204.facenet_android.data.FuncionariosEntity,
        faceImages: List<com.ml.shubham0204.facenet_android.data.FaceImageRecord>
    ): TabletDataRequest = withContext(Dispatchers.IO) {
        // Converter embeddings para string (usar o primeiro embedding como representativo)
        val faceEmbedding = if (faceImages.isNotEmpty()) {
            faceImages.first().faceEmbedding.joinToString(",")
        } else {
            ""
        }
        
        // ✅ NOVO: Buscar imagens reais do cacheDir e converter para base64
        val realImages = getRealImagesFromCache(funcionario, faceImages)
        
        // Validar e preparar as imagens para o servidor
        // Se não encontrar fotos específicas, usar as 3 fotos mais antigas disponíveis
        val image1 = if (realImages.isNotEmpty() && realImages[0].isNotEmpty() && realImages[0].length > 100) {
            val base64 = realImages[0]
            Log.d("TabletDataSyncUtil", "📸 Image_1: ${base64.length} chars, prefixo: ${base64.take(20)}")
            "data:image/jpeg;base64," + base64
        } else {
            Log.w("TabletDataSyncUtil", "⚠️ Nenhuma foto específica encontrada para ${funcionario.nome} - usando foto genérica para image_1")
            val genericBase64 = getGenericImageBase64()
            "data:image/jpeg;base64," + genericBase64
        }
        
        val image2 = if (realImages.size > 1 && realImages[1].isNotEmpty() && realImages[1].length > 100) {
            val base64 = realImages[1]
            Log.d("TabletDataSyncUtil", "📸 Image_2: ${base64.length} chars, prefixo: ${base64.take(20)}")
            "data:image/jpeg;base64," + base64
        } else {
            Log.w("TabletDataSyncUtil", "⚠️ Nenhuma foto específica encontrada para ${funcionario.nome} - usando foto genérica para image_2")
            val genericBase64 = getGenericImageBase64()
            "data:image/jpeg;base64," + genericBase64
        }
        
        val image3 = if (realImages.size > 2 && realImages[2].isNotEmpty() && realImages[2].length > 100) {
            val base64 = realImages[2]
            Log.d("TabletDataSyncUtil", "📸 Image_3: ${base64.length} chars, prefixo: ${base64.take(20)}")
            "data:image/jpeg;base64," + base64
        } else {
            Log.w("TabletDataSyncUtil", "⚠️ Nenhuma foto específica encontrada para ${funcionario.nome} - usando foto genérica para image_3")
            val genericBase64 = getGenericImageBase64()
            "data:image/jpeg;base64," + genericBase64
        }
        
        val request = TabletDataRequest(
            numero_cpf = funcionario.cpf,
            face = faceEmbedding,
            image_1 = image1,
            image_2 = image2,
            image_3 = image3
        )
        
        // Log final para debug
        Log.d("TabletDataSyncUtil", "📤 Enviando para servidor:")
        Log.d("TabletDataSyncUtil", "👤 Funcionário: ${funcionario.nome} (ID: ${funcionario.id})")
        Log.d("TabletDataSyncUtil", "👤 CPF: ${funcionario.cpf}")
        Log.d("TabletDataSyncUtil", "📸 Fotos encontradas: ${realImages.size}")
        Log.d("TabletDataSyncUtil", "📸 Image_1 length: ${image1.length}")
        Log.d("TabletDataSyncUtil", "📸 Image_2 length: ${image2.length}")
        Log.d("TabletDataSyncUtil", "📸 Image_3 length: ${image3.length}")
        Log.d("TabletDataSyncUtil", "🔍 Image_1 prefix: ${image1.take(50)}")
        Log.d("TabletDataSyncUtil", "🔍 Image_2 prefix: ${image2.take(50)}")
        Log.d("TabletDataSyncUtil", "🔍 Image_3 prefix: ${image3.take(50)}")
        
        Log.d("TabletDataSyncUtil", "📦 Dados preparados para ${funcionario.nome}:")
        Log.d("TabletDataSyncUtil", "   - CPF: ${funcionario.cpf}")
        Log.d("TabletDataSyncUtil", "   - Faces: ${faceImages.size}")
        Log.d("TabletDataSyncUtil", "   - Embedding size: ${faceEmbedding.length}")
        Log.d("TabletDataSyncUtil", "   - Imagens reais encontradas: ${realImages.size}")
        Log.d("TabletDataSyncUtil", "   - Image 1: ${if (realImages.isNotEmpty()) "REAL" else "VAZIA"}")
        Log.d("TabletDataSyncUtil", "   - Image 2: ${if (realImages.size > 1) "REAL" else "VAZIA"}")
        Log.d("TabletDataSyncUtil", "   - Image 3: ${if (realImages.size > 2) "REAL" else "VAZIA"}")
        
        return@withContext request
    }
    
    /**
     * Envia dados para o backend usando Retrofit
     */
    private suspend fun sendDataToBackend(data: TabletDataRequest): retrofit2.Response<com.ml.shubham0204.facenet_android.data.api.TabletDataResponse> = withContext(Dispatchers.IO) {
        // Obter a entidade configurada
        val configuracoesDao = ConfiguracoesDao()
        val configuracoes = configuracoesDao.getConfiguracoes()
        val entidadeId = configuracoes?.entidadeId ?: "default"
        
        Log.d("TabletDataSyncUtil", "🌐 Enviando dados para entidade: $entidadeId")
        Log.d("TabletDataSyncUtil", "📤 CPF: ${data.numero_cpf}")
        Log.d("TabletDataSyncUtil", "📤 Face embedding size: ${data.face.length}")
        Log.d("TabletDataSyncUtil", "📤 Image 1: ${data.image_1.take(50)}...")
        Log.d("TabletDataSyncUtil", "📤 Image 2: ${data.image_2.take(50)}...")
        Log.d("TabletDataSyncUtil", "📤 Image 3: ${data.image_3.take(50)}...")
        
        try {
            val response = apiService.adicionarDadosDoTablet(entidadeId, data)
            Log.d("TabletDataSyncUtil", "📥 Resposta recebida: ${response.code()} - ${response.message()}")
            
            // Se houver erro, mostrar o corpo da resposta
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e("TabletDataSyncUtil", "❌ Corpo da resposta de erro: $errorBody")
            }
            
            response
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro de rede: ${e.message}")
            throw e
        }
    }
    
    /**
     * Converte uma imagem para base64 válido para o servidor
     * Usa EXATAMENTE a mesma lógica do BitmapUtils que funciona no sistema de pontos
     */
    private fun imageToBase64(bitmap: Bitmap): String {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
            Log.d("TabletDataSyncUtil", "✅ Bitmap convertido para base64 (${base64.length} chars)")
            base64
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro ao converter bitmap para base64: ${e.message}")
            ""
        }
    }
    
    /**
     * Cria uma imagem genérica válida quando não encontra fotos específicas
     */
    private fun getGenericImageBase64(): String {
        return try {
            // Criar um bitmap 100x100 pixel com cor sólida
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GRAY)
            
            // Converter para base64 usando a mesma lógica
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
            
            Log.d("TabletDataSyncUtil", "✅ Imagem genérica criada: ${base64.length} chars")
            base64
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro ao criar imagem genérica: ${e.message}")
            ""
        }
    }
    

    // Auxiliar: lista recursivamente arquivos de imagem
    private fun listImageFilesRecursively(root: File?): List<File> {
        if (root == null || !root.exists() || !root.isDirectory) return emptyList()
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeFirst()
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    stack.add(file)
                } else {
                    val nameLower = file.name.lowercase()
                    if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png")) {
                        result.add(file)
                    }
                }
            }
        }
        return result
    }
    
    /**
     * Busca as 3 fotos reais de CADASTRO do funcionário
     * Tenta usar caminhos salvos no banco, senão usa migração por tempo
     */
    private suspend fun getRealImagesFromCache(
        funcionario: com.ml.shubham0204.facenet_android.data.FuncionariosEntity,
        faceImages: List<com.ml.shubham0204.facenet_android.data.FaceImageRecord>
    ): List<String> = withContext(Dispatchers.IO) {
        val realImages = mutableListOf<String>()
        
        try {
            Log.d("TabletDataSyncUtil", "🔍 Buscando fotos para funcionário: ${funcionario.nome} (ID: ${funcionario.id})")
            Log.d("TabletDataSyncUtil", "📸 Faces no banco: ${faceImages.size}")
            
            // ✅ ESTRATÉGIA 1: Tentar usar caminhos salvos no FaceImageRecord
            var foundImages = 0
            faceImages.forEachIndexed { index, faceImage ->
                try {
                    val imagePath = faceImage.originalImagePath
                    Log.d("TabletDataSyncUtil", "📸 Processando face $index: $imagePath")
                    
                    if (!imagePath.isNullOrEmpty()) {
                        val imageFile = File(imagePath)
                        if (imageFile.exists() && imageFile.isFile) {
                            Log.d("TabletDataSyncUtil", "✅ Arquivo encontrado: ${imageFile.absolutePath}")
                            
                            // Carregar a imagem como Bitmap
                            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                                val base64 = imageToBase64(bitmap)
                                if (base64.isNotEmpty()) {
                                    realImages.add(base64)
                                    foundImages++
                                    Log.d("TabletDataSyncUtil", "✅ Foto convertida: ${imageFile.name} (${base64.length} chars)")
                                }
                            }
                        }
                    } else {
                        Log.d("TabletDataSyncUtil", "📸 Face $index: Caminho vazio ou null")
                    }
                } catch (e: Exception) {
                    Log.e("TabletDataSyncUtil", "❌ Erro ao processar face $index: ${e.message}")
                }
            }
            
            // ✅ ESTRATÉGIA 2: Se não encontrou imagens, NÃO fazer migração
            if (foundImages == 0) {
              
            }
            
            Log.d("TabletDataSyncUtil", "📊 Total de fotos encontradas para ${funcionario.nome}: ${realImages.size}")
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro ao buscar fotos: ${e.message}")
            e.printStackTrace()
        }
        
        return@withContext realImages
    }
    
    /**
     * Migra imagens antigas baseado no tempo de cadastro
     * Busca em TODOS os diretórios possíveis do Android
     */
    private suspend fun migrateImagesByTime(
        funcionario: com.ml.shubham0204.facenet_android.data.FuncionariosEntity,
        faceImages: List<com.ml.shubham0204.facenet_android.data.FaceImageRecord>
    ): List<String> = withContext(Dispatchers.IO) {
        val migratedImages = mutableListOf<String>()
        
        try {
            // Buscar pessoa associada ao funcionário
            val person = personDB.getPersonByFuncionarioId(funcionario.id)
            if (person == null) {
                Log.w("TabletDataSyncUtil", "⚠️ Nenhuma pessoa encontrada para funcionário ${funcionario.nome}")
                return@withContext migratedImages
            }
            
            Log.d("TabletDataSyncUtil", "🔄 Migração por tempo para ${funcionario.nome}")
            Log.d("TabletDataSyncUtil", "📅 Data de cadastro: ${person.addTime}")
            Log.d("TabletDataSyncUtil", "📸 Faces esperadas: ${person.numImages}")
            
            // ✅ NOVO: Buscar em TODOS os diretórios possíveis
            val allPhotos = searchAllImageDirectories()
            Log.d("TabletDataSyncUtil", "📸 Total de fotos encontradas em todos os diretórios: ${allPhotos.size}")
            
            if (allPhotos.isNotEmpty()) {
                allPhotos.forEach { photo ->
                    Log.d("TabletDataSyncUtil", "📸 Foto disponível: ${photo.name} (${photo.lastModified()}) - ${photo.parent}")
                }
                
                // ✅ ESTRATÉGIA DEFINITIVA: Buscar fotos baseado no timestamp EXATO do cadastro
                Log.d("TabletDataSyncUtil", "🔍 === BUSCA DEFINITIVA POR TIMESTAMP ===")
                Log.d("TabletDataSyncUtil", "📅 Tempo de cadastro: ${person.addTime}")
                Log.d("TabletDataSyncUtil", "📅 Data de cadastro: ${java.util.Date(person.addTime)}")
                Log.d("TabletDataSyncUtil", "👤 Funcionário: ${funcionario.nome} (ID: ${funcionario.id})")
                
                // ✅ ESTRATÉGIA DEFINITIVA: Buscar fotos que foram capturadas DURANTE o cadastro
                // O cadastro acontece em ~10 segundos, então as fotos devem estar próximas
                val funcionarioPhotos = allPhotos.filter { photo ->
                    try {
                        val fileName = photo.name
                        if (fileName.startsWith("photo_") && fileName.endsWith(".jpg")) {
                            val timestampStr = fileName.removePrefix("photo_").removeSuffix(".jpg")
                            val photoTimestamp = timestampStr.toLongOrNull()
                            
                            if (photoTimestamp != null) {
                                // ✅ LÓGICA DEFINITIVA: Fotos capturadas DURANTE o cadastro
                                // O cadastro acontece em ~10 segundos, então as fotos devem estar próximas
                                val timeDiff = kotlin.math.abs(photoTimestamp - person.addTime)
                                val isDuringRegistration = timeDiff <= (30 * 1000L) // 30 segundos
                                
                                Log.d("TabletDataSyncUtil", "📸 ${fileName}: timestamp=$photoTimestamp, diff=${timeDiff}ms, during=$isDuringRegistration")
                                isDuringRegistration
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        Log.e("TabletDataSyncUtil", "❌ Erro ao processar ${photo.name}: ${e.message}")
                        false
                    }
                }
                
                Log.d("TabletDataSyncUtil", "📸 Fotos capturadas durante cadastro: ${funcionarioPhotos.size}")
                
                // ✅ ESTRATÉGIA DEFINITIVA: Se encontrou fotos, pegar as 3 mais próximas
                val photosToUse = if (funcionarioPhotos.isNotEmpty()) {
                    val sortedPhotos = funcionarioPhotos.sortedBy { photo ->
                        val timestampStr = photo.name.removePrefix("photo_").removeSuffix(".jpg")
                        val photoTimestamp = timestampStr.toLongOrNull() ?: 0L
                        kotlin.math.abs(photoTimestamp - person.addTime)
                    }
                    
                    // ✅ PEGAR EXATAMENTE 3 FOTOS mais próximas do tempo de cadastro
                    val selectedPhotos = sortedPhotos.take(3)
                    
                    Log.d("TabletDataSyncUtil", "✅ Fotos selecionadas para ${funcionario.nome}: ${selectedPhotos.size}")
                    selectedPhotos.forEach { photo ->
                        val timestampStr = photo.name.removePrefix("photo_").removeSuffix(".jpg")
                        val photoTimestamp = timestampStr.toLongOrNull() ?: 0L
                        val timeDiff = kotlin.math.abs(photoTimestamp - person.addTime)
                        Log.d("TabletDataSyncUtil", "✅ ${photo.name}: timestamp=$photoTimestamp, diff=${timeDiff}ms")
                    }
                    
                    selectedPhotos
                } else {
                    Log.w("TabletDataSyncUtil", "⚠️ NENHUMA foto encontrada durante o cadastro de ${funcionario.nome}")
                    Log.w("TabletDataSyncUtil", "⚠️ Tentando estratégia alternativa...")
                    
                    // ✅ ESTRATÉGIA ALTERNATIVA: Buscar por ordem de cadastro dos funcionários
                    val alternativePhotos = findPhotosByRegistrationOrder(funcionario, person, allPhotos)
                    Log.d("TabletDataSyncUtil", "🔄 Fotos encontradas por ordem de cadastro: ${alternativePhotos.size}")
                    alternativePhotos
                }
                
                photosToUse.forEach { photo ->
                    try {
                        Log.d("TabletDataSyncUtil", "📸 Migrando foto: ${photo.name}")
                        
                        val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
                        if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                            val base64 = imageToBase64(bitmap)
                            if (base64.isNotEmpty()) {
                                migratedImages.add(base64)
                                Log.d("TabletDataSyncUtil", "✅ Foto migrada: ${photo.name} (${base64.length} chars)")
                                
                                // ✅ NOVO: Atualizar o FaceImageRecord com o caminho correto
                                updateFaceImagePath(photo.absolutePath, faceImages)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TabletDataSyncUtil", "❌ Erro ao migrar foto ${photo.name}: ${e.message}")
                    }
                }
            } else {
                Log.w("TabletDataSyncUtil", "⚠️ NENHUMA foto encontrada em nenhum diretório!")
            }
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro na migração: ${e.message}")
        }
        
        return@withContext migratedImages
    }
    
    /**
     * Busca imagens em TODOS os diretórios possíveis do Android
     */
    private fun searchAllImageDirectories(): List<File> {
        val allPhotos = mutableListOf<File>()
        
        try {
            Log.d("TabletDataSyncUtil", "🔍 === BUSCANDO IMAGENS EM TODOS OS DIRETÓRIOS ===")
            
            // 1. CacheDir (diretório principal)
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                val cachePhotos = cacheDir.listFiles()?.filter { file ->
                    file.isFile && file.name.startsWith("photo_") && file.name.endsWith(".jpg")
                } ?: emptyList()
                allPhotos.addAll(cachePhotos)
                Log.d("TabletDataSyncUtil", "📁 CacheDir: ${cachePhotos.size} fotos")
            }
            
            // 2. FilesDir
            val filesDir = context.filesDir
            if (filesDir.exists()) {
                val filesPhotos = listImageFilesRecursively(filesDir).filter { 
                    it.name.startsWith("photo_") && it.name.endsWith(".jpg")
                }
                allPhotos.addAll(filesPhotos)
                Log.d("TabletDataSyncUtil", "📁 FilesDir: ${filesPhotos.size} fotos")
            }
            
            // 3. External Files Dir
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null && externalFilesDir.exists()) {
                val externalPhotos = listImageFilesRecursively(externalFilesDir).filter { 
                    it.name.startsWith("photo_") && it.name.endsWith(".jpg")
                }
                allPhotos.addAll(externalPhotos)
                Log.d("TabletDataSyncUtil", "📁 ExternalFilesDir: ${externalPhotos.size} fotos")
            }
            
            // 4. External Cache Dir
            val externalCacheDir = context.externalCacheDir
            if (externalCacheDir != null && externalCacheDir.exists()) {
                val externalCachePhotos = listImageFilesRecursively(externalCacheDir).filter { 
                    it.name.startsWith("photo_") && it.name.endsWith(".jpg")
                }
                allPhotos.addAll(externalCachePhotos)
                Log.d("TabletDataSyncUtil", "📁 ExternalCacheDir: ${externalCachePhotos.size} fotos")
            }
            
            // 5. Pictures Directory
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            if (picturesDir.exists()) {
                val picturesPhotos = listImageFilesRecursively(picturesDir).filter { 
                    it.name.startsWith("photo_") && it.name.endsWith(".jpg")
                }
                allPhotos.addAll(picturesPhotos)
                Log.d("TabletDataSyncUtil", "📁 PicturesDir: ${picturesPhotos.size} fotos")
            }
            
            // 6. DCIM Directory (Camera)
            val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
            if (dcimDir.exists()) {
                val dcimPhotos = listImageFilesRecursively(dcimDir).filter { 
                    it.name.startsWith("photo_") && it.name.endsWith(".jpg")
                }
                allPhotos.addAll(dcimPhotos)
                Log.d("TabletDataSyncUtil", "📁 DCIMDir: ${dcimPhotos.size} fotos")
            }
            
            // 7. Downloads Directory
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists()) {
                val downloadsPhotos = listImageFilesRecursively(downloadsDir).filter { 
                    it.name.startsWith("photo_") && it.name.endsWith(".jpg")
                }
                allPhotos.addAll(downloadsPhotos)
                Log.d("TabletDataSyncUtil", "📁 DownloadsDir: ${downloadsPhotos.size} fotos")
            }
            
            // Ordenar por data de modificação
            val sortedPhotos = allPhotos.sortedBy { it.lastModified() }
            
            Log.d("TabletDataSyncUtil", "📊 === RESUMO DA BUSCA ===")
            Log.d("TabletDataSyncUtil", "📊 Total de fotos encontradas: ${sortedPhotos.size}")
            sortedPhotos.forEach { photo ->
                Log.d("TabletDataSyncUtil", "📸 ${photo.name} - ${photo.parent} - ${photo.lastModified()}")
            }
            
            return sortedPhotos
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro ao buscar imagens: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Busca fotos baseada na ordem de cadastro dos funcionários
     */
    private fun findPhotosByRegistrationOrder(
        funcionario: com.ml.shubham0204.facenet_android.data.FuncionariosEntity,
        person: com.ml.shubham0204.facenet_android.data.PersonRecord,
        allPhotos: List<File>
    ): List<File> {
        val foundPhotos = mutableListOf<File>()
        
        try {
            Log.d("TabletDataSyncUtil", "🔍 === BUSCA POR ORDEM DE CADASTRO ===")
            
            // ✅ ESTRATÉGIA INTELIGENTE: Ordenar funcionários por tempo de cadastro
            val allPersons = personDB.getAllPersonsSortedByTime()
            val funcionarioIndex = allPersons.indexOfFirst { it.funcionarioId == funcionario.id }
            
            Log.d("TabletDataSyncUtil", "📊 Funcionário ${funcionario.nome} é o ${funcionarioIndex + 1}º cadastrado")
            Log.d("TabletDataSyncUtil", "📊 Total de pessoas cadastradas: ${allPersons.size}")
            
            if (funcionarioIndex >= 0) {
                // ✅ LÓGICA CORRETA: Cada funcionário tem 3 fotos consecutivas
                // Funcionário 1: fotos 0,1,2
                // Funcionário 2: fotos 3,4,5
                // Funcionário 3: fotos 6,7,8
                val startIndex = funcionarioIndex * 3
                val endIndex = startIndex + 3
                
                Log.d("TabletDataSyncUtil", "📸 Índices calculados: $startIndex até $endIndex")
                
                if (startIndex < allPhotos.size) {
                    val selectedPhotos = allPhotos.subList(startIndex, minOf(endIndex, allPhotos.size))
                    Log.d("TabletDataSyncUtil", "✅ Fotos selecionadas por ordem: ${selectedPhotos.size}")
                    
                    selectedPhotos.forEach { photo ->
                        Log.d("TabletDataSyncUtil", "📸 ${photo.name}")
                    }
                    
                    foundPhotos.addAll(selectedPhotos)
                } else {
                    Log.w("TabletDataSyncUtil", "⚠️ Índice calculado ($startIndex) maior que total de fotos (${allPhotos.size})")
                }
            } else {
                Log.w("TabletDataSyncUtil", "⚠️ Funcionário não encontrado na lista de pessoas cadastradas")
            }
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro na busca por ordem: ${e.message}")
        }
        
        return foundPhotos
    }
    
    /**
     * Métodos alternativos para encontrar fotos específicas do funcionário
     */
    private fun findPhotosByAlternativeMethods(
        funcionario: com.ml.shubham0204.facenet_android.data.FuncionariosEntity,
        person: com.ml.shubham0204.facenet_android.data.PersonRecord,
        allPhotos: List<File>
    ): List<File> {
        val foundPhotos = mutableListOf<File>()
        
        try {
            Log.d("TabletDataSyncUtil", "🔍 === MÉTODOS ALTERNATIVOS PARA ${funcionario.nome} ===")
            
            // Estratégia 1: Buscar por padrões no nome do arquivo
            val namePatterns = listOf(
                funcionario.nome.lowercase().replace(" ", "_"),
                funcionario.nome.lowercase().replace(" ", ""),
                funcionario.cpf,
                funcionario.id.toString()
            )
            
            Log.d("TabletDataSyncUtil", "🔍 Padrões de busca: $namePatterns")
            
            val patternPhotos = allPhotos.filter { photo ->
                val fileName = photo.name.lowercase()
                namePatterns.any { pattern -> fileName.contains(pattern.lowercase()) }
            }
            
            Log.d("TabletDataSyncUtil", "📸 Fotos encontradas por padrão: ${patternPhotos.size}")
            foundPhotos.addAll(patternPhotos)
            
            // Estratégia 2: Se ainda não encontrou, usar timestamp com janela mais ampla
            if (foundPhotos.isEmpty()) {
                Log.d("TabletDataSyncUtil", "🔄 Tentando estratégia de timestamp com janela ampla...")
                
                // Buscar fotos em uma janela de tempo mais ampla (24 horas)
                val timeWindow = 24 * 60 * 60 * 1000L // 24 horas
                val windowPhotos = allPhotos.filter { photo ->
                    try {
                        val fileName = photo.name
                        if (fileName.startsWith("photo_") && fileName.endsWith(".jpg")) {
                            val timestampStr = fileName.removePrefix("photo_").removeSuffix(".jpg")
                            val photoTimestamp = timestampStr.toLongOrNull()
                            
                            if (photoTimestamp != null) {
                                val timeDiff = kotlin.math.abs(photoTimestamp - person.addTime)
                                val isInWindow = timeDiff <= timeWindow
                                Log.d("TabletDataSyncUtil", "📸 ${fileName}: timestamp=$photoTimestamp, diff=${timeDiff}ms, inWindow=$isInWindow")
                                isInWindow
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        Log.e("TabletDataSyncUtil", "❌ Erro ao processar ${photo.name}: ${e.message}")
                        false
                    }
                }.sortedBy { photo ->
                    val timestampStr = photo.name.removePrefix("photo_").removeSuffix(".jpg")
                    val photoTimestamp = timestampStr.toLongOrNull() ?: 0L
                    kotlin.math.abs(photoTimestamp - person.addTime)
                }
                
                Log.d("TabletDataSyncUtil", "📸 Fotos na janela de 24h: ${windowPhotos.size}")
                
                // Pegar as 3 fotos mais próximas do tempo de cadastro
                val selectedPhotos = windowPhotos.take(3)
                Log.d("TabletDataSyncUtil", "📸 Fotos selecionadas por timestamp amplo: ${selectedPhotos.size}")
                foundPhotos.addAll(selectedPhotos)
            }
            
            // Estratégia 3: Se ainda não encontrou, pegar as fotos mais antigas disponíveis
            if (foundPhotos.isEmpty()) {
                Log.d("TabletDataSyncUtil", "🔄 Usando fotos mais antigas disponíveis...")
                val oldestPhotos = allPhotos.sortedBy { it.lastModified() }.take(3)
                foundPhotos.addAll(oldestPhotos)
                Log.d("TabletDataSyncUtil", "📸 Fotos mais antigas: ${oldestPhotos.size}")
            }
            
            Log.d("TabletDataSyncUtil", "📊 Total de fotos encontradas por métodos alternativos: ${foundPhotos.size}")
            
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro nos métodos alternativos: ${e.message}")
        }
        
        return foundPhotos.distinctBy { it.absolutePath }
    }
    
    /**
     * Atualiza o caminho da imagem no FaceImageRecord
     */
    private suspend fun updateFaceImagePath(imagePath: String, faceImages: List<com.ml.shubham0204.facenet_android.data.FaceImageRecord>) {
        try {
            // Encontrar o primeiro FaceImageRecord sem caminho
            val faceToUpdate = faceImages.firstOrNull { it.originalImagePath.isNullOrEmpty() }
            if (faceToUpdate != null) {
                val updatedFace = faceToUpdate.copy(originalImagePath = imagePath)
                imagesVectorDB.addFaceImageRecord(updatedFace)
                Log.d("TabletDataSyncUtil", "✅ Caminho atualizado para face: $imagePath")
            }
        } catch (e: Exception) {
            Log.e("TabletDataSyncUtil", "❌ Erro ao atualizar caminho: ${e.message}")
        }
    }
    
    /**
     * Resultado da sincronização
     */
    data class SyncResult(
        val success: Boolean,
        val successCount: Int,
        val errorCount: Int,
        val errors: List<String>
    )
}
