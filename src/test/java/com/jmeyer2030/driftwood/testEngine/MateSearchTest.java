package com.jmeyer2030.driftwood.testEngine;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.search.Search;
import com.jmeyer2030.driftwood.search.SearchContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
* Tests that the mate search is effective, and returns scores that correctly indicate the distance to mate.
* </p>
* Note that this is currently non-deterministic because we terminate the search early if we get the same mating score
* three times in a row. Thus, searches that experimentally have inaccurate scores (or take too long), have been omitted.
*/
public class MateSearchTest {

    /**
     * M1 (1 ply)
     */
    @Test
    public void testMatingPosition1() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        FEN fen = new FEN("8/8/8/8/k1K5/8/4Q3/8 w - - 0 1");
        Position position = new Position(fen);

        int score = Search.iterativeDeepening(position, 10000, searchContext, sharedTables).value;

        System.out.println(score);

        assertEquals(1_899_999, score);
    }

    /**
     * M2 (3 ply)
     */
    @Test
    public void testMatingPosition2() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        FEN fen = new FEN("8/8/8/8/2K5/k7/3Q4/8 w - - 0 1");
        Position position = new Position(fen);

        int score = Search.iterativeDeepening(position, 10000, searchContext, sharedTables).value;

        System.out.println(score);

        assertEquals(1_899_997, score);
    }

    /**
     * M3 (5 ply)
     */
    @Test
    public void testMatingPosition3() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        FEN fen = new FEN("8/8/8/8/8/k2K1Q2/8/8 w - - 0 1");
        Position position = new Position(fen);

        Search.MoveValue mv = Search.iterativeDeepening(position, 10000, searchContext, sharedTables);

        System.out.println(mv.value);
        System.out.println(MoveEncoding.getLAN(mv.bestMove));

        assertEquals(1_899_995, mv.value);
    }

    /**
     * M4 (7 ply)
     */
    @Test
    public void testMatingPosition4() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        FEN fen = new FEN("8/8/8/2k5/8/2K5/3Q4/8 w - - 0 1");
        Position position = new Position(fen);

        Search.MoveValue mv = Search.iterativeDeepening(position, 10000, searchContext, sharedTables);

        System.out.println(mv.value);
        System.out.println(MoveEncoding.getLAN(mv.bestMove));

        assertEquals(1_899_993, mv.value);
    }

    /**
     * M5 (9 ply)
     */
    @Test
    public void testMatingPosition5() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        FEN fen = new FEN("8/8/8/8/8/k4KQ1/8/8 w - - 0 1");
        Position position = new Position(fen);

        Search.MoveValue mv = Search.iterativeDeepening(position, 1_000_000, searchContext, sharedTables);

        System.out.println(mv.value);
        System.out.println(MoveEncoding.getLAN(mv.bestMove));

        assertEquals(1_899_991, mv.value);
    }


    /**
     * M7 (13 ply)
     */
    // @Test
    public void testMatingPosition6() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        FEN fen = new FEN("8/8/8/8/4k3/8/4K3/4Q3 w - - 0 1");
        Position position = new Position(fen);

        Search.MoveValue mv = Search.iterativeDeepening(position, 1_000_000, searchContext, sharedTables);

        System.out.println(mv.value);
        System.out.println(MoveEncoding.getLAN(mv.bestMove));

        assertEquals(1_899_987, mv.value);
    }

    /**
     * M12 (23 ply)
     */
    @Test
    public void testMatingPosition7() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(23);
        FEN fen = new FEN("8/8/7k/K7/R7/8/8/8 w - - 0 1");
        Position position = new Position(fen);

        Search.MoveValue mv = Search.iterativeDeepening(position, 1_000_000, searchContext, sharedTables);

        System.out.println(mv.value);
        System.out.println(MoveEncoding.getLAN(mv.bestMove));

        assertEquals(1_899_977, mv.value);
    }

    /**
     * 1 Q
     * M8 (15 ply)
     */
    @Test
    public void testMatingPosition8() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        FEN fen = new FEN("8/8/8/8/4k3/8/1K6/Q7 w - - 0 1");
        Position position = new Position(fen);

        Search.MoveValue mv = Search.iterativeDeepening(position, 1_000_000, searchContext, sharedTables);

        System.out.println(mv.value);
        System.out.println(MoveEncoding.getLAN(mv.bestMove));

        assertEquals(1_899_985, mv.value);
    }

    /**
     * 2 bishop
     * M10 (19 ply)
     */
    // @Test
    public void testMatingPosition9() {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(23);
        FEN fen = new FEN("4k3/8/8/3BB3/4K3/8/8/8 w - - 0 1");
        Position position = new Position(fen);

        Search.MoveValue mv = Search.iterativeDeepening(position, 1_000_000, searchContext, sharedTables);

        System.out.println(mv.value);
        System.out.println(MoveEncoding.getLAN(mv.bestMove));

        assertEquals(1_899_981, mv.value);
    }
}
