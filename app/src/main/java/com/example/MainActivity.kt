package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chess.ChessViewModel
import com.example.chess.ChessViewModelFactory
import com.example.chess.ui.AnalysisLabScreen
import com.example.chess.ui.DashboardScreen
import com.example.chess.ui.MatchLedgerScreen
import com.example.chess.ui.PlayArenaScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121214)
                ) {
                    ChessAppNavigation()
                }
            }
        }
    }
}

@Composable
fun ChessAppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Instantiate unified state ViewModel using custom factory provider
    val chessViewModel: ChessViewModel = viewModel(
        factory = ChessViewModelFactory(context)
    )

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                viewModel = chessViewModel,
                onNavigateToPlay = { navController.navigate("play") },
                onNavigateToAnalysis = { navController.navigate("analysis") },
                onNavigateToLedger = { navController.navigate("ledger") }
            )
        }
        
        composable("play") {
            PlayArenaScreen(
                viewModel = chessViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("analysis") {
            AnalysisLabScreen(
                viewModel = chessViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("ledger") {
            MatchLedgerScreen(
                viewModel = chessViewModel,
                onNavigateToAnalysis = { navController.navigate("analysis") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
