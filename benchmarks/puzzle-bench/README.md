# Puzzle Benchmark

Standalone benchmark that tests DriftWood against puzzles from the
[Lichess open puzzle database](https://database.lichess.org/#puzzles).
It is **not** part of the JUnit test suite, it is run separately via a batch script.

## How It Works

1. Streams through the CSV file, filtering by rating range, using **reservoir sampling** to
   select _N_ puzzles with a seeded RNG (reproducible puzzle selection).
2. For each puzzle:
   - Parses the FEN and applies the opponent's setup move (first move in the `Moves` field).
   - Runs `Search.iterativeDeepening` with the configured time budget.
   - Compares the engine's best move against the expected answer (second move in the `Moves` field).
3. Prints per-puzzle `[PASS]`/`[FAIL]` lines and a summary with accuracy by rating bucket.

Only the **first engine move** is checked (not the full solution sequence).

## Quick Start

```bat
cd benchmarks\puzzle-bench
run-puzzle-bench.bat
```

Or from the project root:

```bat
benchmarks\puzzle-bench\run-puzzle-bench.bat
```

The script builds the engine (`mvn package -DskipTests`) and then runs the benchmark.

## Configuration

Edit the `SET` variables at the top of `run-puzzle-bench.bat`:

| Variable | Default | Description |
|---|---|---|
| `PUZZLE_CSV` | `benchmarks\puzzle-bench\data\lichess_db_puzzle.csv` | Path to the puzzle CSV |
| `COUNT` | `500` | Number of puzzles to sample and test |
| `TIME_PER_PUZZLE` | `2000` | Milliseconds the engine gets per puzzle |
| `SEED` | `42` | Random seed for reproducible sampling |
| `MIN_RATING` | `0` | Minimum puzzle rating (inclusive) |
| `MAX_RATING` | `9999` | Maximum puzzle rating (inclusive) |
| `TT_SIZE` | `18` | Transposition table size in bits (2^N entries) |

## Interpreting Results

```
[PASS] #1   00sHx  (rating 1760)  expected=e8d7   got=e8d7    time=312ms
[FAIL] #2   00sJ9  (rating 2671)  expected=e3g3   got=d1d7    time=2001ms
...

=== Puzzle Benchmark Results ===
Puzzles tested: 500
Correct:        312 / 500 (62.4%)
Total time:     1032.5s
Seed: 42 | Time/puzzle: 2000ms | Rating filter: 0-9999 | TT: 18 bits

By rating:
     0-1199:   45 /  60  (75.0%)
  1200-1599:   80 / 120  (66.7%)
  1600-1999:   95 / 150  (63.3%)
  2000-2399:   70 / 130  (53.8%)
      2400+:   22 /  40  (55.0%)
```

- **PASS** — engine found the expected move.
- **FAIL** — engine returned a different move (may still be reasonable but doesn't match the puzzle solution).
- **ERR** — the puzzle could not be processed (malformed FEN, illegal move, etc.).

## CSV Format

The CSV is the full Lichess puzzle database (~5.8M puzzles, ~800 MB). Each row:

```
PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags
```

- `Moves` is a space-separated list of LAN moves. The first move is the opponent's setup move; subsequent moves are the solution.
- The file is not committed to git due to size. Download it from https://database.lichess.org/#puzzles and place it at `benchmarks/puzzle-bench/data/lichess_db_puzzle.csv`.

## Notes

- **Reservoir sampling** is used to select puzzles, so only `COUNT` puzzle objects are held in memory at once — the full 5.8M CSV is streamed, not loaded. The CSV scan itself takes a few seconds.
- Engine search info output (`info depth ...`) is suppressed.
- Each puzzle gets a fresh context (e.g. new tt).





