package com.jmeyer2030.driftwood.userfeatures.perft;

import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;

public class Perft {

    private static final int DEFAULT_TABLE_BITS = 22; // ~80 MiB of primitive table storage.

    /**
     * Counts legal move sequences using a bounded transposition table local to this run.
     */
    public static long perft(int depth, Position position) {
        return perft(depth, position, depth > 1 ? new PerftTable(DEFAULT_TABLE_BITS) : null);
    }

    /**
     * Counts legal move sequences using the supplied table. Pass null to run uncached
     * for regression testing. A supplied table may be reused across positions and depths,
     * but must not be shared by concurrent runs.
     */
    public static long perft(int depth, Position position, PerftTable table) {
        if (depth < 1)
            return 1;

        int[] moveBuffer = new int[2048];
        System.out.println("Perft begin: Depth: " + depth);
        return perftRecursion(depth, position, moveBuffer, 0, table);
    }

    private static long perftRecursion(int depth, Position position, int[] moveBuffer, int firstNonMove,
                                      PerftTable table) {
        if (depth == 0)
            return 1;

        long zobristHash = position.zobristHash;
        // Depth-one nodes only count moves. Caching them floods the table with cheap
        // results and evicts subtrees that would save many move generations.
        if (table != null && depth > 1) {
            long cached = table.probe(zobristHash, depth);
            if (cached != PerftTable.MISS) {
                return cached;
            }
        }

        long result = 0;
        int nextFirstNonMove = MoveGenerator.generateAllMoves(position, moveBuffer, firstNonMove);
        if (depth == 1) {
            result = nextFirstNonMove - firstNonMove;
        } else {
            for (int i = firstNonMove; i < nextFirstNonMove; i++) {
                int move = moveBuffer[i];

                position.makeMove(move);
                result += perftRecursion(depth - 1, position, moveBuffer, nextFirstNonMove, table);
                position.unMakeMove(move);
            }
        }

        if (table != null && depth > 1) {
            table.addElement(zobristHash, depth, result);
        }
        return result;
    }
}
