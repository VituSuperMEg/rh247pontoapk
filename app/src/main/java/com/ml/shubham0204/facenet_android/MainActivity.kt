package com.ml.shubham0204.facenet_android

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
import com.ml.shubham0204.facenet_android.ui.screens.LogsScreen
import com.ml.shubham0204.facenet_android.utils.ClearFacesUtil
import com.ml.shubham0204.facenet_android.utils.ClearAdeiltonPointsUtil
import com.ml.shubham0204.facenet_android.utils.TabletDataSyncUtil
import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.ConfiguracoesDao
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.utils.CacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val appPreferences: AppPreferences by inject()
    private val cacheManager: CacheManager by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (appPreferences.telaCheiaHabilitada) {
            setupFullscreenMode()
        }
        

        updateFuncionariosEntidadeId()
        
        performStartupCacheCleanup()
        
        // syncDataWithBackend()

        setContent {
            val navHostController = rememberNavController()




            SetupFullscreenSystem()


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
                            longitude = 0.0,
                            entidadeId = "FALLBACK" // ✅ NOVO: Entidade para fallback
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
                        onNavigateBack = { navHostController.navigateUp() },
                        onNavigateToLogs = { navHostController.navigate("logs") }
                    )
                }
                composable("reports") {
                    ReportsScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onSyncClick = { /* TODO: Implementar sincronização */ },
                        onExportClick = { /* TODO: Implementar exportação */ }
                    )
                }
                composable("logs") {
                    LogsScreen(
                        onNavigateBack = { navHostController.navigateUp() }
                    )
                }
            }

            BackHandler {
                navHostController.navigate("login") {
                    popUpTo(0)
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
    
    /**
     * ✅ NOVO: Função para sincronizar dados dos funcionários e faces com o backend
     * Esta função pode ser chamada quando necessário para enviar todos os dados
     * dos funcionários e suas faces para o endpoint /services/util/adicionar-dados-do-tablet
     */
    fun syncDataWithBackend() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("MainActivity", "🚀 Iniciando sincronização de dados com backend...")
                
                val syncUtil = TabletDataSyncUtil(this@MainActivity)
                val result = syncUtil.syncAllDataWithBackend()
                
                if (result.success) {
                    android.util.Log.d("MainActivity", "🎉 Sincronização concluída com sucesso!")
                    android.util.Log.d("MainActivity", "   - Funcionários sincronizados: ${result.successCount}")
                } else {
                    android.util.Log.w("MainActivity", "⚠️ Sincronização concluída com erros:")
                    android.util.Log.w("MainActivity", "   - Sucessos: ${result.successCount}")
                    android.util.Log.w("MainActivity", "   - Erros: ${result.errorCount}")
                    result.errors.forEach { error ->
                        android.util.Log.e("MainActivity", "   - Erro: $error")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Erro na sincronização: ${e.message}")
            }
        }
    }
    
    /**
     * 🖥️ CONFIGURAÇÃO DE TELA CHEIA: Esconde botões de navegação e barra de status
     * Ideal para tablets em modo kiosk ou experiência imersiva
     */
    private fun setupFullscreenMode() {
        try {
            // Configurar flags para tela cheia
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            
            // Esconder barra de navegação (botões de ação)
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
            
            // Configurar para manter tela ligada (opcional)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            android.util.Log.d("MainActivity", "🖥️ Modo de tela cheia configurado com sucesso")
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Erro ao configurar tela cheia: ${e.message}")
        }
    }
    
    /**
     * 🖥️ COMPOSABLE PARA CONFIGURAR SISTEMA DE BARRAS
     * Aplica configurações de tela cheia via Compose
     */
    @Composable
    private fun SetupFullscreenSystem() {
        val view = LocalView.current
        
        SideEffect {
            val window = (view.context as ComponentActivity).window
            
            // Configurar barra de status transparente
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            // Configurar controlador de barras do sistema
            val insetsController = WindowCompat.getInsetsController(window, view)
            
            // Esconder barras do sistema
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Configurar comportamento imersivo
            insetsController.systemBarsBehavior = 
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            android.util.Log.d("MainActivity", "🖥️ Sistema de barras configurado via Compose")
        }
    }
    
    /**
     * 🖥️ MÉTODO PARA ALTERNAR MODO DE TELA CHEIA
     * Pode ser chamado para ativar/desativar tela cheia dinamicamente
     */
    fun toggleFullscreenMode(isFullscreen: Boolean) {
        try {
            if (isFullscreen) {
                // Ativar tela cheia
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                android.util.Log.d("MainActivity", "🖥️ Tela cheia ativada")
            } else {
                // Desativar tela cheia
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                android.util.Log.d("MainActivity", "🖥️ Tela cheia desativada")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Erro ao alternar tela cheia: ${e.message}")
        }
    }
    
    /**
     * ✅ NOVO: Limpeza automática de cache no startup
     * Executa limpeza rápida para evitar acúmulo de 4GB+ de cache
     */
    private fun performStartupCacheCleanup() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("MainActivity", "🚀 Iniciando limpeza automática de cache...")
                
                // Executar limpeza rápida (não bloqueia o startup)
                val result = cacheManager.performQuickCacheCleanup()
                
                result.fold(
                    onSuccess = { message ->
                        android.util.Log.d("MainActivity", "✅ Limpeza automática concluída: $message")
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainActivity", "❌ Erro na limpeza automática: ${error.message}")
                    }
                )
                
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Erro inesperado na limpeza automática", e)
            }
        }
    }
}

