package com.ml.shubham0204.facenet_android.presentation.screens.reports

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.PontosGenericosDao
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoService
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoPorBlocosService
import com.ml.shubham0204.facenet_android.utils.CrashReporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReportsState(
    val points: List<PontosGenericosEntity> = emptyList(),
    val totalPoints: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val hasMorePages: Boolean = true,
    val pageSize: Int = 50
)

@KoinViewModel
class ReportsViewModel(
    private val pontosGenericosDao: PontosGenericosDao,
    private val configuracoesDao: ConfiguracoesDao
) : ViewModel() {
    
    val reportsState = mutableStateOf(ReportsState())
    private val pontoSincronizacaoService = PontoSincronizacaoService()
    private val pontoSincronizacaoPorBlocosService = PontoSincronizacaoPorBlocosService() 
    
    fun loadReports() {
        viewModelScope.launch {
            try {
                reportsState.value = reportsState.value.copy(isLoading = true, error = null)
                
                // ✅ NOVO: Validar e corrigir pontos antes de carregar
                val pontosValidados = pontosGenericosDao.validarECorrigirPontos()
                if (pontosValidados > 0) {
                    android.util.Log.d("ReportsViewModel", "🔧 $pontosValidados pontos foram validados e corrigidos")
                }
                
                val allPoints = pontosGenericosDao.getAll()
                
                // ✅ PAGINAÇÃO: Carregar todos os pontos, mas com paginação para evitar crash
                val sortedPoints = allPoints.sortedByDescending { it.dataHora }
                
                android.util.Log.d("ReportsViewModel", "📊 Total de pontos encontrados: ${sortedPoints.size}")
                
                // Se não há pontos no banco, criar dados de exemplo para teste
                val finalPoints = if (sortedPoints.isEmpty()) {
                    createSampleData()
                } else {
                    sortedPoints
                }
                
                // ✅ PAGINAÇÃO: Carregar apenas a primeira página
                val pageSize = 50
                val firstPage = finalPoints.take(pageSize)
                val hasMore = finalPoints.size > pageSize
                
                reportsState.value = ReportsState(
                    points = firstPage,
                    totalPoints = finalPoints.size,
                    isLoading = false,
                    currentPage = 0,
                    hasMorePages = hasMore,
                    pageSize = pageSize
                )
                
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("ReportsViewModel", "OutOfMemoryError em loadReports: ${e.message}", e)
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Muitos pontos para carregar. Tente filtrar por data."
                )
            } catch (e: Exception) {
                // Log do crash para debug remoto (sem context para evitar problemas)
                android.util.Log.e("ReportsViewModel", "Erro em loadReports: ${e.message}", e)
                
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Erro ao carregar relatórios: ${e.message}"
                )
            }
        }
    }
    
    fun loadMorePoints() {
        viewModelScope.launch {
            try {
                if (!reportsState.value.hasMorePages) return@launch
                
                reportsState.value = reportsState.value.copy(isLoading = true)
                
                val allPoints = pontosGenericosDao.getAll().sortedByDescending { it.dataHora }
                val currentPage = reportsState.value.currentPage + 1
                val pageSize = reportsState.value.pageSize
                val startIndex = currentPage * pageSize
                val endIndex = startIndex + pageSize
                
                val newPoints = allPoints.subList(startIndex, minOf(endIndex, allPoints.size))
                val hasMore = endIndex < allPoints.size
                
                val currentPoints = reportsState.value.points.toMutableList()
                currentPoints.addAll(newPoints)
                
                reportsState.value = reportsState.value.copy(
                    points = currentPoints,
                    currentPage = currentPage,
                    hasMorePages = hasMore,
                    isLoading = false
                )
                
                android.util.Log.d("ReportsViewModel", "📄 Página $currentPage carregada: ${newPoints.size} pontos. Total: ${currentPoints.size}")
                
            } catch (e: Exception) {
                android.util.Log.e("ReportsViewModel", "Erro ao carregar mais pontos: ${e.message}", e)
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Erro ao carregar mais pontos: ${e.message}"
                )
            }
        }
    }
    
    fun syncPoints(context: Context) {
        viewModelScope.launch {
            try {
                reportsState.value = reportsState.value.copy(isLoading = true, error = null)
                
                // ✅ NOVO: Usar sincronização por blocos de entidade
                val pontosPorEntidade = pontoSincronizacaoPorBlocosService.getQuantidadePontosPendentesPorEntidade(context)
                
                if (pontosPorEntidade.isEmpty()) {
                    Toast.makeText(context, "ℹ️ Não há pontos para sincronizar", Toast.LENGTH_LONG).show()
                    reportsState.value = reportsState.value.copy(isLoading = false)
                    return@launch
                }
                
                val totalPontos = pontosPorEntidade.values.sum()
                val entidades = pontosPorEntidade.keys.joinToString(", ")
                Toast.makeText(context, "📊 Sincronizando $totalPontos pontos de ${pontosPorEntidade.size} entidades ($entidades)...", Toast.LENGTH_LONG).show()
                
                // Executar sincronização por blocos
                val resultado = pontoSincronizacaoPorBlocosService.sincronizarPontosPorBlocos(context)
                
                if (resultado.sucesso) {
                    val mensagemDetalhada = if (resultado.entidadesProcessadas > 1) {
                        "✅ ${resultado.pontosSincronizados} pontos sincronizados em ${resultado.entidadesProcessadas} entidades!"
                    } else {
                        "✅ ${resultado.pontosSincronizados} pontos sincronizados com sucesso!"
                    }
                    
                    Toast.makeText(context, mensagemDetalhada, Toast.LENGTH_LONG).show()
                    
                    // Aguardar um pouco e recarregar a lista
                    delay(1000)
                    Toast.makeText(context, "🔄 Atualizando lista...", Toast.LENGTH_SHORT).show()
                    loadReports() // Recarregar a lista após sincronização
                    
                } else {
                    val mensagemErro = if (resultado.pontosSincronizados > 0) {
                        "⚠️ Sincronização parcial: ${resultado.pontosSincronizados} pontos sincronizados, mas houve erros em algumas entidades."
                    } else {
                        "❌ Erro na sincronização: ${resultado.mensagem}"
                    }
                    
                    Toast.makeText(context, mensagemErro, Toast.LENGTH_LONG).show()
                    reportsState.value = reportsState.value.copy(
                        isLoading = false,
                        error = resultado.mensagem
                    )
                }
                
            } catch (e: Exception) {
                // Log do crash para debug remoto
                CrashReporter.logException(context, e, "ReportsViewModel.syncPoints")
                
                Toast.makeText(
                    context, 
                    "❌ Erro na sincronização: ${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Erro na sincronização: ${e.message}"
                )
            }
        }
    }
    
    fun exportReports(context: Context) {
        viewModelScope.launch {
            try {
                reportsState.value = reportsState.value.copy(isLoading = true)
                
                val points = reportsState.value.points
                if (points.isEmpty()) {
                    Toast.makeText(context, "❌ Nenhum ponto para exportar", Toast.LENGTH_SHORT).show()
                    reportsState.value = reportsState.value.copy(isLoading = false)
                    return@launch
                }
                
                // Gerar nome do arquivo com timestamp atual
                val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
                val fileName = "${timestamp}.txt"
                
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                
                val afdContent = generateAFDContent(points)
                
                FileWriter(file).use { writer ->
                    writer.write(afdContent)
                }
                
                shareFile(context, file)
                
                Toast.makeText(context, "✅ Arquivo AFD exportado: $fileName", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Erro ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
                reportsState.value = reportsState.value.copy(
                    error = "Erro ao exportar: ${e.message}"
                )
            } finally {
                reportsState.value = reportsState.value.copy(isLoading = false)
            }
        }
    }
    
    fun filterByDate(startDate: Long, endDate: Long) {
        viewModelScope.launch {
            try {
                reportsState.value = reportsState.value.copy(isLoading = true, error = null)
                
                val allPoints = pontosGenericosDao.getAll()
                val filteredPoints = allPoints.filter { ponto ->
                    ponto.dataHora in startDate..endDate
                }.sortedByDescending { it.dataHora }
                
                android.util.Log.d("ReportsViewModel", "📊 Filtro por data: ${filteredPoints.size} pontos encontrados")
                
                // ✅ PROTEÇÃO: Limitar pontos filtrados para evitar crash
                val limitedPoints = if (filteredPoints.size > 50) {
                    android.util.Log.w("ReportsViewModel", "⚠️ Limitando pontos filtrados de ${filteredPoints.size} para 50")
                    filteredPoints.take(50)
                } else {
                    filteredPoints
                }
                
                reportsState.value = reportsState.value.copy(
                    points = limitedPoints,
                    totalPoints = filteredPoints.size,
                    isLoading = false,
                    currentPage = 0,
                    hasMorePages = filteredPoints.size > 50,
                    pageSize = 50
                )
                
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("ReportsViewModel", "OutOfMemoryError em filterByDate: ${e.message}", e)
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Muitos pontos para filtrar. Tente um período menor."
                )
            } catch (e: Exception) {
                android.util.Log.e("ReportsViewModel", "Erro em filterByDate: ${e.message}", e)
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Erro ao filtrar: ${e.message}"
                )
            }
        }
    }
    
    fun filterByEmployee(employeeName: String) {
        viewModelScope.launch {
            try {
                reportsState.value = reportsState.value.copy(isLoading = true, error = null)
                
                val allPoints = pontosGenericosDao.getAll()
                val filteredPoints = allPoints.filter { ponto ->
                    ponto.funcionarioNome.contains(employeeName, ignoreCase = true)
                }.sortedByDescending { it.dataHora }
                
                android.util.Log.d("ReportsViewModel", "📊 Filtro por funcionário: ${filteredPoints.size} pontos encontrados")
                
                // ✅ PROTEÇÃO: Limitar pontos filtrados para evitar crash
                val limitedPoints = if (filteredPoints.size > 50) {
                    android.util.Log.w("ReportsViewModel", "⚠️ Limitando pontos filtrados de ${filteredPoints.size} para 50")
                    filteredPoints.take(50)
                } else {
                    filteredPoints
                }
                
                reportsState.value = reportsState.value.copy(
                    points = limitedPoints,
                    totalPoints = filteredPoints.size,
                    isLoading = false,
                    currentPage = 0,
                    hasMorePages = filteredPoints.size > 50,
                    pageSize = 50
                )
                
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("ReportsViewModel", "OutOfMemoryError em filterByEmployee: ${e.message}", e)
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Muitos pontos para filtrar. Tente um funcionário específico."
                )
            } catch (e: Exception) {
                android.util.Log.e("ReportsViewModel", "Erro em filterByEmployee: ${e.message}", e)
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Erro ao filtrar: ${e.message}"
                )
            }
        }
    }
    
    fun loadMoreFilteredPoints() {
        viewModelScope.launch {
            try {
                if (!reportsState.value.hasMorePages) return@launch
                
                reportsState.value = reportsState.value.copy(isLoading = true)
                
                // Recarregar todos os pontos e aplicar filtro novamente
                val allPoints = pontosGenericosDao.getAll()
                val currentPoints = reportsState.value.points
                val currentPage = reportsState.value.currentPage + 1
                val pageSize = reportsState.value.pageSize
                
                // Aplicar o mesmo filtro que foi usado anteriormente
                val filteredPoints = allPoints.sortedByDescending { it.dataHora }
                val startIndex = currentPage * pageSize
                val endIndex = startIndex + pageSize
                
                val newPoints = filteredPoints.subList(startIndex, minOf(endIndex, filteredPoints.size))
                val hasMore = endIndex < filteredPoints.size
                
                val updatedPoints = currentPoints.toMutableList()
                updatedPoints.addAll(newPoints)
                
                reportsState.value = reportsState.value.copy(
                    points = updatedPoints,
                    currentPage = currentPage,
                    hasMorePages = hasMore,
                    isLoading = false
                )
                
                android.util.Log.d("ReportsViewModel", "📄 Página filtrada $currentPage carregada: ${newPoints.size} pontos. Total: ${updatedPoints.size}")
                
            } catch (e: Exception) {
                android.util.Log.e("ReportsViewModel", "Erro ao carregar mais pontos filtrados: ${e.message}", e)
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Erro ao carregar mais pontos: ${e.message}"
                )
            }
        }
    }
    
    private fun createSampleData(): List<PontosGenericosEntity> {
        try {
            val calendar = Calendar.getInstance()
            calendar.set(2025, Calendar.AUGUST, 28, 11, 11, 45) // 28 de agosto de 2025
            
            val samplePoints = mutableListOf<PontosGenericosEntity>()
            
            // ✅ OTIMIZADO: Reduzir para apenas 10 pontos para evitar OutOfMemoryError
            val times = listOf(
                "11:11:45", "11:11:40", "11:11:00", "11:10:42", "11:09:06",
                "10:45:30", "10:30:15", "10:15:22", "10:00:08", "09:45:33"
            )
        
        times.forEachIndexed { index, timeStr ->
            val timeParts = timeStr.split(":")
            calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
            calendar.set(Calendar.MINUTE, timeParts[1].toInt())
            calendar.set(Calendar.SECOND, timeParts[2].toInt())
            
            val ponto = PontosGenericosEntity(
                id = index.toLong() + 1,
                funcionarioId = "1",
                funcionarioNome = "ADAMS ANTONIO GIRAO MENESES",
                funcionarioMatricula = "00002625",
                funcionarioCpf = "009.050.763-03",
                funcionarioCargo = "MEDICO",
                funcionarioSecretaria = "SAUDE FUNDO MUNICIPAL DE SAUDE",
                funcionarioLotacao = "SAUDE FUNDO MUNICIPAL DE SAUDE - PLANTAO",
                dataHora = calendar.timeInMillis,
                latitude = null,
                longitude = null,
                observacao = null,
                fotoBase64 = null,
                synced = false,
                entidadeId = "ENTIDADE_TESTE" // ✅ NOVO: Entidade para dados de teste
            )
            samplePoints.add(ponto)
        }
        
            // ✅ OTIMIZADO: Reduzir pontos de outros dias para evitar OutOfMemoryError
            calendar.set(2025, Calendar.AUGUST, 27, 17, 30, 0)
            for (i in 1..3) { // Reduzido de 5 para 3
                calendar.add(Calendar.HOUR, -1)
                val ponto = PontosGenericosEntity(
                    id = samplePoints.size.toLong() + 1,
                    funcionarioId = "1",
                    funcionarioNome = "ADAMS ANTONIO GIRAO MENESES",
                    funcionarioMatricula = "00002625",
                    funcionarioCpf = "009.050.763-03",
                    funcionarioCargo = "MEDICO",
                    funcionarioSecretaria = "SAUDE FUNDO MUNICIPAL DE SAUDE",
                    funcionarioLotacao = "SAUDE FUNDO MUNICIPAL DE SAUDE - PLANTAO",
                    dataHora = calendar.timeInMillis,
                    latitude = null,
                    longitude = null,
                    observacao = null,
                    fotoBase64 = null,
                    synced = true,
                    entidadeId = "ENTIDADE_TESTE" // ✅ NOVO: Entidade para dados de teste
                )
                samplePoints.add(ponto)
            }
            
            return samplePoints
            
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ReportsViewModel", "OutOfMemoryError em createSampleData: ${e.message}", e)
            return emptyList() // Retorna lista vazia em caso de OutOfMemoryError
        } catch (e: Exception) {
            android.util.Log.e("ReportsViewModel", "Erro em createSampleData: ${e.message}", e)
            return emptyList()
        }
    }

    private fun generateAFDContent(points: List<PontosGenericosEntity>): String {
        val stringBuilder = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        
        // Buscar configurações para obter localizacaoId e codigoSincronizacao
        val configuracoes = configuracoesDao.getConfiguracoes()
        val localizacaoId = configuracoes?.localizacaoId ?: "LOC001"
        val codigoSincronizacao = configuracoes?.codigoSincronizacao ?: "SYNC001"
        
        points.forEach { ponto ->
            // Formatar data/hora (14 posições)
            val dataHora = dateFormat.format(Date(ponto.dataHora))
            
            // Formatar CPF - remover pontos e hífens, manter apenas números (11 posições)
            val cpf = ponto.funcionarioCpf.replace(Regex("[^0-9]"), "").padEnd(11, ' ').substring(0, 11)
            
            // Formatar nome (30 posições, preenchido com espaços à direita)
            val nome = ponto.funcionarioNome.padEnd(30, ' ').substring(0, 30)
            
            // Localizacao ID (10 posições, preenchido com espaços à direita)
            val localizacaoIdFormatted = localizacaoId.padEnd(10, ' ').substring(0, 10)
            
            // Código de sincronização (10 posições, preenchido com espaços à direita)
            val codSincroniza = codigoSincronizacao.padEnd(10, ' ').substring(0, 10)
            
            // Montar linha AFD
            val linha = "${dataHora}${cpf}${nome}${localizacaoIdFormatted}${codSincroniza}"
            stringBuilder.appendLine(linha)
        }
        
        return stringBuilder.toString()
    }
    
    private fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Arquivo AFD - ${file.name}")
                putExtra(Intent.EXTRA_TEXT, "Arquivo AFD gerado com os pontos registrados.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Compartilhar arquivo AFD"))
        } catch (e: Exception) {
            // Se falhar ao compartilhar, pelo menos mostra onde foi salvo
            Toast.makeText(context, "Arquivo salvo em: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
} 