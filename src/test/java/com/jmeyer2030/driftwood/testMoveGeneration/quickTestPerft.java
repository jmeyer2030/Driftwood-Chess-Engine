package com.jmeyer2030.driftwood.testMoveGeneration;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.Position;
import org.junit.jupiter.api.Test;
import com.jmeyer2030.driftwood.userfeatures.perft.Perft;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
    Test perft for different positions
    Tests sourced from: http://www.rocechess.ch/perft.html
*/
public class quickTestPerft {

    public static long[] startPosPerftResults = {1L, 20L, 400L, 8_902L, 197_281L, 4_865_609L, 119_060_324L, 3_195_901_860L, 84_998_978_956L};

    public static String testPosFEN1 = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    public static long[] testPos1PerftResults = {1L, 48L, 2_039L, 97862L, 4_085_603L, 193_690_690L, 8_031_647_685L};

    public static String testPosFEN2 = "n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0 1";
    public static long[] testPos2PerftResults = {1L, 24L, 496L, 9_483L, 182_838L, 3_605_103L, 71_179_139L};



    @Test void testStartPosPerft() {
        int maxDepth = 7;
        for (int depth = 0; depth <= maxDepth; depth++) {
            long result = perftStartingPosition(depth);
            assertEquals(startPosPerftResults[depth], result);
        }
    }

    @Test void testPos1Perft() {
        int maxDepth = 6;
        for (int depth = 0; depth <= maxDepth; depth++) {
            long result = perftFromFen(testPosFEN1, depth);
            assertEquals(testPos1PerftResults[depth], result);
        }
    }

    @Test void testPos2Perft() {
        int maxDepth = 6;
        for (int depth = 0; depth <= maxDepth; depth++) {
            long result = perftFromFen(testPosFEN2, depth);
            assertEquals(testPos2PerftResults[depth], result);
        }
    }


    public static long perftStartingPosition(int depth) {
        Position position = Position.getPerftPosition(null);
        long start = System.currentTimeMillis();
        long result = Perft.perft(depth, position);
        long end = System.currentTimeMillis();

        System.out.println("Total time: " + (end - start));
        return result;
    }

    public static long perftFromFen(String fen, int depth) {
        FEN fenP = new FEN(fen);
        Position position = Position.getPerftPosition(fenP);

        long start = System.currentTimeMillis();
        long result = Perft.perft(depth, position);
        long end = System.currentTimeMillis();

        System.out.println("Total time: " + (end - start));
        return result;
    }
}
