package com.ml.shubham0204.facenet_android.presentation.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onExportClick: () -> Unit = {}
) {
    val viewModel: ReportsViewModel = koinViewModel()
    val reportsState by remember { viewModel.reportsState }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.loadReports()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pontos Registrados",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    // Ícone de gráfico e contador
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Gráfico",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${reportsState.totalPoints} pontos encontrados",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp
                        )
                    }
                    
                    // Dropdown/filtro
                    IconButton(onClick = { /* TODO: Implementar filtros */ }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtros"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(end = 16.dp)
            ) {
                // Floating Action Button para Exportar
                FloatingActionButton(
                    onClick = { viewModel.exportReports(context) },
                    containerColor = Color.Green,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Exportar",
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Floating Action Button para Sincronizar
                FloatingActionButton(
                    onClick = { viewModel.syncPoints(context) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sincronizar",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val groupedPoints = reportsState.points.groupBy { ponto ->
                    val calendar = Calendar.getInstance().apply { timeInMillis = ponto.dataHora }
                    calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
                }
                
                groupedPoints.forEach { (dateKey, points) ->
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.YEAR, dateKey.first)
                        set(Calendar.DAY_OF_YEAR, dateKey.second)
                    }
                    
                    // Header da data
                    item {
                        DateHeader(
                            date = calendar.time,
                            pointCount = points.size
                        )
                    }
                    
                    // Lista de pontos da data
                    items(points) { ponto ->
                        PointCard(ponto = ponto)
                    }
                }
            }
            
            // Indicador de carregamento
            if (reportsState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(
    date: Date,
    pointCount: Int
) {
    val dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lado esquerdo - Data com ícone de relógio
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Data",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dateFormat.format(date),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Lado direito - Contagem de pontos
        Text(
            text = "$pointCount pontos",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF666666)
        )
    }
}

@Composable
private fun PointCard(
    ponto: PontosGenericosEntity
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeDisplayFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Lado esquerdo - Detalhes principais
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Nome do funcionário com ícone de relógio
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Horário",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ponto.funcionarioNome,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Informações do funcionário
                Text(
                    text = "Matrícula: ${ponto.funcionarioMatricula}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                
                Text(
                    text = "CPF: ${ponto.funcionarioCpf}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                
                Text(
                    text = "Secretaria: ${ponto.funcionarioSecretaria}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                
                Text(
                    text = "Data: ${dateFormat.format(Date(ponto.dataHora))} ${timeFormat.format(Date(ponto.dataHora))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
            }
            
            // Lado direito - Detalhes adicionais e status
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                // Cargo e Lotação
                Text(
                    text = "Cargo: ${ponto.funcionarioCargo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.End
                )
                
                Text(
                    text = "Lotação: ${ponto.funcionarioLotacao}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.End
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Horário grande (azul)
                Text(
                    text = timeDisplayFormat.format(Date(ponto.dataHora)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3) // Azul
                )
                
                // Status (laranja/amarelo)
                Text(
                    text = if (ponto.synced) "Sincronizado" else "Pendente",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ponto.synced) Color.Green else Color(0xFFFF9800) // Laranja para pendente
                )
            }
        }
    }
} 