package com.ml.shubham0204.facenet_android.presentation.screens.reports

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.PontosGenericosDao
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.service.PontoSincronizacaoService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.util.Calendar

data class ReportsState(
    val points: List<PontosGenericosEntity> = emptyList(),
    val totalPoints: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@KoinViewModel
class ReportsViewModel(
    private val pontosGenericosDao: PontosGenericosDao
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
                // TODO: Implementar exportação de relatórios
                // Pode ser CSV, PDF, etc.
                Toast.makeText(context, "📤 Exportação em desenvolvimento...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                reportsState.value = reportsState.value.copy(
                    error = "Erro ao exportar: ${e.message}"
                )
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
} 