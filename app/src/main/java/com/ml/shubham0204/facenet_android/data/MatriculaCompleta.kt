package com.ml.shubham0204.facenet_android.data

data class MatriculaCompleta(
    val matricula: String,
    val cargoDescricao: String,
    val ativo: Int,
    val setorDescricao: String,
    val orgaoDescricao: String
) {
    fun isAtivo(): Boolean = ativo == 1
    
    fun getStatusText(): String = if (isAtivo()) "ATIVO" else "INATIVO"
    
    fun getDisplayText(): String {
        return """
            Matrícula: $matricula
            Cargo: $cargoDescricao
            Setor: $setorDescricao
            Órgão: $orgaoDescricao
            Status: ${getStatusText()}
        """.trimIndent()
    }
}
