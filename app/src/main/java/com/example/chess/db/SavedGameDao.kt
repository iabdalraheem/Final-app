package com.example.chess.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_games ORDER BY timestamp DESC")
    fun getAllSavedGames(): Flow<List<SavedGameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: SavedGameEntity): Long

    @Query("DELETE FROM saved_games WHERE id = :gameId")
    suspend fun deleteGame(gameId: Long)

    @Query("DELETE FROM saved_games")
    suspend fun deleteAllGames()
}
