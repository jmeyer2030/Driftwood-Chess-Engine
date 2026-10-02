package com.jmeyer2030.driftwood.userfeatures.perft;

import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.InvalidPositionException;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;

public class Perft {

    /**
     * Runs a perft test on a position and prints results for each legal move
     * @Param depth
     * @Param position
     */
    public static long perft(int depth, Position position) {
        if (depth < 1)
            return 1;

        int[] moveBuffer = new int[2048];
        int firstNonMove =  MoveGenerator.generateAllMoves(position, moveBuffer, 0);

        long total = 0;
        for (int i = 0; i < firstNonMove; i++) {
            position.makeMove(moveBuffer[i]);
            long thisMove = perftRecursion(depth - 1, position, moveBuffer, firstNonMove);
            position.unMakeMove(moveBuffer[i]);

            total += thisMove;
        }
        return total;
    }

    private static long perftRecursion(int depth, Position position, int[] moveBuffer, int firstNonMove) {
        try {
            position.validPosition();
        } catch (InvalidPositionException ipe) {
            throw new RuntimeException();
        }
        if (depth == 0)
            return 1;
        long result = 0;
        int nextFirstNonMove = MoveGenerator.generateAllMoves(position, moveBuffer, firstNonMove);
        if (depth == 1) {
            return nextFirstNonMove - firstNonMove;
        }

        for (int i = firstNonMove; i < nextFirstNonMove; i++) {
            int move = moveBuffer[i];

            position.makeMove(move);
            result += perftRecursion(depth - 1, position, moveBuffer, nextFirstNonMove);
            position.unMakeMove(move);
        }

        return result;
    }
}
