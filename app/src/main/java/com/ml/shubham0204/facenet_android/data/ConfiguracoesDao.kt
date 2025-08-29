package com.ml.shubham0204.facenet_android.data

import io.objectbox.Box

class ConfiguracoesDao {
    private val box: Box<ConfiguracoesEntity> = ObjectBoxStore.store.boxFor(ConfiguracoesEntity::class.java)

    fun getConfiguracoes(): ConfiguracoesEntity? {
        return box.all.firstOrNull() // Pega a primeira configuração disponível
    }

    fun salvarConfiguracoes(configuracoes: ConfiguracoesEntity): Long {
        // Limpar todas as configurações existentes (só deve ter uma)
        box.removeAll()
        
        // Criar nova configuração com ID 0 (ObjectBox vai gerar automaticamente)
        val novaConfig = configuracoes.copy(id = 0)
        return box.put(novaConfig)
    }

    fun atualizarConfiguracoes(configuracoes: ConfiguracoesEntity) {
        box.put(configuracoes)
    }

    fun limparConfiguracoes() {
        box.removeAll()
    }

    fun existeConfiguracao(): Boolean {
        return box.get(1) != null
    }
} 