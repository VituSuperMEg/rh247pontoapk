package com.ml.shubham0204.facenet_android.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class FuncionariosEntity(
    @Id var id: Long = 0,
    val codigo: String,
    val nome: String,
    val ativo: Int = 1,
    val matricula: String = "",
    val cpf: String = "",
    val cargo: String = "",
    val secretaria: String = "",
    val lotacao: String = "",
    val apiId: Long = 0, // ID original da API
    val dataImportacao: Long = System.currentTimeMillis() // Timestamp da importação
) 