package com.example.chess

import kotlin.math.max
import kotlin.math.min

object ChessAI {

    // Piece-Square tables mapping from White's perspective (Pawn, Knight, Bishop, Rook, King)
    private val pawnTable = intArrayOf(
        0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0
    )

    private val knightTable = intArrayOf(
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    )

    private val bishopTable = intArrayOf(
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    )

    private val rookTable = intArrayOf(
          0,  0,  0,  0,  0,  0,  0,  0,
          5, 10, 10, 10, 10, 10, 10,  5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  5,  5,  5,  0,  0
    )

    // King middle game table
    private val kingTable = intArrayOf(
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    )

    // Evaluate standard score: White is positive (advantage), Black is negative
    fun evaluate(board: ChessBoard): Int {
        var score = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board.getPiece(Position(r, c)) ?: continue
                val materialVal = piece.type.value
                val isWhite = piece.color == PieceColor.WHITE
                
                // Get positional score
                val posIndex = if (isWhite) r * 8 + c else (7 - r) * 8 + c
                val psqValue = when (piece.type) {
                    PieceType.PAWN -> pawnTable[posIndex]
                    PieceType.KNIGHT -> knightTable[posIndex]
                    PieceType.BISHOP -> bishopTable[posIndex]
                    PieceType.ROOK -> rookTable[posIndex]
                    PieceType.KING -> kingTable[posIndex]
                    PieceType.QUEEN -> 0 // Balanced material
                }

                val netVal = materialVal + psqValue
                if (isWhite) {
                    score += netVal
                } else {
                    score -= netVal
                }
            }
        }
        return score
    }

    // Alpha-Beta Minimax search
    fun minimax(
        game: ChessGame,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean
    ): Int {
        if (depth == 0) {
            return evaluate(game.board)
        }

        val legalMoves = game.getAllLegalMoves(game.turn)
        if (legalMoves.isEmpty()) {
            return if (game.isInCheck(game.turn)) {
                // Checkmate
                if (game.turn == PieceColor.WHITE) {
                    // Black won
                    -100000 - depth
                } else {
                    // White won
                    100000 + depth
                }
            } else {
                // Stalemate
                0
            }
        }

        var localAlpha = alpha
        var localBeta = beta

        if (isMaximizing) {
            var maxEval = -1000000
            // Sort moves roughly to optimize alpha-beta cuts (captures first)
            val sortedMoves = legalMoves.sortedByDescending { it.capturedPiece?.type?.value ?: 0 }
            for (move in sortedMoves) {
                val cloned = game.cloneGame()
                cloned.makeMove(move)
                val evaluation = minimax(cloned, depth - 1, localAlpha, localBeta, false)
                maxEval = max(maxEval, evaluation)
                localAlpha = max(localAlpha, evaluation)
                if (localBeta <= localAlpha) {
                    break // Beta cutoff
                }
            }
            return maxEval
        } else {
            var minEval = 1000000
            val sortedMoves = legalMoves.sortedByDescending { it.capturedPiece?.type?.value ?: 0 }
            for (move in sortedMoves) {
                val cloned = game.cloneGame()
                cloned.makeMove(move)
                val evaluation = minimax(cloned, depth - 1, localAlpha, localBeta, true)
                minEval = min(minEval, evaluation)
                localBeta = min(localBeta, evaluation)
                if (localBeta <= localAlpha) {
                    break // Alpha cutoff
                }
            }
            return minEval
        }
    }

    // Solve the best move for current color
    fun getBestMove(game: ChessGame, depth: Int): Pair<Move?, Int> {
        val legalMoves = game.getAllLegalMoves(game.turn)
        if (legalMoves.isEmpty()) return Pair(null, 0)

        var bestMove: Move? = null
        val isWhite = game.turn == PieceColor.WHITE
        
        // Simple move ordering: captures first
        val sortedMoves = legalMoves.sortedByDescending { it.capturedPiece?.type?.value ?: 0 }

        if (isWhite) {
            var bestScore = -1000000
            for (move in sortedMoves) {
                val cloned = game.cloneGame()
                cloned.makeMove(move)
                val score = minimax(cloned, depth - 1, -1000000, 1000000, false)
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move
                }
            }
            return Pair(bestMove, bestScore)
        } else {
            var bestScore = 1000000
            for (move in sortedMoves) {
                val cloned = game.cloneGame()
                cloned.makeMove(move)
                val score = minimax(cloned, depth - 1, -1000000, 1000000, true)
                if (score < bestScore) {
                    bestScore = score
                    bestMove = move
                }
            }
            return Pair(bestMove, bestScore)
        }
    }
}
