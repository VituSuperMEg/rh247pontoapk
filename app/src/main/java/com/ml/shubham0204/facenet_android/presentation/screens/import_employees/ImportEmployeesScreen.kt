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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.data.api.FuncionariosModel
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEmployeesScreen(
    onNavigateBack: () -> Unit
) {
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
                    }
                )
            },
            floatingActionButton = {
                val viewModel: ImportEmployeesViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                
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
            val viewModel: ImportEmployeesViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsState()
            
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
