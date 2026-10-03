package com.jmeyer2030.driftwood.testMoveGeneration;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;
import com.jmeyer2030.driftwood.userfeatures.perft.Perft;
import com.jmeyer2030.driftwood.userfeatures.perft.PerftTable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PerftCachingTest {

    @Test
    void reusesTranspositionsAndRestoresPosition() {
        Position position = Position.getPerftPosition(null);
        MoveGenerator.computePins(position);
        Position before = new Position(position);
        CountingTable table = new CountingTable();

        assertEquals(4_865_609L, Perft.perft(5, position, table));
        assertTrue(table.hits > 0, "The first traversal should reuse transposed subtrees");
        assertEquals(4_865_609L, table.probe(before.zobristHash, 5));
        assertTrue(before.equals(position), "Perft must restore the root board and move state");
        assertEquals(before.zobristHash, position.zobristHash);
        assertEquals(before.checkers, position.checkers);
        assertEquals(before.inCheck, position.inCheck);
        assertEquals(before.pinnedBB, position.pinnedBB);
        assertArrayEquals(before.kingLocs, position.kingLocs);

        table.probes = 0;
        table.hits = 0;
        assertEquals(4_865_609L, Perft.perft(5, position, table));
        assertEquals(1, table.probes, "A cached root should return without visiting children");
        assertEquals(1, table.hits);
    }

    @Test
    void cachedAndUncachedMatchKnownCountsAcrossPositionsAndDepths() throws IOException {
        PerftTable table = new PerftTable(12);
        for (String line : Files.readAllLines(Path.of("src/test/resources/perftSuite.txt"))) {
            String fen = PerftSuiteTest.getFEN(line);
            long[] expected = PerftSuiteTest.getValues(line);
            Position position = Position.getPerftPosition(new FEN(fen));

            assertEquals(1L, Perft.perft(0, position, table));
            for (int depth = 1; depth <= Math.min(3, expected.length); depth++) {
                String context = fen + " at depth " + depth;
                assertEquals(expected[depth - 1], Perft.perft(depth, position, null), context);
                assertEquals(expected[depth - 1], Perft.perft(depth, position, table), context);
            }
        }
    }

    @Test
    void indexCollisionsAndDepthChangesDoNotReuseWrongCounts() {
        // Two slots force frequent index collisions between different full hashes.
        PerftTable table = new PerftTable(1);
        Position position = Position.getPerftPosition(null);

        assertEquals(197_281L, Perft.perft(4, position, table));
        assertEquals(400L, Perft.perft(2, position, table));
        assertEquals(8_902L, Perft.perft(3, position, table));
        assertEquals(20L, Perft.perft(1, position, table));
    }

    @Test
    void deeperSubtreesSurviveShallowerIndexCollisions() {
        PerftTable table = new PerftTable(1);
        long firstHash = 0L;
        long collidingHash = 2L;

        assertEquals(PerftTable.MISS, table.probe(firstHash, 4));
        table.addElement(firstHash, 4, 197_281L);
        table.addElement(collidingHash, 2, 400L);
        assertEquals(197_281L, table.probe(firstHash, 4));
        assertEquals(PerftTable.MISS, table.probe(collidingHash, 2));
        assertEquals(PerftTable.MISS, table.probe(firstHash, 3));

        // Equally deep results may replace each other; the full hash still must match.
        table.addElement(collidingHash, 4, 0L);
        assertEquals(PerftTable.MISS, table.probe(firstHash, 4));
        assertEquals(0L, table.probe(collidingHash, 4));
    }

    @Test
    void cachesZeroForMateAndStalemateButDepthZeroIsOne() {
        PerftTable table = new PerftTable(4);
        String[] terminalPositions = {
                "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1",
                "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"
        };

        for (String fen : terminalPositions) {
            Position position = Position.getPerftPosition(new FEN(fen));
            assertEquals(0L, Perft.perft(1, position, table));
            for (int depth = 2; depth <= 3; depth++) {
                assertEquals(0L, Perft.perft(depth, position, table));
                assertEquals(0L, table.getElement(position.zobristHash, depth).perftResult());
                assertEquals(0L, Perft.perft(depth, position, table));
            }
            assertEquals(1L, Perft.perft(0, position, table));
        }
    }

    private static class CountingTable extends PerftTable {
        int probes;
        int hits;

        CountingTable() {
            super(16);
        }

        @Override
        public long probe(long zobristHash, int depth) {
            probes++;
            long count = super.probe(zobristHash, depth);
            if (count != MISS) {
                hits++;
            }
            return count;
        }
    }
}
