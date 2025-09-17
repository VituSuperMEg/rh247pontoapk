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
        @Query("orgao") orgao: String? = null
    ): FuncionariosResponse


    @GET("/{entidade}/ponto/orgaos/list")
    suspend fun getOrgaos(
        @Path("entidade") entidade: String
    ): OrgaoResponse

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
}

data class OrgaoResponse (
    val data: List<OrgaoModel>?
)

data class OrgaoModel (
   val id: Int,
   val codigo: String,
   val descricao: String
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