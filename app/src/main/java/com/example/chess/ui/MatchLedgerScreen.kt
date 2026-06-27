package com.example.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.db.SavedGameEntity
import com.example.chess.ChessViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchLedgerScreen(
    viewModel: ChessViewModel,
    onNavigateToAnalysis: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val savedGamesList by viewModel.savedGamesHistory.collectAsState(initial = emptyList())
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDF8F6))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("ledger_back_btn")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B1B1F))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "سجل المباريات والأرشيف",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1F)
                    )
                }

                // Delete All Button
                if (savedGamesList.isNotEmpty()) {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Games", tint = Color(0xFFB3261E))
                    }
                }
            }

            // Games LazyColumn
            if (savedGamesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color(0xFFCAC4D0),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "لم يتم تسجيل مباريات بعد",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ابدأ جولة جديدة ضد الحاسوب أو في وضع التناوب لتسجيل مجريات مباراتك وأرشفة نقلاتك تكتيكياً هنا.",
                            color = Color(0xFF49454F),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(savedGamesList) { savedGame ->
                        SavedGameCard(
                            game = savedGame,
                            onReplayClick = {
                                viewModel.loadSavedGameToAnalysis(savedGame)
                                onNavigateToAnalysis()
                            },
                            onDeleteClick = {
                                viewModel.cleanAllLogs()
                            }
                        )
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("مسح أرشيف السجلات؟", fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F)) },
                text = { Text("سيؤدي هذا الإجراء إلى مسح كافة سجلات المباريات والمواجهات والتحليلات المخزنة بشكل نهائي ولا يمكن استعادتها.", color = Color(0xFF49454F)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.cleanAllLogs()
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White)
                    ) {
                        Text("مسح الكل", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("إلغاء", color = Color(0xFF6750A4))
                    }
                },
                containerColor = Color.White
            )
        }
    }
}

@Composable
fun SavedGameCard(
    game: SavedGameEntity,
    onReplayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()) }
    val dateString = formatter.format(Date(game.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
            .clickable { onReplayClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top info: Mode and Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val modeColor = if (game.gameMode == "COMPUTER") Color(0xFF6750A4) else Color(0xFF0061A4)
                    val modeText = if (game.gameMode == "COMPUTER") "ضد الحاسوب (مستوى ${game.difficultyLevel})" else "لاعب ضد لاعب (تناوب)"
                    val modeIcon = if (game.gameMode == "COMPUTER") Icons.Default.SmartToy else Icons.Default.People

                    Icon(modeIcon, contentDescription = null, sizeIndex = 14, tint = modeColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = modeText,
                        color = modeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = dateString,
                    color = Color(0xFF49454F),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle info: Result announcement
            val outcomeHeadline = when (game.result) {
                "WHITE_WIN_CHECKMATE" -> "فاز الأبيض (كش مات)"
                "BLACK_WIN_CHECKMATE" -> "فاز الأسود (كش مات)"
                "WHITE_WIN_TIME" -> "فاز الأبيض (بالوقت)"
                "BLACK_WIN_TIME" -> "فاز الأسود (بالوقت)"
                "DRAW_STALEMATE" -> "تعادل بخنق الملك"
                "MANUAL_DRAW" -> "تعادل بالاتفاق"
                else -> "منتهية"
            }
            val outcomeCaption = when (game.result) {
                "WHITE_WIN_CHECKMATE", "BLACK_WIN_CHECKMATE" -> "حسم تكتيكي بالكش مات"
                "WHITE_WIN_TIME", "BLACK_WIN_TIME" -> "تجاوز وقت الساعة"
                else -> "قرار تعادل فني"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = outcomeHeadline,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1B1B1F)
                    )
                    Text(
                        text = "${game.totalMoves} نقلة ملعوبة • $outcomeCaption",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Interactive click details icon
                Button(
                    onClick = onReplayClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7), contentColor = Color(0xFF6750A4)),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تحليل النقلات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Inline Icon overlay sizing helper
@Composable
fun Icon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    sizeIndex: Int,
    tint: Color
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(sizeIndex.dp)
    )
}
