package com.ml.shubham0204.facenet_android.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class ConfiguracoesEntity(
    @Id var id: Long = 1, // Sempre será 1, pois só teremos uma configuração
    val entidadeId: String = "",
    val localizacaoId: String = "",
    val codigoSincronizacao: String = "",
    val horaSincronizacao: Int = 8,
    val minutoSincronizacao: Int = 0,
    val sincronizacaoAtiva: Boolean = false,
    val intervaloSincronizacao: Int = 24,
    val geolocalizacaoHabilitada: Boolean = true,
    val latitudeFixa: Double? = null,
    val longitudeFixa: Double? = null
) 