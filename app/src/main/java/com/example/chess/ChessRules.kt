package com.example.chess

import kotlin.math.abs

class ChessGame {
    var board = ChessBoard()
    var turn = PieceColor.WHITE
    
    // Castling rights
    var whiteCanCastleKingSide = true
    var whiteCanCastleQueenSide = true
    var blackCanCastleKingSide = true
    var blackCanCastleQueenSide = true
    
    // En passant target position
    var enPassantTarget: Position? = null
    
    // Draw rule helpers
    var halfmoveClock = 0
    var fullmoveNumber = 1
    
    // Captured pieces lists maintained for UX stats
    val capturedByWhite = mutableListOf<Piece>()
    val capturedByBlack = mutableListOf<Piece>()
    
    // Move log for back-tracking or list display
    val moveHistory = mutableListOf<Move>()
    val algebraicHistory = mutableListOf<String>()

    init {
        resetGame()
    }

    fun resetGame() {
        board.resetToStartingPosition()
        turn = PieceColor.WHITE
        whiteCanCastleKingSide = true
        whiteCanCastleQueenSide = true
        blackCanCastleKingSide = true
        blackCanCastleQueenSide = true
        enPassantTarget = null
        halfmoveClock = 0
        fullmoveNumber = 1
        capturedByWhite.clear()
        capturedByBlack.clear()
        moveHistory.clear()
        algebraicHistory.clear()
    }

    fun loadFromFen(fen: String): Boolean {
        try {
            val parts = fen.trim().split("\\s+".toRegex())
            if (parts.size < 2) return false
            
            board.loadFromFen(parts[0])
            turn = if (parts[1].lowercase() == "b") PieceColor.BLACK else PieceColor.WHITE
            
            // Castling rights
            whiteCanCastleKingSide = false
            whiteCanCastleQueenSide = false
            blackCanCastleKingSide = false
            blackCanCastleQueenSide = false
            if (parts.size >= 3) {
                val castling = parts[2]
                if (castling.contains("K")) whiteCanCastleKingSide = true
                if (castling.contains("Q")) whiteCanCastleQueenSide = true
                if (castling.contains("k")) blackCanCastleKingSide = true
                if (castling.contains("q")) blackCanCastleQueenSide = true
            }
            
            // En passant
            enPassantTarget = null
            if (parts.size >= 4) {
                val ep = parts[3]
                if (ep != "-") {
                    enPassantTarget = Position.fromAlgebraic(ep)
                }
            }
            
            // Half & Full moves
            halfmoveClock = if (parts.size >= 5) parts[4].toIntOrNull() ?: 0 else 0
            fullmoveNumber = if (parts.size >= 6) parts[5].toIntOrNull() ?: 1 else 1
            
            calculateCapturedPieces()
            moveHistory.clear()
            algebraicHistory.clear()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun toFen(): String {
        val boardPart = board.toFenBoardPart()
        val turnPart = if (turn == PieceColor.WHITE) "w" else "b"
        
        // Castling
        val castlingSb = StringBuilder()
        if (whiteCanCastleKingSide) castlingSb.append("K")
        if (whiteCanCastleQueenSide) castlingSb.append("Q")
        if (blackCanCastleKingSide) castlingSb.append("k")
        if (blackCanCastleQueenSide) castlingSb.append("q")
        val castlingPart = if (castlingSb.isEmpty()) "-" else castlingSb.toString()
        
        // En passant
        val epPart = enPassantTarget?.toAlgebraic() ?: "-"
        
        return "$boardPart $turnPart $castlingPart $epPart $halfmoveClock $fullmoveNumber"
    }

    private fun calculateCapturedPieces() {
        capturedByWhite.clear()
        capturedByBlack.clear()
        
        // Count pieces currently on board to determine what is captured
        val baseCount = mutableMapOf<Piece, Int>()
        // Initialize base pieces
        baseCount[Piece(PieceType.PAWN, PieceColor.WHITE)] = 8
        baseCount[Piece(PieceType.KNIGHT, PieceColor.WHITE)] = 2
        baseCount[Piece(PieceType.BISHOP, PieceColor.WHITE)] = 2
        baseCount[Piece(PieceType.ROOK, PieceColor.WHITE)] = 2
        baseCount[Piece(PieceType.QUEEN, PieceColor.WHITE)] = 1
        baseCount[Piece(PieceType.KING, PieceColor.WHITE)] = 1

        baseCount[Piece(PieceType.PAWN, PieceColor.BLACK)] = 8
        baseCount[Piece(PieceType.KNIGHT, PieceColor.BLACK)] = 2
        baseCount[Piece(PieceType.BISHOP, PieceColor.BLACK)] = 2
        baseCount[Piece(PieceType.ROOK, PieceColor.BLACK)] = 2
        baseCount[Piece(PieceType.QUEEN, PieceColor.BLACK)] = 1
        baseCount[Piece(PieceType.KING, PieceColor.BLACK)] = 1

        for (r in 0..7) {
            for (c in 0..7) {
                val p = board.getPiece(Position(r, c))
                if (p != null) {
                    baseCount[p] = (baseCount[p] ?: 0) - 1
                }
            }
        }

        // Add to capture logs
        for ((p, missingCount) in baseCount) {
            if (missingCount > 0) {
                repeat(missingCount) {
                    if (p.color == PieceColor.WHITE) {
                        capturedByBlack.add(p)
                    } else {
                        capturedByWhite.add(p)
                    }
                }
            }
        }
    }

    fun isPositionAttacked(pos: Position, byColor: PieceColor): Boolean {
        // Find if any piece of 'byColor' can attack 'pos'
        for (r in 0..7) {
            for (c in 0..7) {
                val attacker = board.getPiece(Position(r, c))
                if (attacker != null && attacker.color == byColor) {
                    val pseudoMoves = getPseudoLegalMoves(Position(r, c), attacker, checkCastling = false)
                    if (pseudoMoves.any { it.to == pos }) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun getKingPosition(color: PieceColor): Position {
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board.getPiece(Position(r, c))
                if (p != null && p.type == PieceType.KING && p.color == color) {
                    return Position(r, c)
                }
            }
        }
        // Fallback, shouldn't reach
        return Position(0, 0)
    }

    fun isInCheck(color: PieceColor): Boolean {
        val kingPos = getKingPosition(color)
        return isPositionAttacked(kingPos, color.opponent())
    }

    fun getPseudoLegalMoves(pos: Position, piece: Piece, checkCastling: Boolean = true): List<Move> {
        val moves = mutableListOf<Move>()
        val row = pos.row
        val col = pos.col
        val color = piece.color
        val opp = color.opponent()

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (color == PieceColor.WHITE) -1 else 1
                val startRow = if (color == PieceColor.WHITE) 6 else 1
                val promoRow = if (color == PieceColor.WHITE) 0 else 7

                // 1 step forward
                val f1 = Position(row + dir, col)
                if (f1.isValid() && board.getPiece(f1) == null) {
                    if (f1.row == promoRow) {
                        listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach {
                            moves.add(Move(pos, f1, piece, promotionResult = it))
                        }
                    } else {
                        moves.add(Move(pos, f1, piece))
                    }

                    // 2 steps forward
                    val f2 = Position(row + 2 * dir, col)
                    if (row == startRow && board.getPiece(f2) == null) {
                        moves.add(Move(pos, f2, piece))
                    }
                }

                // Normal Diag captures
                for (dc in listOf(-1, 1)) {
                    val dest = Position(row + dir, col + dc)
                    if (dest.isValid()) {
                        val occupier = board.getPiece(dest)
                        if (occupier != null && occupier.color == opp) {
                            if (dest.row == promoRow) {
                                listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach {
                                    moves.add(Move(pos, dest, piece, capturedPiece = occupier, promotionResult = it))
                                }
                            } else {
                                moves.add(Move(pos, dest, piece, capturedPiece = occupier))
                            }
                        }
                        
                        // En Passant capture
                        if (enPassantTarget != null && dest == enPassantTarget) {
                            val victimPos = Position(row, col + dc)
                            moves.add(Move(pos, dest, piece, capturedPiece = board.getPiece(victimPos), isEnPassant = true))
                        }
                    }
                }
            }
            PieceType.KNIGHT -> {
                val offsets = listOf(
                    -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                    1 to -2, 1 to 2, 2 to -1, 2 to 1
                )
                for ((dr, dc) in offsets) {
                    val dest = Position(row + dr, col + dc)
                    if (dest.isValid()) {
                        val occupier = board.getPiece(dest)
                        if (occupier == null) {
                            moves.add(Move(pos, dest, piece))
                        } else if (occupier.color == opp) {
                            moves.add(Move(pos, dest, piece, capturedPiece = occupier))
                        }
                    }
                }
            }
            PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN -> {
                val directions = mutableListOf<Pair<Int, Int>>()
                if (piece.type == PieceType.BISHOP || piece.type == PieceType.QUEEN) {
                    directions.addAll(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
                }
                if (piece.type == PieceType.ROOK || piece.type == PieceType.QUEEN) {
                    directions.addAll(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
                }

                for ((dr, dc) in directions) {
                    var currRow = row + dr
                    var currCol = col + dc
                    while (Position(currRow, currCol).isValid()) {
                        val currPos = Position(currRow, currCol)
                        val occupier = board.getPiece(currPos)
                        if (occupier == null) {
                            moves.add(Move(pos, currPos, piece))
                        } else {
                            if (occupier.color == opp) {
                                moves.add(Move(pos, currPos, piece, capturedPiece = occupier))
                            }
                            break // blocked
                        }
                        currRow += dr
                        currCol += dc
                    }
                }
            }
            PieceType.KING -> {
                val offsets = listOf(
                    -1 to -1, -1 to 0, -1 to 1,
                    0 to -1,           0 to 1,
                    1 to -1,  1 to 0,  1 to 1
                )
                for ((dr, dc) in offsets) {
                    val dest = Position(row + dr, col + dc)
                    if (dest.isValid()) {
                        val occupier = board.getPiece(dest)
                        if (occupier == null) {
                            moves.add(Move(pos, dest, piece))
                        } else if (occupier.color == opp) {
                            moves.add(Move(pos, dest, piece, capturedPiece = occupier))
                        }
                    }
                }

                // Castling check (only checked if checkCastling runs and King not original inside check)
                if (checkCastling && !isInCheck(color)) {
                    if (color == PieceColor.WHITE) {
                        // King side
                        if (whiteCanCastleKingSide &&
                            board.getPiece(Position(7, 5)) == null &&
                            board.getPiece(Position(7, 6)) == null &&
                            !isPositionAttacked(Position(7, 5), PieceColor.BLACK) &&
                            !isPositionAttacked(Position(7, 6), PieceColor.BLACK)
                        ) {
                            moves.add(Move(pos, Position(7, 6), piece, isCastlingKingSide = true))
                        }
                        // Queen side
                        if (whiteCanCastleQueenSide &&
                            board.getPiece(Position(7, 1)) == null &&
                            board.getPiece(Position(7, 2)) == null &&
                            board.getPiece(Position(7, 3)) == null &&
                            !isPositionAttacked(Position(7, 3), PieceColor.BLACK) &&
                            !isPositionAttacked(Position(7, 2), PieceColor.BLACK)
                        ) {
                            moves.add(Move(pos, Position(7, 2), piece, isCastlingQueenSide = true))
                        }
                    } else {
                        // King side
                        if (blackCanCastleKingSide &&
                            board.getPiece(Position(0, 5)) == null &&
                            board.getPiece(Position(0, 6)) == null &&
                            !isPositionAttacked(Position(0, 5), PieceColor.WHITE) &&
                            !isPositionAttacked(Position(0, 6), PieceColor.WHITE)
                        ) {
                            moves.add(Move(pos, Position(0, 6), piece, isCastlingKingSide = true))
                        }
                        // Queen side
                        if (blackCanCastleQueenSide &&
                            board.getPiece(Position(0, 1)) == null &&
                            board.getPiece(Position(0, 2)) == null &&
                            board.getPiece(Position(0, 3)) == null &&
                            !isPositionAttacked(Position(0, 3), PieceColor.WHITE) &&
                            !isPositionAttacked(Position(0, 2), PieceColor.WHITE)
                        ) {
                            moves.add(Move(pos, Position(0, 2), piece, isCastlingQueenSide = true))
                        }
                    }
                }
            }
        }
        return moves
    }

    fun getLegalMoves(pos: Position): List<Move> {
        val piece = board.getPiece(pos) ?: return emptyList()
        if (piece.color != turn) return emptyList()

        val pseudo = getPseudoLegalMoves(pos, piece, checkCastling = true)
        val legal = mutableListOf<Move>()

        for (m in pseudo) {
            // Simulate move
            val nextGame = cloneGame()
            nextGame.executeSimulation(m)
            if (!nextGame.isInCheck(piece.color)) {
                legal.add(m)
            }
        }
        return legal
    }

    fun getAllLegalMoves(color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board.getPiece(Position(r, c))
                if (p != null && p.color == color) {
                    moves.addAll(getLegalMoves(Position(r, c)))
                }
            }
        }
        return moves
    }

    private fun executeSimulation(m: Move) {
        // Simple move execution specifically for check verification
        val piece = m.pieceMoved
        board.setPiece(m.from, null)
        board.setPiece(m.to, piece)

        if (m.isEnPassant) {
            val epRow = m.from.row
            val epCol = m.to.col
            board.setPiece(Position(epRow, epCol), null)
        }

        if (m.isCastlingKingSide) {
            val rRow = if (piece.color == PieceColor.WHITE) 7 else 0
            val rook = board.getPiece(Position(rRow, 7))
            board.setPiece(Position(rRow, 7), null)
            board.setPiece(Position(rRow, 5), rook)
        }

        if (m.isCastlingQueenSide) {
            val rRow = if (piece.color == PieceColor.WHITE) 7 else 0
            val rook = board.getPiece(Position(rRow, 0))
            board.setPiece(Position(rRow, 0), null)
            board.setPiece(Position(rRow, 3), rook)
        }

        if (m.promotionResult != null) {
            board.setPiece(m.to, Piece(m.promotionResult, piece.color))
        }
    }

    fun makeMove(m: Move): Boolean {
        val actualLegal = getLegalMoves(m.from)
        if (!actualLegal.any { it.to == m.to && it.promotionResult == m.promotionResult }) {
            return false
        }

        // Generate algebraic notation BEFORE modifying the board completely (or capture checking)
        val testNextGame = cloneGame()
        testNextGame.executeSimulation(m)
        val isOpponentCheck = testNextGame.isInCheck(turn.opponent())
        val isOpponentMate = testNextGame.getAllLegalMoves(turn.opponent()).isEmpty() && isOpponentCheck
        val textRepresentation = m.toAlgebraic(isCheck = isOpponentCheck, isMate = isOpponentMate)

        val piece = m.pieceMoved
        
        // Piece capture effects
        m.capturedPiece?.let {
            if (turn == PieceColor.WHITE) {
                capturedByWhite.add(it)
            } else {
                capturedByBlack.add(it)
            }
        }

        // Update squares
        board.setPiece(m.from, null)
        board.setPiece(m.to, if (m.promotionResult != null) Piece(m.promotionResult, piece.color) else piece)

        // Handle specials
        if (m.isEnPassant) {
            val enemyRow = m.from.row
            val enemyCol = m.to.col
            board.setPiece(Position(enemyRow, enemyCol), null)
        }

        if (m.isCastlingKingSide) {
            val rookRow = if (piece.color == PieceColor.WHITE) 7 else 0
            val rook = board.getPiece(Position(rookRow, 7))
            board.setPiece(Position(rookRow, 7), null)
            board.setPiece(Position(rookRow, 5), rook)
        }

        if (m.isCastlingQueenSide) {
            val rookRow = if (piece.color == PieceColor.WHITE) 7 else 0
            val rook = board.getPiece(Position(rookRow, 0))
            board.setPiece(Position(rookRow, 0), null)
            board.setPiece(Position(rookRow, 3), rook)
        }

        // Set en passant details
        enPassantTarget = if (piece.type == PieceType.PAWN && abs(m.from.row - m.to.row) == 2) {
            Position((m.from.row + m.to.row) / 2, m.from.col)
        } else {
            null
        }

        // Update castling rights
        if (piece.type == PieceType.KING) {
            if (piece.color == PieceColor.WHITE) {
                whiteCanCastleKingSide = false
                whiteCanCastleQueenSide = false
            } else {
                blackCanCastleKingSide = false
                blackCanCastleQueenSide = false
            }
        } else if (piece.type == PieceType.ROOK) {
            if (piece.color == PieceColor.WHITE) {
                if (m.from == Position(7, 7)) whiteCanCastleKingSide = false
                if (m.from == Position(7, 0)) whiteCanCastleQueenSide = false
            } else {
                if (m.from == Position(0, 7)) blackCanCastleKingSide = false
                if (m.from == Position(0, 0)) blackCanCastleQueenSide = false
            }
        }

        // Move target checks (if rooks are captured)
        m.capturedPiece?.let {
            if (it.type == PieceType.ROOK) {
                if (it.color == PieceColor.WHITE) {
                    if (m.to == Position(7, 7)) whiteCanCastleKingSide = false
                    if (m.to == Position(7, 0)) whiteCanCastleQueenSide = false
                } else {
                    if (m.to == Position(0, 7)) blackCanCastleKingSide = false
                    if (m.to == Position(0, 0)) blackCanCastleQueenSide = false
                }
            }
        }

        // Rule clocks
        if (piece.type == PieceType.PAWN || m.capturedPiece != null) {
            halfmoveClock = 0
        } else {
            halfmoveClock++
        }

        if (turn == PieceColor.BLACK) {
            fullmoveNumber++
        }

        // Log move histories
        moveHistory.add(m)
        algebraicHistory.add(textRepresentation)

        // Switch Turn
        turn = turn.opponent()
        return true
    }

    fun isCheckmate(): Boolean {
        return getAllLegalMoves(turn).isEmpty() && isInCheck(turn)
    }

    fun isStalemate(): Boolean {
        return getAllLegalMoves(turn).isEmpty() && !isInCheck(turn)
    }

    fun cloneGame(): ChessGame {
        val copy = ChessGame()
        copy.board = this.board.clone()
        copy.turn = this.turn
        copy.whiteCanCastleKingSide = this.whiteCanCastleKingSide
        copy.whiteCanCastleQueenSide = this.whiteCanCastleQueenSide
        copy.blackCanCastleKingSide = this.blackCanCastleKingSide
        copy.blackCanCastleQueenSide = this.blackCanCastleQueenSide
        copy.enPassantTarget = this.enPassantTarget
        copy.halfmoveClock = this.halfmoveClock
        copy.fullmoveNumber = this.fullmoveNumber
        copy.capturedByWhite.addAll(this.capturedByWhite)
        copy.capturedByBlack.addAll(this.capturedByBlack)
        copy.moveHistory.addAll(this.moveHistory)
        copy.algebraicHistory.addAll(this.algebraicHistory)
        return copy
    }
}
