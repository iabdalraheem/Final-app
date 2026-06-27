package com.example.chess.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val gameMode: String, // "PASS_PLAY", "COMPUTER"
    val difficultyLevel: Int = 1, // applicable if computer
    val result: String, // "WHITE_WIN", "BLACK_WIN", "DRAW", "ABANDONED"
    val pgnMoves: String, // Comma-separated list of algebraic moves
    val fenStates: String, // Newline-separated lists of FEN states
    val totalMoves: Int
)
