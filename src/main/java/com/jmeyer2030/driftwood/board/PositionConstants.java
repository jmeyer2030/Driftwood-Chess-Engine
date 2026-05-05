package com.jmeyer2030.driftwood.board;

/**
 * Static constants describing fixed board topology used by Position and move generation.
 * These are pure configuration, no mutable state.
 */
public final class PositionConstants {

    // Castle rights bits, indexed within Position.castleRights byte: 0b0000(WQ)(WK)(BQ)(BK)
    public static final int CASTLE_RIGHT_WK = 1 << 2;  // white king-side
    public static final int CASTLE_RIGHT_WQ = 1 << 3;  // white queen-side
    public static final int CASTLE_RIGHT_BK = 1;       // black king-side
    public static final int CASTLE_RIGHT_BQ = 1 << 1;  // black queen-side

    // Castle king destination squares
    public static final int CASTLE_DEST_WK = 6;   // g1, white king-side
    public static final int CASTLE_DEST_WQ = 2;   // c1, white queen-side
    public static final int CASTLE_DEST_BK = 62;  // g8, black king-side
    public static final int CASTLE_DEST_BQ = 58;  // c8, black queen-side

    // Castle pass-through squares (the square the king crosses; must not be attacked)
    public static final int CASTLE_PASSTHROUGH_WK = 5;   // f1
    public static final int CASTLE_PASSTHROUGH_WQ = 3;   // d1
    public static final int CASTLE_PASSTHROUGH_BK = 61;  // f8
    public static final int CASTLE_PASSTHROUGH_BQ = 59;  // d8

    // Queen-side only: extra square between rook and king that must be empty (b-file)
    public static final int CASTLE_QS_ROOK_CROSS_W = 1;   // b1
    public static final int CASTLE_QS_ROOK_CROSS_B = 57;  // b8

    // Castle rook start squares
    public static final int ROOK_START_WK = 7;   // h1
    public static final int ROOK_START_WQ = 0;   // a1
    public static final int ROOK_START_BK = 63;  // h8
    public static final int ROOK_START_BQ = 56;  // a8

    // Castle rook destination squares
    public static final int WHITE_KING_ROOK_DEST  = 5;   // f1
    public static final int WHITE_QUEEN_ROOK_DEST = 3;   // d1
    public static final int BLACK_KING_ROOK_DEST  = 61;  // f8
    public static final int BLACK_QUEEN_ROOK_DEST = 59;  // d8

    // Indexed as [activePlayer][castleSide] where castleSide 0=king, 1=queen
    public static final int[][] CASTLE_ROOK_STARTS = new int[][]{
            {ROOK_START_WK, ROOK_START_WQ},
            {ROOK_START_BK, ROOK_START_BQ}
    };
    public static final int[][] CASTLE_ROOK_DESTINATIONS = new int[][]{
            {WHITE_KING_ROOK_DEST, WHITE_QUEEN_ROOK_DEST},
            {BLACK_KING_ROOK_DEST, BLACK_QUEEN_ROOK_DEST}
    };

    // [activePlayer], mask to remove castle rights of the active player
    public static final byte[] CASTLE_RIGHTS_MASK = new byte[]{0b0000_0011, 0b0000_1100};

    // All castle rights set
    public static final byte ALL_CASTLE_RIGHTS = (byte) (CASTLE_RIGHT_WK | CASTLE_RIGHT_WQ | CASTLE_RIGHT_BK | CASTLE_RIGHT_BQ);

    // Revocation masks, AND with castleRights to remove a specific right
    public static final byte REVOKE_WQ = (byte) ~CASTLE_RIGHT_WQ;  // removes white queen-side
    public static final byte REVOKE_WK = (byte) ~CASTLE_RIGHT_WK;  // removes white king-side
    public static final byte REVOKE_BQ = (byte) ~CASTLE_RIGHT_BQ;  // removes black queen-side
    public static final byte REVOKE_BK = (byte) ~CASTLE_RIGHT_BK;  // removes black king-side

    private PositionConstants() {}
}

