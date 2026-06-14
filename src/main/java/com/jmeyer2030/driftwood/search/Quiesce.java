package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;

import static com.jmeyer2030.driftwood.search.Search.NEG_INFINITY;
import static com.jmeyer2030.driftwood.search.Search.MATED_VALUE;
import static com.jmeyer2030.driftwood.search.Search.scoreFromTT;

public class Quiesce {

    static final int DELTA_MARGIN = 200;

    /**
     * Performs a search of capture moves only to reduce the horizon effect.
     * Uses {@link QSearchMovePicker} for staged move generation:
     * <ul>
     *   <li>Not in check: TT move (if capture) → generate captures → yield captures</li>
     *   <li>In check: TT move → generate all evasions → yield evasions</li>
     * </ul>
     * This avoids per-move TT probes and enables early cutoffs before full generation.
     *
     * <p>Probes the transposition table before stand-pat evaluation. A TT hit can
     * produce an early cutoff that skips NNUE evaluation and move generation entirely.
     * Results are <b>not</b> stored back into the TT from qsearch.</p>
     *
     * @param alpha         max score guaranteed for the position's active player
     * @param beta          max score guaranteed for the position's active player
     * @param position      position to search
     * @param searchContext per-search scratch space (move buffer, scores)
     * @param sharedTables  shared tables (threefold)
     * @param ply           count
     * @return score
     */
    public static int quiescenceSearch(int alpha, int beta, Position position, SearchContext searchContext, SharedTables sharedTables, int ply) {

        //=============== Check transposition table ===============
        // Probe with requiredDepth = 0 so any entry for this position qualifies.
        // Since qsearch does not store into the TT, hits come from main-search entries
        // (depth >= 1) that already incorporate a full qsearch result.
        int ttMove = 0;
        /*
        long ttPacked = (sharedTables.tt != null) ? sharedTables.tt.probe(position.zobristHash, 0) : 0;
        if (ttPacked != 0) {
            int ttScore = TranspositionTable.unpackScore(ttPacked);
            int nodeType = TranspositionTable.unpackNodeType(ttPacked);
            ttMove = TranspositionTable.unpackBestMove(ttPacked);

            ttScore = scoreFromTT(ttScore, ply);

            if (nodeType == NodeType.EXACT) {
                return ttScore;
            } else if (nodeType == NodeType.LOWER_BOUND) {
                alpha = Math.max(alpha, ttScore);
            } else { // UPPER_BOUND
                beta = Math.min(beta, ttScore);
            }

            if (alpha >= beta) {
                return ttScore;
            }
        }
        */

        //=============== Stand-pat evaluation ===============
        int standPat = position.evaluator.computeOutput(position.activePlayer);

        int bestValue = standPat;

        // Stand-pat cutoffs only apply when NOT in check because when in check, we can't, "stand pat", we have to get out of check.
        if (!position.inCheck) {
            if (standPat >= beta)
                return bestValue;

            if (alpha < standPat)
                alpha = standPat;



        } else {
            bestValue = NEG_INFINITY;
        }


        QSearchMovePicker picker = searchContext.qSearchMovePickers[ply];
        picker.init(position, searchContext, ttMove, position.inCheck);

        int move;
        while ((move = picker.nextMove()) != 0) {
            // =============== SKIP LOSING CAPTURES ===============
            //  - Skip all captures that aren't good
            //  - Don't skip if in check to avoid skipping forced captures
            /*
            if (!position.inCheck && searchContext.see.see(move, position) < 0) {
                continue;
            }
            */
            boolean deltaPruningCandidate = !position.inCheck
                    && !MoveEncoding.getIsPromotion(move)
                    && standPat + MoveOrder.PIECE_VALUES[MoveEncoding.getCapturedPiece(move)] + DELTA_MARGIN < alpha;


            // "open" the position
            position.makeMove(move);
            // Checking captures can exceed their immediate material gain, so they are
            // exempted after makeMove has established the resulting check state.
            if (deltaPruningCandidate && !position.inCheck) {
                position.unMakeMove(move);
                continue;
            }

            sharedTables.threeFoldTable.addPosition(position.zobristHash, move);

            // compute the score
            int score;
            try {
                score = -quiescenceSearch(-beta, -alpha, position, searchContext, sharedTables, ply + 1);
            } finally { // "close" the position
                sharedTables.threeFoldTable.popPosition();
                position.unMakeMove(move);
            }

            bestValue = Math.max(bestValue, score);

            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                picker.restoreBuffer();
                return bestValue;
            }
        }

        // =============== Mate detection ===============
        // If in check and no legal moves were yielded, it's checkmate
        if (position.inCheck && picker.moveCount() == 0) {
            picker.restoreBuffer();
            return -(MATED_VALUE - ply);
        }

        picker.restoreBuffer();
        return bestValue;
    }
}
