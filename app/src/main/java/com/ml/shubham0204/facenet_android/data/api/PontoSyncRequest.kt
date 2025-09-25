package com.ml.shubham0204.facenet_android.data.api

data class PontoSyncRequest(
    val funcionarioId: String,
    val funcionarioNome: String,
    val dataHora: String,
    val tipoPonto: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fotoBase64: String? = null,
    val observacao: String? = null
)

data class PontoSyncCompleteRequest(
    val localizacao_id: String,
    val cod_sincroniza: String,
    val pontos: List<PontoSyncRequest>
)

data class PontoSyncResponse(
    val success: Boolean,
    val message: String,
    val pontosSincronizados: Int
)

data class PontoSyncFlexibleResponse(
    val success: Boolean = false,
    val message: String = "",
    val pontosSincronizados: Int = 0,
    val rawResponse: String = ""
) {
    companion object {
        fun fromRawResponse(rawResponse: String): PontoSyncFlexibleResponse {
            return when {
                rawResponse.contains("Pontos Sincronizado com Sucesso") -> {
                    PontoSyncFlexibleResponse(
                        success = true,
                        message = "Pontos Sincronizado com Sucesso",
                        pontosSincronizados = 0,
                        rawResponse = rawResponse
                    )
                }
                rawResponse.contains("success") || rawResponse.contains("Sucesso") -> {
                    PontoSyncFlexibleResponse(
                        success = true,
                        message = "Sincronização realizada",
                        pontosSincronizados = 0,
                        rawResponse = rawResponse
                    )
                }
                else -> {
                    PontoSyncFlexibleResponse(
                        success = false,
                        message = "Resposta não reconhecida",
                        pontosSincronizados = 0,
                        rawResponse = rawResponse
                    )
                }
            }
        }
    }
} 