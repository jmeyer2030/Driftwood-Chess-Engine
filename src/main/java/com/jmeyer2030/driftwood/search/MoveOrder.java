package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;

public class MoveOrder {

    // Rough piece values used for MVV and SEE
    public static final int[] PIECE_VALUES = new int[]{100, 300, 330, 500, 900, 1_000};

    // Multiplier on victim value so MVV is the primary capture sort key.
    // With values 100–1000 this gives base scores 700–7000.
    public static final int MVV_MULTIPLIER = 7;

    // Capture history (+/- 16_384) is divided by this so it acts as a tiebreaker
    // within MVV-based ordering.  256 → effective range ±64, well under the
    // smallest MVV gap (210 between knight and bishop at ×7).
    public static final int CAPTURE_HISTORY_DIVISOR = 256;

    public static final int PV_BONUS = 2_000_000;
    public static final int TT_BONUS = 1_000_000;
    public static final int PROMOTION_BONUS = 500_000;
    public static final int CAPTURE_BONUS = 50_000;
    public static final int FIRST_KILLER_BONUS = 30_000;
    public static final int SECOND_KILLER_BONUS = 29_000;

    /**
     * Scores captures using MVV (Most Valuable Victim) as the primary key and
     * capture history as a scaled tiebreaker.  Capture history is divided by
     * {@link #CAPTURE_HISTORY_DIVISOR} so it cannot override the MVV ordering.
     *
     * @param position      position for active player color
     * @param searchContext source of capture history and move buffer/scores
     * @param firstMove     the start of the move-window in the table
     * @param firstNonMove  the first non-move after the window
     */
    public static void scoreLoudMoves(Position position, SearchContext searchContext, int firstMove, int firstNonMove) {
        int color = position.activePlayer;
        for (int i = firstMove; i < firstNonMove; i++) {
            int move = searchContext.moveBuffer[i];
            int score = PIECE_VALUES[MoveEncoding.getCapturedPiece(move)] * MVV_MULTIPLIER
                      + searchContext.captureHistory.getScore(color, move) / CAPTURE_HISTORY_DIVISOR;
            if (MoveEncoding.getIsPromotion(move)) {
                score += PROMOTION_BONUS;
            }
            searchContext.moveScores[i] = score;
        }
    }

    /**
     * Scores quiet moves using history heuristic (and promotion bonus).
     * Used by the staged move picker for the QUIETS stage.
     *
     * @param position      position for history color
     * @param searchContext source of history heuristic and move buffer/scores
     * @param firstMove     the start of the move-window in the table
     * @param firstNonMove  the first non-move after the window
     */
    public static void scoreQuietMoves(Position position, SearchContext searchContext, int firstMove, int firstNonMove) {
        for (int i = firstMove; i < firstNonMove; i++) {
            int move = searchContext.moveBuffer[i];
            int score = 0;
            if (MoveEncoding.getIsPromotion(move)) {
                score += PROMOTION_BONUS;
            }
            score += searchContext.historyHeuristic.getHeuristic(move, position.activePlayer);
            searchContext.moveScores[i] = score;
        }
    }


    /**
     * Performs one iteration of a selection sort by:
     * - Finding the move with the highest value within the window
     * - Swapping its location with the first element in the window
     *
     * @param searchContext used to access moveBuffer and moveScores
     * @param firstMove     the first move to start our search
     * @param firstNonMove  the element after the last move to search
     */
    public static void bestMoveFirst(SearchContext searchContext, int firstMove, int firstNonMove) {
        // Initialize to first move, so that we are safe if firstMove == firstNonMove
        int bestIndex = firstMove;
        int bestScore = searchContext.moveScores[firstMove];

        // Find the best scoring move and its index
        for (int i = firstMove; i < firstNonMove; i++) {
            int currentMoveScore = searchContext.moveScores[i];

            if (currentMoveScore > bestScore) {
                bestScore = currentMoveScore;
                bestIndex = i;
            }
        }

        // Swap the first move/score with the best move/score
        int tempBestMove = searchContext.moveBuffer[bestIndex];
        searchContext.moveBuffer[bestIndex] = searchContext.moveBuffer[firstMove];
        searchContext.moveScores[bestIndex] = searchContext.moveScores[firstMove];

        searchContext.moveBuffer[firstMove] = tempBestMove;
        searchContext.moveScores[firstMove] = bestScore;
    }

    /**
     * Scores evasion moves (used by QSearchMovePicker for the in-check path).
     * Captures use MVV + scaled capture history + CAPTURE_BONUS; quiets use history heuristic.
     * No per-move TT probe, the TT move is handled by a separate stage.
     *
     * @param position       current position
     * @param searchContext  source of capture history, quiet history, move buffer/scores
     * @param firstMove      the start of the move-window in the table
     * @param firstNonMove   the first non-move after the window
     */
    public static void scoreEvasionMoves(Position position, SearchContext searchContext, int firstMove, int firstNonMove) {
        int color = position.activePlayer;
        for (int i = firstMove; i < firstNonMove; i++) {
            int move = searchContext.moveBuffer[i];
            int score = 0;
            if (MoveEncoding.getIsPromotion(move)) {
                score += PROMOTION_BONUS;
            }
            if (MoveEncoding.getIsCapture(move)) {
                score += CAPTURE_BONUS
                       + PIECE_VALUES[MoveEncoding.getCapturedPiece(move)] * MVV_MULTIPLIER
                       + searchContext.captureHistory.getScore(color, move) / CAPTURE_HISTORY_DIVISOR;
            } else {
                score += searchContext.historyHeuristic.getHeuristic(move, position.activePlayer);
            }
            searchContext.moveScores[i] = score;
        }
    }

    /**
     * Returns the net value of a capture if the capturer is then taken
     *
     * @param move
     * @return exchange evaluation
     */
    private static int mvvlva(int move) {
        return PIECE_VALUES[MoveEncoding.getCapturedPiece(move)] - PIECE_VALUES[MoveEncoding.getMovedPiece(move)];
    }

    /**
     * For winning captures, just return MVVLVA (skip expensive SEE).
     * For losing/equal captures, return full SEE.
     *
     * @param move     to score
     * @param position the move is made on
     * @param see      SEE instance for exchange evaluation
     */
    private static int quickEvaluateExchange(int move, Position position, SEE see) {
        int mvvlvaScore = mvvlva(move);

        if (mvvlvaScore > 0) {
            return mvvlvaScore;
        } else {
            return see.see(move, position);
        }
    }

}
