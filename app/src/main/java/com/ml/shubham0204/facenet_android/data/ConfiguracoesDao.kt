package com.ml.shubham0204.facenet_android.data

import io.objectbox.Box

class ConfiguracoesDao {
    private val box: Box<ConfiguracoesEntity> = ObjectBoxStore.store.boxFor(ConfiguracoesEntity::class.java)

    fun getConfiguracoes(): ConfiguracoesEntity? {
        return box.all.firstOrNull()
    }

    fun salvarConfiguracoes(configuracoes: ConfiguracoesEntity): Long {
        box.removeAll()
        
        val novaConfig = configuracoes.copy(id = 0)
        return box.put(novaConfig)
    }

    fun atualizarConfiguracoes(configuracoes: ConfiguracoesEntity) {
        box.put(configuracoes)
    }
} 