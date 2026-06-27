package com.example.chess.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chess.*
import com.example.chess.components.ChessBoardTheme
import com.example.chess.components.ChessBoardUi
import com.example.chess.components.EvaluationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayArenaScreen(
    viewModel: ChessViewModel,
    onNavigateBack: () -> Unit
) {
    val activeGame by viewModel.game.collectAsState()
    val selectedPos by viewModel.selectedPosition.collectAsState()
    val legalMoves by viewModel.legalMoves.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val gameMode by viewModel.gameMode.collectAsState()
    val activeDifficulty by viewModel.difficultyLevel.collectAsState()
    val checkmateState = activeGame.isCheckmate()
    val stalemateState = activeGame.isStalemate()
    val inCheckState = activeGame.isInCheck(activeGame.turn)
    val kingPos = if (inCheckState) activeGame.getKingPosition(activeGame.turn) else null

    val whiteTimeLeft by viewModel.whiteTimeMs.collectAsState()
    val blackTimeLeft by viewModel.blackTimeMs.collectAsState()
    val selectedClockOption by viewModel.selectedClockControl.collectAsState()

    val gameResultState by viewModel.gameResult.collectAsState()
    val promotionTargetMove by viewModel.showPromotionDialog.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()
    val evalScore by viewModel.engineEvaluationScore.collectAsState()

    var activeBoardTheme by remember { mutableStateOf(ChessBoardTheme.WOOD) }
    var useTextBadgesStyle by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    // Determine target player designations depending on Mode
    val playerWhiteName = if (gameMode == GameMode.COMPUTER && viewModel.playerColorVsCpu.value == PieceColor.BLACK) "الذكاء الاصطناعي (مستوى $activeDifficulty)" else "اللاعب الأبيض"
    val playerBlackName = if (gameMode == GameMode.COMPUTER && viewModel.playerColorVsCpu.value == PieceColor.WHITE) "الذكاء الاصطناعي (مستوى $activeDifficulty)" else "اللاعب الأسود"

    // Map which index of grid to associate to top vs bottom
    val topPlayerName = if (isFlipped) playerWhiteName else playerBlackName
    val topPlayerColor = if (isFlipped) PieceColor.WHITE else PieceColor.BLACK
    val topPlayerTime = if (isFlipped) whiteTimeLeft else blackTimeLeft
    val topCapturedList = if (isFlipped) activeGame.capturedByBlack else activeGame.capturedByWhite

    val bottomPlayerName = if (isFlipped) playerBlackName else playerWhiteName
    val bottomPlayerColor = if (isFlipped) PieceColor.BLACK else PieceColor.WHITE
    val bottomPlayerTime = if (isFlipped) blackTimeLeft else whiteTimeLeft
    val bottomCapturedList = if (isFlipped) activeGame.capturedByWhite else activeGame.capturedByBlack

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
            // Screen Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_btn")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B1B1F))
                }
                Text(
                    text = if (gameMode == GameMode.COMPUTER) "مواجهة الذكاء الاصطناعي" else "لاعب ضد لاعب (تناوب)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Theme toggler Icon
                    IconButton(onClick = {
                        val nextThemeIndex = (activeBoardTheme.ordinal + 1) % ChessBoardTheme.values().size
                        activeBoardTheme = ChessBoardTheme.values()[nextThemeIndex]
                    }) {
                        Icon(Icons.Default.Palette, contentDescription = "Change Theme", tint = Color(0xFF6750A4))
                    }
                    // Font/Badges toggler Icon
                    IconButton(onClick = { useTextBadgesStyle = !useTextBadgesStyle }) {
                        Icon(
                            imageVector = if (useTextBadgesStyle) Icons.Default.FontDownload else Icons.Default.TextFields,
                            contentDescription = "Change piece style",
                            tint = Color(0xFF1B1B1F)
                        )
                    }
                }
            }

            // ================== TOP PLAYER CARD ==================
            PlayerHeaderRow(
                playerName = topPlayerName,
                playerColor = topPlayerColor,
                timeLeftMs = topPlayerTime,
                clockControl = selectedClockOption,
                capturedPieces = topCapturedList,
                isActiveTurn = activeGame.turn == topPlayerColor && gameResultState == GameResult.ONGOING
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ================== CHESSBOARD CONTAINER ==================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Optional Live Engine Evaluation bar on the left side
                if (gameMode == GameMode.COMPUTER) {
                    EvaluationBar(
                        score = evalScore,
                        isFlipped = isFlipped,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 8.dp)
                    )
                }

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
                    onSquareClick = { clickedPos -> viewModel.selectSquare(clickedPos) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ================== BOTTOM PLAYER CARD ==================
            PlayerHeaderRow(
                playerName = bottomPlayerName,
                playerColor = bottomPlayerColor,
                timeLeftMs = bottomPlayerTime,
                clockControl = selectedClockOption,
                capturedPieces = bottomCapturedList,
                isActiveTurn = activeGame.turn == bottomPlayerColor && gameResultState == GameResult.ONGOING
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Match notifications banner
            GameNotificationBanner(
                gameResult = gameResultState,
                isAiThinking = isAiThinking,
                inCheckState = inCheckState,
                activeTurnColor = activeGame.turn
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ================== FOOTER CONTROLS ==================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.flipBoard() }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Flip, contentDescription = "Flip Board", tint = Color(0xFF6750A4))
                            Text("قلب الرقعة", fontSize = 9.sp, color = Color(0xFF49454F))
                        }
                    }

                    IconButton(onClick = { viewModel.resetGameToMode() }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Restart Match", tint = Color(0xFF6750A4))
                            Text("إعادة تعيين", fontSize = 9.sp, color = Color(0xFF49454F))
                        }
                    }

                    if (selectedClockOption != ClockControl.UNLIMITED) {
                        IconButton(onClick = { viewModel.offerDraw() }, enabled = gameResultState == GameResult.ONGOING) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Handshake, contentDescription = "Offer Draw", tint = if (gameResultState == GameResult.ONGOING) Color(0xFF6750A4) else Color.LightGray)
                                Text("طلب تعادل", fontSize = 9.sp, color = Color(0xFF49454F))
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.resignGame() }, enabled = gameResultState == GameResult.ONGOING) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Flag, contentDescription = "Resign Game", tint = if (gameResultState == GameResult.ONGOING) Color(0xFFB3261E) else Color.LightGray)
                            Text("انسحاب", fontSize = 9.sp, color = Color(0xFF49454F))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ================== DIALOGS ==================

        // 1. Pawn Promotion choice overlay
        if (promotionTargetMove != null) {
            PromotionChoiceDialog(
                onChoiceSelected = { type -> viewModel.choosePromotion(type) },
                onDismiss = { viewModel.cancelPromotion() }
            )
        }

        // 2. Concluded Game Result Display Overlay
        if (gameResultState != GameResult.ONGOING) {
            MatchConcludedDialog(
                result = gameResultState,
                movesCount = activeGame.algebraicHistory.size,
                onDismiss = { viewModel.resetGameToMode() },
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
fun PlayerHeaderRow(
    playerName: String,
    playerColor: PieceColor,
    timeLeftMs: Long,
    clockControl: ClockControl,
    capturedPieces: List<com.example.chess.Piece>,
    isActiveTurn: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActiveTurn) Color(0xFFF3EDF7) else Color.Transparent)
            .border(
                1.dp,
                if (isActiveTurn) Color(0xFF6750A4).copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Little color-dot indicating piece alignment
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (playerColor == PieceColor.WHITE) Color.White else Color(0xFF1B1B1F))
                    .border(1.5.dp, Color(0xFF49454F), CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                     text = playerName,
                     color = Color(0xFF1B1B1F),
                     fontWeight = FontWeight.Bold,
                     fontSize = 15.sp
                )
                
                // Captured pieces listing
                if (capturedPieces.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        capturedPieces.take(12).forEach { p ->
                            Text(
                                text = p.getSymbol(useTextBadges = false),
                                color = if (p.color == PieceColor.WHITE) Color(0xFFAB8C6F) else Color(0xFF1B1B1F),
                                fontSize = 12.sp
                            )
                        }
                        if (capturedPieces.size > 12) {
                            Text(
                                text = "+${capturedPieces.size - 12}",
                                fontSize = 10.sp,
                                color = Color(0xFF1B1B1F),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Realtime Clock timer readout
        if (clockControl != ClockControl.UNLIMITED) {
            val minutes = (timeLeftMs / 1000) / 60
            val seconds = (timeLeftMs / 1000) % 60
            val subSec = (timeLeftMs % 1000) / 100 // tenths of seconds if under 20s

            val clockBg = if (timeLeftMs < 20000L) Color(0xFFF9DEDC) else if (isActiveTurn) Color(0xFF6750A4) else Color(0xFFF3EDF7)
            val clockTextColor = if (timeLeftMs < 20000L) Color(0xFFB3261E) else if (isActiveTurn) Color.White else Color(0xFF1B1B1F)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(clockBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (timeLeftMs < 20000L) String.format("%02d:%02d.%1d", minutes, seconds, subSec) else String.format("%02d:%02d", minutes, seconds),
                    color = clockTextColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun GameNotificationBanner(
    gameResult: GameResult,
    isAiThinking: Boolean,
    inCheckState: Boolean,
    activeTurnColor: PieceColor
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (gameResult != GameResult.ONGOING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6750A4).copy(alpha = 0.15f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "اكتملت المباراة الفنية!",
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        } else if (isAiThinking) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6750A4).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFF6750A4).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF6750A4)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "الذكاء الاصطناعي يحسب أفضل نقلة تكتيكية...",
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else if (inCheckState) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF9DEDC))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val turnFormattedName = if (activeTurnColor == PieceColor.WHITE) "اللاعب الأبيض" else "اللاعب الأسود"
                Text(
                    text = "كش ملك! عاهل $turnFormattedName تحت الهجوم المباشر!",
                    color = Color(0xFFB3261E),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val turnFormattedName = if (activeTurnColor == PieceColor.WHITE) "اللاعب الأبيض (دور الأبيض)" else "اللاعب الأسود (دور الأسود)"
                Text(
                    text = turnFormattedName,
                    color = Color(0xFF49454F),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun PromotionChoiceDialog(
    onChoiceSelected: (PieceType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ترقية البيدق",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val items = listOf(
                        PieceType.QUEEN to "♛",
                        PieceType.ROOK to "♜",
                        PieceType.BISHOP to "♝",
                        PieceType.KNIGHT to "♞"
                    )
                    items.forEach { (type, symbol) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF3EDF7))
                                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
                                .clickable { onChoiceSelected(type) }
                                .testTag("promo_${type.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = symbol,
                                fontSize = 32.sp,
                                color = Color(0xFF6750A4)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchConcludedDialog(
    result: GameResult,
    movesCount: Int,
    onDismiss: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Dialog(onDismissRequest = { /* force action choice */ }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFF6750A4), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Triumph",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(54.dp)
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Text(
                    text = "انتهت المباراة",
                    fontSize = 22.sp,
                    color = Color(0xFF1B1B1F),
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val announcement = when (result) {
                    GameResult.WHITE_WIN_CHECKMATE -> "انتصر اللاعب الأبيض كش مات (Checkmate)!"
                    GameResult.BLACK_WIN_CHECKMATE -> "انتصر اللاعب الأسود كش مات (Checkmate)!"
                    GameResult.WHITE_WIN_TIME -> "فوز الأبيض بانتهاء وقت خصمه!"
                    GameResult.BLACK_WIN_TIME -> "فوز الأسود بانتهاء وقت خصمه!"
                    GameResult.DRAW_STALEMATE -> "تعادل قهري بسبب خنق الملك (Stalemate)!"
                    GameResult.MANUAL_DRAW -> "تعادل بالاتفاق بين الطرفين!"
                    else -> "انتهت المباراة الملعوبة!"
                }

                Text(
                    text = announcement,
                    fontSize = 16.sp,
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "اكتملت المعركة في $movesCount نقلة تكتيكية معتمدة.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F),
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                        border = borderStroke(1.dp, Color(0xFF6750A4)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("الرئيسية", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("جولة جديدة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Simple borderStroke inline definition for accessibility
@Composable
fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = 
    androidx.compose.foundation.BorderStroke(width, color)
