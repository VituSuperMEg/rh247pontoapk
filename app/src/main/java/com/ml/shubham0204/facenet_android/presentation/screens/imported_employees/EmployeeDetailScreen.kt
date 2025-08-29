package com.ml.shubham0204.facenet_android.presentation.screens.imported_employees

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.ml.shubham0204.facenet_android.data.FuncionariosEntity
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme
import com.ml.shubham0204.facenet_android.domain.PersonUseCase
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailScreen(
    funcionario: FuncionariosEntity,
    onNavigateBack: () -> Unit,
    onCaptureFacesClick: () -> Unit
) {
    // ✅ NOVO: Injetar PersonUseCase
    val personUseCase: PersonUseCase = koinViewModel()
    
    // ✅ NOVO: Estados para controlar as faces cadastradas
    var hasRegisteredFaces by remember { mutableStateOf(false) }
    var registeredFacesCount by remember { mutableStateOf(0) }
    var registeredFacesImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingFaces by remember { mutableStateOf(true) }
    
    // ✅ NOVO: Verificar se o funcionário tem faces cadastradas
    LaunchedEffect(funcionario.id) {
        Log.d("EmployeeDetailScreen", "🔍 Verificando faces para funcionário ID: ${funcionario.id}")
        
        try {
            CoroutineScope(Dispatchers.IO).launch {
                // ✅ NOVO: Verificação real no banco de dados
                val personRecord = personUseCase.getPersonByFuncionarioId(funcionario.id)
                
                if (personRecord != null) {
                    hasRegisteredFaces = true
                    registeredFacesCount = personRecord.numImages.toInt()
                    Log.d("EmployeeDetailScreen", "✅ Faces encontradas: $registeredFacesCount")
                } else {
                    hasRegisteredFaces = false
                    registeredFacesCount = 0
                    Log.d("EmployeeDetailScreen", "❌ Nenhuma face encontrada")
                }
                
                isLoadingFaces = false
            }
        } catch (e: Exception) {
            Log.e("EmployeeDetailScreen", "❌ Erro ao verificar faces: ${e.message}")
            hasRegisteredFaces = false
            registeredFacesCount = 0
            isLoadingFaces = false
        }
    }
    
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Detalhes do Funcionário",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Card com dados do funcionário
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Nome do funcionário
                        Text(
                            text = funcionario.nome,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Dados do funcionário
                        InfoRow("CPF", formatCPF(funcionario.cpf))
                        InfoRow("Cargo", funcionario.cargo)
                        InfoRow("Órgão", funcionario.secretaria)
                        InfoRow("Lotação", funcionario.lotacao)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // ✅ NOVO: Status das faces cadastradas
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasRegisteredFaces) Color.Green.copy(alpha = 0.1f) else Color(0xFFFF9800).copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasRegisteredFaces) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = "Status das faces",
                                tint = if (hasRegisteredFaces) Color.Green else Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (hasRegisteredFaces) "Faces Cadastradas" else "Faces Não Cadastradas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hasRegisteredFaces) Color.Green else Color(0xFFFF9800)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (hasRegisteredFaces) 
                                "$registeredFacesCount face(s) cadastrada(s) no sistema" 
                            else 
                                "Nenhuma face cadastrada. Clique no botão abaixo para cadastrar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // ✅ NOVO: Grid de faces cadastradas (se houver)
                if (hasRegisteredFaces && registeredFacesCount > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Faces Cadastradas ($registeredFacesCount)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Grid de faces
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(200.dp)
                            ) {
                                items(registeredFacesCount) { index ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = 2.dp
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // TODO: Carregar imagem real do banco
                                            Text(
                                                text = "Face ${index + 1}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            
                                            // Número da face
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .background(
                                                        color = Color.Black.copy(alpha = 0.7f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Botão Capturar Faces
                Button(
                    onClick = onCaptureFacesClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasRegisteredFaces) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Capturar Faces",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (hasRegisteredFaces) "Recadastrar Facial" else "Cadastrar Facial",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Texto explicativo
                Text(
                    text = if (hasRegisteredFaces) 
                        "Clique no botão acima para recadastrar as faces do funcionário." 
                    else 
                        "Clique no botão acima para capturar 3 fotos do funcionário e cadastrar no sistema de reconhecimento facial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value.ifEmpty { "Não informado" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.7f)
        )
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