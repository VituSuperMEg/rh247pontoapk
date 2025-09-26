package com.ml.shubham0204.facenet_android.data.api

import com.ml.shubham0204.facenet_android.data.model.VerificacaoCodigoClienteResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part

interface ApiService {
    @GET("/{entidade}/services/funcionarios/list")
    suspend fun getFuncionarios(
        @Path("entidade") entidade: String,
        @Query("page") page: Int,
        @Query("descricao") descricao: String? = null,
        @Query("orgao") orgao: String? = null,
        @Query("localizacao") localizacao: String? = null
    ): FuncionariosResponse


    @GET("/{entidade}/ponto/orgaos/list-tablet")
    suspend fun getOrgaos(
        @Path("entidade") entidade: String
    ): OrgaoResponse

    @GET("/{entidade}/ponto/localizacoes/list-tablet")
    suspend fun getLocalizacao(
        @Path("entidade") entidade: String
    ): LocalizacaoResponse

    @POST("/{entidade}/services/util/sincronizar-ponto-table")
    suspend fun sincronizarPontosCompleto(
        @Path("entidade") entidade: String,
        @Body request: PontoSyncCompleteRequest
    ): Response<String>

    @GET("/{entidade}/services/util/verificar-codigo-cliente")
    suspend fun verificarCodigoCliente(
        @Path("entidade") entidade: String
    ): VerificacaoCodigoClienteResponse

    @Multipart
    @POST("/{entidade}/services/util/backup-tablet")
    suspend fun uploadBackupToCloud(
        @Path("entidade") entidade: String,
        @Part("localizacao_id") localizacaoId: okhttp3.RequestBody,
        @Part file: MultipartBody.Part
    ): Response<BackupUploadResponse>

    @GET("/{entidade}/services/util/backup-tablet")
    suspend fun listBackupsFromCloud(
        @Path("entidade") entidade: String,
        @Query("localizacao_id") localizacaoId: String
    ): Response<BackupListResponse>

    @GET("/{entidade}/services/util/backup-tablet")
    suspend fun downloadBackupFromCloud(
        @Path("entidade") entidade: String,
        @Query("localizacao_id") localizacaoId: String
    ): Response<okhttp3.ResponseBody>

    @GET("/{entidade}/services/util/download-arquivo-tablet")
    suspend fun downloadSpecificBackupFile(
        @Path("entidade") entidade: String,
        @Query("localizacao_id") localizacaoId: String,
        @Query("arquivo") arquivo: String
    ): Response<okhttp3.ResponseBody>

    @POST("/{entidade}/services/util/adicionar-dados-do-tablet")
    suspend fun adicionarDadosDoTablet(
        @Path("entidade") entidade: String,
        @Body request: TabletDataRequest
    ): Response<TabletDataResponse>

}

data class OrgaoResponse (
    val data: List<OrgaoModel>?
)

data class OrgaoModel (
   val id: Int,
   val codigo: String,
   val descricao: String
)

data class LocalizacaoResponse (
    val data: List<LocalizacaoModel>?
)

data class LocalizacaoModel (
    val id: Int,
    val descricao: String,
    val orgao: String,
    val entidade_id: Int,
    val ativo: String,
    val status_registro: String
)

data class FuncionariosResponse(
    val success: Boolean,
    val message: String,
    val data: List<FuncionariosModel>?
)

data class FuncionariosModel(
    val id: Int,
    val nome: String,
    val numero_cpf: String,
    val matricula: String,
    val cargo_descricao: String,
    val orgao_descricao: String?,
    val setor_descricao: String?
)

data class BackupUploadResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: Any? = null,
    val backupId: String? = null,
    val uploadDate: String? = null
)

data class BackupListRequest(
    val localizacao_id: String
)

data class BackupListResponse(
    val arquivo: List<String>
)

// ✅ NOVO: Modelos para sincronização de dados do tablet
data class TabletDataRequest(
    val numero_cpf: String,
    val face: String, // Embedding da face como string
    val image_1: String, // Base64 da primeira imagem
    val image_2: String, // Base64 da segunda imagem
    val image_3: String  // Base64 da terceira imagem
)

data class TabletDataResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: Any? = null
) 