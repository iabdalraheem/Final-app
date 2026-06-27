package com.example.chess.ui

import android.widget.Toast
import com.example.chess.db.SavedGameEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.*
import com.example.chess.components.ChessBoardTheme
import com.example.chess.components.ChessBoardUi
import com.example.chess.components.EvaluationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisLabScreen(
    viewModel: ChessViewModel,
    onNavigateBack: () -> Unit
) {
    val activeGame by viewModel.game.collectAsState()
    val selectedPos by viewModel.selectedPosition.collectAsState()
    val legalMoves by viewModel.legalMoves.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val inCheckState = activeGame.isInCheck(activeGame.turn)
    val kingPos = if (inCheckState) activeGame.getKingPosition(activeGame.turn) else null

    val evalScore by viewModel.engineEvaluationScore.collectAsState()
    val engineSuggestion by viewModel.engineSuggestedMove.collectAsState()

    var activeBoardTheme by remember { mutableStateOf(ChessBoardTheme.WOOD) }
    var useTextBadgesStyle by remember { mutableStateOf(false) }

    var importFenInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDF8F6))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("analysis_back_btn")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B1B1F))
                }
                Text(
                    text = "مختبر تحليل النقلات",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        val nextThemeIndex = (activeBoardTheme.ordinal + 1) % ChessBoardTheme.values().size
                        activeBoardTheme = ChessBoardTheme.values()[nextThemeIndex]
                    }) {
                        Icon(Icons.Default.Palette, contentDescription = "Change Theme", tint = Color(0xFF6750A4))
                    }
                    IconButton(onClick = { useTextBadgesStyle = !useTextBadgesStyle }) {
                        Icon(
                            imageVector = if (useTextBadgesStyle) Icons.Default.FontDownload else Icons.Default.TextFields,
                            contentDescription = "Change pieces style",
                            tint = Color(0xFF1B1B1F)
                        )
                    }
                }
            }

            // ================== MAIN BOARD FRAME (Evaluation Bar + Board) ==================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Persistent Lichess Evaluation Bar
                EvaluationBar(
                    score = evalScore,
                    isFlipped = isFlipped,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                )

                ChessBoardUi(
                    modifier = Modifier.weight(1f),
                    boardTheme = activeBoardTheme,
                    useTextBadges = useTextBadgesStyle,
                    isFlipped = isFlipped,
                    selectedPos = selectedPos,
                    legalDestinations = legalMoves.map { it.to },
                    lastMove = activeGame.moveHistory.lastOrNull(),
                    isKingInCheck = inCheckState,
                    kingInCheckPos = kingPos,
                    piecesState = Array(8) { r -> Array(8) { c -> activeGame.board.getPiece(Position(r, c)) } },
                    onSquareClick = { pos -> viewModel.selectSquare(pos) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ================== LIVE LOCAL ENGINE SCORECARD ==================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "تقييم المحرك الذكي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            // Evaluation Badge
                            val formattedScore = if (evalScore >= 0) String.format("+%.2f", evalScore) else String.format("%.2f", evalScore)
                            val badgeColor = if (evalScore >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                            Text(
                                text = formattedScore,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeColor,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (evalScore > 0) "أفضلية للاعب الأبيض" else if (evalScore < 0) "أفضلية للاعب الأسود" else "الموقف متكافئ",
                                fontSize = 11.sp,
                                color = Color(0xFF1B1B1F)
                            )
                        }
                    }

                    // Suggested best continuation move
                    if (engineSuggestion != null) {
                        Button(
                            onClick = {
                                engineSuggestion?.let {
                                    viewModel.selectSquare(it.from)
                                    viewModel.selectSquare(it.to)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7), contentColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Recommend", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "أفضل حركة: ${engineSuggestion?.from?.toAlgebraic()}${engineSuggestion?.to?.toAlgebraic()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Color(0xFF6750A4))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ================== ALGEBRAIC GAME ACTIONS (PGN/FEN utilities) ==================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "سجل تفصيل الحركات",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1F),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Compact wrapping view displaying alphabetical list of previous moves
                    if (activeGame.algebraicHistory.isEmpty()) {
                        Text(
                            text = "لا توجد حركات مسجلة حالياً. حرك القطع بحرية على الرقعة لتسجيل النقلات وتحليل الخطوط.",
                            fontSize = 11.sp,
                            color = Color(0xFF49454F),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        // Render moves lists elegantly wrapping
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            // Re-bundle list in groups of 2 (Whites/Blacks pairs)
                            val pairsList = activeGame.algebraicHistory.chunked(2)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                pairsList.take(6).forEachIndexed { i, pair ->
                                    val moveNumber = i + 1
                                    val whitePart = pair.getOrNull(0) ?: ""
                                    val blackPart = pair.getOrNull(1) ?: ""
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$moveNumber.",
                                            color = Color(0xFF6750A4),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.width(28.dp)
                                        )
                                        Text(
                                            text = whitePart,
                                            color = Color(0xFF1B1B1F),
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = blackPart,
                                            color = Color(0xFF49454F),
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (pairsList.size > 6) {
                                    Text(
                                        text = "+ ${pairsList.size - 6} مجموعات حركات إضافية...",
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFCAC4D0), modifier = Modifier.padding(vertical = 8.dp))

                    // Utility Buttons: Copy FEN, Import FEN, New Sandbox Game
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val currentFen = activeGame.toFen()
                                clipboardManager.setText(AnnotatedString(currentFen))
                                Toast.makeText(context, "تم نسخ كود FEN الحالي للذاكرة تلقائياً!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = borderStroke(1.dp, Color(0xFF6750A4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ FEN", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            border = borderStroke(1.dp, Color(0xFF6750A4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استيراد FEN", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.resetGameToMode() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة تعيين", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Import FEN dialog overlay
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("استيراد حالة الرقعة (FEN)", fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F)) },
                text = {
                    Column {
                        Text(
                            text = "ألصق الصيغة القياسية لـ FEN لوصف حالة أحجار الرقعة وتوزيع المناصب أدناه:",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = importFenInput,
                            onValueChange = { importFenInput = it },
                            placeholder = { Text("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6750A4),
                                unfocusedBorderColor = Color(0xFFCAC4D0),
                                focusedTextColor = Color(0xFF1B1B1F),
                                unfocusedTextColor = Color(0xFF1B1B1F)
                            ),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("fen_input_field")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val worked = activeGame.loadFromFen(importFenInput)
                            if (worked) {
                                // Refresh viewModel states
                                viewModel.loadSavedGameToAnalysis(
                                    SavedGameEntity(
                                        timestamp = System.currentTimeMillis(),
                                        gameMode = GameMode.ANALYSIS.name,
                                        result = "",
                                        pgnMoves = "",
                                        fenStates = importFenInput,
                                        totalMoves = 0
                                    )
                                )
                                showImportDialog = false
                                Toast.makeText(context, "تم تحميل حالة الرقعة بنجاح!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "كود FEN غير صالح. يرجى التحقق من صحة الصيغة البرمجية.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White)
                    ) {
                        Text("تحميل", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("إلغاء", color = Color(0xFF6750A4))
                    }
                },
                containerColor = Color.White
            )
        }
    }
}
