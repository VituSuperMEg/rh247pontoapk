package com.ml.shubham0204.facenet_android.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class MatriculasEntity(
    @Id var id: Long = 0,
    var funcionarioId: String = "",
    var funcionarioCpf: String = "",
    var matricula: List<String> = listOf(""),
    var cargoDescricao: List<String>? = null, // ✅ CORRIGIDO: Tornar nullable para compatibilidade
    var ativo: List<String>? = null, // ✅ CORRIGIDO: Tornar nullable para compatibilidade
    var setorDescricao: List<String>? = null, // ✅ CORRIGIDO: Tornar nullable para compatibilidade
    var orgaoDescricao: List<String>? = null // ✅ CORRIGIDO: Tornar nullable para compatibilidade
)