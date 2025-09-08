package com.ml.shubham0204.facenet_android.presentation.screens.imported_employees

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportedEmployeesScreen(
    onNavigateBack: () -> Unit,
    onAddFaceClick: (FuncionariosEntity) -> Unit
) {
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Funcionários Importados",
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
                    }
                )
            }
        ) { innerPadding ->
            val viewModel: ImportedEmployeesViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsState()
            
            // ✅ NOVO: Estado para o campo de busca
            var searchQuery by remember { mutableStateOf("") }
            
            // ✅ NOVO: Recarregar funcionários quando a tela se tornar ativa
            LaunchedEffect(Unit) {
                viewModel.refreshFuncionarios()
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
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
                                text = "Nenhum funcionário importado",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Importe funcionários da tela de importação",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    // ✅ NOVO: Campo de busca
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        placeholder = {
                            Text("Buscar funcionário por nome...")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar"
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFF264064),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF264064)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    // ✅ NOVO: Toggle para mostrar funcionários inativos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mostrar funcionários inativos",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.showInactiveFuncionarios,
                            onCheckedChange = { viewModel.toggleShowInactiveFuncionarios() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF264064),
                                checkedTrackColor = Color(0xFF264064).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // ✅ NOVO: Lista filtrada por busca
                    val funcionariosFiltrados = if (searchQuery.isBlank()) {
                        uiState.funcionarios
                    } else {
                        uiState.funcionarios.filter { funcionarioComFacial ->
                            funcionarioComFacial.funcionario.nome.contains(
                                searchQuery,
                                ignoreCase = true
                            )
                        }
                    }
                    
                    if (funcionariosFiltrados.isEmpty() && searchQuery.isNotBlank()) {
                        // ✅ NOVO: Mensagem quando não há resultados na busca
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Nenhum funcionário encontrado",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tente com outro termo de busca",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(funcionariosFiltrados) { funcionarioComFacial ->
                                ImportedFuncionarioCard(
                                    funcionarioComFacial = funcionarioComFacial,
                                    onAddFaceClick = { onAddFaceClick(funcionarioComFacial.funcionario) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Função para aplicar máscara no CPF
private fun formatCPF(cpf: String): String {
    return if (cpf.length >= 11) {
        "${cpf.substring(0, 3)}.***.***-${cpf.substring(9, 11)}"
    } else {
        cpf
    }
}

// Função para formatar a data de importação
private fun formatDataImportacao(timestamp: Long): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = timestamp
    
    val dia = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val mes = calendar.get(java.util.Calendar.MONTH) + 1 // Janeiro é 0
    val ano = calendar.get(java.util.Calendar.YEAR)
    val hora = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minuto = calendar.get(java.util.Calendar.MINUTE)
    
    return String.format("%02d/%02d/%04d às %02d:%02d", dia, mes, ano, hora, minuto)
}

@Composable
private fun ImportedFuncionarioCard(
    funcionarioComFacial: FuncionarioComFacial,
    onAddFaceClick: () -> Unit
) {
    val funcionario = funcionarioComFacial.funcionario
    val temFacial = funcionarioComFacial.temFacial
    val isActive = funcionario.ativo == 1
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isActive) Color.White else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        // Borda azul na esquerda (ativa) ou cinza (inativa)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(
                    color = if (isActive) Color(0xFF2196F3) else Color.Gray,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                )
        )
        
        // Borda amarela na direita (ativa) ou cinza (inativa)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .align(Alignment.CenterEnd)
                .background(
                    color = if (isActive) Color(0xFFFFEB3B) else Color.Gray,
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                )
        )
        
        // Conteúdo do card
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
                // Nome e status
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = funcionario.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.Black else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                    }
                    
                    // ✅ NOVO: Texto de status
                    Text(
                        text = if (isActive) "Status: ATIVO" else "Status: INATIVO",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Informações detalhadas
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mat
                Text(
                    text = "Matrícula: ${funcionario.matricula}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color.Gray else Color(0xFF9E9E9E)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // CPF com máscara
                Text(
                    text = "CPF: ${formatCPF(funcionario.cpf)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color.Gray else Color(0xFF9E9E9E)
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                // Cargo
                Text(
                    text = "Cargo: ${funcionario.cargo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color.Gray else Color(0xFF9E9E9E)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Secretaria (Órgão)
                Text(
                    text = "Órgão: ${funcionario.secretaria}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color.Gray else Color(0xFF9E9E9E)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Lotação
                Text(
                    text = "Setor: ${funcionario.lotacao}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color.Gray else Color(0xFF9E9E9E)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // ✅ NOVO: Data de Importação
                Text(
                    text = "Importado em: ${formatDataImportacao(funcionario.dataImportacao)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color.Gray else Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // ✅ NOVO: Campo Facial Cadastrada
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (temFacial) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Facial Cadastrada: ${if (temFacial) "SIM" else "NÃO"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (temFacial) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // ✅ NOVO: Botões de ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botão Cadastrar Facial (só para funcionários ativos)
                        Button(
                            onClick = onAddFaceClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF264064)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Detalhes (Cadastro de Facial)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                }
            }
        }
    }
} 