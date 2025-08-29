package com.ml.shubham0204.facenet_android.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body

interface ApiService {
    @GET("/{entidade}/services/funcionarios/list")
    suspend fun getFuncionarios(
        @Path("entidade") entidade: String,
        @Query("page") page: Int,
        @Query("descricao") descricao: String? = null
    ): FuncionariosResponse
    
    @POST("/{entidade}/services/util/sincronizar-ponto-table")
    suspend fun sincronizarPontosCompleto(
        @Path("entidade") entidade: String,
        @Body request: PontoSyncCompleteRequest
    ): Response<String>
}

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