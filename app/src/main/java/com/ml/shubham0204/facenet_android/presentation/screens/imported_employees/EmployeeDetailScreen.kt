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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
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
    // ✅ NOVO: Injetar PersonUseCase e ImageVectorUseCase
    val personUseCase: PersonUseCase = koinViewModel()
    val imageVectorUseCase: com.ml.shubham0204.facenet_android.domain.ImageVectorUseCase = koinViewModel()
    
    // ✅ NOVO: Estados para controlar as faces cadastradas
    var hasRegisteredFaces by remember { mutableStateOf(false) }
    var registeredFacesCount by remember { mutableStateOf(0) }
    var personRecord by remember { mutableStateOf<com.ml.shubham0204.facenet_android.data.PersonRecord?>(null) }
    var faceImages by remember { mutableStateOf<List<com.ml.shubham0204.facenet_android.data.FaceImageRecord>>(emptyList()) }
    var isLoadingFaces by remember { mutableStateOf(true) }
    
    // ✅ NOVO: Verificar se o funcionário tem faces cadastradas
    LaunchedEffect(funcionario.id) {
        Log.d("EmployeeDetailScreen", "🔍 === VERIFICANDO FUNCIONÁRIO ===")
        Log.d("EmployeeDetailScreen", "🔍 ID do funcionário: ${funcionario.id}")
        Log.d("EmployeeDetailScreen", "🔍 Nome: ${funcionario.nome}")
        Log.d("EmployeeDetailScreen", "🔍 CPF: ${funcionario.cpf}")
        Log.d("EmployeeDetailScreen", "🔍 Matrícula: ${funcionario.matricula}")
        Log.d("EmployeeDetailScreen", "🔍 Cargo: ${funcionario.cargo}")
        Log.d("EmployeeDetailScreen", "🔍 Órgão: ${funcionario.secretaria}")
        Log.d("EmployeeDetailScreen", "🔍 Lotação: ${funcionario.lotacao}")
        Log.d("EmployeeDetailScreen", "🔍 ID da Entidade: ${funcionario.entidadeId ?: "null"}")
        Log.d("EmployeeDetailScreen", "🔍 Status: ${if (funcionario.ativo == 1) "Ativo" else "Inativo"}")
        Log.d("EmployeeDetailScreen", "🔍 API ID: ${funcionario.apiId}")
        Log.d("EmployeeDetailScreen", "🔍 Código: ${funcionario.codigo}")
        
        // ✅ NOVO: Verificar se os campos estão vazios
        Log.d("EmployeeDetailScreen", "🔍 === VERIFICAÇÃO DE CAMPOS VAZIOS ===")
        Log.d("EmployeeDetailScreen", "🔍 CPF vazio: ${funcionario.cpf.isEmpty()}")
        Log.d("EmployeeDetailScreen", "🔍 Cargo vazio: ${funcionario.cargo.isEmpty()}")
        Log.d("EmployeeDetailScreen", "🔍 Órgão vazio: ${funcionario.secretaria.isEmpty()}")
        Log.d("EmployeeDetailScreen", "🔍 Lotação vazio: ${funcionario.lotacao.isEmpty()}")
        Log.d("EmployeeDetailScreen", "🔍 Matrícula vazio: ${funcionario.matricula.isEmpty()}")
        
        try {
            CoroutineScope(Dispatchers.IO).launch {
                // ✅ NOVO: Verificação real no banco de dados
                val person = personUseCase.getPersonByFuncionarioId(funcionario.id)
                
                if (person != null) {
                    hasRegisteredFaces = true
                    registeredFacesCount = person.numImages.toInt()
                    personRecord = person
                    
                    Log.d("EmployeeDetailScreen", "✅ === PESSOA ENCONTRADA ===")
                    Log.d("EmployeeDetailScreen", "✅ Person ID: ${person.personID}")
                    Log.d("EmployeeDetailScreen", "✅ Nome cadastrado: ${person.personName}")
                    Log.d("EmployeeDetailScreen", "✅ Número de imagens: ${person.numImages}")
                    Log.d("EmployeeDetailScreen", "✅ Data de cadastro: ${formatDate(person.addTime)}")
                    Log.d("EmployeeDetailScreen", "✅ Funcionário ID: ${person.funcionarioId}")
                    Log.d("EmployeeDetailScreen", "✅ API ID: ${person.funcionarioApiId}")
                    
                    // ✅ NOVO: Buscar as imagens das faces
                    val images = imageVectorUseCase.getImagesByPersonID(person.personID)
                    faceImages = images
                    
                    Log.d("EmployeeDetailScreen", "📸 === IMAGENS DAS FACES ===")
                    Log.d("EmployeeDetailScreen", "📸 Total de imagens: ${images.size}")
                    images.forEachIndexed { index, image ->
                        Log.d("EmployeeDetailScreen", "📸 Face $index:")
                        Log.d("EmployeeDetailScreen", "   - Record ID: ${image.recordID}")
                        Log.d("EmployeeDetailScreen", "   - Person ID: ${image.personID}")
                        Log.d("EmployeeDetailScreen", "   - Nome: ${image.personName}")
                        Log.d("EmployeeDetailScreen", "   - Embedding: ${image.faceEmbedding.size}D")
                    }
                } else {
                    hasRegisteredFaces = false
                    registeredFacesCount = 0
                    personRecord = null
                    faceImages = emptyList()
                    Log.d("EmployeeDetailScreen", "❌ Nenhuma face encontrada para funcionário ID: ${funcionario.id}")
                }
                
                isLoadingFaces = false
            }
        } catch (e: Exception) {
            Log.e("EmployeeDetailScreen", "❌ Erro ao verificar faces: ${e.message}")
            e.printStackTrace()
            hasRegisteredFaces = false
            registeredFacesCount = 0
            personRecord = null
            faceImages = emptyList()
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // ✅ NOVO: Dados principais destacados
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                // ✅ NOVO: Status do funcionário em destaque
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (funcionario.ativo == 1) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = if (funcionario.ativo == 1) "Ativo" else "Inativo",
                                        tint = if (funcionario.ativo == 1) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Status: ${if (funcionario.ativo == 1) "ATIVO" else "INATIVO"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (funcionario.ativo == 1) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                }
                                
                                // ✅ NOVO: Aviso para funcionários inativos
                                if (funcionario.ativo == 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFFFEBEE)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = Color(0xFFF44336)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Aviso",
                                                tint = Color(0xFFD32F2F),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Funcionário inativo - operações de facial bloqueadas",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFD32F2F),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "Dados Principais",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                InfoRow("CPF", if (funcionario.cpf.isNotEmpty()) formatCPF(funcionario.cpf) else "Não informado")
                                InfoRow("Matrícula", if (funcionario.matricula.isNotEmpty()) funcionario.matricula else "Não informado")
                                InfoRow("Cargo", if (funcionario.cargo.isNotEmpty()) funcionario.cargo else "Não informado")
                                InfoRow("Órgão", if (funcionario.secretaria.isNotEmpty()) funcionario.secretaria else "Não informado")
                                InfoRow("Lotação", if (funcionario.lotacao.isNotEmpty()) funcionario.lotacao else "Não informado")
                                InfoRow("ID da Entidade", if (!funcionario.entidadeId.isNullOrEmpty()) funcionario.entidadeId else "Não configurado")
                                
                                // ✅ NOVO: Aviso se os dados principais estão vazios
                                if (funcionario.cpf.isEmpty() || funcionario.cargo.isEmpty() || funcionario.secretaria.isEmpty() || funcionario.lotacao.isEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Aviso",
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Alguns dados podem estar incompletos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFF9800)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Dados do funcionário (todos os campos)
                        Text(
                            text = "Informações Completas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Nome", funcionario.nome)
                        InfoRow("Status", if (funcionario.ativo == 1) "Ativo" else "Inativo")
                        InfoRow("Código", funcionario.codigo)
                        InfoRow("ID do Funcionário", funcionario.id.toString())
                        InfoRow("ID da API", funcionario.apiId.toString())
                        
                        // ✅ NOVO: Informações das faces cadastradas
                        if (personRecord != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Informações das Faces",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow("ID da Pessoa", personRecord!!.personID.toString())
                            InfoRow("Nome Cadastrado", personRecord!!.personName)
                            InfoRow("Número de Faces", personRecord!!.numImages.toString())
                            InfoRow("Data de Cadastro", formatDate(personRecord!!.addTime))
                            InfoRow("Funcionário ID", personRecord!!.funcionarioId.toString())
                            InfoRow("API ID", personRecord!!.funcionarioApiId.toString())
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // ✅ NOVO: Estatísticas das faces
                if (hasRegisteredFaces && faceImages.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Estatísticas das Faces",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatisticItem(
                                    label = "Total de Faces",
                                    value = faceImages.size.toString(),
                                    icon = Icons.Default.Face
                                )
                                StatisticItem(
                                    label = "Dimensão Embedding",
                                    value = "${faceImages.firstOrNull()?.faceEmbedding?.size ?: 0}D",
                                    icon = Icons.Default.Person
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
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
                if (hasRegisteredFaces && faceImages.isNotEmpty()) {
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
                                text = "Faces Cadastradas (${faceImages.size})",
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
                                items(faceImages.size) { index ->
                                    val faceImage = faceImages[index]
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
                                            // ✅ NOVO: Mostrar informações da face
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Face,
                                                    contentDescription = "Face ${index + 1}",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Face ${index + 1}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "ID: ${faceImage.recordID}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Person ID: ${faceImage.personID}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Nome: ${faceImage.personName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Embedding: ${faceImage.faceEmbedding.size}D",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
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
                    enabled = funcionario.ativo == 1, // ✅ NOVO: Desabilitar para funcionários inativos
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            funcionario.ativo == 0 -> Color.Gray // ✅ NOVO: Cinza para inativos
                            hasRegisteredFaces -> Color(0xFFFF9800) // Laranja para recadastro
                            else -> MaterialTheme.colorScheme.primary // Azul para primeiro cadastro
                        }
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
                        text = when {
                            funcionario.ativo == 0 -> "Facial Bloqueada"
                            hasRegisteredFaces -> "Recadastrar Facial"
                            else -> "Cadastrar Facial"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // ✅ NOVO: Texto explicativo baseado no status
                Text(
                    text = when {
                        funcionario.ativo == 0 -> "Funcionário inativo - operações de facial bloqueadas. Reative o funcionário para permitir cadastro/edição de faces."
                        hasRegisteredFaces -> "Clique no botão acima para recadastrar as faces do funcionário."
                        else -> "Clique no botão acima para capturar 3 fotos do funcionário e cadastrar no sistema de reconhecimento facial."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        funcionario.ativo == 0 -> Color(0xFFD32F2F) // Vermelho para inativos
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
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

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

// ✅ NOVO: Função para formatar data
private fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR"))
    return formatter.format(date)
} 