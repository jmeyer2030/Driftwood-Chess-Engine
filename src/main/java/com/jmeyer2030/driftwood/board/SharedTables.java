package com.jmeyer2030.driftwood.board;

import com.jmeyer2030.driftwood.search.TranspositionTable;

/**
 * Long-lived tables shared across the lifetime of a game. Created once per {@code ucinewgame}.
 * <ul>
 *   <li>{@link TranspositionTable}, may be {@code null} when TT is disabled (e.g. in tests)</li>
 *   <li>{@link ThreeFoldTable}, mutable; replaced on each {@code position} command,
 *       and pushed/popped during search</li>
 * </ul>
 */
public class SharedTables {
    public final TranspositionTable tt;
    public ThreeFoldTable threeFoldTable;

    public SharedTables(int numBits) {
        if (numBits == 0) {
            this.tt = null;
        } else {
            this.tt = new TranspositionTable(numBits);
        }
        this.threeFoldTable = new ThreeFoldTable();
    }

    /**
     * Makes a move on the given position and records it in the three-fold table.
     *
     * @param move     int-encoded move
     * @param position the position to apply the move to
     */
    public void applyMove(int move, Position position) {
        position.makeMove(move);
        threeFoldTable.addPosition(position.zobristHash, move);
    }
}

