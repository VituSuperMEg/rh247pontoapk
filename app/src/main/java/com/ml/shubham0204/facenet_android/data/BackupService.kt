package com.ml.shubham0204.facenet_android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.data.api.RetrofitClient

class BackupService(private val context: Context) {
    
    companion object {
        private const val TAG = "BackupService"
        private const val BACKUP_FOLDER = "backups"
    }
    
    /**
     * Gera o nome do arquivo de backup seguindo a nomenclatura:
     * codigo_cliente + localizacao_id + data(20250715) + HHMMSS(171930)
     */
    private fun generateBackupFileName(configuracoes: ConfiguracoesEntity?): String {
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
        
        val data = dateFormat.format(now)
        val hora = timeFormat.format(now)
        
        // Usar valores das configurações ou valores padrão
        val codigoCliente = configuracoes?.entidadeId?.takeIf { it.isNotBlank() } ?: "CLIENTE"
        val localizacaoId = configuracoes?.localizacaoId?.takeIf { it.isNotBlank() } ?: "LOCAL"
        
        // Limpar caracteres especiais que podem causar problemas no nome do arquivo
        val codigoClienteLimpo = codigoCliente.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val localizacaoIdLimpo = localizacaoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        
        // Formato: codigo_cliente + localizacao_id + data(20250715) + HHMMSS(171930)
        val fileName = "${codigoClienteLimpo}_${localizacaoIdLimpo}_${data}_$hora.json"
        
        Log.d(TAG, "📝 Nome do arquivo de backup gerado: $fileName")
        return fileName
    }
    
    /**
     * Cria um backup completo do banco de dados ObjectBox e salva na pasta Downloads
     */
    suspend fun createBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Iniciando criação de backup...")
            
            // Obter configurações para gerar nome do arquivo
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            // Gerar nome do arquivo com nomenclatura específica
            val backupFileName = generateBackupFileName(configuracoes)
            
            // Salvar na pasta Downloads
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(downloadsDir, backupFileName)
            
            // Coletar dados de todas as entidades
            val backupData = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0")
                put("data", JSONObject().apply {
                    // Backup dos funcionários
                    put("funcionarios", exportFuncionarios())
                    
                    // Backup das configurações
                    put("configuracoes", exportConfiguracoes())
                    
                    // Backup das pessoas
                    put("pessoas", exportPessoas())
                    
                    // Backup das imagens de face
                    put("faceImages", exportFaceImages())
                    
                    // Backup dos pontos genéricos
                    put("pontosGenericos", exportPontosGenericos())
                })
            }
            
            // Salvar arquivo de backup
            FileOutputStream(backupFile).use { fos ->
                fos.write(backupData.toString(2).toByteArray())
            }
            
            Log.d(TAG, "✅ Backup criado com sucesso: ${backupFile.absolutePath}")
            Result.success(backupFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar backup", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cria um Intent para selecionar arquivo de backup para restauração
     */
    fun createRestoreIntent(): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        return Intent.createChooser(intent, "Selecionar arquivo de backup")
    }
    
    /**
     * Restaura o banco de dados a partir de um arquivo de backup
     */
    suspend fun restoreBackup(backupFilePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Iniciando restauração do backup: $backupFilePath")
            
            val backupFile = File(backupFilePath)
            if (!backupFile.exists()) {
                throw Exception("Arquivo de backup não encontrado")
            }
            
            // Ler arquivo de backup
            val backupContent = FileInputStream(backupFile).use { fis ->
                fis.bufferedReader().use { it.readText() }
            }
            
            val backupData = JSONObject(backupContent)
            val data = backupData.getJSONObject("data")
            
            // Limpar dados existentes
            clearAllData()
            
            // Restaurar dados
            if (data.has("funcionarios")) {
                importFuncionarios(data.getJSONArray("funcionarios"))
            }
            
            if (data.has("configuracoes")) {
                importConfiguracoes(data.getJSONArray("configuracoes"))
            }
            
            if (data.has("pessoas")) {
                importPessoas(data.getJSONArray("pessoas"))
            }
            
            if (data.has("faceImages")) {
                importFaceImages(data.getJSONArray("faceImages"))
            }
            
            if (data.has("pontosGenericos")) {
                importPontosGenericos(data.getJSONArray("pontosGenericos"))
            }
            
            // Atualizar informações da entidade após restauração
            atualizarInformacoesEntidade()
            
            Log.d(TAG, "✅ Backup restaurado com sucesso")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao restaurar backup", e)
            Result.failure(e)
        }
    }
    
    /**
     * Lista todos os arquivos de backup disponíveis
     */
    fun getAvailableBackups(): List<BackupInfo> {
        val backupDir = File(context.filesDir, BACKUP_FOLDER)
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles()?.filter { it.name.endsWith(".json") }?.map { file ->
            BackupInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified()
            )
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }
    
    /**
     * Remove um arquivo de backup
     */
    fun deleteBackup(backupFilePath: String): Boolean {
        return try {
            val file = File(backupFilePath)
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar backup", e)
            false
        }
    }
    
    /**
     * Cria um Intent para compartilhar/baixar o arquivo de backup
     */
    fun createShareIntent(backupFilePath: String): Intent? {
        return try {
            val file = File(backupFilePath)
            if (!file.exists()) {
                Log.e(TAG, "Arquivo de backup não encontrado: $backupFilePath")
                return null
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup do Banco de Dados")
                putExtra(Intent.EXTRA_TEXT, "Arquivo de backup gerado em ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            Log.d(TAG, "📤 Intent de compartilhamento criado para: ${file.name}")
            shareIntent
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar intent de compartilhamento", e)
            null
        }
    }
    
    /**
     * Cria um Intent para salvar o arquivo diretamente na pasta Downloads
     */
    fun createDownloadIntent(backupFilePath: String): Intent? {
        return try {
            val file = File(backupFilePath)
            if (!file.exists()) {
                Log.e(TAG, "Arquivo de backup não encontrado: $backupFilePath")
                return null
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val downloadIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup do Banco de Dados")
                putExtra(Intent.EXTRA_TEXT, "Arquivo de backup para salvar na pasta Downloads")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            Log.d(TAG, "💾 Intent de download criado para: ${file.name}")
            downloadIntent
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar intent de download", e)
            null
        }
    }
    
    // Métodos privados para exportar dados
    private fun exportFuncionarios(): JSONArray {
        val funcionariosDao = FuncionariosDao()
        val funcionarios = funcionariosDao.getAll()
        
        return JSONArray().apply {
            funcionarios.forEach { funcionario ->
                put(JSONObject().apply {
                    put("id", funcionario.id)
                    put("codigo", funcionario.codigo)
                    put("nome", funcionario.nome)
                    put("ativo", funcionario.ativo)
                    put("matricula", funcionario.matricula)
                    put("cpf", funcionario.cpf)
                    put("cargo", funcionario.cargo)
                    put("secretaria", funcionario.secretaria)
                    put("lotacao", funcionario.lotacao)
                    put("apiId", funcionario.apiId)
                    put("dataImportacao", funcionario.dataImportacao)
                })
            }
        }
    }
    
    private fun exportConfiguracoes(): JSONArray {
        val configuracoesDao = ConfiguracoesDao()
        val configuracoes = configuracoesDao.getConfiguracoes()
        
        return JSONArray().apply {
            if (configuracoes != null) {
                put(JSONObject().apply {
                    put("id", configuracoes.id)
                    put("entidadeId", configuracoes.entidadeId)
                    put("localizacaoId", configuracoes.localizacaoId)
                    put("codigoSincronizacao", configuracoes.codigoSincronizacao)
                    put("horaSincronizacao", configuracoes.horaSincronizacao)
                    put("minutoSincronizacao", configuracoes.minutoSincronizacao)
                    put("sincronizacaoAtiva", configuracoes.sincronizacaoAtiva)
                    put("intervaloSincronizacao", configuracoes.intervaloSincronizacao)
                })
            }
        }
    }
    
    private fun exportPessoas(): JSONArray {
        val personDB = PersonDB()
        val pessoas = mutableListOf<PersonRecord>()
        
        // Como getAll() retorna Flow, precisamos coletar os dados
        // Por simplicidade, vamos usar uma abordagem direta
        val personBox = ObjectBoxStore.store.boxFor(PersonRecord::class.java)
        val allPersons = personBox.all
        
        return JSONArray().apply {
            allPersons.forEach { pessoa ->
                put(JSONObject().apply {
                    put("personID", pessoa.personID)
                    put("personName", pessoa.personName)
                    put("numImages", pessoa.numImages)
                    put("addTime", pessoa.addTime)
                    put("funcionarioId", pessoa.funcionarioId)
                    put("funcionarioApiId", pessoa.funcionarioApiId)
                })
            }
        }
    }
    
    private fun exportFaceImages(): JSONArray {
        val faceBox = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)
        val faceImages = faceBox.all
        
        Log.d(TAG, "🔄 Exportando ${faceImages.size} imagens de face...")
        
        return JSONArray().apply {
            faceImages.forEach { faceImage ->
                put(JSONObject().apply {
                    put("recordID", faceImage.recordID)
                    put("personID", faceImage.personID)
                    put("personName", faceImage.personName)
                    put("faceEmbedding", JSONArray(faceImage.faceEmbedding.toList()))
                })
                Log.d(TAG, "✅ Imagem de face exportada: ${faceImage.personName} (recordID: ${faceImage.recordID})")
            }
        }
    }
    
    private fun exportPontosGenericos(): JSONArray {
        val pontosDao = PontosGenericosDao()
        val pontos = pontosDao.getAll()
        
        return JSONArray().apply {
            pontos.forEach { ponto ->
                put(JSONObject().apply {
                    put("id", ponto.id)
                    put("funcionarioId", ponto.funcionarioId)
                    put("funcionarioNome", ponto.funcionarioNome)
                    put("funcionarioMatricula", ponto.funcionarioMatricula)
                    put("funcionarioCpf", ponto.funcionarioCpf)
                    put("funcionarioCargo", ponto.funcionarioCargo)
                    put("funcionarioSecretaria", ponto.funcionarioSecretaria)
                    put("funcionarioLotacao", ponto.funcionarioLotacao)
                    put("dataHora", ponto.dataHora)
                    put("tipoPonto", ponto.tipoPonto)
                    put("latitude", ponto.latitude)
                    put("longitude", ponto.longitude)
                    put("observacao", ponto.observacao)
                    put("fotoBase64", ponto.fotoBase64)
                    put("synced", ponto.synced)
                })
            }
        }
    }
    
    // Métodos privados para importar dados
    private fun importFuncionarios(funcionariosArray: JSONArray) {
        val funcionariosDao = FuncionariosDao()
        
        for (i in 0 until funcionariosArray.length()) {
            val json = funcionariosArray.getJSONObject(i)
            val funcionario = FuncionariosEntity(
                id = json.getLong("id"),
                codigo = json.getString("codigo"),
                nome = json.getString("nome"),
                ativo = json.getInt("ativo"),
                matricula = json.getString("matricula"),
                cpf = json.getString("cpf"),
                cargo = json.getString("cargo"),
                secretaria = json.getString("secretaria"),
                lotacao = json.getString("lotacao"),
                apiId = json.getLong("apiId"),
                dataImportacao = json.getLong("dataImportacao")
            )
            funcionariosDao.insert(funcionario)
        }
    }
    
    private fun importConfiguracoes(configuracoesArray: JSONArray) {
        if (configuracoesArray.length() > 0) {
            val configuracoesDao = ConfiguracoesDao()
            val json = configuracoesArray.getJSONObject(0)
            val configuracoes = ConfiguracoesEntity(
                id = json.getLong("id"),
                entidadeId = json.getString("entidadeId"),
                localizacaoId = json.getString("localizacaoId"),
                codigoSincronizacao = json.getString("codigoSincronizacao"),
                horaSincronizacao = json.getInt("horaSincronizacao"),
                minutoSincronizacao = json.getInt("minutoSincronizacao"),
                sincronizacaoAtiva = json.getBoolean("sincronizacaoAtiva"),
                intervaloSincronizacao = json.getInt("intervaloSincronizacao")
            )
            configuracoesDao.salvarConfiguracoes(configuracoes)
        }
    }
    
    private fun importPessoas(pessoasArray: JSONArray) {
        val personBox = ObjectBoxStore.store.boxFor(PersonRecord::class.java)
        
        Log.d(TAG, "🔄 Importando ${pessoasArray.length()} pessoas...")
        
        for (i in 0 until pessoasArray.length()) {
            try {
                val json = pessoasArray.getJSONObject(i)
                val pessoa = PersonRecord(
                    personID = 0, // ObjectBox vai gerar novo ID automaticamente
                    personName = json.getString("personName"),
                    numImages = json.getLong("numImages"),
                    addTime = json.getLong("addTime"),
                    funcionarioId = json.getLong("funcionarioId"),
                    funcionarioApiId = json.getLong("funcionarioApiId")
                )
                val insertedId = personBox.put(pessoa)
                Log.d(TAG, "✅ Pessoa importada: ${pessoa.personName} (ID: $insertedId)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao importar pessoa $i: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ Importação de pessoas concluída")
    }
    
    private fun importFaceImages(faceImagesArray: JSONArray) {
        val faceBox = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)
        val personBox = ObjectBoxStore.store.boxFor(PersonRecord::class.java)
        
        Log.d(TAG, "🔄 Importando ${faceImagesArray.length()} imagens de face...")
        
        for (i in 0 until faceImagesArray.length()) {
            try {
                val json = faceImagesArray.getJSONObject(i)
                val embeddingArray = json.getJSONArray("faceEmbedding")
                val embedding = FloatArray(embeddingArray.length()) { j ->
                    embeddingArray.getDouble(j).toFloat()
                }
                
                // Buscar a pessoa correspondente pelo nome (já que os IDs mudaram)
                val personName = json.getString("personName")
                val pessoa = personBox.all.find { it.personName == personName }
                
                if (pessoa != null) {
                    val faceImage = FaceImageRecord(
                        recordID = 0, // ObjectBox vai gerar novo ID automaticamente
                        personID = pessoa.personID, // Usar o novo personID da pessoa importada
                        personName = personName,
                        faceEmbedding = embedding
                    )
                    val insertedId = faceBox.put(faceImage)
                    Log.d(TAG, "✅ Imagem de face importada: ${faceImage.personName} (ID: $insertedId, personID: ${pessoa.personID})")
                } else {
                    Log.w(TAG, "⚠️ Pessoa não encontrada para imagem de face: $personName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao importar imagem de face $i: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ Importação de imagens de face concluída")
    }
    
    private fun importPontosGenericos(pontosArray: JSONArray) {
        val pontosDao = PontosGenericosDao()
        
        Log.d(TAG, "🔄 Importando ${pontosArray.length()} pontos genéricos...")
        
        for (i in 0 until pontosArray.length()) {
            try {
                val json = pontosArray.getJSONObject(i)
                val ponto = PontosGenericosEntity(
                    id = 0, // ObjectBox vai gerar novo ID automaticamente
                    funcionarioId = json.getString("funcionarioId"),
                    funcionarioNome = json.getString("funcionarioNome"),
                    funcionarioMatricula = json.getString("funcionarioMatricula"),
                    funcionarioCpf = json.getString("funcionarioCpf"),
                    funcionarioCargo = json.getString("funcionarioCargo"),
                    funcionarioSecretaria = json.getString("funcionarioSecretaria"),
                    funcionarioLotacao = json.getString("funcionarioLotacao"),
                    dataHora = json.getLong("dataHora"),
                    tipoPonto = json.getString("tipoPonto"),
                    latitude = if (json.has("latitude") && !json.isNull("latitude")) json.getDouble("latitude") else null,
                    longitude = if (json.has("longitude") && !json.isNull("longitude")) json.getDouble("longitude") else null,
                    observacao = if (json.has("observacao") && !json.isNull("observacao")) json.getString("observacao") else null,
                    fotoBase64 = if (json.has("fotoBase64") && !json.isNull("fotoBase64")) json.getString("fotoBase64") else null,
                    synced = json.getBoolean("synced")
                )
                val insertedId = pontosDao.insert(ponto)
                Log.d(TAG, "✅ Ponto importado: ${ponto.funcionarioNome} (ID: $insertedId)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao importar ponto $i: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ Importação de pontos genéricos concluída")
    }
    
    private fun clearAllData() {
        // Limpar todas as tabelas
        ObjectBoxStore.store.boxFor(FuncionariosEntity::class.java).removeAll()
        ObjectBoxStore.store.boxFor(ConfiguracoesEntity::class.java).removeAll()
        ObjectBoxStore.store.boxFor(PersonRecord::class.java).removeAll()
        ObjectBoxStore.store.boxFor(FaceImageRecord::class.java).removeAll()
        ObjectBoxStore.store.boxFor(PontosGenericosEntity::class.java).removeAll()
    }
    
    /**
     * Atualiza as informações da entidade após a restauração do backup
     */
    private suspend fun atualizarInformacoesEntidade() {
        try {
            Log.d(TAG, "🔄 Atualizando informações da entidade após restauração...")
            
            val configuracoesDao = ConfiguracoesDao()
            val configuracoes = configuracoesDao.getConfiguracoes()
            
            if (configuracoes != null && configuracoes.entidadeId.isNotEmpty()) {
                Log.d(TAG, "🔍 Buscando informações da entidade: ${configuracoes.entidadeId}")
                
                val apiService = RetrofitClient.instance
                val response = apiService.verificarCodigoCliente(configuracoes.entidadeId)
                
                if (response.status == "SUCCESS" && response.entidade != null) {
                    // Salvar informações da entidade nas preferências
                    val appPreferences = AppPreferences(context)
                    appPreferences.entidadeInfo = response.entidade
                    
                    Log.d(TAG, "✅ Informações da entidade atualizadas:")
                    Log.d(TAG, "   - Entidade: ${response.entidade.nomeEntidade}")
                    Log.d(TAG, "   - Município: ${response.entidade.municipio}")
                    Log.d(TAG, "   - UF: ${response.entidade.municipioUf}")
                } else {
                    Log.w(TAG, "⚠️ Não foi possível atualizar informações da entidade: ${response.message}")
                }
            } else {
                Log.w(TAG, "⚠️ Configurações não encontradas ou entidadeId vazio")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao atualizar informações da entidade: ${e.message}")
        }
    }
}

data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val size: Long,
    val lastModified: Long
)
