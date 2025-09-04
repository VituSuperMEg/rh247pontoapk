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
    val error: String? = null
)

@KoinViewModel
class ReportsViewModel(
    private val pontosGenericosDao: PontosGenericosDao,
    private val configuracoesDao: ConfiguracoesDao
) : ViewModel() {
    
    val reportsState = mutableStateOf(ReportsState())
    private val pontoSincronizacaoService = PontoSincronizacaoService()
    
    fun loadReports() {
        viewModelScope.launch {
            try {
                reportsState.value = reportsState.value.copy(isLoading = true, error = null)
                
                val points = pontosGenericosDao.getAll()
                
                // Se não há pontos no banco, criar dados de exemplo para teste
                val finalPoints = if (points.isEmpty()) {
                    createSampleData()
                } else {
                    points
                }
                
                val sortedPoints = finalPoints.sortedByDescending { it.dataHora }
                
                reportsState.value = ReportsState(
                    points = sortedPoints,
                    totalPoints = sortedPoints.size,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                reportsState.value = reportsState.value.copy(
                    isLoading = false,
                    error = "Erro ao carregar relatórios: ${e.message}"
                )
            }
        }
    }
    
    fun syncPoints(context: Context) {
        viewModelScope.launch {
            try {
                reportsState.value = reportsState.value.copy(isLoading = true, error = null)
                
                // Verificar quantidade de pontos pendentes
                val pontosPendentes = pontoSincronizacaoService.getQuantidadePontosPendentes(context)
                
                if (pontosPendentes == 0) {
                    Toast.makeText(context, "ℹ️ Não há pontos para sincronizar", Toast.LENGTH_LONG).show()
                    reportsState.value = reportsState.value.copy(isLoading = false)
                    return@launch
                }
                
                Toast.makeText(context, "📊 Sincronizando $pontosPendentes pontos...", Toast.LENGTH_LONG).show()
                
                // Executar sincronização
                val resultado = pontoSincronizacaoService.sincronizarPontosPendentes(context)
                
                if (resultado.sucesso) {
                    Toast.makeText(
                        context, 
                        "✅ ${resultado.quantidadePontos} pontos sincronizados com sucesso!", 
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Aguardar um pouco e recarregar a lista
                    delay(1000)
                    Toast.makeText(context, "🔄 Atualizando lista...", Toast.LENGTH_SHORT).show()
                    loadReports() // Recarregar a lista após sincronização
                    
                } else {
                    Toast.makeText(
                        context, 
                        "❌ Erro na sincronização: ${resultado.mensagem}", 
                        Toast.LENGTH_LONG
                    ).show()
                    reportsState.value = reportsState.value.copy(
                        isLoading = false,
                        error = resultado.mensagem
                    )
                }
                
            } catch (e: Exception) {
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
                val allPoints = pontosGenericosDao.getAll()
                val filteredPoints = allPoints.filter { ponto ->
                    ponto.dataHora in startDate..endDate
                }.sortedByDescending { it.dataHora }
                
                reportsState.value = reportsState.value.copy(
                    points = filteredPoints,
                    totalPoints = filteredPoints.size
                )
            } catch (e: Exception) {
                reportsState.value = reportsState.value.copy(
                    error = "Erro ao filtrar: ${e.message}"
                )
            }
        }
    }
    
    fun filterByEmployee(employeeName: String) {
        viewModelScope.launch {
            try {
                val allPoints = pontosGenericosDao.getAll()
                val filteredPoints = allPoints.filter { ponto ->
                    ponto.funcionarioNome.contains(employeeName, ignoreCase = true)
                }.sortedByDescending { it.dataHora }
                
                reportsState.value = reportsState.value.copy(
                    points = filteredPoints,
                    totalPoints = filteredPoints.size
                )
            } catch (e: Exception) {
                reportsState.value = reportsState.value.copy(
                    error = "Erro ao filtrar: ${e.message}"
                )
            }
        }
    }
    
    private fun createSampleData(): List<PontosGenericosEntity> {
        val calendar = Calendar.getInstance()
        calendar.set(2025, Calendar.AUGUST, 28, 11, 11, 45) // 28 de agosto de 2025
        
        val samplePoints = mutableListOf<PontosGenericosEntity>()
        
        // Criar 25 pontos para o ADAMS no dia 28 de agosto
        val times = listOf(
            "11:11:45", "11:11:40", "11:11:00", "11:10:42", "11:09:06", "11:09:03",
            "10:45:30", "10:30:15", "10:15:22", "10:00:08", "09:45:33", "09:30:17",
            "09:15:44", "09:00:29", "08:45:11", "08:30:55", "08:15:38", "08:00:12",
            "07:45:26", "07:30:49", "07:15:03", "07:00:37", "06:45:21", "06:30:14",
            "06:15:58"
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
                tipoPonto = "PONTO",
                latitude = -6.377917793252374,
                longitude = -39.316891286420876,
                observacao = null,
                fotoBase64 = null,
                synced = false
            )
            samplePoints.add(ponto)
        }
        
        // Adicionar alguns pontos de outros dias para mostrar agrupamento
        calendar.set(2025, Calendar.AUGUST, 27, 17, 30, 0)
        for (i in 1..5) {
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
                tipoPonto = "PONTO",
                latitude = -6.377917793252374,
                longitude = -39.316891286420876,
                observacao = null,
                fotoBase64 = null,
                synced = true
            )
            samplePoints.add(ponto)
        }
        
        return samplePoints
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