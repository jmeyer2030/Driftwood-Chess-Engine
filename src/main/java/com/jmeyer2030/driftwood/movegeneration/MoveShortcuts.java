package com.jmeyer2030.driftwood.movegeneration;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Piece;
import com.jmeyer2030.driftwood.board.Position;

/**
* This class is used to generate moves based on a template so that we don't need to explicitely set all fields in MoveGenerator
*/
public class MoveShortcuts {
/*
    Move templates, built from MoveEncoding flag masks and Piece constants
*/
    public static final int PAWN_EN_PASSANT_TEMPLATE =            MoveEncoding.IS_EP_MASK;
    public static final int PAWN_PROMOTION_NO_CAPTURE_TEMPLATE = MoveEncoding.IS_PROMOTION_MASK;
    public static final int PAWN_PROMOTION_CAPTURE_TEMPLATE =    MoveEncoding.IS_PROMOTION_MASK | MoveEncoding.IS_CAPTURE_MASK;
    public static final int PAWN_DOUBLE_PUSH_TEMPLATE =          MoveEncoding.IS_DOUBLE_PUSH_MASK | MoveEncoding.IS_QUIET_MASK;
    public static final int PAWN_SINGLE_PUSH_TEMPLATE =          MoveEncoding.IS_QUIET_MASK;
    public static final int PAWN_CAPTURE_TEMPLATE =              MoveEncoding.IS_CAPTURE_MASK;
    public static final int KNIGHT_NO_CAPTURE_TEMPLATE =         MoveEncoding.IS_REVERSIBLE_MASK | MoveEncoding.IS_QUIET_MASK | (Piece.KNIGHT << 12);
    public static final int KNIGHT_CAPTURE_TEMPLATE =            MoveEncoding.IS_CAPTURE_MASK | (Piece.KNIGHT << 12);
    public static final int BISHOP_NO_CAPTURE_TEMPLATE =         MoveEncoding.IS_REVERSIBLE_MASK | MoveEncoding.IS_QUIET_MASK | (Piece.BISHOP << 12);
    public static final int BISHOP_CAPTURE_TEMPLATE =            MoveEncoding.IS_CAPTURE_MASK | (Piece.BISHOP << 12);
    public static final int ROOK_NO_CAPTURE_TEMPLATE =           MoveEncoding.IS_REVERSIBLE_MASK | MoveEncoding.IS_QUIET_MASK | (Piece.ROOK << 12);
    public static final int ROOK_CAPTURE_TEMPLATE =              MoveEncoding.IS_CAPTURE_MASK | (Piece.ROOK << 12);
    public static final int QUEEN_NO_CAPTURE_TEMPLATE =          MoveEncoding.IS_REVERSIBLE_MASK | MoveEncoding.IS_QUIET_MASK | (Piece.QUEEN << 12);
    public static final int QUEEN_CAPTURE_TEMPLATE =             MoveEncoding.IS_CAPTURE_MASK | (Piece.QUEEN << 12);
    public static final int KING_NO_CAPTURE_TEMPLATE =           MoveEncoding.IS_REVERSIBLE_MASK | MoveEncoding.IS_QUIET_MASK | (Piece.KING << 12);
    public static final int KING_CAPTURE_TEMPLATE =              MoveEncoding.IS_CAPTURE_MASK | (Piece.KING << 12);
    public static final int KING_KING_SIDE_CASTLE_TEMPLATE =     MoveEncoding.IS_REVERSIBLE_MASK | MoveEncoding.IS_CASTLE_MASK | MoveEncoding.IS_QUIET_MASK | (Piece.KING << 12);
    public static final int KING_QUEEN_SIDE_CASTLE_TEMPLATE =    MoveEncoding.CASTLE_SIDE_MASK | MoveEncoding.IS_REVERSIBLE_MASK | MoveEncoding.IS_CASTLE_MASK | MoveEncoding.IS_QUIET_MASK | (Piece.KING << 12);

    public static int generatePawnPromotionNoCapture(int start, int destination, int promotionPiece, Position position) {
        int move = PAWN_PROMOTION_NO_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setPromotionType(move, promotionPiece);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generatePawnPromotionCapture(int start, int destination, int promotionPiece, Position position) {
        int move = PAWN_PROMOTION_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setPromotionType(move, promotionPiece);
        move = MoveEncoding.setCapturedPiece(move, position.getPieceType(destination));
        move = MoveEncoding.setCaptureColor(move, 1 - position.activePlayer);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generatePawnEnPassant(int start, int destination, Position position) {
        int move = PAWN_EN_PASSANT_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generatePawnDoublePush(int start, int destination, Position position) {
        int move = PAWN_DOUBLE_PUSH_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }
    public static int generatePawnSinglePush(int start, int destination, Position position) {
        int move = PAWN_SINGLE_PUSH_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }
    public static int generatePawnCapture(int start, int destination, Position position) {
        int move = PAWN_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setCapturedPiece(move, position.getPieceType(destination));
        move = MoveEncoding.setCaptureColor(move, 1 - position.activePlayer);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }
    public static int generateKnightNoCapture(int start, int destination, Position position) {
        int move = KNIGHT_NO_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateKnightCapture(int start, int destination, Position position) {
        int move = KNIGHT_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setCapturedPiece(move, position.getPieceType(destination));
        move = MoveEncoding.setCaptureColor(move, 1 - position.activePlayer);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateBishopNoCapture(int start, int destination, Position position) {
        int move = BISHOP_NO_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateBishopCapture(int start, int destination, Position position) {
        int move = BISHOP_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setCapturedPiece(move, position.getPieceType(destination));
        move = MoveEncoding.setCaptureColor(move, 1 - position.activePlayer);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateRookNoCapture(int start, int destination, Position position) {
        int move = ROOK_NO_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateRookCapture(int start, int destination, Position position) {
        int move = ROOK_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setCapturedPiece(move, position.getPieceType(destination));
        move = MoveEncoding.setCaptureColor(move, 1 - position.activePlayer);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateQueenNoCapture(int start, int destination, Position position) {
        int move = QUEEN_NO_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateQueenCapture(int start, int destination, Position position) {
        int move = QUEEN_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setCapturedPiece(move, position.getPieceType(destination));
        move = MoveEncoding.setCaptureColor(move, 1 - position.activePlayer);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateKingNoCapture(int start, int destination, Position position) {
        int move = KING_NO_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateKingCapture(int start, int destination, Position position) {
        int move = KING_CAPTURE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setCapturedPiece(move, position.getPieceType(destination));
        move = MoveEncoding.setCaptureColor(move, 1 - position.activePlayer);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateKingKingSideCastle(int start, int destination, Position position) {
        int move = KING_KING_SIDE_CASTLE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }

    public static int generateKingQueenSideCastle(int start, int destination, Position position) {
        int move = KING_QUEEN_SIDE_CASTLE_TEMPLATE;
        move = MoveEncoding.setStart(move, start);
        move = MoveEncoding.setDestination(move, destination);
        move = MoveEncoding.setWasInCheck(move, position.inCheck ? 1 : 0);
        return move;
    }
}
