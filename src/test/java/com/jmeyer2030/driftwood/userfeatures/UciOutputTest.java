package com.jmeyer2030.driftwood.userfeatures;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.search.SearchContext;
import com.jmeyer2030.driftwood.search.SearchIteration;
import com.jmeyer2030.driftwood.userfeatures.commands.uci.Go;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UciOutputTest {
    @Test
    void formatsScoresAndAbsentMoves() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        UciOutput output = new UciOutput(new PrintStream(bytes));
        output.onIterationCompleted(new SearchIteration(4, 125, List.of()));
        output.onIterationCompleted(new SearchIteration(5, 1_899_997, List.of()));
        output.onIterationCompleted(new SearchIteration(6, -1_899_996, List.of()));
        output.bestMove(0, 0);
        assertEquals(List.of("info depth 4 score cp 125", "info depth 5 score mate 2",
                "info depth 6 score mate -2", "bestmove 0000"), bytes.toString().lines().toList());
    }

    @Test
    void goAlwaysReportsIterationsBeforeBestMoveToInjectedOutput() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ChessEngine engine = new ChessEngine(new UciOutput(new PrintStream(bytes)));
        engine.position = new Position(new FEN("8/8/8/8/k1K5/8/4Q3/8 w - - 0 1"));
        engine.searchContext = new SearchContext();
        engine.sharedTables = new SharedTables(18);
        new Go(engine).execute(new String[]{"wtime", "300000"});
        List<String> lines = bytes.toString().lines().toList();
        assertTrue(lines.size() >= 4, bytes.toString());
        assertTrue(lines.getLast().startsWith("bestmove "), bytes.toString());
        for (int i = 0; i < lines.size() - 1; i++) {
            assertTrue(lines.get(i).startsWith("info depth " + (i + 1) + " score "), bytes.toString());
            assertTrue(lines.get(i).contains(" pv "), bytes.toString());
        }
    }
}
