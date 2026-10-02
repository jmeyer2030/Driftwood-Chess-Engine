package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.config.GlobalConstants;

import java.util.ArrayList;
import java.util.List;

import static com.jmeyer2030.driftwood.search.Search.MAX_SEARCH_DEPTH;

/**
* Represents a triangular PV table:
* <br>
* For a search of depth N, for a leaf node we would store 0 PV moves (since there are no following moves)
* at the root node, we would store a PV of length N, since there are N moves made until a leaf node.
* Thus, to represent this triangular array in 1-D, it's size should be 256 + 255 + 254 + ... + 1
* This is known as the triangular number of N, computed as N(N+1)/2
* if search depth is n, triangularPV[n] stores nothing
*/
public class TriangularPVTable {
    private final int[][] triangularPV;
    private final int[] pvLength;

    /**
     * Creates an empty PV table sized for the engine's maximum ply.
     */
    public TriangularPVTable() {
        this.triangularPV = new int[GlobalConstants.MAX_PLY][GlobalConstants.MAX_PLY];

        pvLength = new int[MAX_SEARCH_DEPTH];
    }

    /**
     * Stores the given move as the first PV move at the supplied search depth.
     *
     * @param move the encoded move to store
     * @param depth the search depth row to update
     */
    public void storePV(int move, int depth) {
       triangularPV[depth][0] = move;
    }

    /**
     * Marks the PV at the supplied ply as ending at that ply.
     *
     * @param ply the ply whose PV length should be initialized
     */
    public void setPVLength(int ply) {
        this.pvLength[ply] = ply;
    }

    /**
     * Writes a PV move at the supplied ply and copies the child PV continuation.
     *
     * @param move the encoded move to write
     * @param ply the current search ply
     */
    public void writePVMove(int move, int ply) {
        triangularPV[ply][ply] = move;

        for (int nextPly = ply + 1; nextPly < pvLength[ply + 1]; nextPly++) {
            triangularPV[ply][nextPly] = triangularPV[ply + 1][nextPly];
        }

        pvLength[ply] = pvLength[ply + 1];
    }

    /**
     * Returns the root PV move.
     *
     * @return the encoded best move from the root PV
     */
    public int getPVMove() {
        return triangularPV[0][0];
    }

    /**
     * Returns the second move in the root PV line.
     *
     * @return the encoded best response to the root PV move
     */
    public int getBestResponse() {
        return triangularPV[0][1];
    }

    /** Returns a detached, immutable root PV for a completed iteration. */
    public List<Integer> getPVSnapshot() {
        List<Integer> moves = new ArrayList<>(pvLength[0]);
        for (int i = 0; i < pvLength[0]; i++) {
            moves.add(triangularPV[0][i]);
        }
        return List.copyOf(moves);
    }

    /**
     * Formats the root PV line in long algebraic notation.
     *
     * @return the root PV line, with moves separated by spaces
     */
    public String getPVLine() {
        StringBuilder bldr = new StringBuilder();
        for (int i = 0; i < pvLength[0]; i++) {
            bldr.append(MoveEncoding.getLAN(triangularPV[0][i]));
            if (i != pvLength[0] - 1) {
                bldr.append(" ");
            }
        }

        return bldr.toString();
    }
}
