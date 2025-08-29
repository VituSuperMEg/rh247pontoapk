package com.ml.shubham0204.facenet_android.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class PontosGenericosEntity(
    @Id var id: Long = 0,
    var funcionarioId: String = "",
    var funcionarioNome: String = "",
    var funcionarioMatricula: String = "",
    var funcionarioCpf: String = "",
    var funcionarioCargo: String = "",
    var funcionarioSecretaria: String = "",
    var funcionarioLotacao: String = "",
    var dataHora: Long = System.currentTimeMillis(),
    var tipoPonto: String = "PONTO",
    var latitude: Double? = null,
    var longitude: Double? = null,
    var observacao: String? = null,
    var fotoBase64: String? = null,
    var synced: Boolean = false
) 