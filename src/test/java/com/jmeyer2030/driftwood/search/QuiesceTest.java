package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuiesceTest {

    private SearchContext searchContext() {
        return new SearchContext();
    }

    private SharedTables sharedTables() {
        return new SharedTables(18);
    }

    // ======================== In-check behaviour ========================

    @Test
    @DisplayName("Quiesce returns NEG_INFINITY when position is checkmate")
    void returnsMatedScoreOnCheckmate() {
        // Arrange, scholar's mate final position: white is checkmated
        FEN fen = new FEN("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1");
        Position position = new Position(fen);
        SearchContext sc = searchContext();
        SharedTables st = sharedTables();

        // Act
        int score = Quiesce.quiescenceSearch(Search.NEG_INFINITY, Search.POS_INFINITY, position, sc, st, 0);

        // Assert
        assertEquals(-1_900_000, score);
    }

    @Test
    @DisplayName("Quiesce searches quiet evasions when in check with no captures available")
    void searchesQuietEvasionsWhenInCheck() {
        // Arrange, White king e1, black knight d3 checking, black king f3.
        // Only quiet evasions exist (Kd1, Kd2, Kf1). No captures.
        FEN fen = new FEN("8/8/8/8/8/3n1k2/8/4K3 w - - 0 1");
        Position position = new Position(fen);
        assertTrue(position.inCheck, "Precondition: white must be in check");
        SearchContext sc = searchContext();
        SharedTables st = sharedTables();

        int standPat = position.evaluator.computeOutput(position.activePlayer);

        // Act
        int score = Quiesce.quiescenceSearch(Search.NEG_INFINITY, Search.POS_INFINITY, position, sc, st, 0);

        // Assert, score should differ from standPat because evasions were actually searched
        assertNotEquals(standPat, score,
                "qsearch should search quiet evasions when in check, not just return standPat");
    }

    @Test
    @DisplayName("Quiesce does not use stand-pat beta cutoff when in check")
    void noStandPatBetaCutoffWhenInCheck() {
        // Arrange, White king e4, black rook a4 giving check, black king h8.
        // White has quiet evasions (Kd3, Kd5, Ke3, Ke5, Kf3, Kf5) and can capture (Kxa4? probably bad).
        // Use a tight beta so that if standPat were used for cutoff it would trigger.
        FEN fen = new FEN("7k/8/8/8/r3K3/8/8/8 w - - 0 1");
        Position position = new Position(fen);
        assertTrue(position.inCheck, "Precondition: white must be in check");
        SearchContext sc = searchContext();
        SharedTables st = sharedTables();

        int standPat = position.evaluator.computeOutput(position.activePlayer);

        // Act, use beta well below standPat so a stand-pat cutoff would have triggered
        int narrowBeta = standPat - 500;
        int score = Quiesce.quiescenceSearch(Search.NEG_INFINITY, narrowBeta, position, sc, st, 0);

        // Assert, should NOT just return standPat; it should search evasions
        assertNotEquals(standPat, score,
                "qsearch must not use stand-pat beta cutoff when in check");
    }

    // ======================== Not-in-check behaviour ========================

    @Test
    @DisplayName("Quiesce returns stand-pat when no captures improve the score")
    void returnsStandPatWhenNoCapturesHelp() {
        // Arrange, quiet position, no captures available.
        // Kings only, NNUE eval should be near 0.
        FEN fen = new FEN("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        Position position = new Position(fen);
        assertFalse(position.inCheck, "Precondition: not in check");
        SearchContext sc = searchContext();
        SharedTables st = sharedTables();

        int standPat = position.evaluator.computeOutput(position.activePlayer);

        // Act
        int score = Quiesce.quiescenceSearch(Search.NEG_INFINITY, Search.POS_INFINITY, position, sc, st, 0);

        // Assert, with no captures, qsearch returns standPat unchanged
        assertEquals(standPat, score);
    }

    @Test
    @DisplayName("Quiesce returns stand-pat on immediate beta cutoff when eval exceeds beta")
    void standPatBetaCutoffWhenNotInCheck() {
        // Arrange, white has overwhelming material advantage
        FEN fen = new FEN("4k3/8/8/8/8/8/8/1Q2K2R w - - 0 1");
        Position position = new Position(fen);
        assertFalse(position.inCheck, "Precondition: not in check");
        SearchContext sc = searchContext();
        SharedTables st = sharedTables();

        int standPat = position.evaluator.computeOutput(position.activePlayer);

        // Act, beta much lower than standPat, so stand-pat cutoff should fire
        int lowBeta = standPat - 500;
        int score = Quiesce.quiescenceSearch(Search.NEG_INFINITY, lowBeta, position, sc, st, 0);

        // Assert, should return standPat immediately (beta cutoff)
        assertEquals(standPat, score);
    }

    @Test
    @DisplayName("Quiesce score is at least stand-pat when not in check")
    void scoreAtLeastStandPatWhenNotInCheck() {
        // Arrange, position with a hanging piece (free capture available)
        FEN fen = new FEN("4k3/8/8/3p4/8/8/8/4K2R w - - 0 1");
        Position position = new Position(fen);
        assertFalse(position.inCheck, "Precondition: not in check");
        SearchContext sc = searchContext();
        SharedTables st = sharedTables();

        int standPat = position.evaluator.computeOutput(position.activePlayer);

        // Act
        int score = Quiesce.quiescenceSearch(Search.NEG_INFINITY, Search.POS_INFINITY, position, sc, st, 0);

        // Assert, the "stand pat" option means the score can never drop below it
        assertTrue(score >= standPat,
                "qsearch score (" + score + ") must be >= standPat (" + standPat + ") when not in check");
    }
}

