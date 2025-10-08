package com.ml.shubham0204.facenet_android.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.facenet_android.presentation.theme.FaceNetAndroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogs: () -> Unit = {}
) {
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Configuração/Sincronização",
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
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val tabs = listOf("Configurações", "Backup", "Histórico", "Sobre")
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                when (selectedTabIndex) {
                    0 -> ConfiguracoesTab(
                        onSalvar = { /* TODO */ },
                        onCancelar = onNavigateBack,
                        onSair = { /* TODO */ },
                        onNavigateToLogs = onNavigateToLogs
                    )
                    1 -> BackupTab()
                    2 -> HistoricoTab()
                    3 -> SobreTab()
                }
            }
        }
    }
} 