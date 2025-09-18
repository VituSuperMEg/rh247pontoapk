package com.ml.shubham0204.facenet_android.presentation.screens.import_employees

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.data.api.FuncionariosModel
import com.ml.shubham0204.facenet_android.data.api.OrgaoModel
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEmployeesScreen(
    onNavigateBack: () -> Unit
) {

    var showFilterDialog by remember { mutableStateOf(false) }
    var showFilterOrgao by remember { mutableStateOf(false) }
    var showFilterEntidade by remember { mutableStateOf(false) }
    
    val viewModel: ImportEmployeesViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Importar Funcionários",
                            style = MaterialTheme.typography.headlineSmall
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
                        IconButton(
                            onClick = { /* TODO: Implementar sincronização */ }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar"
                            )
                        }
                        Box {
                            BadgedBox(
                                badge = {
                                    if (uiState.selectedOrgao != null || uiState.selectedEntidade != null) {
                                        Badge(
                                            containerColor = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            ) {
                                IconButton(onClick = { showFilterDialog = true}) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filtros",
                                        tint = if (uiState.selectedOrgao != null || uiState.selectedEntidade != null) Color(0xFF4CAF50) else Color(0xFF264064)
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                // Mostrar FAB apenas na segunda aba e quando há funcionários na lista
                if (uiState.selectedTabIndex == 1 && uiState.funcionariosParaImportar.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { viewModel.importAllFromQueue() },
                        containerColor = Color(0xFF264064),
                        contentColor = Color.White,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (uiState.isImportingBatch) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Importar Todos",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->


            if(showFilterDialog) {
                FilterDialog (
                    onDismiss = { showFilterDialog = false},
                    onOrgaoFilter = {
                        showFilterDialog = false
                        showFilterOrgao = true
                    },
                    onEntidadeFilter = {
                        showFilterDialog = false
                        showFilterEntidade = true
                    },
                    selectedOrgao = uiState.selectedOrgao,
                    selectedEntidade = uiState.selectedEntidade,
                    onClearOrgaoFilter = { viewModel.clearOrgaoFilter() },
                    onClearEntidadeFilter = { viewModel.clearEntidadeFilter() }
                )
            }

            if(showFilterEntidade) {
                FilterEntidade (
                    onDismiss = {
                        showFilterEntidade = false
                        showFilterDialog = true
                    },
                    onEntidadeSelected = { entidade ->
                        viewModel.setSelectedEntidade(entidade)
                        showFilterEntidade = false
                        showFilterDialog = false
                    },
                    viewModel = viewModel
                )
            }

            if(showFilterOrgao) {
                FilterOrgao (
                    onDismiss = {
                        showFilterOrgao = false
                        showFilterDialog = true
                    },
                    onOrgaoSelected = { orgao ->
                        viewModel.setSelectedOrgao(orgao)
                        showFilterOrgao = false
                        showFilterDialog = false
                    },
                    viewModel = viewModel
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // TabRow para as duas abas
                TabRow(
                    selectedTabIndex = uiState.selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = uiState.selectedTabIndex == 0,
                        onClick = { viewModel.updateSelectedTab(0) },
                        text = { Text("Pesquisar Online") }
                    )
                    Tab(
                        selected = uiState.selectedTabIndex == 1,
                        onClick = { viewModel.updateSelectedTab(1) },
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Listagem para Importar")
                                if (uiState.funcionariosParaImportar.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                color = Color.Red,
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = uiState.funcionariosParaImportar.size.toString(),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
                
                // Conteúdo das abas
                when (uiState.selectedTabIndex) {
                    0 -> OnlineSearchTab(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                    1 -> ImportQueueTab(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
            
            // Dialog de confirmação
            if (uiState.showImportDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.hideImportDialog() },
                    title = { Text("Importar Funcionário") },
                    text = { 
                        Text("Deseja importar ${uiState.selectedFuncionario?.nome} ao sistema?") 
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.confirmImport()
                            }
                        ) {
                            Text("Sim")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.hideImportDialog() }
                        ) {
                            Text("Não")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OnlineSearchTab(
    uiState: ImportEmployeesUiState,
    viewModel: ImportEmployeesViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Campo de busca
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Buscar funcionário...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar"
                )
            },
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lista de funcionários
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.funcionarios.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhum funcionário encontrado",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val totalItems = uiState.funcionarios.size
                    
                    val shouldLoad = lastVisibleItem >= totalItems - 5 && 
                                   totalItems > 0 && 
                                   !uiState.isLoadingMore && 
                                   uiState.hasMorePages
                    
                    if (shouldLoad) {
                        Log.d("ImportEmployeesScreen", "🔄 Deve carregar mais:")
                        Log.d("ImportEmployeesScreen", "   - lastVisibleItem: $lastVisibleItem")
                        Log.d("ImportEmployeesScreen", "   - totalItems: $totalItems")
                        Log.d("ImportEmployeesScreen", "   - isLoadingMore: ${uiState.isLoadingMore}")
                        Log.d("ImportEmployeesScreen", "   - hasMorePages: ${uiState.hasMorePages}")
                        Log.d("ImportEmployeesScreen", "   - Diferença: ${totalItems - lastVisibleItem}")
                    }
                    
                    shouldLoad
                }
            }
            
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) {
                    Log.d("ImportEmployeesScreen", "🚀 Carregando mais funcionários...")
                    viewModel.loadMoreFuncionarios()
                }
            }
            
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .collectLatest { visibleItems ->
                        val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0
                        val totalItems = uiState.funcionarios.size

                        if (lastVisibleIndex >= totalItems - 2 && 
                            totalItems > 0 && 
                            !uiState.isLoadingMore && 
                            uiState.hasMorePages) {
                            
                            viewModel.loadMoreFuncionarios()
                        }
                    }
            }
            
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.funcionarios) { funcionario ->
                    FuncionarioCard(
                        funcionario = funcionario,
                        isImported = uiState.importedIds.contains(funcionario.id.toLong()),
                        isInQueue = uiState.funcionariosParaImportar.any { it.id == funcionario.id },
                        onImportClick = { viewModel.importFuncionario(funcionario) },
                        onAddToQueueClick = { viewModel.addToImportQueue(funcionario) }
                    )
                }
                
                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Carregando mais funcionários...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                
                if (!uiState.hasMorePages && uiState.funcionarios.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Todos os funcionários foram carregados",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportQueueTab(
    uiState: ImportEmployeesUiState,
    viewModel: ImportEmployeesViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Lista de funcionários para importar
        if (uiState.funcionariosParaImportar.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhum funcionário na lista de importação",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use o botão \"Adicionar para Importação\" na primeira aba",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.funcionariosParaImportar) { funcionario ->
                    QueuedFuncionarioCard(
                        funcionario = funcionario,
                        onRemoveClick = { viewModel.removeFromImportQueue(funcionario) },
                        onImportClick = { viewModel.importFuncionario(funcionario) }
                    )
                }
            }
        }
    }
}

private fun formatCPF(cpf: String): String {
    return if (cpf.length >= 11) {
        "${cpf.substring(0, 3)}.***.***-${cpf.substring(9, 11)}"
    } else {
        cpf
    }
}

@Composable
private fun FuncionarioCard(
    funcionario: FuncionariosModel,
    isImported: Boolean,
    isInQueue: Boolean,
    onImportClick: () -> Unit,
    onAddToQueueClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isImported -> Color(0xFFE8F5E8)
                isInQueue -> Color(0xFFFFF3CD)
                else -> Color.White
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header com nome e status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = when {
                                isImported -> Color(0xFF4CAF50)
                                isInQueue -> Color(0xFFFFC107)
                                else -> Color(0xFF264064)
                            },
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isImported -> Icons.Default.Check
                            isInQueue -> Icons.Default.PlaylistAdd
                            else -> Icons.Default.Person
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Nome e status
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = funcionario.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = funcionario.cargo_descricao,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                
                // Status de importação
                when {
                    isImported -> {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFF4CAF50),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Já importado",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isInQueue -> {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFFC107),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Na lista",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Informações detalhadas
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Matrícula: ${funcionario.matricula}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "CPF: ${formatCPF(funcionario.numero_cpf)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Cargo: ${funcionario.cargo_descricao}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (funcionario.orgao_descricao != null) {
                    Text(
                        text = "Órgão: ${funcionario.orgao_descricao}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                if (funcionario.setor_descricao != null) {
                    Text(
                        text = "Setor: ${funcionario.setor_descricao}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            // Botões de ação - APENAS para funcionários não importados
            if (!isImported) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Se não está na lista, mostrar botão para adicionar
                if (!isInQueue) {
                    Button(
                        onClick = onAddToQueueClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF264064),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adicionar para Importação")
                    }
                }
                // Se já está na lista, mostrar mensagem informativa
                else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFFFF3CD),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Funcionário adicionado à lista de importação",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF856404),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuedFuncionarioCard(
    funcionario: FuncionariosModel,
    onRemoveClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color(0xFF264064),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = funcionario.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                }
                
                IconButton(
                    onClick = onRemoveClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remover da lista",
                        tint = Color.Red
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Informações
            Text(
                text = "Matrícula: ${funcionario.matricula}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            Text(
                text = "CPF: ${formatCPF(funcionario.numero_cpf)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Text(
                text = "Cargo: ${funcionario.cargo_descricao}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Text(
                text = "Órgão: ${funcionario.orgao_descricao}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Text(
                text = "Setor: ${funcionario.setor_descricao}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
//            // Botão Importar Individual
//            Button(
//                onClick = onImportClick,
//                modifier = Modifier.fillMaxWidth(),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = Color(0xFF264064)
//                )
//            ) {
//                Icon(
//                    imageVector = Icons.Default.Check,
//                    contentDescription = null,
//                    modifier = Modifier.size(16.dp)
//                )
//                Spacer(modifier = Modifier.width(8.dp))
//                Text("Importar Funcionário")
//            }
        }
    }
}

@Composable
private fun FilterDialog(
    onDismiss: () -> Unit,
    onOrgaoFilter: () -> Unit,
    onEntidadeFilter: () -> Unit,
    selectedOrgao: OrgaoModel? = null,
    selectedEntidade: String? = null,
    onClearOrgaoFilter: () -> Unit = {},
    onClearEntidadeFilter: () -> Unit = {}
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
           Column {
               // Entidade selecionada
               if (selectedEntidade != null) {
                   Card(
                       colors = CardDefaults.cardColors(
                           containerColor = Color(0xFFE8F5E8)
                       )
                   ) {
                       Row(
                           modifier = Modifier
                               .fillMaxWidth()
                               .padding(16.dp),
                           verticalAlignment = Alignment.CenterVertically
                       ) {
                           Column(modifier = Modifier.weight(1f)) {
                               Text(
                                   text = "Entidade selecionada:",
                                   style = MaterialTheme.typography.bodySmall,
                                   color = Color.Gray
                               )
                               Text(
                                   text = selectedEntidade,
                                   style = MaterialTheme.typography.bodyMedium,
                                   fontWeight = FontWeight.Medium,
                                   color = Color(0xFF2E7D32)
                               )
                           }
                           IconButton(
                               onClick = onClearEntidadeFilter
                           ) {
                               Icon(
                                   imageVector = Icons.Default.Clear,
                                   contentDescription = "Limpar filtro",
                                   tint = Color.Red
                               )
                           }
                       }
                   }
                   Spacer(modifier = Modifier.height(8.dp))
               }
               
               // Órgão selecionado
               if (selectedOrgao != null) {
                   Card(
                       colors = CardDefaults.cardColors(
                           containerColor = Color(0xFFE3F2FD)
                       )
                   ) {
                       Row(
                           modifier = Modifier
                               .fillMaxWidth()
                               .padding(16.dp),
                           verticalAlignment = Alignment.CenterVertically
                       ) {
                           Column(modifier = Modifier.weight(1f)) {
                               Text(
                                   text = "Órgão selecionado:",
                                   style = MaterialTheme.typography.bodySmall,
                                   color = Color.Gray
                               )
                               Text(
                                   text = "${selectedOrgao.codigo} - ${selectedOrgao.descricao}",
                                   style = MaterialTheme.typography.bodyMedium,
                                   fontWeight = FontWeight.Medium,
                                   color = Color(0xFF264064)
                               )
                           }
                           IconButton(
                               onClick = onClearOrgaoFilter
                           ) {
                               Icon(
                                   imageVector = Icons.Default.Clear,
                                   contentDescription = "Limpar filtro",
                                   tint = Color.Red
                               )
                           }
                       }
                   }
                   Spacer(modifier = Modifier.height(8.dp))
               }
               
               // Botão para selecionar entidade
               Card(
                   onClick = onEntidadeFilter,
                   colors = CardDefaults.cardColors(
                       containerColor = if (selectedEntidade != null) Color(0xFFF5F5F5) else Color.White
                   )
               ) {
                   Row(
                       modifier = Modifier
                           .fillMaxWidth()
                           .padding(16.dp),
                       verticalAlignment = Alignment.CenterVertically
                   ) {
                       Icon(
                           imageVector = Icons.Default.Search,
                           contentDescription = "Filtro de Entidade",
                           tint = Color(0xFF2E7D32),
                           modifier = Modifier.size(20.dp)
                       )
                       Spacer(modifier = Modifier.width(12.dp))
                       Text(
                           text = if (selectedEntidade != null) "Alterar Entidade" else "Buscar por Entidade",
                           style = MaterialTheme.typography.bodyLarge,
                           fontWeight = FontWeight.Medium,
                           color = Color(0xFF2E7D32)
                       )
                   }
               }
               
               Spacer(modifier = Modifier.height(8.dp))
               
               // Botão para selecionar órgão
               Card(
                   onClick = onOrgaoFilter,
                   colors = CardDefaults.cardColors(
                       containerColor = if (selectedOrgao != null) Color(0xFFF5F5F5) else Color.White
                   )
               ) {
                   Row(
                       modifier = Modifier
                           .fillMaxWidth()
                           .padding(16.dp),
                       verticalAlignment = Alignment.CenterVertically
                   ) {
                       Icon(
                           imageVector = Icons.Default.FilterList,
                           contentDescription = "Filtro de Órgão",
                           tint = Color(0xFF264064),
                           modifier = Modifier.size(20.dp)
                       )
                       Spacer(modifier = Modifier.width(12.dp))
                       Text(
                           text = if (selectedOrgao != null) "Alterar Órgão" else "Selecionar Órgão",
                           style = MaterialTheme.typography.bodyLarge,
                           fontWeight = FontWeight.Medium,
                           color = Color(0xFF264064)
                       )
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
private fun FilterOrgao(
    onDismiss: () -> Unit,
    onOrgaoSelected: (OrgaoModel) -> Unit = {},
    viewModel: ImportEmployeesViewModel
) {
    var orgaos by remember { mutableStateOf<List<OrgaoModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedOrgao by remember { mutableStateOf<OrgaoModel?>(null) }

    // Carregar órgãos quando o composable é criado
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        
        // Carregar órgãos da API
        val orgaosCarregados = viewModel.loadOrgaos()
        
        if (orgaosCarregados.isEmpty()) {
            // Fallback para dados mockados se a API não retornar dados
            val mockOrgaos = listOf(
                OrgaoModel(16, "18", "SECRETARIA DE ESPORTES"),
                OrgaoModel(15, "15", "FUNDO MUNICIPAL DE ASSISTENCIA SOCIAL"),
                OrgaoModel(14, "10", "SECRETARIA DE FINANCAS"),
                OrgaoModel(13, "09", "SECRETARIA DE CULTURA"),
                OrgaoModel(12, "08", "SECRETARIA DA INDUSTRIA E COMERCIO"),
                OrgaoModel(11, "07", "SECRETARIA DE OBRAS MEIO AMBIENTE"),
                OrgaoModel(10, "06", "SECRETARIA DE AGRICULTURA E ABASTECIMENTO"),
                OrgaoModel(9, "05", "SECRETARIA DE ACAO SOCIAL"),
                OrgaoModel(8, "04", "SAUDE FUNDO MUNICIPAL DE SAUDE"),
                OrgaoModel(7, "03", "SECRETARIA DE EDUCACAO E DESPORTO"),
                OrgaoModel(6, "02", "SECRETARIA DE ADMINISTRACAO"),
                OrgaoModel(5, "01", "GABINETE DO PREFEITO")
            )
            // Ordenar dados mockados alfabeticamente por descrição
            orgaos = mockOrgaos.sortedBy { it.descricao }
        } else {
            orgaos = orgaosCarregados
        }
        
        isLoading = false
    }

    // Filtrar órgãos baseado na busca
    val filteredOrgaos = remember(orgaos, searchQuery) {
        if (searchQuery.isBlank()) {
            orgaos
        } else {
            orgaos.filter { orgao ->
                orgao.descricao.contains(searchQuery, ignoreCase = true) ||
                orgao.codigo.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filtro de Órgãos",
                    tint = Color(0xFF264064),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Selecionar Órgão",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF264064)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Campo de busca
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar órgão...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar"
                        )
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Lista de órgãos
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Carregando órgãos...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    
                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.Red
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Red,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    filteredOrgaos.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isBlank()) "Nenhum órgão encontrado" else "Nenhum órgão corresponde à busca",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredOrgaos) { orgao ->
                                OrgaoItem(
                                    orgao = orgao,
                                    isSelected = selectedOrgao?.id == orgao.id,
                                    onClick = { selectedOrgao = orgao }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        selectedOrgao?.let { orgao ->
                            onOrgaoSelected(orgao)
                        }
                        onDismiss()
                    },
                    enabled = selectedOrgao != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF264064)
                    )
                ) {
                    Text("Selecionar")
                }
            }
        }
    )
}

@Composable
private fun FilterEntidade(
    onDismiss: () -> Unit,
    onEntidadeSelected: (String) -> Unit = {},
    viewModel: ImportEmployeesViewModel
) {
    var entidadeCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar Entidade",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buscar por Entidade",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Campo de busca
                OutlinedTextField(
                    value = entidadeCode,
                    onValueChange = { entidadeCode = it },
                    label = { Text("Código da Entidade") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar"
                        )
                    },
                    singleLine = true,
                    placeholder = { Text("Digite o código da entidade...") }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Mensagem de instrução
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E8)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "💡 Instruções:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Digite o código da entidade desejada",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "• Clique em 'Buscar' para carregar funcionários",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "• Os funcionários serão filtrados por esta entidade",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
                
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Red
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (entidadeCode.isNotBlank()) {
                            isLoading = true
                            error = null
                            
                            // Testar se a entidade existe fazendo uma busca
                            viewModel.testEntidadeAndLoad(entidadeCode.trim()) { success, errorMessage ->
                                isLoading = false
                                if (success) {
                                    onEntidadeSelected(entidadeCode.trim())
                                    onDismiss()
                                } else {
                                    error = errorMessage
                                }
                            }
                        }
                    },
                    enabled = entidadeCode.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Buscar")
                }
            }
        }
    )
}

@Composable
private fun OrgaoItem(
    orgao: OrgaoModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Código do órgão
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) Color(0xFF264064) else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = orgao.codigo,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color.Black
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Descrição do órgão
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = orgao.descricao,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF264064) else Color.Black
                )
            }
            
            // Indicador de seleção
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selecionado",
                    tint = Color(0xFF264064),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}