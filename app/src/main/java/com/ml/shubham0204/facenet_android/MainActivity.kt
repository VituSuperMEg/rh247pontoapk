package com.ml.shubham0204.facenet_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ml.shubham0204.facenet_android.presentation.screens.add_face.AddFaceScreen
import com.ml.shubham0204.facenet_android.presentation.screens.detect_screen.DetectScreen
import com.ml.shubham0204.facenet_android.presentation.screens.face_list.FaceListScreen
import com.ml.shubham0204.facenet_android.presentation.screens.home.HomeScreen
import com.ml.shubham0204.facenet_android.presentation.screens.import_employees.ImportEmployeesScreen
import com.ml.shubham0204.facenet_android.data.PontosGenericosEntity
import com.ml.shubham0204.facenet_android.presentation.screens.imported_employees.ImportedEmployeesScreen
import com.ml.shubham0204.facenet_android.presentation.screens.ponto_success.PontoSuccessScreen
import com.ml.shubham0204.facenet_android.presentation.screens.reports.ReportsScreen
import com.ml.shubham0204.facenet_android.presentation.screens.settings.SettingsScreen
import com.ml.shubham0204.facenet_android.presentation.screens.login.LoginScreen
import com.ml.shubham0204.facenet_android.utils.ClearFacesUtil
import com.ml.shubham0204.facenet_android.utils.ClearAdeiltonPointsUtil
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Atualizar entidade_id dos funcionários existentes automaticamente
        updateFuncionariosEntidadeId()

        setContent {
            val navHostController = rememberNavController()
            
            NavHost(
                navController = navHostController,
                startDestination = "detect",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable("home") {
                    HomeScreen(
                        onRegisterTimeClick = { navHostController.navigate("detect") },
                        onImportedEmployeesClick = { navHostController.navigate("face-list") },
                        onImportEmployeesClick = { navHostController.navigate("import-employees") },
                        onReportsClick = { navHostController.navigate("reports") },
                        onSettingsClick = { navHostController.navigate("settings") },
                        onAdminAccessClick = { navHostController.navigate("login") }
                    )
                }
                composable(
                    route = "add-face/{personName}/{funcionarioId}/{funcionarioCpf}/{funcionarioCargo}/{funcionarioOrgao}/{funcionarioLotacao}/{funcionarioEntidadeId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("personName") {
                            type = androidx.navigation.NavType.StringType
                            defaultValue = ""
                        },
                        androidx.navigation.navArgument("funcionarioId") {
                            type = androidx.navigation.NavType.LongType
                            defaultValue = 0L
                        },
                        androidx.navigation.navArgument("funcionarioCpf") {
                            type = androidx.navigation.NavType.StringType
                            defaultValue = ""
                        },
                        androidx.navigation.navArgument("funcionarioCargo") {
                            type = androidx.navigation.NavType.StringType
                            defaultValue = ""
                        },
                        androidx.navigation.navArgument("funcionarioOrgao") {
                            type = androidx.navigation.NavType.StringType
                            defaultValue = ""
                        },
                        androidx.navigation.navArgument("funcionarioLotacao") {
                            type = androidx.navigation.NavType.StringType
                            defaultValue = ""
                        },
                        androidx.navigation.navArgument("funcionarioEntidadeId") {
                            type = androidx.navigation.NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val personName = backStackEntry.arguments?.getString("personName")?.replace("_", " ") ?: ""
                    val funcionarioId = backStackEntry.arguments?.getLong("funcionarioId") ?: 0L
                    val funcionarioCpf = backStackEntry.arguments?.getString("funcionarioCpf")?.replace("_", "") ?: ""
                    val funcionarioCargo = backStackEntry.arguments?.getString("funcionarioCargo")?.replace("_", " ") ?: ""
                    val funcionarioOrgao = backStackEntry.arguments?.getString("funcionarioOrgao")?.replace("_", " ") ?: ""
                    val funcionarioLotacao = backStackEntry.arguments?.getString("funcionarioLotacao")?.replace("_", " ") ?: ""
                    val funcionarioEntidadeId = backStackEntry.arguments?.getString("funcionarioEntidadeId")?.replace("_", "") ?: ""
                    
                    // ✅ NOVO: Logs para verificar os dados passados
                    android.util.Log.d("MainActivity", "🚀 === NAVEGAÇÃO PARA ADD FACE SCREEN ===")
                    android.util.Log.d("MainActivity", "🚀 Nome: '$personName'")
                    android.util.Log.d("MainActivity", "🚀 ID: $funcionarioId")
                    android.util.Log.d("MainActivity", "🚀 CPF: '$funcionarioCpf'")
                    android.util.Log.d("MainActivity", "🚀 Cargo: '$funcionarioCargo'")
                    android.util.Log.d("MainActivity", "🚀 Órgão: '$funcionarioOrgao'")
                    android.util.Log.d("MainActivity", "🚀 Lotação: '$funcionarioLotacao'")
                    android.util.Log.d("MainActivity", "🚀 ID da Entidade: '$funcionarioEntidadeId'")
                    
                    AddFaceScreen(
                        personName = personName,
                        funcionarioId = funcionarioId,
                        funcionarioCpf = funcionarioCpf,
                        funcionarioCargo = funcionarioCargo,
                        funcionarioOrgao = funcionarioOrgao,
                        funcionarioLotacao = funcionarioLotacao,
                        funcionarioEntidadeId = funcionarioEntidadeId,
                        onNavigateBack = { navHostController.navigateUp() }
                    )
                }
                
                composable(
                    route = "add-face/{personName}",
                    arguments = listOf(
                        androidx.navigation.navArgument("personName") {
                            type = androidx.navigation.NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val personName = backStackEntry.arguments?.getString("personName") ?: ""
                    AddFaceScreen(
                        personName = personName,
                        onNavigateBack = { navHostController.navigateUp() }
                    )
                }
                
                composable("add-face") { 
                    AddFaceScreen(
                        personName = "",
                        onNavigateBack = { navHostController.navigateUp() }
                    ) 
                }
                composable("detect") { 
                    DetectScreen(
                        onOpenFaceListClick = { navHostController.navigate("face-list") },
                        onNavigateBack = { navHostController.navigateUp() },
                        onPontoSuccess = { ponto ->
                            navHostController.navigate("ponto-success/${ponto.id}")
                        },
                        onAdminAccessClick = { navHostController.navigate("login") }
                    ) 
                }
                
                composable(
                    route = "ponto-success/{pontoId}",
                    arguments = listOf(
                        navArgument("pontoId") {
                            type = androidx.navigation.NavType.LongType
                            defaultValue = 0L
                        }
                    )
                ) { backStackEntry ->
                    val pontoId = backStackEntry.arguments?.getLong("pontoId") ?: 0L
                    // Buscar o ponto real do banco de dados
                    val pontosDao = com.ml.shubham0204.facenet_android.data.PontosGenericosDao()
                    val ponto = pontosDao.getById(pontoId)
                    
                    if (ponto != null) {
                        PontoSuccessScreen(
                            ponto = ponto,
                            onNavigateBack = { navHostController.navigateUp() }
                        )
                    } else {
                        // Fallback caso não encontre o ponto
                        val fallbackPonto = PontosGenericosEntity(
                            id = pontoId,
                            funcionarioNome = "Funcionário não encontrado",
                            dataHora = System.currentTimeMillis(),
                            latitude = 0.0,
                            longitude = 0.0
                        )
                        PontoSuccessScreen(
                            ponto = fallbackPonto,
                            onNavigateBack = { navHostController.navigateUp() }
                        )
                    }
                }
                composable("face-list") {
                    ImportedEmployeesScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onAddFaceClick = { funcionario ->
                            // ✅ NOVO: Navegar para AddFaceScreen com todos os dados do funcionário
                            val encodedNome = funcionario.nome.replace("/", "_").replace(" ", "_")
                            val encodedCpf = funcionario.cpf.replace("/", "_")
                            val encodedCargo = funcionario.cargo.replace("/", "_").replace(" ", "_")
                            val encodedOrgao = funcionario.secretaria.replace("/", "_").replace(" ", "_")
                            val encodedLotacao = funcionario.lotacao.replace("/", "_").replace(" ", "_")
                            val encodedEntidadeId = (funcionario.entidadeId ?: "").replace("/", "_").replace(" ", "_")
                            
                            // ✅ NOVO: Logs para verificar os dados originais e codificados
                            android.util.Log.d("MainActivity", "📤 === DADOS ORIGINAIS DO FUNCIONÁRIO ===")
                            android.util.Log.d("MainActivity", "📤 Nome original: '${funcionario.nome}'")
                            android.util.Log.d("MainActivity", "📤 CPF original: '${funcionario.cpf}'")
                            android.util.Log.d("MainActivity", "📤 Cargo original: '${funcionario.cargo}'")
                            android.util.Log.d("MainActivity", "📤 Órgão original: '${funcionario.secretaria}'")
                            android.util.Log.d("MainActivity", "📤 Lotação original: '${funcionario.lotacao}'")
                            android.util.Log.d("MainActivity", "📤 ID da Entidade original: '${funcionario.entidadeId ?: "null"}'")
                            
                            android.util.Log.d("MainActivity", "📤 === DADOS CODIFICADOS ===")
                            android.util.Log.d("MainActivity", "📤 Nome codificado: '$encodedNome'")
                            android.util.Log.d("MainActivity", "📤 CPF codificado: '$encodedCpf'")
                            android.util.Log.d("MainActivity", "📤 Cargo codificado: '$encodedCargo'")
                            android.util.Log.d("MainActivity", "📤 Órgão codificado: '$encodedOrgao'")
                            android.util.Log.d("MainActivity", "📤 Lotação codificada: '$encodedLotacao'")
                            android.util.Log.d("MainActivity", "📤 ID da Entidade codificado: '$encodedEntidadeId'")
                            
                            val route = "add-face/$encodedNome/${funcionario.id}/$encodedCpf/$encodedCargo/$encodedOrgao/$encodedLotacao/$encodedEntidadeId"
                            android.util.Log.d("MainActivity", "📤 Rota completa: '$route'")
                            
                            navHostController.navigate(route)
                        }
                    )
                }
                composable("import-employees") {
                    ImportEmployeesScreen(
                        onNavigateBack = { navHostController.navigateUp() }
                    )
                }
                composable("login") {
                    LoginScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onLoginSuccess = { 
                            navHostController.navigate("home")
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onNavigateBack = { navHostController.navigateUp() }
                    )
                }
                composable("reports") {
                    ReportsScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onSyncClick = { /* TODO: Implementar sincronização */ },
                        onExportClick = { /* TODO: Implementar exportação */ }
                    )
                }
            }
        }
    }
    
    /**
     * Atualiza o campo entidade_id dos funcionários existentes que ainda não possuem esse campo preenchido.
     * Esta rotina é executada na inicialização da aplicação para garantir que todos os funcionários
     * tenham o controle de entidade configurado.
     */
    private fun updateFuncionariosEntidadeId() {
        try {
            android.util.Log.d("MainActivity", "🔄 Iniciando atualização de entidade_id dos funcionários...")
            
            val funcionariosDao = FuncionariosDao()
            val configuracoesDao = ConfiguracoesDao()
            
            // Obter a entidade configurada
            val configuracoes = configuracoesDao.getConfiguracoes()
            val entidadeId = configuracoes?.entidadeId ?: ""
            
            if (entidadeId.isEmpty()) {
                android.util.Log.w("MainActivity", "⚠️ Entidade ID não configurada - pulando atualização")
                return
            }
            
            android.util.Log.d("MainActivity", "🏢 Entidade ID configurada: '$entidadeId'")
            
            // Buscar todos os funcionários
            val todosFuncionarios = funcionariosDao.getAll()
            android.util.Log.d("MainActivity", "📊 Total de funcionários encontrados: ${todosFuncionarios.size}")
            
            // Filtrar funcionários que não têm entidade_id preenchido
            val funcionariosSemEntidade = todosFuncionarios.filter { funcionario ->
                funcionario.entidadeId.isNullOrEmpty()
            }
            
            android.util.Log.d("MainActivity", "📋 Funcionários sem entidade_id: ${funcionariosSemEntidade.size}")
            
            if (funcionariosSemEntidade.isEmpty()) {
                android.util.Log.d("MainActivity", "✅ Todos os funcionários já possuem entidade_id configurado")
                return
            }
            
            // Atualizar cada funcionário usando uma abordagem mais segura
            var atualizados = 0
            var erros = 0
            
            funcionariosSemEntidade.forEach { funcionario ->
                try {
                    android.util.Log.d("MainActivity", "🔄 Atualizando: ${funcionario.nome} (ID: ${funcionario.id})")
                    
                    // Usar uma abordagem mais segura - apenas atualizar o campo específico
                    // Primeiro, vamos verificar se o funcionário ainda existe
                    val funcionarioExistente = funcionariosDao.getById(funcionario.id)
                    if (funcionarioExistente != null) {
                        // Verificar se o funcionário realmente não tem entidade_id
                        if (funcionarioExistente.entidadeId.isNullOrEmpty()) {
                            // Criar uma nova instância com o entidade_id preenchido
                            val funcionarioAtualizado = funcionarioExistente.copy(
                                entidadeId = entidadeId
                            )
                            
                            // Atualizar no banco de dados
                            funcionariosDao.update(funcionarioAtualizado)
                            atualizados++
                            
                            android.util.Log.d("MainActivity", "✅ Atualizado com sucesso: ${funcionario.nome} (ID: ${funcionario.id})")
                        } else {
                            android.util.Log.d("MainActivity", "ℹ️ Funcionário já tem entidade_id: ${funcionario.nome} (ID: ${funcionario.id})")
                        }
                    } else {
                        android.util.Log.w("MainActivity", "⚠️ Funcionário não encontrado: ${funcionario.nome} (ID: ${funcionario.id})")
                        erros++
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "❌ Erro ao atualizar funcionário ${funcionario.nome}: ${e.message}")
                    erros++
                }
            }
            
            if (erros > 0) {
                android.util.Log.w("MainActivity", "⚠️ Atualização concluída com erros: $atualizados atualizados, $erros erros")
            } else {
                android.util.Log.d("MainActivity", "🎉 Atualização concluída com sucesso: $atualizados funcionários atualizados com entidade_id: '$entidadeId'")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Erro na rotina de atualização de entidade_id: ${e.message}")
        }
    }
}

