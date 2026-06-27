package com.example.chess

import kotlin.math.abs

enum class PieceColor {
    WHITE, BLACK;
    fun opponent() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val notation: String, val value: Int) {
    PAWN("", 100),
    KNIGHT("N", 320),
    BISHOP("B", 330),
    ROOK("R", 500),
    QUEEN("Q", 900),
    KING("K", 20000)
}

data class Piece(val type: PieceType, val color: PieceColor) {
    fun getSymbol(useTextBadges: Boolean): String {
        return if (useTextBadges) {
            val letter = when (type) {
                PieceType.PAWN -> "P"
                PieceType.KNIGHT -> "N"
                PieceType.BISHOP -> "B"
                PieceType.ROOK -> "R"
                PieceType.QUEEN -> "Q"
                PieceType.KING -> "K"
            }
            if (color == PieceColor.WHITE) letter.uppercase() else letter.lowercase()
        } else {
            when (color) {
                PieceColor.WHITE -> when (type) {
                    PieceType.PAWN -> "♙"
                    PieceType.KNIGHT -> "♘"
                    PieceType.BISHOP -> "♗"
                    PieceType.ROOK -> "♖"
                    PieceType.QUEEN -> "♕"
                    PieceType.KING -> "♔"
                }
                PieceColor.BLACK -> when (type) {
                    PieceType.PAWN -> "♟"
                    PieceType.KNIGHT -> "♞"
                    PieceType.BISHOP -> "♝"
                    PieceType.ROOK -> "♜"
                    PieceType.QUEEN -> "♛"
                    PieceType.KING -> "♚"
                }
            }
        }
    }
}

data class Position(val row: Int, val col: Int) {
    fun isValid() = row in 0..7 && col in 0..7
    
    fun toAlgebraic(): String {
        val file = ('a'.code + col).toChar()
        val rank = 8 - row
        return "$file$rank"
    }

    companion object {
        fun fromAlgebraic(alg: String): Position? {
            if (alg.length < 2) return null
            val file = alg[0] - 'a'
            val rank = 8 - (alg[1] - '0')
            if (file in 0..7 && rank in 0..7) {
                return Position(rank, file)
            }
            return null
        }
    }
}

data class Move(
    val from: Position,
    val to: Position,
    val pieceMoved: Piece,
    val capturedPiece: Piece? = null,
    val isCastlingKingSide: Boolean = false,
    val isCastlingQueenSide: Boolean = false,
    val isEnPassant: Boolean = false,
    val promotionResult: PieceType? = null
) {
    fun toAlgebraic(isCapture: Boolean = capturedPiece != null || isEnPassant, isCheck: Boolean = false, isMate: Boolean = false): String {
        if (isCastlingKingSide) return "O-O"
        if (isCastlingQueenSide) return "O-O-O"
        
        val pNotation = if (pieceMoved.type == PieceType.PAWN) {
            if (isCapture) ('a'.code + from.col).toChar().toString() else ""
        } else {
            pieceMoved.type.notation
        }
        val captureSign = if (isCapture) "x" else ""
        val dst = to.toAlgebraic()
        val promo = if (promotionResult != null) "=${promotionResult.notation}" else ""
        val suffix = if (isMate) "#" else if (isCheck) "+" else ""
        return "$pNotation$captureSign$dst$promo$suffix"
    }
}

class ChessBoard {
    private val squares: Array<Array<Piece?>> = Array(8) { Array(8) { null } }

    init {
        resetToStartingPosition()
    }

    fun getPiece(pos: Position): Piece? {
        if (!pos.isValid()) return null
        return squares[pos.row][pos.col]
    }

    fun setPiece(pos: Position, piece: Piece?) {
        if (pos.isValid()) {
            squares[pos.row][pos.col] = piece
        }
    }

    fun clear() {
        for (r in 0..7) {
            for (c in 0..7) {
                squares[r][c] = null
            }
        }
    }

    fun resetToStartingPosition() {
        clear()
        
        // Rooks
        setPiece(Position(0, 0), Piece(PieceType.ROOK, PieceColor.BLACK))
        setPiece(Position(0, 7), Piece(PieceType.ROOK, PieceColor.BLACK))
        setPiece(Position(7, 0), Piece(PieceType.ROOK, PieceColor.WHITE))
        setPiece(Position(7, 7), Piece(PieceType.ROOK, PieceColor.WHITE))

        // Knights
        setPiece(Position(0, 1), Piece(PieceType.KNIGHT, PieceColor.BLACK))
        setPiece(Position(0, 6), Piece(PieceType.KNIGHT, PieceColor.BLACK))
        setPiece(Position(7, 1), Piece(PieceType.KNIGHT, PieceColor.WHITE))
        setPiece(Position(7, 6), Piece(PieceType.KNIGHT, PieceColor.WHITE))

        // Bishops
        setPiece(Position(0, 2), Piece(PieceType.BISHOP, PieceColor.BLACK))
        setPiece(Position(0, 5), Piece(PieceType.BISHOP, PieceColor.BLACK))
        setPiece(Position(7, 2), Piece(PieceType.BISHOP, PieceColor.WHITE))
        setPiece(Position(7, 5), Piece(PieceType.BISHOP, PieceColor.WHITE))

        // Queens
        setPiece(Position(0, 3), Piece(PieceType.QUEEN, PieceColor.BLACK))
        setPiece(Position(7, 3), Piece(PieceType.QUEEN, PieceColor.WHITE))

        // Kings
        setPiece(Position(0, 4), Piece(PieceType.KING, PieceColor.BLACK))
        setPiece(Position(7, 4), Piece(PieceType.KING, PieceColor.WHITE))

        // Pawns
        for (col in 0..7) {
            setPiece(Position(1, col), Piece(PieceType.PAWN, PieceColor.BLACK))
            setPiece(Position(6, col), Piece(PieceType.PAWN, PieceColor.WHITE))
        }
    }

    fun clone(): ChessBoard {
        val copy = ChessBoard()
        copy.clear()
        for (r in 0..7) {
            for (c in 0..7) {
                copy.squares[r][c] = this.squares[r][c]
            }
        }
        return copy
    }

    fun loadFromFen(fen: String) {
        clear()
        val parts = fen.split(" ")
        if (parts.isEmpty()) return
        val rows = parts[0].split("/")
        for (r in 0 until minOf(8, rows.size)) {
            var col = 0
            val rowStr = rows[r]
            for (i in 0 until rowStr.length) {
                if (col >= 8) break
                val char = rowStr[i]
                if (char.isDigit()) {
                    col += char.toString().toInt()
                } else {
                    val color = if (char.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = when (char.lowercaseChar()) {
                        'p' -> PieceType.PAWN
                        'n' -> PieceType.KNIGHT
                        'b' -> PieceType.BISHOP
                        'r' -> PieceType.ROOK
                        'q' -> PieceType.QUEEN
                        'k' -> PieceType.KING
                        else -> PieceType.PAWN
                    }
                    setPiece(Position(r, col), Piece(type, color))
                    col++
                }
            }
        }
    }

    fun toFenBoardPart(): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var emptyCount = 0
            for (c in 0..7) {
                val piece = squares[r][c]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    val char = when (piece.type) {
                        PieceType.PAWN -> 'p'
                        PieceType.KNIGHT -> 'n'
                        PieceType.BISHOP -> 'b'
                        PieceType.ROOK -> 'r'
                        PieceType.QUEEN -> 'q'
                        PieceType.KING -> 'k'
                    }
                    sb.append(if (piece.color == PieceColor.WHITE) char.uppercaseChar() else char)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (r < 7) {
                sb.append('/')
            }
        }
        return sb.toString()
    }
}
