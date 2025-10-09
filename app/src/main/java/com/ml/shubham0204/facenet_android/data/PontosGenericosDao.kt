package com.ml.shubham0204.facenet_android.data

import android.content.Context
import android.util.Log
import com.ml.shubham0204.facenet_android.data.ObjectBoxStore.store
import com.ml.shubham0204.facenet_android.utils.DeviceMacUtils
import io.objectbox.Box

class PontosGenericosDao {
    private val box: Box<PontosGenericosEntity> = store.boxFor(PontosGenericosEntity::class.java)
    
    fun insert(ponto: PontosGenericosEntity, context: Context? = null): Long {
        return try {
            // ✅ NOVO: Garantir que o fuso horário seja sempre do Brasil se não estiver definido
            if (ponto.fusoHorario.isNullOrBlank()) {
                ponto.fusoHorario = "America/Sao_Paulo"
            }
            
            // ✅ NOVO: Garantir que o MAC do dispositivo seja sempre definido
            if ((ponto.macDispositivoCriptografado == null || ponto.macDispositivoCriptografado!!.isBlank()) && context != null) {
                val macCriptografado = DeviceMacUtils.getMacDispositivoCriptografado(context)
                ponto.macDispositivoCriptografado = macCriptografado // pode ser nulo; mantemos nulo se não conseguir obter
            }
            
            val id = box.put(ponto)
            val macLog = ponto.macDispositivoCriptografado?.take(8) ?: "<null>"
            val fusoLog = ponto.fusoHorario ?: "<null>"
            Log.d("PontosGenericosDao", "✅ Ponto salvo com ID: $id (fuso: $fusoLog, MAC: $macLog)")
            id
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao salvar ponto: ${e.message}")
            throw e
        }
    }
    
    // ✅ NOVO: Método para criar ponto com fuso horário do Brasil e MAC do dispositivo
    fun criarPontoComFusoBrasil(
        context: Context,
        funcionarioId: String,
        funcionarioNome: String,
        funcionarioMatricula: String,
        funcionarioCpf: String,
        funcionarioCargo: String,
        funcionarioSecretaria: String,
        funcionarioLotacao: String,
        dataHora: Long = System.currentTimeMillis(),
        latitude: Double? = null,
        longitude: Double? = null,
        observacao: String? = null,
        fotoBase64: String? = null,
        entidadeId: String? = null
    ): Long {
        // ✅ NOVO: Obter MAC do dispositivo criptografado
        val macCriptografado = DeviceMacUtils.getMacDispositivoCriptografado(context)
        
        val ponto = PontosGenericosEntity(
            funcionarioId = funcionarioId,
            funcionarioNome = funcionarioNome,
            funcionarioMatricula = funcionarioMatricula,
            funcionarioCpf = funcionarioCpf,
            funcionarioCargo = funcionarioCargo,
            funcionarioSecretaria = funcionarioSecretaria,
            funcionarioLotacao = funcionarioLotacao,
            dataHora = dataHora,
            macDispositivoCriptografado = macCriptografado ?: "", // ✅ MAC do dispositivo criptografado
            latitude = latitude,
            longitude = longitude,
            observacao = observacao,
            fotoBase64 = fotoBase64,
            synced = false,
            entidadeId = entidadeId,
            fusoHorario = "America/Sao_Paulo" // ✅ Fuso horário do Brasil
        )
        
        return insert(ponto, context)
    }
    
    fun getAll(): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all
            Log.d("PontosGenericosDao", "📋 Total de pontos: ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos: ${e.message}")
            emptyList()
        }
    }
    
    fun getById(id: Long): PontosGenericosEntity? {
        return try {
            val ponto = box.get(id)
            if (ponto != null) {
                Log.d("PontosGenericosDao", "✅ Ponto encontrado: ${ponto.funcionarioNome}")
            } else {
                Log.w("PontosGenericosDao", "⚠️ Ponto não encontrado com ID: $id")
            }
            ponto
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar ponto por ID: ${e.message}")
            null
        }
    }
    
    fun getByFuncionarioId(funcionarioId: String): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all.filter { it.funcionarioCpf == funcionarioId } // Usar CPF em vez do ID interno
            Log.d("PontosGenericosDao", "📋 Pontos do funcionário $funcionarioId: ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos do funcionário: ${e.message}")
            emptyList()
        }
    }
    
    fun getNaoSincronizados(): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all.filter { !it.synced }
            Log.d("PontosGenericosDao", "📋 Pontos não sincronizados: ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos não sincronizados: ${e.message}")
            emptyList()
        }
    }
    
    fun marcarComoSincronizado(id: Long) {
        try {
            val ponto = box.get(id)
            if (ponto != null) {
                ponto.synced = true
                box.put(ponto)
                Log.d("PontosGenericosDao", "✅ Ponto $id marcado como sincronizado")
            }
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao marcar ponto como sincronizado: ${e.message}")
        }
    }
    
    fun delete(id: Long) {
        try {
            box.remove(id)
            Log.d("PontosGenericosDao", "✅ Ponto $id removido")
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao remover ponto: ${e.message}")
        }
    }
    
    fun deleteAll() {
        try {
            box.removeAll()
            Log.d("PontosGenericosDao", "✅ Todos os pontos removidos")
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao remover todos os pontos: ${e.message}")
        }
    }
    
    fun deleteByFuncionarioNome(funcionarioNome: String): Int {
        return try {
            val pontosParaRemover = box.all.filter { it.funcionarioNome == funcionarioNome }
            val idsParaRemover = pontosParaRemover.map { it.id }
            
            if (idsParaRemover.isNotEmpty()) {
                box.remove(*idsParaRemover.toLongArray()) // Usar spread operator
                Log.d("PontosGenericosDao", "✅ ${idsParaRemover.size} pontos do funcionário '$funcionarioNome' removidos")
                idsParaRemover.size
            } else {
                Log.d("PontosGenericosDao", "ℹ️ Nenhum ponto encontrado para o funcionário '$funcionarioNome'")
                0
            }
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao remover pontos do funcionário '$funcionarioNome': ${e.message}")
            0
        }
    }
    
    // ✅ NOVO: Buscar pontos não sincronizados agrupados por entidade
    fun getNaoSincronizadosPorEntidade(): Map<String, List<PontosGenericosEntity>> {
        return try {
            val pontosNaoSincronizados = box.all.filter { !it.synced }
            val pontosAgrupados = pontosNaoSincronizados.groupBy { ponto ->
                ponto.entidadeId ?: "SEM_ENTIDADE"
            }
            
            Log.d("PontosGenericosDao", "📊 Pontos não sincronizados agrupados por entidade:")
            pontosAgrupados.forEach { (entidade, pontos) ->
                Log.d("PontosGenericosDao", "  🏢 Entidade '$entidade': ${pontos.size} pontos")
            }
            
            pontosAgrupados
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos agrupados por entidade: ${e.message}")
            emptyMap()
        }
    }
    
    // ✅ NOVO: Buscar pontos não sincronizados de uma entidade específica
    fun getNaoSincronizadosPorEntidade(entidadeId: String): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all.filter { !it.synced && it.entidadeId == entidadeId }
            Log.d("PontosGenericosDao", "📋 Pontos não sincronizados da entidade '$entidadeId': ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos da entidade '$entidadeId': ${e.message}")
            emptyList()
        }
    }
    
    // ✅ NOVO: Obter lista de entidades únicas com pontos não sincronizados
    fun getEntidadesComPontosPendentes(): List<String> {
        return try {
            val entidades = box.all
                .filter { !it.synced && !it.entidadeId.isNullOrEmpty() }
                .map { it.entidadeId!! }
                .distinct()
                .sorted()
            
            Log.d("PontosGenericosDao", "🏢 Entidades com pontos pendentes: $entidades")
            entidades
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar entidades com pontos pendentes: ${e.message}")
            emptyList()
        }
    }
    
    // ✅ NOVO: Corrigir pontos antigos que não têm entidadeId
    fun corrigirPontosSemEntidade(): Int {
        return try {
            val pontosSemEntidade = box.all.filter { it.entidadeId.isNullOrEmpty() }
            
            if (pontosSemEntidade.isEmpty()) {
                Log.d("PontosGenericosDao", "✅ Todos os pontos já têm entidadeId definido")
                return 0
            }
            
            Log.d("PontosGenericosDao", "🔧 Corrigindo ${pontosSemEntidade.size} pontos sem entidadeId...")
            
            var pontosCorrigidos = 0
            
            pontosSemEntidade.forEach { ponto ->
                // Buscar funcionário pelo CPF para obter a entidade
                val funcionarioDao = FuncionariosDao()
                val funcionario = funcionarioDao.getAll().find { it.cpf == ponto.funcionarioCpf }
                
                if (funcionario != null && !funcionario.entidadeId.isNullOrEmpty()) {
                    ponto.entidadeId = funcionario.entidadeId
                    box.put(ponto)
                    pontosCorrigidos++
                    Log.d("PontosGenericosDao", "✅ Ponto corrigido: ${ponto.funcionarioNome} -> entidade: ${funcionario.entidadeId}")
                } else {
                    // Se não encontrar funcionário, usar entidade das configurações como fallback
                    val configuracoesDao = ConfiguracoesDao()
                    val configuracoes = configuracoesDao.getConfiguracoes()
                    if (configuracoes != null && configuracoes.entidadeId.isNotEmpty()) {
                        ponto.entidadeId = configuracoes.entidadeId
                        box.put(ponto)
                        pontosCorrigidos++
                        Log.d("PontosGenericosDao", "✅ Ponto corrigido com entidade das configurações: ${ponto.funcionarioNome} -> entidade: ${configuracoes.entidadeId}")
                    } else {
                        Log.w("PontosGenericosDao", "⚠️ Não foi possível corrigir ponto: ${ponto.funcionarioNome} - funcionário não encontrado e configurações vazias")
                    }
                }
            }
            
            Log.d("PontosGenericosDao", "✅ Correção concluída: $pontosCorrigidos pontos corrigidos")
            pontosCorrigidos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao corrigir pontos sem entidade: ${e.message}")
            0
        }
    }
    
    // ✅ NOVO: Validar e corrigir pontos com campos vazios ou nulos
    fun validarECorrigirPontos(): Int {
        return try {
            val todosPontos = box.all
            var pontosCorrigidos = 0
            
            Log.d("PontosGenericosDao", "🔍 Validando ${todosPontos.size} pontos...")
            
            todosPontos.forEach { ponto ->
                var precisaCorrigir = false
                
                // ✅ Validar funcionarioNome (obrigatório)
                if (ponto.funcionarioNome.isBlank()) {
                    ponto.funcionarioNome = "FUNCIONARIO_DESCONHECIDO"
                    precisaCorrigir = true
                    Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: funcionarioNome vazio, corrigido para 'FUNCIONARIO_DESCONHECIDO'")
                }
                
                // ✅ Validar funcionarioCpf (obrigatório)
                if (ponto.funcionarioCpf.isBlank()) {
                    ponto.funcionarioCpf = "000.000.000-00"
                    precisaCorrigir = true
                    Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: funcionarioCpf vazio, corrigido para '000.000.000-00'")
                }
                
                // ✅ Validar funcionarioMatricula (obrigatório)
                if (ponto.funcionarioMatricula.isBlank()) {
                    ponto.funcionarioMatricula = "00000000"
                    precisaCorrigir = true
                    Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: funcionarioMatricula vazio, corrigido para '00000000'")
                }
                
                // ✅ Validar funcionarioCargo (obrigatório)
                if (ponto.funcionarioCargo.isBlank()) {
                    ponto.funcionarioCargo = "CARGO_NAO_INFORMADO"
                    precisaCorrigir = true
                    Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: funcionarioCargo vazio, corrigido para 'CARGO_NAO_INFORMADO'")
                }
                
                // ✅ Validar funcionarioSecretaria (obrigatório)
                if (ponto.funcionarioSecretaria.isBlank()) {
                    ponto.funcionarioSecretaria = "SECRETARIA_NAO_INFORMADA"
                    precisaCorrigir = true
                    Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: funcionarioSecretaria vazio, corrigido para 'SECRETARIA_NAO_INFORMADA'")
                }
                
                // ✅ Validar funcionarioLotacao (obrigatório)
                if (ponto.funcionarioLotacao.isBlank()) {
                    ponto.funcionarioLotacao = "LOTACAO_NAO_INFORMADA"
                    precisaCorrigir = true
                    Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: funcionarioLotacao vazio, corrigido para 'LOTACAO_NAO_INFORMADA'")
                }
                
                // ✅ Validar dataHora (obrigatório)
                if (ponto.dataHora <= 0) {
                    ponto.dataHora = System.currentTimeMillis()
                    precisaCorrigir = true
                    Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: dataHora inválida, corrigido para timestamp atual")
                }
                
                // ✅ Validar entidadeId (obrigatório)
                if (ponto.entidadeId.isNullOrBlank()) {
                    val funcionarioDao = FuncionariosDao()
                    val funcionario = funcionarioDao.getAll().find { it.cpf == ponto.funcionarioCpf }
                    
                    if (funcionario != null && !funcionario.entidadeId.isNullOrEmpty()) {
                        ponto.entidadeId = funcionario.entidadeId
                        precisaCorrigir = true
                        Log.d("PontosGenericosDao", "✅ Ponto ${ponto.id}: entidadeId corrigido com dados do funcionário: ${funcionario.entidadeId}")
                    } else {
                        val configuracoesDao = ConfiguracoesDao()
                        val configuracoes = configuracoesDao.getConfiguracoes()
                        if (configuracoes != null && configuracoes.entidadeId.isNotEmpty()) {
                            ponto.entidadeId = configuracoes.entidadeId
                            precisaCorrigir = true
                            Log.d("PontosGenericosDao", "✅ Ponto ${ponto.id}: entidadeId corrigido com entidade das configurações: ${configuracoes.entidadeId}")
                        } else {
                            ponto.entidadeId = "76"
                            precisaCorrigir = true
                            Log.w("PontosGenericosDao", "⚠️ Ponto ${ponto.id}: entidadeId não encontrado, usando 'ENTIDADE_PADRAO'")
                        }
                    }
                }
                
                // ✅ Validar fusoHorario (obrigatório)
                if (ponto.fusoHorario.isNullOrBlank()) {
                    ponto.fusoHorario = "America/Sao_Paulo"
                    precisaCorrigir = true
                    Log.d("PontosGenericosDao", "✅ Ponto ${ponto.id}: fusoHorario corrigido para 'America/Sao_Paulo'")
                }
                
                // ✅ Validar macDispositivoCriptografado (pode ser nulo, mas se vazio, melhor definir como nulo)
                if (ponto.macDispositivoCriptografado != null && ponto.macDispositivoCriptografado!!.isBlank()) {
                    ponto.macDispositivoCriptografado = null
                    precisaCorrigir = true
                    Log.d("PontosGenericosDao", "✅ Ponto ${ponto.id}: macDispositivoCriptografado vazio convertido para null")
                }
                
                if (precisaCorrigir) {
                    box.put(ponto)
                    pontosCorrigidos++
                }
            }
            
            Log.d("PontosGenericosDao", "✅ Validação concluída: $pontosCorrigidos pontos corrigidos")
            pontosCorrigidos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao validar pontos: ${e.message}")
            0
        }
    }
    
    // ✅ NOVO: Corrigir pontos antigos que não têm fuso horário definido
    fun corrigirPontosSemFusoHorario(): Int {
        return try {
            val pontosSemFuso = box.all.filter { it.fusoHorario.isNullOrBlank() }
            
            if (pontosSemFuso.isEmpty()) {
                Log.d("PontosGenericosDao", "✅ Todos os pontos já têm fuso horário definido")
                return 0
            }
            
            Log.d("PontosGenericosDao", "🔧 Corrigindo ${pontosSemFuso.size} pontos sem fuso horário...")
            
            var pontosCorrigidos = 0
            
            pontosSemFuso.forEach { ponto ->
                ponto.fusoHorario = "America/Sao_Paulo" // Fuso horário do Brasil
                box.put(ponto)
                pontosCorrigidos++
                val fusoLog = ponto.fusoHorario ?: "<null>"
                Log.d("PontosGenericosDao", "✅ Ponto corrigido: ${ponto.funcionarioNome} -> fuso: $fusoLog")
            }
            
            Log.d("PontosGenericosDao", "✅ Correção de fuso horário concluída: $pontosCorrigidos pontos corrigidos")
            pontosCorrigidos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao corrigir pontos sem fuso horário: ${e.message}")
            0
        }
    }
    
    // ✅ NOVO: Corrigir pontos antigos que não têm MAC do dispositivo definido
    fun corrigirPontosSemMacDispositivo(context: Context): Int {
        return try {
            val pontosSemMac = box.all.filter { it.macDispositivoCriptografado.isNullOrBlank() }
            
            if (pontosSemMac.isEmpty()) {
                Log.d("PontosGenericosDao", "✅ Todos os pontos já têm MAC do dispositivo definido")
                return 0
            }
            
            Log.d("PontosGenericosDao", "🔧 Corrigindo ${pontosSemMac.size} pontos sem MAC do dispositivo...")
            
            val macCriptografado = DeviceMacUtils.getMacDispositivoCriptografado(context)
            if (macCriptografado == null) {
                Log.e("PontosGenericosDao", "❌ Não foi possível obter MAC do dispositivo para correção")
                return 0
            }
            
            var pontosCorrigidos = 0
            
            pontosSemMac.forEach { ponto ->
                ponto.macDispositivoCriptografado = macCriptografado
                box.put(ponto)
                pontosCorrigidos++
                val macLog = macCriptografado.take(8)
                Log.d("PontosGenericosDao", "✅ Ponto corrigido: ${ponto.funcionarioNome} -> MAC: $macLog...")
            }
            
            Log.d("PontosGenericosDao", "✅ Correção de MAC do dispositivo concluída: $pontosCorrigidos pontos corrigidos")
            pontosCorrigidos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao corrigir pontos sem MAC do dispositivo: ${e.message}")
            0
        }
    }
    
    // ✅ NOVO: Buscar pontos por MAC do dispositivo
    fun getByMacDispositivo(macCriptografado: String): List<PontosGenericosEntity> {
        return try {
            val pontos = box.all.filter { it.macDispositivoCriptografado == macCriptografado }
            Log.d("PontosGenericosDao", "📋 Pontos do dispositivo ${macCriptografado.take(8)}...: ${pontos.size}")
            pontos
        } catch (e: Exception) {
            Log.e("PontosGenericosDao", "❌ Erro ao buscar pontos por MAC do dispositivo: ${e.message}")
            emptyList()
        }
    }
} 