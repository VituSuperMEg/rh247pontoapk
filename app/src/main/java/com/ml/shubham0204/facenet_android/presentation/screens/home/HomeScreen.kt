package com.ml.shubham0204.facenet_android.presentation.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.R
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRegisterTimeClick: () -> Unit,
    onImportedEmployeesClick: () -> Unit,
    onImportEmployeesClick: () -> Unit,
    onReportsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // Logo e título
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Logo real
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo RH247",
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Sistema de Ponto",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242) // Dark gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bem-vindo ao painel administrativo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF757575) // Light gray
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Main grid of cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top left card - Funcionários Importados
                    MenuCard(
                        icon = Icons.Default.Group,
                        title = "Funcionários Importados",
                        subtitle = "",
                        modifier = Modifier.weight(1f).height(180.dp),
                        onClick = onImportedEmployeesClick
                    )
                    
                    // Top right card - Importar Funcionários
                    MenuCard(
                        icon = Icons.Default.Face,
                        title = "Importar Funcionários",
                        subtitle = "",
                        modifier = Modifier.weight(1f).height(180.dp),
                        onClick = onImportEmployeesClick
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Bottom left card - Registrar Ponto
                    MenuCard(
                        icon = Icons.Default.Schedule,
                        title = "Registrar Ponto",
                        subtitle = "",
                        modifier = Modifier.weight(1f).height(180.dp),
                        onClick = onRegisterTimeClick
                    )
                    
                    // Bottom right card - Relatórios
                    MenuCard(
                        icon = Icons.Default.Summarize,
                        title = "Pontos Registrados",
                        subtitle = "",
                        modifier = Modifier.weight(1f).height(180.dp),
                        onClick = onReportsClick
                    )
                }
                
                Spacer(modifier = Modifier.height(60.dp))
                
                // Settings card at bottom - takes space of two buttons
                MenuCard(
                    icon = Icons.Default.Settings,
                    title = "Configurações",
                    subtitle = "",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun MenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF264064) // Black color
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color(0xFF424242) // Dark gray
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = Color(0xFF757575) // Light gray
            )
        }
    }
} 