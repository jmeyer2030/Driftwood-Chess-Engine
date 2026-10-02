package com.jmeyer.bench;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;
import com.jmeyer2030.driftwood.search.Search;
import com.jmeyer2030.driftwood.search.SearchContext;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Standalone puzzle benchmark harness for the DriftWood chess engine.
 *
 * <p>Reads puzzles from the Lichess puzzle CSV, samples a configurable number using a seeded RNG,
 * searches each position, and reports whether the engine found the expected first move.
 *
 * <p>This is NOT a JUnit test - it is run via {@code java -cp} (see {@code run-puzzle-bench.bat}).
 */
public class PuzzleBenchmark {

    // Rating bucket boundaries (inclusive lower, exclusive upper)
    private static final int[][] RATING_BUCKETS = {
            {0, 1200},
            {1200, 1600},
            {1600, 2000},
            {2000, 2400},
            {2400, Integer.MAX_VALUE}
    };

    private static final String[] BUCKET_LABELS = {
            "   0-1199",
            "1200-1599",
            "1600-1999",
            "2000-2399",
            "    2400+"
    };

    public static void main(String[] args) {
        // ========== Parse CLI arguments ==========
        String csvPath = "benchmarks/puzzle-bench/data/lichess_db_puzzle.csv";
        int count = 500;
        long timePerPuzzle = 2000;
        long seed = 42;
        int minRating = 0;
        int maxRating = 9999;
        int ttSize = 18;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--csv":        csvPath       = args[++i]; break;
                case "--count":      count         = Integer.parseInt(args[++i]); break;
                case "--time":       timePerPuzzle = Long.parseLong(args[++i]); break;
                case "--seed":       seed          = Long.parseLong(args[++i]); break;
                case "--minRating":  minRating     = Integer.parseInt(args[++i]); break;
                case "--maxRating":  maxRating     = Integer.parseInt(args[++i]); break;
                case "--ttSize":     ttSize        = Integer.parseInt(args[++i]); break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        // ========== Print configuration ==========
        System.out.println("=== DriftWood Puzzle Benchmark ===");
        System.out.println("CSV:            " + csvPath);
        System.out.println("Puzzles:        " + count);
        System.out.println("Time/puzzle:    " + timePerPuzzle + "ms");
        System.out.println("Seed:           " + seed);
        System.out.println("Rating filter:  " + minRating + "-" + maxRating);
        System.out.println("TT size:        " + ttSize + " bits");
        System.out.println();

        // ========== Sample puzzles via reservoir sampling ==========
        // Uses reservoir sampling so only 'count' Puzzle objects are in memory at once,
        // avoiding OOM on the full 5.8M-row CSV.
        System.out.println("Sampling puzzles (reservoir sampling)...");
        List<Puzzle> sample = samplePuzzles(csvPath, count, seed, minRating, maxRating);
        int actualCount = sample.size();
        System.out.println("Sampled " + actualCount + " puzzles.");

        if (actualCount == 0) {
            System.err.println("No puzzles found matching the rating filter. Exiting.");
            System.exit(1);
        }
        if (actualCount < count) {
            System.out.println("Warning: only " + actualCount + " puzzles available (requested " + count + ").");
        }
        System.out.println();

        // ========== Run benchmark ==========
        int[] bucketTotal = new int[RATING_BUCKETS.length];
        int[] bucketCorrect = new int[RATING_BUCKETS.length];
        int totalCorrect = 0;
        int totalErrors = 0;
        long benchStart = System.currentTimeMillis();

        // Shared move buffer for LAN-to-move conversion (not used during search)
        int[] moveBuffer = new int[256];

        for (int i = 0; i < actualCount; i++) {
            Puzzle puzzle = sample.get(i);
            int bucketIdx = bucketFor(puzzle.rating);
            bucketTotal[bucketIdx]++;

            try {
                // Set up position: parse FEN, apply the opponent's setup move
                Position position = new Position(new FEN(puzzle.fen));
                int setupMove = MoveGenerator.getMoveFromLAN(puzzle.setupMove(), position, moveBuffer);
                position.makeMove(setupMove);

                // Fresh search state for each puzzle
                SearchContext searchContext = new SearchContext();
                SharedTables sharedTables = new SharedTables(ttSize);

                // Search is quiet unless a progress listener is supplied.
                long searchStart = System.currentTimeMillis();
                Search.MoveValue result = Search.iterativeDeepening(position, timePerPuzzle, searchContext, sharedTables);
                long searchElapsed = System.currentTimeMillis() - searchStart;

                // Compare engine's best move with expected
                String engineMove = MoveEncoding.getLAN(result.bestMove);
                String expected = puzzle.expectedMove();
                boolean pass = engineMove.equals(expected);

                if (pass) {
                    totalCorrect++;
                    bucketCorrect[bucketIdx]++;
                }

                System.out.printf("[%s] #%-4d %-6s (rating %4d)  expected=%-6s got=%-6s  time=%dms%n",
                        pass ? "PASS" : "FAIL",
                        i + 1,
                        puzzle.id,
                        puzzle.rating,
                        expected,
                        engineMove,
                        searchElapsed);

            } catch (Exception e) {
                totalErrors++;
                System.out.printf("[ERR ] #%-4d %-6s (rating %4d)  %s: %s%n",
                        i + 1,
                        puzzle.id,
                        puzzle.rating,
                        e.getClass().getSimpleName(),
                        e.getMessage());
            }
        }

        long benchElapsed = System.currentTimeMillis() - benchStart;

        // ========== Print summary ==========
        System.out.println();
        System.out.println("=== Puzzle Benchmark Results ===");
        System.out.printf("Puzzles tested: %d%n", actualCount);
        System.out.printf("Correct:        %d / %d (%.1f%%)%n",
                totalCorrect, actualCount, 100.0 * totalCorrect / actualCount);
        if (totalErrors > 0) {
            System.out.printf("Errors:         %d%n", totalErrors);
        }
        System.out.printf("Total time:     %.1fs%n", benchElapsed / 1000.0);
        System.out.printf("Seed: %d | Time/puzzle: %dms | Rating filter: %d-%d | TT: %d bits%n",
                seed, timePerPuzzle, minRating, maxRating, ttSize);
        System.out.println();
        System.out.println("By rating:");
        for (int b = 0; b < RATING_BUCKETS.length; b++) {
            if (bucketTotal[b] > 0) {
                System.out.printf("  %s:  %3d / %3d  (%.1f%%)%n",
                        BUCKET_LABELS[b],
                        bucketCorrect[b],
                        bucketTotal[b],
                        100.0 * bucketCorrect[b] / bucketTotal[b]);
            }
        }
    }

    /**
     * Samples up to {@code count} puzzles from the CSV using reservoir sampling.
     * Only {@code count} Puzzle objects are in memory at any time, so this works
     * even for very large CSVs (5.8M+ rows) without blowing the heap.
     *
     * <p>Algorithm: standard reservoir sampling (Vitter's Algorithm R).
     * Each qualifying row has an equal probability of being selected.
     */
    private static List<Puzzle> samplePuzzles(String csvPath, int count, long seed, int minRating, int maxRating) {
        Puzzle[] reservoir = new Puzzle[count];
        int seen = 0; // number of qualifying rows seen so far
        Random random = new Random(seed);

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                // Skip header row if present
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("PuzzleId")) {
                        continue;
                    }
                }

                try {
                    // Quick rating filter before full parse: extract the 4th CSV field
                    int rating = quickParseRating(line);
                    if (rating < minRating || rating > maxRating) {
                        continue;
                    }

                    Puzzle puzzle = new Puzzle(line);

                    // Require at least 2 moves (setup + expected answer)
                    if (puzzle.moves.length < 2) {
                        continue;
                    }

                    // Reservoir sampling
                    if (seen < count) {
                        reservoir[seen] = puzzle;
                    } else {
                        int j = random.nextInt(seen + 1);
                        if (j < count) {
                            reservoir[j] = puzzle;
                        }
                    }
                    seen++;
                } catch (Exception e) {
                    // Skip malformed lines silently
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read puzzle CSV: " + csvPath);
            System.exit(1);
        }

        // If fewer qualifying rows than requested, return only what we have
        int actualSize = Math.min(seen, count);
        List<Puzzle> result = new ArrayList<>(actualSize);
        for (int i = 0; i < actualSize; i++) {
            result.add(reservoir[i]);
        }

        // Shuffle the reservoir so execution order is randomized (reservoir sampling
        // doesn't guarantee random ordering within the reservoir)
        Collections.shuffle(result, random);
        return result;
    }

    /**
     * Quickly extracts the rating (4th field) from a CSV line without fully parsing it.
     * Returns -1 if the line can't be parsed.
     */
    private static int quickParseRating(String line) {
        try {
            // Fields: PuzzleId,FEN,Moves,Rating,...
            // Skip 3 commas to reach the Rating field
            int commaCount = 0;
            int start = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ',') {
                    commaCount++;
                    if (commaCount == 3) {
                        start = i + 1;
                    } else if (commaCount == 4) {
                        return Integer.parseInt(line.substring(start, i));
                    }
                }
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        return -1;
    }

    /**
     * Returns the index into RATING_BUCKETS for a given rating.
     */
    private static int bucketFor(int rating) {
        for (int i = 0; i < RATING_BUCKETS.length; i++) {
            if (rating >= RATING_BUCKETS[i][0] && rating < RATING_BUCKETS[i][1]) {
                return i;
            }
        }
        return RATING_BUCKETS.length - 1; // fallback to highest bucket
    }

    private static void printUsage() {
        System.err.println("Usage: java com.jmeyer.bench.PuzzleBenchmark [options]");
        System.err.println("Options:");
        System.err.println("  --csv <path>       Path to lichess_db_puzzle.csv (default: benchmarks/puzzle-bench/data/lichess_db_puzzle.csv)");
        System.err.println("  --count <n>        Number of puzzles to test (default: 500)");
        System.err.println("  --time <ms>        Time budget per puzzle in milliseconds (default: 2000)");
        System.err.println("  --seed <long>      Random seed for puzzle sampling (default: 42)");
        System.err.println("  --minRating <int>  Minimum puzzle rating, inclusive (default: 0)");
        System.err.println("  --maxRating <int>  Maximum puzzle rating, inclusive (default: 9999)");
        System.err.println("  --ttSize <int>     Transposition table size in bits (default: 18)");
    }
}



