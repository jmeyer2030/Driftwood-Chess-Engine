package com.jmeyer2030.driftwood.movegeneration;

import com.jmeyer2030.driftwood.board.Color;
import com.jmeyer2030.driftwood.board.Piece;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.PositionConstants;

public class KingLogic {

    private static final long[] moveBoards = new long[64];

    static {
        generateMoveBoards();
    }

    public static long getCaptures(int square, Position position) {
        long enemyPieces = position.pieceColors[1 - position.activePlayer];
        return moveBoards[square] & enemyPieces;
    }

    public static long getQuietMoves(int square, Position position) {
        return moveBoards[square] & ~position.occupancy;
    }

    public static long getKingAttacks(int square) {
        return moveBoards[square];
    }

    public static long generateCastles(int square, Position position) {
        return position.activePlayer == Color.WHITE ? generateWhiteCastles(position) : generateBlackCastles(position);
    }

    //Private Helper Methods
    private static long generateWhiteCastles(Position position) {
        long result = 0L;
        // queen side
        if (((position.castleRights & PositionConstants.CASTLE_RIGHT_WQ) != 0) && // Has castle rights for that side
                (((position.pieces[Piece.ROOK] & position.pieceColors[Color.WHITE]) & (1L << PositionConstants.ROOK_START_WQ)) != 0L) && // Rook is in place (TODO: is this not needed because of the above check?)
                ((position.occupancy & (1L << PositionConstants.CASTLE_QS_ROOK_CROSS_W)) == 0L) && // White knight starting square is empty
                ((position.occupancy & (1L << PositionConstants.CASTLE_DEST_WQ)) == 0L) && // White bishop starting square is empty
                ((position.occupancy & (1L << PositionConstants.CASTLE_PASSTHROUGH_WQ)) == 0L)) // White queen starting square is empty
            result |= (1L << PositionConstants.CASTLE_DEST_WQ);
        // king side
        if (((position.castleRights & PositionConstants.CASTLE_RIGHT_WK) != 0) &&
                (((position.pieces[Piece.ROOK] & position.pieceColors[Color.WHITE]) & (1L << PositionConstants.ROOK_START_WK)) != 0L) &&
                ((position.occupancy & (1L << PositionConstants.CASTLE_PASSTHROUGH_WK)) == 0L) &&
                ((position.occupancy & (1L << PositionConstants.CASTLE_DEST_WK)) == 0L))
            result |= (1L << PositionConstants.CASTLE_DEST_WK);
        return result;
    }

    private static long generateBlackCastles(Position position) {
        long result = 0L;
        // queen side
        if (((position.castleRights & PositionConstants.CASTLE_RIGHT_BQ) != 0) &&
                (((position.pieces[Piece.ROOK] & position.pieceColors[Color.BLACK]) & (1L << PositionConstants.ROOK_START_BQ)) != 0L) &&
                ((position.occupancy & (1L << PositionConstants.CASTLE_QS_ROOK_CROSS_B)) == 0L) &&
                ((position.occupancy & (1L << PositionConstants.CASTLE_DEST_BQ)) == 0L) &&
                ((position.occupancy & (1L << PositionConstants.CASTLE_PASSTHROUGH_BQ)) == 0L))
            result |= (1L << PositionConstants.CASTLE_DEST_BQ);
        // king side
        if (((position.castleRights & PositionConstants.CASTLE_RIGHT_BK) != 0) &&
                (((position.pieces[Piece.ROOK] & position.pieceColors[Color.BLACK]) & (1L << PositionConstants.ROOK_START_BK)) != 0L) &&
                ((position.occupancy & (1L << PositionConstants.CASTLE_PASSTHROUGH_BK)) == 0L) &&
                ((position.occupancy & (1L << PositionConstants.CASTLE_DEST_BK)) == 0L))
            result |= (1L << PositionConstants.CASTLE_DEST_BK);
        return result;
    }

    /**
    * Generates bitboards for king moves of all squares
    */
    private static void generateMoveBoards() {
        for (int i = 0; i < 64; i++) {
            moveBoards[i] = generateMoveBoard(i);
        }
    }


    /**
     * Returns a bitboard of squares that the king attacks at a given position
     *
     * @Param square an integer between 0 and 63
     * @Return a bitboard of squares the king attacks
     */
    private static long generateMoveBoard(int square) {
        assert square < 64;
        assert square >= 0;
        int rankLoc = square / 8;
        int fileLoc = square % 8;

        long moveBoard = 0;

        int testRank = rankLoc + 1;
        int testFile = fileLoc;
        if (testRank < 8)
            moveBoard |= 1L << (8 * testRank + testFile);

        testRank = rankLoc + 1;
        testFile = fileLoc + 1;
        if (testRank < 8 && testFile < 8)
            moveBoard |= 1L << (8 * testRank + testFile);

        testRank = rankLoc;
        testFile = fileLoc + 1;
        if (testFile < 8)
            moveBoard |= 1L << (8 * testRank + testFile);

        testRank = rankLoc - 1;
        testFile = fileLoc + 1;
        if (testRank >= 0 && testFile < 8)
            moveBoard |= 1L << (8 * testRank + testFile);

        testRank = rankLoc - 1;
        testFile = fileLoc;
        if (testRank >= 0)
            moveBoard |= 1L << (8 * testRank + testFile);

        testRank = rankLoc - 1;
        testFile = fileLoc - 1;
        if (testRank >= 0 && testFile >= 0)
            moveBoard |= 1L << (8 * testRank + testFile);

        testRank = rankLoc;
        testFile = fileLoc - 1;
        if (testFile >= 0)
            moveBoard |= 1L << (8 * testRank + testFile);

        testRank = rankLoc + 1;
        testFile = fileLoc - 1;
        if (testRank < 8 && testFile >= 0)
            moveBoard |= 1L << (8 * testRank + testFile);


        return moveBoard;
    }

}