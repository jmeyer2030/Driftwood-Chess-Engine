package com.jmeyer2030.driftwood.search;

import java.util.ArrayList;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.board.InvalidPositionException;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;


public class Search {

    // Values representing very high scores minimizing risk of over/underflow.
    // Kept within 22-bit signed range (±2_097_151) so they pack into the TT.
    public static final int POS_INFINITY = 2_000_000;
    public static final int NEG_INFINITY = -2_000_000;

    public static final int MATED_VALUE = 1_900_000;
    public static final int MATED_SCORE = 1_800_000;

    public static final int[] futilityMargin = {0, 200, 500, 900};

    public static final int MAX_SEARCH_DEPTH = 256;

    public static final int NULL_MOVE_PRUNING_REDUCTION = 3;
    public static final int NUM_NON_PV_FULL_DEPTH_SEARCHES = 3;

    /**
     * Class representing a move and evaluation
     */
    public static class MoveValue {
        public int value;
        public int bestMove;

        public MoveValue(int value, int bestMove) {
            this.value = value;
            this.bestMove = bestMove;
        }
    }

    /**
     * Runs negamax on increasing depths until the time limit is exceeded
     *
     * @param position      position to run the search on
     * @param limitMillis   time limit in milliseconds for the search
     * @param searchContext per-search scratch space
     * @param sharedTables  long-lived shared tables (TT, threefold)
     * @return moveValue associated with the deepest search of the position
     */
    public static MoveValue iterativeDeepening(Position position, long limitMillis, SearchContext searchContext, SharedTables sharedTables) {
        // Advance the TT generation so entries from the previous search become stale
        if (sharedTables.tt != null) {
            sharedTables.tt.newSearch();
        }

        // Create a list representing the best moves generated at each depth
        List<MoveValue> searchResults = new ArrayList<>();

        // Store the current time so we know when to stop searching
        long start = System.currentTimeMillis();

        // Create a new thread to run the search on
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // Initialize search depth and search while time hasn't been exceeded
        int depth = 0;
        while (depth < MAX_SEARCH_DEPTH) {
            depth++;

            // Create and submit search task
            Callable<MoveValue> task = getSearchCallable(position, depth, searchContext, sharedTables);
            Future<MoveValue> future = executor.submit(task);

            try {
                // Compute the max amount of time this search can take
                long maxTimeForSearch = start + limitMillis - System.currentTimeMillis();

                // Try to get the result of the task. If time limit exceeded, throws timeout exception
                MoveValue result = future.get(maxTimeForSearch, TimeUnit.MILLISECONDS);
                searchResults.add(result);

                System.out.println("info depth " + depth + " pv " +
                        searchContext.pvTable.getPVLine()
                        + " score cp " + searchResults.getLast().value);

                // If mate found, try to reduce moves until mate by requiring that the same score is found for 3 consecutive depths.
                if (Math.abs(searchResults.getLast().value) >= (MATED_SCORE) && searchResults.size() >= 3) {
                    int size = searchResults.size();
                    int lastValue = searchResults.get(size - 1).value;
                    int secondValue = searchResults.get(size - 2).value;
                    int thirdValue = searchResults.get(size - 3).value;
                    if (lastValue == secondValue && secondValue == thirdValue)
                        break;
                }

            } catch (TimeoutException te) {
                System.out.println("Time limit reached");

                future.cancel(true); // Tells the thread that it was interrupted
                executor.shutdown();

                try {
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) { // Wait for it to finish
                        executor.shutdownNow(); // Force shutdown if it didn't finish
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                }

                // reset firstNonMove since search timed out
                searchContext.firstNonMove = 0;

                break; //Exit the search loop
            } catch (ExecutionException e) { // This should never happen so we throw an exception
                e.printStackTrace();
                throw new RuntimeException("Unexpected execution exception caught");
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
                throw new RuntimeException("Unexpected interrupted exception caught");
            }
        }

        if (searchResults.size() == 0) {
            throw new RuntimeException("Search failed because a single depth search didn't finish in time");
        }

        return searchResults.get(searchResults.size() - 1);
    }

    /**
     * Generates a Callable task that runs negamax
     *
     * @param position      negamax param
     * @param depth         negamax param
     * @param searchContext per-search scratch space
     * @param sharedTables  long-lived shared tables
     * @return MoveValue callable
     */
    public static Callable<MoveValue> getSearchCallable(Position position, int depth, SearchContext searchContext, SharedTables sharedTables) {
        return () -> {
            try {
                int score = pvSearch(NEG_INFINITY, POS_INFINITY, depth, position, searchContext, sharedTables, true, 0, true);
                return new MoveValue(score, searchContext.bestMoves[0]);
            } catch (InterruptedException e) {
                System.out.println("Negamax was interrupted.");
                throw e;
            } catch (InvalidPositionException ipe) {
                System.out.println("IPE caught at callable");
                ipe.printStackTrace();
                throw ipe;
            }
        };
    }


    /**
     * Realistically tests the search speed by stopping search at a certain depth rather than time
     * Uses tt and starts at depth 1 which makes it preferable to just testing negamax search
     *
     * @param position position to search
     * @param depth    depth to search to
     */
    public static void iterativeDeepeningFixedDepth(Position position, int depth) {
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(32);

        // Advance the TT generation so entries from the previous search become stale
        if (sharedTables.tt != null) {
            sharedTables.tt.newSearch();
        }

        for (int i = 1; i <= depth; i++) {
            try {
                getSearchCallable(position, i, searchContext, sharedTables).call();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Unexpected exception from search", e);
            }
        }

        System.out.println(searchContext.pvTable.getPVLine());
    }

    /**
     * PVS Search
     *
     * @param alpha         the score that the active player is guaranteed in another branch
     * @param beta          the score the non-active player is guaranteed in another branch
     * @param depthLeft     the depth left in the current search
     * @param position      the position to search
     * @param searchContext per-search scratch space
     * @param sharedTables  long-lived shared tables
     * @param isRoot        if this call is the root
     * @param ply           the current ply we are on
     * @param isPV          if the current node is the principle variation
     *
     * @return score the evaluation of the best line
     */
    public static int pvSearch(int alpha, int beta, int depthLeft, Position position, SearchContext searchContext, SharedTables sharedTables, boolean isRoot, int ply, boolean isPV)
            throws InterruptedException {

        // Check for signal to interrupt the search
        if (Thread.currentThread().isInterrupted()) {
            System.out.println("interrupted in negamax!");
            throw new InterruptedException("Negamax was interrupted by iterative deepening");
        }
        searchContext.pvTable.setPVLength(ply);

        int alphaOrig = alpha;

        //=============== Look for draw with 50-move or repetition ===============
        if (!isRoot) {
            if (sharedTables.threeFoldTable.positionRepeated(position.zobristHash)) {
                return 0;
            } else if (position.halfMoveCount >= 100) {
                return 0;
            }
        }

        //=============== Check transposition table ===============
        // Probed before move generation, on a cutoff we skip movegen entirely.
        int eval = 0;
        boolean foundTTScore = false;
        long ttPacked = (sharedTables.tt != null) ? sharedTables.tt.probe(position.zobristHash, depthLeft) : 0;
        if (ttPacked != 0) {
            foundTTScore = true;
            eval = TranspositionTable.unpackScore(ttPacked);
            int nodeType = TranspositionTable.unpackNodeType(ttPacked);
            int bestMove = TranspositionTable.unpackBestMove(ttPacked);

            eval = scoreFromTT(eval, ply);

            if (nodeType == NodeType.EXACT && !isPV) {
                searchContext.bestMoves[ply] = bestMove;
                return eval;
            } else if (nodeType == NodeType.LOWER_BOUND) {
                alpha = Math.max(alpha, eval);
            } else { // Upper bound
                beta = Math.min(beta, eval);
            }

            if (alpha >= beta && !isPV) {
                searchContext.bestMoves[ply] = bestMove;
                return eval;
            }
        }

        //=============== Internal Iterative Reduction ===============
        /*
        if (ttPacked == 0 && depthLeft >= 5 && isPV) {
            depthLeft--;
        }
        */

        //=============== Return based on search depth ===============
        if (depthLeft <= 0) {
            return Quiesce.quiescenceSearch(alpha, beta, position, searchContext, sharedTables, ply + 1);
        }

        //=============== Compute Eval ===============
        // Should be done before uses of eval in case TT didn't find
        if (!foundTTScore) {
            eval = position.evaluator.computeOutput(position.activePlayer);
        }

        // ===============Reverse Futility Pruning===============
        //  - If not root, eval is so good that it and a margin is better than beta
        //  - If opponent is getting mated, don't reduce so we can find quicker mates
        int margin = 150 * depthLeft;

        if (!isRoot && eval >= beta + margin && !position.inCheck && !isPV && beta > -MATED_SCORE) {
            return eval;
        }

        // ===============Null Move Pruning===============
        if (!isRoot && depthLeft >= 3 && eval >= beta && nmpConditionsMet(position)) {
            int reduction = NULL_MOVE_PRUNING_REDUCTION;
            position.makeNullMove();
            int score;
            try {
                score = -pvSearch(-beta, -beta + 1, depthLeft - reduction, position, searchContext, sharedTables, false, ply + 1, false);
            } finally {
                position.unMakeNullMove();
            }
            // If a null move failed high over beta, then certainly the best move would as well, so we prune
            if (score >= beta) {
                return score;
            }
        }

        //=============== Get TT best move for move ordering ===============
        int ttBestMove = 0;
        if (ttPacked != 0) {
            ttBestMove = TranspositionTable.unpackBestMove(ttPacked);
        } else if (sharedTables.tt != null) {
            ttBestMove = sharedTables.tt.checkedGetBestMove(position.zobristHash);
        }

        // Check if we can use futility pruning
        boolean useFutilityPruning = depthLeft <= 3 && !isPV
                && !position.inCheck
                && Math.abs(alpha) < MATED_SCORE
                && eval + futilityMargin[depthLeft] <= alpha;

        //=============== Staged Move Generation ===============
        MovePicker picker = searchContext.movePickers[ply];
        picker.init(position, searchContext, sharedTables, ply, ttBestMove, position.inCheck);

        int bestScore = Integer.MIN_VALUE;
        searchContext.bestMoves[ply] = 0;

        // Track quiets searched for history penalization on beta cutoff (pooled per-ply)
        int[] quietsSearched = searchContext.quietsSearched[ply];
        searchContext.numQuietsSearched[ply] = 0;

        // Track captures searched for capture history penalization on beta cutoff (pooled per-ply)
        int[] capturesSearched = searchContext.capturesSearched[ply];
        searchContext.numCapturesSearched[ply] = 0;

        int moveIndex = 0;
        int move;

        while ((move = picker.nextMove()) != 0) {
            moveIndex++;

            if (Thread.currentThread().isInterrupted()) {
                picker.restoreBuffer();
                throw new InterruptedException("Negamax was interrupted by iterative deepening");
            }

            boolean isQuietMove = !MoveEncoding.getIsCapture(move) && !MoveEncoding.getIsEP(move);

            // "open" the position
            position.makeMove(move);
            sharedTables.threeFoldTable.addPosition(position.zobristHash, move);

            if (useFutilityPruning &&
                    moveIndex > 1 &&
                    isQuietMove &&
                    !MoveEncoding.getIsPromotion(move) &&
                    !position.inCheck) {
                if (searchContext.numQuietsSearched[ply] < quietsSearched.length) {
                    quietsSearched[searchContext.numQuietsSearched[ply]++] = move;
                }
                sharedTables.threeFoldTable.popPosition();
                position.unMakeMove(move);
                continue;
            }

            // ===============PV search===============
            int score;
            try {
                if (moveIndex == 1) {
                    score = -pvSearch(-beta, -alpha, depthLeft - 1, position, searchContext, sharedTables, false, ply + 1, isPV);
                } else {
                    // ===============Late Move Reduction===============
                    if (isQuietMove && moveIndex > NUM_NON_PV_FULL_DEPTH_SEARCHES && depthLeft > 3) {
                        int reduction = (int) Math.round(.99 + (Math.log(depthLeft) * Math.log(moveIndex - 1)) / 3.14);
                        score = -pvSearch(-alpha - 1, -alpha, depthLeft - 1 - reduction, position, searchContext, sharedTables, false, ply + 1, false);

                        if (score >= beta) {
                            score = -pvSearch(-alpha - 1, -alpha, depthLeft - 1, position, searchContext, sharedTables, false, ply + 1, false);
                        }
                    } else {
                        score = -pvSearch(-alpha - 1, -alpha, depthLeft - 1, position, searchContext, sharedTables, false, ply + 1, false);
                    }

                    if (score > alpha && (beta - alpha) > 1) {
                        score = -pvSearch(-beta, -alpha, depthLeft - 1, position, searchContext, sharedTables, false, ply + 1, isPV);
                    }
                }
            } finally {
                sharedTables.threeFoldTable.popPosition();
                position.unMakeMove(move);
            }

            // Track quiet for history penalization
            if (isQuietMove && searchContext.numQuietsSearched[ply] < quietsSearched.length) {
                quietsSearched[searchContext.numQuietsSearched[ply]++] = move;
            }

            // Track capture for capture history penalization
            if (!isQuietMove && searchContext.numCapturesSearched[ply] < capturesSearched.length) {
                capturesSearched[searchContext.numCapturesSearched[ply]++] = move;
            }

            if (score > bestScore) {
                bestScore = score;
                searchContext.bestMoves[ply] = move;
            }

            if (score > alpha) {
                alpha = score;
                searchContext.pvTable.writePVMove(move, ply);

                if (alpha >= beta) {
                    if (isQuietMove) {
                        if (searchContext.killerMoves.killerMoves[0][ply] != move) {
                            searchContext.killerMoves.killerMoves[1][ply] = searchContext.killerMoves.killerMoves[0][ply];
                            searchContext.killerMoves.killerMoves[0][ply] = move;
                        }

                        searchContext.historyHeuristic.addMove(position.activePlayer, move, depthLeft);

                        for (int j = 0; j < searchContext.numQuietsSearched[ply] - 1; j++) {
                            searchContext.historyHeuristic.penalizeMove(position.activePlayer, quietsSearched[j], depthLeft);
                        }
                    } else {
                        // Capture caused beta cutoff, update capture history
                        searchContext.captureHistory.addBonus(position.activePlayer, move, depthLeft);

                        for (int j = 0; j < searchContext.numCapturesSearched[ply] - 1; j++) {
                            searchContext.captureHistory.addMalus(position.activePlayer, capturesSearched[j], depthLeft);
                        }
                    }
                    break;
                }
            }
        }

        picker.restoreBuffer();

        //=============== No move game end (mate or stalemate) ===============
        if (moveIndex == 0 && !isRoot) {
            if (position.inCheck) {
                return -(MATED_VALUE - ply);
            } else {
                return 0;
            }
        }

        if (sharedTables.tt != null && searchContext.bestMoves[ply] != 0) {
            int nodeType;

            // Adjust for whatever ply we're on (if mate)
            int ttValue = scoreToTT(bestScore, ply);

            if (bestScore <= alphaOrig) { // Failed to find a better move (than is known to exist with alpha), so the value is AT MAXIMUM score
                nodeType = NodeType.UPPER_BOUND;
            } else if (bestScore >= beta) { // The value is greater than beta, so an opponent would PRUNE it, but the value is at LEAST score
                nodeType = NodeType.LOWER_BOUND;
            } else {
                nodeType = NodeType.EXACT;
            }

            sharedTables.tt.addElement(position.zobristHash, searchContext.bestMoves[ply], depthLeft, ttValue, nodeType);
        }

        return bestScore;
    }

    /**
     * Null Move pruning conditions are NOT met if any:
     * - position is in check
     * - active player has only kings and pawns
     * An alternative approach worth testing:
     * - We can NMP iff legal moves >= 8 (8 is arbitrary, but suggests no zugzwang)
     *
     * @param position position
     * @return if we can do NMP
     */
    public static boolean nmpConditionsMet(Position position) {
        int color = position.activePlayer;
        if (position.inCheck || // If in check OR
                (position.pieceCounts[color][1] == 0 && position.pieceCounts[color][2] == 0 && // STM no non-pawn pieces
                        position.pieceCounts[color][3] == 0 && position.pieceCounts[color][4] == 0))
            return false;
        return true;
    }

    /**
    * Normalizes score so that it is relative to the Position. During search, scores are MATED_VALUE - depth - ply,
    * relative to the depth of the node, and the moves until mate.
    * The TT should score  ONLY care about the moves until mate, and not the ply that we are currently at, so we remove
    * this consideration.
    */
    public static int scoreToTT(int score, int ply) {
        return (score > MATED_SCORE) ? score + ply : score < -MATED_SCORE ? score - ply : score;
    }

    /**
    * TT Score stores as moves from the position to mate. We make it relative to the search here.
    * </p>
    * e.g. mate in 3, at a position 4 deep, should be treated as mate in 7, relative to the search
    * of the root position
    */
    public static int scoreFromTT(int score, int ply) {
        return (score > MATED_SCORE) ? score - ply : score < -MATED_SCORE ? score + ply : score;
    }

}
