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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

private fun formatCPF(cpf: String): String {
    return if (cpf.length >= 11) {
        "${cpf.substring(0, 3)}.***.***-${cpf.substring(9, 11)}"
    } else {
        cpf
    }
}

enum class FilterType {
    DATE_RANGE, EMPLOYEE, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR
}

enum class PeriodFilter(
    val label: String,
    val icon: ImageVector
) {
    TODAY("Hoje", Icons.Default.Today),
    THIS_WEEK("Esta Semana", Icons.Default.DateRange),
    THIS_MONTH("Este Mês", Icons.Default.CalendarMonth),
    THIS_YEAR("Este Ano", Icons.Default.CalendarToday),
    CUSTOM("Período Personalizado", Icons.Default.DateRange)
}

sealed class ActiveFilter {
    data class DATE_RANGE(val startDate: Date, val endDate: Date) : ActiveFilter()
    data class EMPLOYEE(val employeeName: String) : ActiveFilter()
}

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
    
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEmployeePicker by remember { mutableStateOf(false) }
    var selectedStartDate by remember { mutableStateOf<Date?>(null) }
    var selectedEndDate by remember { mutableStateOf<Date?>(null) }
    var selectedEmployee by remember { mutableStateOf<String?>(null) }
    var selectedPeriod by remember { mutableStateOf<PeriodFilter?>(null) }
    
    // Lista de filtros ativos
    var activeFilters by remember { mutableStateOf<List<ActiveFilter>>(emptyList()) }
    
    // Lista de funcionários únicos para o filtro
    val uniqueEmployees = remember(reportsState.points) {
        reportsState.points.map { it.funcionarioNome }.distinct().sorted()
    }
    
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
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF264064)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${reportsState.totalPoints} pontos encontrados",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp
                        )
                    }
                    
                    // Dropdown/filtro
                    Box {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filtros",
                                tint = Color(0xFF264064)
                            )
                        }
                        
                        // Indicador de filtro ativo
                        if (activeFilters.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        Color.Red,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .align(Alignment.TopEnd)
                            )
                        }
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
                    containerColor = Color(0xFF264064),
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
                // Seção de Filtros Ativos
                if (activeFilters.isNotEmpty()) {
                    item {
                        ActiveFiltersSection(
                            activeFilters = activeFilters,
                            onRemoveFilter = { filterToRemove ->
                                activeFilters = activeFilters.filter { it != filterToRemove }
                                applyFilters(viewModel, activeFilters.filter { it != filterToRemove })
                            },
                            onClearAllFilters = {
                                activeFilters = emptyList()
                                selectedStartDate = null
                                selectedEndDate = null
                                selectedEmployee = null
                                selectedPeriod = null
                                viewModel.loadReports()
                            }
                        )
                    }
                }
                
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
    
    // Diálogo de seleção de filtros
    if (showFilterDialog) {
        FilterDialog(
            onDismiss = { showFilterDialog = false },
            onFilterByPeriod = { period ->
                selectedPeriod = period
                showFilterDialog = false
                
                when (period) {
                    PeriodFilter.TODAY, PeriodFilter.THIS_WEEK, PeriodFilter.THIS_MONTH, PeriodFilter.THIS_YEAR -> {
                        // Adicionar filtro de período à lista
                        val periodFilter = applyPeriodFilter(viewModel, period)
                        activeFilters = activeFilters.filter { it !is ActiveFilter.DATE_RANGE } + periodFilter
                        applyFilters(viewModel, activeFilters)
                    }
                    PeriodFilter.CUSTOM -> {
                        showDatePicker = true
                    }
                }
            },
            onFilterByEmployee = { 
                showFilterDialog = false
                showEmployeePicker = true 
            },
            onClearFilters = {
                activeFilters = emptyList()
                selectedStartDate = null
                selectedEndDate = null
                selectedEmployee = null
                selectedPeriod = null
                showFilterDialog = false
                viewModel.loadReports()
            },
            hasActiveFilters = activeFilters.isNotEmpty()
        )
    }
    
    // Date Picker
    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateSelected = { startDate, endDate ->
                selectedStartDate = startDate
                selectedEndDate = endDate
                selectedPeriod = PeriodFilter.CUSTOM
                showDatePicker = false
                
                // Adicionar filtro de data à lista
                val dateFilter = ActiveFilter.DATE_RANGE(startDate, endDate)
                activeFilters = activeFilters.filter { it !is ActiveFilter.DATE_RANGE } + dateFilter
                applyFilters(viewModel, activeFilters)
            }
        )
    }
    
    // Employee Picker
    if (showEmployeePicker) {
        EmployeePickerDialog(
            employees = uniqueEmployees,
            onDismiss = { showEmployeePicker = false },
            onEmployeeSelected = { employee ->
                selectedEmployee = employee
                showEmployeePicker = false
                
                // Adicionar filtro de funcionário à lista
                val employeeFilter = ActiveFilter.EMPLOYEE(employee)
                activeFilters = activeFilters.filter { it !is ActiveFilter.EMPLOYEE } + employeeFilter
                applyFilters(viewModel, activeFilters)
            }
        )
    }
}

private fun applyPeriodFilter(viewModel: ReportsViewModel, period: PeriodFilter): ActiveFilter {
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    
    return when (period) {
        PeriodFilter.TODAY -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfDay = calendar.timeInMillis
            
            ActiveFilter.DATE_RANGE(Date(startOfDay), Date(endOfDay))
        }
        PeriodFilter.THIS_WEEK -> {
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfWeek = calendar.timeInMillis
            
            calendar.add(Calendar.DAY_OF_WEEK, 6)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfWeek = calendar.timeInMillis
            
            ActiveFilter.DATE_RANGE(Date(startOfWeek), Date(endOfWeek))
        }
        PeriodFilter.THIS_MONTH -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfMonth = calendar.timeInMillis
            
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfMonth = calendar.timeInMillis
            
            ActiveFilter.DATE_RANGE(Date(startOfMonth), Date(endOfMonth))
        }
        PeriodFilter.THIS_YEAR -> {
            calendar.set(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfYear = calendar.timeInMillis
            
            calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR))
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfYear = calendar.timeInMillis
            
            ActiveFilter.DATE_RANGE(Date(startOfYear), Date(endOfYear))
        }
        PeriodFilter.CUSTOM -> {
            // Será tratado no date picker
            ActiveFilter.DATE_RANGE(Date(), Date())
        }
    }
}

private fun applyFilters(viewModel: ReportsViewModel, filters: List<ActiveFilter>) {
    if (filters.isEmpty()) {
        viewModel.loadReports()
        return
    }
    
    // Aplicar todos os filtros
    filters.forEach { filter ->
        when (filter) {
            is ActiveFilter.DATE_RANGE -> {
                viewModel.filterByDate(filter.startDate.time, filter.endDate.time)
            }
            is ActiveFilter.EMPLOYEE -> {
                viewModel.filterByEmployee(filter.employeeName)
            }
        }
    }
}

@Composable
private fun FilterDialog(
    onDismiss: () -> Unit,
    onFilterByPeriod: (PeriodFilter) -> Unit,
    onFilterByEmployee: () -> Unit,
    onClearFilters: () -> Unit,
    hasActiveFilters: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filtros",
                    tint = Color(0xFF264064),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Filtros",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF264064)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Seção de Filtros por Período
                Text(
                    text = "Filtrar por Período",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Grid de períodos
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PeriodFilterCard(
                            period = PeriodFilter.TODAY,
                            onClick = { onFilterByPeriod(PeriodFilter.TODAY) },
                            modifier = Modifier.weight(1f)
                        )
                        PeriodFilterCard(
                            period = PeriodFilter.THIS_WEEK,
                            onClick = { onFilterByPeriod(PeriodFilter.THIS_WEEK) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PeriodFilterCard(
                            period = PeriodFilter.THIS_MONTH,
                            onClick = { onFilterByPeriod(PeriodFilter.THIS_MONTH) },
                            modifier = Modifier.weight(1f)
                        )
                        PeriodFilterCard(
                            period = PeriodFilter.THIS_YEAR,
                            onClick = { onFilterByPeriod(PeriodFilter.THIS_YEAR) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    PeriodFilterCard(
                        period = PeriodFilter.CUSTOM,
                        onClick = { onFilterByPeriod(PeriodFilter.CUSTOM) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Divider()
                
                // Filtro por funcionário
                Text(
                    text = "Filtrar por Funcionário",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF264064)
                )
                
                Card(
                    onClick = onFilterByEmployee,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Filtrar por funcionário",
                            tint = Color(0xFF264064),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Selecionar Funcionário",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Abrir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Limpar filtros (só aparece se há filtros ativos)
                if (hasActiveFilters) {
                    Divider()
                    Card(
                        onClick = onClearFilters,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar filtros",
                                tint = Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Limpar Todos os Filtros",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.Red
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun PeriodFilterCard(
    period: PeriodFilter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = period.icon,
                contentDescription = period.label,
                tint = Color(0xFF264064),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = period.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Date, Date) -> Unit
) {
    var startDate by remember { mutableStateOf<Date?>(null) }
    var endDate by remember { mutableStateOf<Date?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Período personalizado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Período Personalizado",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Data inicial
                Card(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Data inicial",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Data Inicial",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = startDate?.let { dateFormat.format(it) } ?: "Selecionar data",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Abrir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Data final
                Card(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Data final",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Data Final",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = endDate?.let { dateFormat.format(it) } ?: "Selecionar data",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Abrir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (startDate != null && endDate != null) {
                        onDateSelected(startDate!!, endDate!!)
                    }
                },
                enabled = startDate != null && endDate != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Aplicar",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aplicar Filtro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
    
    // Date Pickers
    if (showStartPicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = Date(millis)
                        }
                        showStartPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (showEndPicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            endDate = Date(millis)
                        }
                        showEndPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun EmployeePickerDialog(
    employees: List<String>,
    onDismiss: () -> Unit,
    onEmployeeSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredEmployees = remember(employees, searchQuery) {
        if (searchQuery.isBlank()) {
            employees
        } else {
            employees.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Funcionários",
                    tint = Color(0xFF264064),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Selecionar Funcionário",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                // Campo de busca
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar funcionário") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Lista de funcionários
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredEmployees) { employee ->
                        Card(
                            onClick = { onEmployeeSelected(employee) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Funcionário",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF264064)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = employee,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForwardIos,
                                    contentDescription = "Selecionar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Data",
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF264064)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dateFormat.format(date),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF264064)
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
                    text = "CPF: ${formatCPF(ponto.funcionarioCpf)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                
                Text(
                    text = "Órgão: ${ponto.funcionarioSecretaria}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                // Cargo e Lotação
                Text(
                    text = "Cargo: ${ponto.funcionarioCargo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                )

                Text(
                    text = "Lotação: ${ponto.funcionarioLotacao}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                )
                
//                Text(
//                    text = "Data: ${dateFormat.format(Date(ponto.dataHora))}",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = Color(0xFF666666)
//                )
            }
            
            // Lado direito - Detalhes adicionais e status
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 16.dp)
            ) {

                // Horário grande (azul)
                Text(
                    text = timeFormat.format(Date(ponto.dataHora)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF264064) // Azul
                )
                Text(
                    text = "${dateFormat.format(Date(ponto.dataHora))}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF666666),
                )
                // Status (laranja/amarelo)
                Text(
                    text = if (ponto.synced) "Sincronizado" else "Pendente",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ponto.synced) Color(0xFF264064) else Color(0xFFFF9800) // Laranja para pendente
                )
            }
        }
    }
}

@Composable
private fun ActiveFiltersSection(
    activeFilters: List<ActiveFilter>,
    onRemoveFilter: (ActiveFilter) -> Unit,
    onClearAllFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chips para cada filtro ativo
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            activeFilters.forEach { filter ->
                when (filter) {
                    is ActiveFilter.DATE_RANGE -> {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                        
                        // Chip para data inicial
                        FilterChip(
                            label = {
                                Text(
                                    text = dateFormat.format(filter.startDate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            },
                            selected = true,
                            onClick = { },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5bc0de),
                                selectedLabelColor = Color.White
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = "Data inicial",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveFilter(filter) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remover filtro",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        )
                        
                        // Chip para data final
                        FilterChip(
                            label = {
                                Text(
                                    text = dateFormat.format(filter.endDate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            },
                            selected = true,
                            onClick = { },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5bc0de),
                                selectedLabelColor = Color.White
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = "Data final",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveFilter(filter) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remover filtro",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        )
                    }
                    is ActiveFilter.EMPLOYEE -> {
                        FilterChip(
                            label = {
                                Text(
                                    text = filter.employeeName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = true,
                            onClick = { },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5bc0de),
                                selectedLabelColor = Color.White
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = "Funcionário",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveFilter(filter) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remover filtro",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
