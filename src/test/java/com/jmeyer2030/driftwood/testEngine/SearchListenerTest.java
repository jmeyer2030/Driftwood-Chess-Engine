package com.jmeyer2030.driftwood.testEngine;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.search.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchListenerTest {
    private Position matingPosition() {
        return new Position(new FEN("8/8/8/8/k1K5/8/4Q3/8 w - - 0 1"));
    }

    @Test
    void reportsCompletedIterationsWithStableSnapshots() {
        SearchContext context = new SearchContext();
        List<SearchIteration> iterations = new ArrayList<>();
        Search.MoveValue result = Search.iterativeDeepening(matingPosition(), 10000,
                context, new SharedTables(18), iterations::add);
        assertTrue(iterations.size() >= 3);
        for (int i = 0; i < iterations.size(); i++) {
            assertEquals(i + 1, iterations.get(i).depth());
        }
        SearchIteration last = iterations.getLast();
        assertEquals(result.value, last.score());
        assertEquals(result.bestMove, last.principalVariation().getFirst());
        List<Integer> saved = List.copyOf(last.principalVariation());
        context.pvTable.storePV(0, 0);
        context.pvTable.setPVLength(0);
        assertEquals(saved, last.principalVariation());
        assertThrows(UnsupportedOperationException.class, () -> last.principalVariation().clear());
    }

    @Test
    void directSearchAndTimeoutDoNotWriteToConsole() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try (PrintStream capture = new PrintStream(bytes)) {
            // Global capture is intentional here: verify the core never bypasses its listener.
            System.setOut(capture);
            System.setErr(capture);
            assertEquals(1_899_999, Search.iterativeDeepening(matingPosition(), 10000,
                    new SearchContext(), new SharedTables(18)).value);
            List<SearchIteration> iterations = new ArrayList<>();
            assertThrows(RuntimeException.class, () -> Search.iterativeDeepening(matingPosition(), 0,
                    new SearchContext(), new SharedTables(18), iterations::add));
            assertTrue(iterations.isEmpty());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        assertEquals("", bytes.toString());
    }
}
