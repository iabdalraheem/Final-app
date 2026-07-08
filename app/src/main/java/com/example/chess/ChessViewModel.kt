package com.example.chess

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chess.db.ChessDatabase
import com.example.chess.db.SavedGameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class GameMode {
    PASS_PLAY,
    COMPUTER,
    ANALYSIS
}

enum class ClockControl(val displayName: String, val msDuration: Long) {
    UNLIMITED("Unlimited", 0L),
    BULLET_1("1 Min (Bullet)", 60000L),
    BLITZ_3("3 Min (Blitz)", 180000L),
    BLITZ_5("5 Min (Blitz)", 300000L),
    RAPID_10("10 Min (Rapid)", 600000L),
    CLASSICAL_30("30 Min (Classical)", 1800000L)
}

enum class GameResult {
    ONGOING,
    WHITE_WIN_CHECKMATE,
    BLACK_WIN_CHECKMATE,
    WHITE_WIN_TIME,
    BLACK_WIN_TIME,
    DRAW_STALEMATE,
    DRAW_REPETITION,
    DRAW_INSUFFICIENT,
    MANUAL_DRAW,
    ABANDONED
}

class ChessViewModel(private val context: Context) : ViewModel() {
    private val database = ChessDatabase.getDatabase(context)
    private val savedGameDao = database.savedGameDao()

    // Active Game State
    private val _game = MutableStateFlow(ChessGame())
    val game: StateFlow<ChessGame> = _game.asStateFlow()

    private val _selectedPosition = MutableStateFlow<Position?>(null)
    val selectedPosition: StateFlow<Position?> = _selectedPosition.asStateFlow()

    private val _legalMoves = MutableStateFlow<List<Move>>(emptyList())
    val legalMoves: StateFlow<List<Move>> = _legalMoves.asStateFlow()

    private val _isFlipped = MutableStateFlow(false) // If true, Black is on bottom
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _gameMode = MutableStateFlow(GameMode.PASS_PLAY)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    private val _difficultyLevel = MutableStateFlow(2) // 1 to 4
    val difficultyLevel: StateFlow<Int> = _difficultyLevel.asStateFlow()

    private val _playerColorVsCpu = MutableStateFlow(PieceColor.WHITE)
    val playerColorVsCpu: StateFlow<PieceColor> = _playerColorVsCpu.asStateFlow()

    // Clocks
    private val _selectedClockControl = MutableStateFlow(ClockControl.RAPID_10)
    val selectedClockControl: StateFlow<ClockControl> = _selectedClockControl.asStateFlow()

    private val _whiteTimeMs = MutableStateFlow(600000L)
    val whiteTimeMs: StateFlow<Long> = _whiteTimeMs.asStateFlow()

    private val _blackTimeMs = MutableStateFlow(600000L)
    val blackTimeMs: StateFlow<Long> = _blackTimeMs.asStateFlow()

    private val _gameResult = MutableStateFlow(GameResult.ONGOING)
    val gameResult: StateFlow<GameResult> = _gameResult.asStateFlow()

    private val _showPromotionDialog = MutableStateFlow<Move?>(null)
    val showPromotionDialog: StateFlow<Move?> = _showPromotionDialog.asStateFlow()

    // AI/Analysis parameters
    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _engineEvaluationScore = MutableStateFlow(0.0) // Advantage: positive is White, negative Black
    val engineEvaluationScore: StateFlow<Double> = _engineEvaluationScore.asStateFlow()

    private val _engineSuggestedMove = MutableStateFlow<Move?>(null)
    val engineSuggestedMove: StateFlow<Move?> = _engineSuggestedMove.asStateFlow()

    // History database listing
    val savedGamesHistory = savedGameDao.getAllSavedGames()

    // Controllers
    private var clockJob: Job? = null
    private var analysisJob: Job? = null

    // Bluetooth LE Manager & States
    val bleManager by lazy { BleManager(context) }
    val bleConnectionState by lazy { bleManager.connectionState }
    val bleScannedDevices by lazy { bleManager.scannedDevices }
    val bleIsScanning by lazy { bleManager.isScanning }
    val bleReceivedMessage by lazy { bleManager.receivedMessage }
    val bleAutoConnectEnabled by lazy { bleManager.autoConnectEnabled }

    fun setBleAutoConnectEnabled(enabled: Boolean) {
        bleManager.setAutoConnectEnabled(enabled)
    }

    var bleLiftedPosition: Position? = null
    var bleLiftedPiece: Piece? = null

    private val _smartBleMode = MutableStateFlow(true)
    val smartBleMode: StateFlow<Boolean> = _smartBleMode.asStateFlow()

    fun setSmartBleMode(enabled: Boolean) {
        _smartBleMode.value = enabled
    }

    init {
        startClocks()
        triggerAnalysis()
        bleManager.onMessageReceived = { message ->
            processBleMessage(message)
        }
    }

    fun startBleScan() {
        bleManager.startScanning()
    }

    fun stopBleScan() {
        bleManager.stopScanning()
    }

    fun connectBleDevice(device: android.bluetooth.BluetoothDevice) {
        bleManager.connect(device)
    }

    fun disconnectBle() {
        bleManager.disconnect()
    }

    private fun parsePiece(pieceStr: String): Piece? {
        val s = pieceStr.lowercase().trim()
        if (s == "empty" || s == "none" || s == "ee" || s == "" || s == "null") return null
        if (s.length < 2) return null
        
        val color = if (s[0] == 'w') PieceColor.WHITE else if (s[0] == 'b') PieceColor.BLACK else return null
        val type = when (s[1]) {
            'p' -> PieceType.PAWN
            'n' -> PieceType.KNIGHT
            'b' -> PieceType.BISHOP
            'r' -> PieceType.ROOK
            'q' -> PieceType.QUEEN
            'k' -> PieceType.KING
            else -> return null
        }
        return Piece(type, color)
    }

    fun processBleMessage(message: String) {
        val parts = message.split(":")
        if (parts.size < 2) return
        
        val coord = parts[0].trim().lowercase()
        val pieceStr = parts[1].trim().lowercase()
        
        val pos = Position.fromAlgebraic(coord) ?: return
        val activeGame = _game.value
        
        val incomingPiece = parsePiece(pieceStr)
        
        if (incomingPiece == null) {
            // Square became empty
            val existingPiece = activeGame.board.getPiece(pos)
            if (existingPiece != null) {
                // Only register the lift if the piece belongs to the active turn side, or in analysis mode
                if (existingPiece.color == activeGame.turn || _gameMode.value == GameMode.ANALYSIS) {
                    bleLiftedPosition = pos
                    bleLiftedPiece = existingPiece
                }
                
                if (!_smartBleMode.value || _gameMode.value == GameMode.ANALYSIS) {
                    activeGame.board.setPiece(pos, null)
                    _game.value = activeGame.cloneGame()
                    triggerAnalysis()
                }
            }
        } else {
            // Square became occupied
            val currentPieceOnDigitalBoard = activeGame.board.getPiece(pos)
            if (currentPieceOnDigitalBoard == incomingPiece) {
                // The digital board already has this piece on this square!
                // This means the physical board is just syncing/mirroring a move that was already made on the screen (e.g., by the computer or via UI click).
                // We don't need to do anything, just clear the lifted state.
                bleLiftedPosition = null
                bleLiftedPiece = null
                return
            }

            val liftedPos = bleLiftedPosition
            val liftedP = bleLiftedPiece
            
            if (_smartBleMode.value) {
                // Find all legal moves for the current turn's color that end at 'pos' and match the incoming piece
                val searchColors = if (_gameMode.value == GameMode.ANALYSIS) {
                    listOf(PieceColor.WHITE, PieceColor.BLACK)
                } else {
                    listOf(activeGame.turn)
                }
                
                val candidateMoves = mutableListOf<Move>()
                for (color in searchColors) {
                    for (r in 0..7) {
                        for (c in 0..7) {
                            val fromPos = Position(r, c)
                            val p = activeGame.board.getPiece(fromPos)
                            if (p != null && p.color == color) {
                                val legalMoves = activeGame.getLegalMoves(fromPos)
                                val matches = legalMoves.filter { move ->
                                    move.to == pos && (
                                        // Either the piece type and color match exactly
                                        (move.pieceMoved.type == incomingPiece.type && move.pieceMoved.color == incomingPiece.color) ||
                                        // Or it's a promotion where the color matches and the promotion result matches the placed piece
                                        (move.promotionResult != null && move.pieceMoved.color == incomingPiece.color && move.promotionResult == incomingPiece.type)
                                    )
                                }
                                candidateMoves.addAll(matches)
                            }
                        }
                    }
                }
                
                var selectedMove: Move? = null
                if (candidateMoves.isNotEmpty()) {
                    if (candidateMoves.size == 1) {
                        // Unambiguous: Only one legal move can reach this square with this piece type and color
                        selectedMove = candidateMoves[0]
                    } else {
                        // Ambiguity: Multiple legal moves of this piece type and color can reach this square
                        // 1. Prioritize matching the lifted position if we have one
                        if (liftedPos != null) {
                            selectedMove = candidateMoves.find { it.from == liftedPos }
                        }
                        
                        // 2. If no lift match, fallback to the first candidate move
                        if (selectedMove == null) {
                            selectedMove = candidateMoves[0]
                        }
                    }
                }
                
                if (selectedMove != null) {
                    executeMove(selectedMove)
                    bleLiftedPosition = null
                    bleLiftedPiece = null
                } else {
                    // Strictly enforce Chess rules: if the move played physically is not legal,
                    // do NOT update the board state. We just vibrate longer as a warning.
                    vibrateDevice(250)
                    android.util.Log.w("BleManager", "Illegal physical move to $coord with piece $pieceStr rejected.")
                    
                    // We keep the board state exactly as is, but we clear the lifted state to allow retrying
                    bleLiftedPosition = null
                    bleLiftedPiece = null
                }
            } else {
                // Free sync mode: just set the piece directly
                activeGame.board.setPiece(pos, incomingPiece)
                _game.value = activeGame.cloneGame()
                triggerAnalysis()
            }
        }
    }


    fun setGameMode(mode: GameMode) {
        _gameMode.value = mode
        _selectedPosition.value = null
        _legalMoves.value = emptyList()
        _gameResult.value = GameResult.ONGOING
        
        val newGame = ChessGame()
        _game.value = newGame
        
        _isFlipped.value = (mode == GameMode.COMPUTER && _playerColorVsCpu.value == PieceColor.BLACK)
        
        resetClockTimes()
        triggerAnalysis()

        // Play Game Start sound
        ChessSoundPlayer.playStart()

        // Handle first computer move if AI is White
        if (mode == GameMode.COMPUTER && _playerColorVsCpu.value == PieceColor.BLACK) {
            triggerAiMove()
        }
    }

    fun setDifficulty(level: Int) {
        _difficultyLevel.value = level.coerceIn(1, 4)
        triggerAnalysis()
    }

    fun setPlayerColorVsCpu(color: PieceColor) {
        _playerColorVsCpu.value = color
        setGameMode(GameMode.COMPUTER)
    }

    fun setClockControl(control: ClockControl) {
        _selectedClockControl.value = control
        resetClockTimes()
    }

    private fun resetClockTimes() {
        clockJob?.cancel()
        val duration = _selectedClockControl.value.msDuration
        _whiteTimeMs.value = duration
        _blackTimeMs.value = duration
        _gameResult.value = GameResult.ONGOING
        startClocks()
    }

    private fun startClocks() {
        if (_selectedClockControl.value == ClockControl.UNLIMITED) return
        
        clockJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(100)
                if (_gameResult.value != GameResult.ONGOING) continue
                
                val currentTurn = _game.value.turn
                if (currentTurn == PieceColor.WHITE) {
                    val nextTime = _whiteTimeMs.value - 100
                    if (nextTime <= 0) {
                        _whiteTimeMs.value = 0
                        _gameResult.value = GameResult.BLACK_WIN_TIME
                        vibrateDevice(500)
                        ChessSoundPlayer.playEnd()
                        saveCompletedGame(GameResult.BLACK_WIN_TIME)
                    } else {
                        _whiteTimeMs.value = nextTime
                    }
                } else {
                    val nextTime = _blackTimeMs.value - 100
                    if (nextTime <= 0) {
                        _blackTimeMs.value = 0
                        _gameResult.value = GameResult.WHITE_WIN_TIME
                        vibrateDevice(500)
                        ChessSoundPlayer.playEnd()
                        saveCompletedGame(GameResult.WHITE_WIN_TIME)
                    } else {
                        _blackTimeMs.value = nextTime
                    }
                }
            }
        }
    }

    fun flipBoard() {
        _isFlipped.value = !_isFlipped.value
    }

    fun selectSquare(pos: Position) {
        if (_gameResult.value != GameResult.ONGOING && _gameMode.value != GameMode.ANALYSIS) return
        if (_isAiThinking.value) return

        val activeGame = _game.value
        val clickedPiece = activeGame.board.getPiece(pos)
        val selected = _selectedPosition.value

        if (selected == null) {
            // Pick piece corresponding to current color turn (unless analysis mode allow free movement of custom sides)
            if (clickedPiece != null && (_gameMode.value == GameMode.ANALYSIS || clickedPiece.color == activeGame.turn)) {
                _selectedPosition.value = pos
                _legalMoves.value = activeGame.getLegalMoves(pos)
            }
        } else {
            // Selected piece exists, check if clicked on target move location
            val matchingMove = _legalMoves.value.find { it.to == pos }
            if (matchingMove != null) {
                // Check if this move is a Pawn Promotion
                if (matchingMove.pieceMoved.type == PieceType.PAWN && (matchingMove.to.row == 0 || matchingMove.to.row == 7)) {
                    _showPromotionDialog.value = matchingMove
                } else {
                    executeMove(matchingMove)
                }
                _selectedPosition.value = null
                _legalMoves.value = emptyList()
            } else {
                // Clicked something else, re-select if player's turn piece or discard
                if (clickedPiece != null && (_gameMode.value == GameMode.ANALYSIS || clickedPiece.color == activeGame.turn)) {
                    _selectedPosition.value = pos
                    _legalMoves.value = activeGame.getLegalMoves(pos)
                } else {
                    _selectedPosition.value = null
                    _legalMoves.value = emptyList()
                }
            }
        }
    }

    fun choosePromotion(type: PieceType) {
        val pending = _showPromotionDialog.value ?: return
        _showPromotionDialog.value = null
        
        val promoMove = Move(
            from = pending.from,
            to = pending.to,
            pieceMoved = pending.pieceMoved,
            capturedPiece = pending.capturedPiece,
            isCastlingKingSide = pending.isCastlingKingSide,
            isCastlingQueenSide = pending.isCastlingQueenSide,
            isEnPassant = pending.isEnPassant,
            promotionResult = type
        )
        executeMove(promoMove)
    }

    fun cancelPromotion() {
        _showPromotionDialog.value = null
        _selectedPosition.value = null
        _legalMoves.value = emptyList()
    }

    private fun executeMove(m: Move) {
        val activeGame = _game.value
        val success = activeGame.makeMove(m)
        if (success) {
            vibrateDevice(50) // Subtle feedback
            
            // Re-broadcast game state manually to trigger Flow update
            _game.value = activeGame.cloneGame()
            
            // Determine and play appropriate movement sound:
            val pNextTurn = activeGame.turn
            if (activeGame.isInCheck(pNextTurn)) {
                ChessSoundPlayer.playCheck()
            } else if (m.capturedPiece != null) {
                ChessSoundPlayer.playCapture()
            } else {
                ChessSoundPlayer.playNormalMove()
            }

            // Trigger local evaluation
            triggerAnalysis()

            // Verify Game Ends (Checkmate/Stalemate)
            checkMatchTermination()

            // Computer turn trigger
            if (_gameResult.value == GameResult.ONGOING && _gameMode.value == GameMode.COMPUTER && _game.value.turn != _playerColorVsCpu.value) {
                triggerAiMove()
            }
        }
    }

    private fun checkMatchTermination() {
        val activeGame = _game.value
        if (activeGame.isCheckmate()) {
            val result = if (activeGame.turn == PieceColor.WHITE) GameResult.BLACK_WIN_CHECKMATE else GameResult.WHITE_WIN_CHECKMATE
            _gameResult.value = result
            vibrateDevice(300)
            ChessSoundPlayer.playEnd()
            saveCompletedGame(result)
        } else if (activeGame.isStalemate()) {
            _gameResult.value = GameResult.DRAW_STALEMATE
            vibrateDevice(200)
            ChessSoundPlayer.playEnd()
            saveCompletedGame(GameResult.DRAW_STALEMATE)
        }
    }

    private fun triggerAiMove() {
        _isAiThinking.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val currentGame = _game.value
            // Capping target depth at max 3 to avoid ANR freeze/exhaustion on slower devices
            val searchDepth = _difficultyLevel.value.coerceAtMost(3)
            
            // Artificial delay to make search feel realistic on instantly simple positions
            val startTime = System.currentTimeMillis()
            val bestSelection = ChessAI.getBestMove(currentGame, searchDepth)
            val computationDuration = System.currentTimeMillis() - startTime
            if (computationDuration < 600) {
                delay(600 - computationDuration)
            }

            withContext(Dispatchers.Main) {
                _isAiThinking.value = false
                val selectedMove = bestSelection.first
                if (selectedMove != null && _gameResult.value == GameResult.ONGOING) {
                    executeMove(selectedMove)
                }
            }
        }
    }

    private fun triggerAnalysis() {
        analysisJob?.cancel()
        val currentMode = _gameMode.value
        if (currentMode == GameMode.PASS_PLAY) {
            _engineEvaluationScore.value = 0.0
            _engineSuggestedMove.value = null
            return
        }

        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            val activeState = _game.value
            // Calculate a score and a best suggestion
            val staticScoreValue = ChessAI.evaluate(activeState.board)
            val evaluationScore = staticScoreValue / 100.0 // Raw advantage in pawns

            // Only find best optimal move suggestion at depth 3 for ANALYSIS mode (saves major CPU)
            val recommendation = if (currentMode == GameMode.ANALYSIS) {
                ChessAI.getBestMove(activeState, 3).first
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                _engineEvaluationScore.value = evaluationScore
                _engineSuggestedMove.value = recommendation
            }
        }
    }

    private fun saveCompletedGame(result: GameResult) {
        val activeState = _game.value
        if (activeState.algebraicHistory.isEmpty()) return // don't write empty runs
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Serialise move files
                val movesString = activeState.algebraicHistory.joinToString(",")
                
                // Serialise state maps (FENS) associated for replay
                val listSteps = mutableListOf<String>()
                // Record history transitions if available
                val cloneGame = ChessGame()
                listSteps.add(cloneGame.toFen())
                for (move in activeState.moveHistory) {
                    cloneGame.makeMove(move)
                    listSteps.add(cloneGame.toFen())
                }
                val statesString = listSteps.joinToString("\n")

                val entry = SavedGameEntity(
                    timestamp = System.currentTimeMillis(),
                    gameMode = _gameMode.value.name,
                    difficultyLevel = _difficultyLevel.value,
                    result = result.name,
                    pgnMoves = movesString,
                    fenStates = statesString,
                    totalMoves = activeState.algebraicHistory.size
                )
                savedGameDao.insertGame(entry)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadSavedGameToAnalysis(savedGame: SavedGameEntity) {
        _gameMode.value = GameMode.ANALYSIS
        _selectedPosition.value = null
        _legalMoves.value = emptyList()
        _gameResult.value = GameResult.ONGOING
        
        val restoredGame = ChessGame()
        _game.value = restoredGame
        
        // Load final FEN state or let user step through FEN states if desired.
        // We can parse the last FEN state for current review
        val fens = savedGame.fenStates.split("\n")
        if (fens.isNotEmpty()) {
            val lastFen = fens.last()
            restoredGame.loadFromFen(lastFen)
            
            // Build reconstructed algebraic moves for history logging
            val parsedMoves = savedGame.pgnMoves.split(",")
            restoredGame.algebraicHistory.clear()
            restoredGame.algebraicHistory.addAll(parsedMoves.filter { it.isNotEmpty() })
            
            _game.value = restoredGame.cloneGame()
        }
        
        triggerAnalysis()
    }

    fun resetGameToMode() {
        setGameMode(_gameMode.value)
    }

    fun offerDraw() {
        _gameResult.value = GameResult.MANUAL_DRAW
        ChessSoundPlayer.playEnd()
        saveCompletedGame(GameResult.MANUAL_DRAW)
    }

    fun resignGame() {
        val termResult = if (_game.value.turn == PieceColor.WHITE) GameResult.BLACK_WIN_CHECKMATE else GameResult.WHITE_WIN_CHECKMATE
        _gameResult.value = termResult
        ChessSoundPlayer.playEnd()
        saveCompletedGame(termResult)
    }

    fun cleanAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            savedGameDao.deleteAllGames()
        }
    }

    private fun vibrateDevice(ms: Long) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(ms)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class ChessViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChessViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChessViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
