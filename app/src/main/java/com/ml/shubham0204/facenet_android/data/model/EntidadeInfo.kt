package com.ml.shubham0204.facenet_android.data.model

import com.google.gson.annotations.SerializedName

data class EntidadeInfo(
    @SerializedName("nome_entidade")
    val nomeEntidade: String?,
    val municipio: String?,
    @SerializedName("municipio_uf")
    val municipioUf: String?
)

data class VerificacaoCodigoClienteResponse(
    val status: String,
    val message: String? = null,
    val entidade: EntidadeInfo? = null
)
