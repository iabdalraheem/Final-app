package com.example.chess.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.Move
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Position

enum class ChessBoardTheme(
    val themeName: String,
    val lightSquare: Color,
    val darkSquare: Color,
    val accentColor: Color
) {
    EDITORIAL("Editorial", Color(0xFFF2E7D5), Color(0xFFAB8C6F), Color(0xFF6750A4)),
    WOOD("Classic Wood", Color(0xFFF0D9B5), Color(0xFFB58863), Color(0xFFFFCC00)),
    BLUE("Midnight Blue", Color(0xFFECECEC), Color(0xFF4B7399), Color(0xFF64B5F6)),
    EMERALD("Forest Emerald", Color(0xFFFFFFDD), Color(0xFF86A666), Color(0xFF81C784)),
    DARK("Brutalist Slate", Color(0xFFEFEFEF), Color(0xFF708090), Color(0xFFFF8A65))
}

@Composable
fun ChessBoardUi(
    modifier: Modifier = Modifier,
    boardTheme: ChessBoardTheme = ChessBoardTheme.EDITORIAL,
    useTextBadges: Boolean = false,
    isFlipped: Boolean = false,
    selectedPos: Position? = null,
    legalDestinations: List<Position> = emptyList(),
    lastMove: Move? = null,
    isKingInCheck: Boolean = false,
    kingInCheckPos: Position? = null,
    piecesState: Array<out Array<com.example.chess.Piece?>> = Array(8) { Array(8) { null } },
    onSquareClick: (Position) -> Unit
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val boardWidth = maxWidth
        val cellSize = boardWidth / 8

        Column(modifier = Modifier.fillMaxSize()) {
            for (displayRow in 0..7) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (displayCol in 0..7) {
                        // Flip coordinates depending on board view state
                        val r = if (isFlipped) 7 - displayRow else displayRow
                        val c = if (isFlipped) 7 - displayCol else displayCol
                        val pos = Position(r, c)

                        val isDark = (r + c) % 2 != 0
                        val baseColor = if (isDark) boardTheme.darkSquare else boardTheme.lightSquare

                        // Determine highlights
                        val isSelected = selectedPos == pos
                        val isLegalDest = legalDestinations.contains(pos)
                        val isLastMoveSrc = lastMove?.from == pos
                        val isLastMoveDst = lastMove?.to == pos
                        val isCheckTarget = isKingInCheck && kingInCheckPos == pos

                        // Dynamic layered coloration
                        val squareColor = when {
                            isCheckTarget -> Color(0xFFFF5252).copy(alpha = 0.85f) // Red highlight for checked king
                            isSelected -> boardTheme.accentColor.copy(alpha = 0.8f) // High-contrast selected piece
                            isLastMoveSrc || isLastMoveDst -> boardTheme.accentColor.copy(alpha = 0.35f) // Last move trails
                            else -> baseColor
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(squareColor)
                                .clickable { onSquareClick(pos) }
                                .testTag("square_${r}_${c}"),
                            contentAlignment = Alignment.Center
                        ) {
                            // Chess piece rendering
                            val piece = piecesState[r][c]
                            if (piece != null) {
                                val isWhitePiece = piece.color == PieceColor.WHITE
                                val pieceBgColor = if (isWhitePiece) Color.White else Color(0xFF212121)
                                val pieceTextColor = if (isWhitePiece) Color.Black else Color.White
                                val pieceBorderColor = if (isWhitePiece) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)

                                if (useTextBadges) {
                                    // Numeric/Text Badging style
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.72f)
                                            .clip(CircleShape)
                                            .background(pieceBgColor)
                                            .border(1.5.dp, pieceBorderColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = piece.getSymbol(useTextBadges = true),
                                            fontSize = (cellSize.value * 0.35f).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = pieceTextColor,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    // Elegant standard unicode chess vector glyphs
                                    Text(
                                        text = piece.getSymbol(useTextBadges = false),
                                        fontSize = (cellSize.value * 0.72f).sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (isWhitePiece) Color.White else Color.Black,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }

                            // Legal destination dot visual indicators
                            if (isLegalDest) {
                                val isCapture = piece != null
                                if (isCapture) {
                                    // Ring around piece
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.85f)
                                            .border(3.5.dp, Color(0xFF4CAF50).copy(alpha = 0.75f), CircleShape)
                                    )
                                } else {
                                    // Compact centered dot
                                    Box(
                                        modifier = Modifier
                                            .size(cellSize * 0.28f)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50).copy(alpha = 0.75f))
                                    )
                                }
                            }

                            // Board coordinates labels overlay (rank/files markers)
                            if (displayCol == 0) {
                                val rankLabel = (8 - r).toString()
                                Text(
                                    text = rankLabel,
                                    color = if (isDark) boardTheme.lightSquare.copy(alpha = 0.5f) else boardTheme.darkSquare.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 2.dp, top = 1.dp)
                                )
                            }

                            if (displayRow == 7) {
                                val fileLabel = ('a'.code + c).toChar().toString()
                                Text(
                                    text = fileLabel,
                                    color = if (isDark) boardTheme.lightSquare.copy(alpha = 0.5f) else boardTheme.darkSquare.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 2.dp, bottom = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EvaluationBar(
    modifier: Modifier = Modifier,
    score: Double, // positive is White, negative Black
    isFlipped: Boolean = false
) {
    // Normalise evaluation score into progress value [0, 1]
    // Bound score to range [-8.0, +8.0]
    val clampedScore = score.coerceIn(-8.0, 8.0)
    // Map -8.0..8.0 to 0.0..1.0
    val rawProgress = ((clampedScore + 8.0) / 16.0).toFloat()
    
    // Smooth transitions
    val animatedProgress by animateFloatAsState(
        targetValue = if (isFlipped) 1f - rawProgress else rawProgress,
        label = "evaluation_progress"
    )

    Column(
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(Color(0xFF2C2C2C)) // Black bottom background
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
    ) {
        // High-contrast filled portion representing White's strength
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f - animatedProgress)
                .background(Color(0xFF1A1A1A)) // Black portion
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(animatedProgress)
                .background(Color.White) // White portion
        )
    }
}
