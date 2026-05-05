package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;

/**
 * Staged move picker for quiescence search nodes. Similar to {@link MovePicker}
 * but simplified: no killer stages, and the not-in-check path only generates captures.
 *
 * <h3>Stages (not in check)</h3>
 * <ol>
 *   <li>TT_MOVE, yield TT best move if it's a legal capture</li>
 *   <li>GEN_CAPTURES, generate captures into buffer, score with SEE</li>
 *   <li>CAPTURES, selection-sort yield one capture at a time (skip TT move)</li>
 *   <li>DONE, return 0</li>
 * </ol>
 *
 * <h3>Stages (in check)</h3>
 * <ol>
 *   <li>TT_MOVE, yield TT best move if legal (any move type)</li>
 *   <li>GEN_EVASIONS, generate all evasions, score with {@link MoveOrder#scoreEvasionMoves}</li>
 *   <li>EVASIONS, selection-sort yield one at a time (skip TT move)</li>
 *   <li>DONE, return 0</li>
 * </ol>
 *
 * <p>{@code position.pinnedBB} is saved/restored on a {@code FixedSizeLongStack} inside
 * {@code Position.makeMove}/{@code unmakeMove}, so pins computed once in {@link #init}
 * survive child searches without recomputation.</p>
 */
public class QSearchMovePicker {

    // ===== Not-in-check stages =====
    private static final int STAGE_TT_MOVE       = 0;
    private static final int STAGE_GEN_CAPTURES   = 1;
    private static final int STAGE_CAPTURES       = 2;
    private static final int STAGE_DONE           = 3;

    // ===== In-check stages =====
    private static final int STAGE_CHECK_TT_MOVE  = 10;
    private static final int STAGE_GEN_EVASIONS   = 11;
    private static final int STAGE_EVASIONS       = 12;
    private static final int STAGE_CHECK_DONE     = 13;

    // ===== Per-node state =====
    private Position position;
    private SearchContext searchContext;
    private int ttMove;

    private int stage;
    private int initialFirstNonMove;

    // Buffer region tracking
    private int regionStart;
    private int regionEnd;
    private int regionIndex;

    // Total legal moves yielded (for mate detection)
    private int moveCount;


    /**
     * Initializes this picker for one qsearch node.
     * Calls {@link MoveGenerator#computePins} once, pins survive child searches
     * because {@code Position.makeMove}/{@code unmakeMove} save/restore pinnedBB on a stack.
     */
    public void init(Position position, SearchContext searchContext, int ttMove, boolean inCheck) {
        this.position = position;
        this.searchContext = searchContext;
        this.ttMove = ttMove;
        this.initialFirstNonMove = searchContext.firstNonMove;
        this.moveCount = 0;

        // Compute pins once, survives child searches via Position's pinnedBBStack
        MoveGenerator.computePins(position);

        this.stage = inCheck ? STAGE_CHECK_TT_MOVE : STAGE_TT_MOVE;
    }


    /**
     * Returns the next move to search, or 0 when all moves are exhausted.
     */
    public int nextMove() {
        while (true) {
            switch (stage) {

                // =============== NOT-IN-CHECK PATH ===============

                case STAGE_TT_MOVE:
                    stage = STAGE_GEN_CAPTURES;
                    // Only yield TT move if it's a capture (qsearch not-in-check only searches captures)
                    if (ttMove != 0
                            && (MoveEncoding.getIsCapture(ttMove) || MoveEncoding.getIsEP(ttMove))
                            && MoveGenerator.isMoveLegal(position, ttMove)) {
                        moveCount++;
                        return ttMove;
                    }
                    break;

                case STAGE_GEN_CAPTURES:
                    regionStart = searchContext.firstNonMove;
                    searchContext.firstNonMove = MoveGenerator.generateCapturesNoPins(
                            position, searchContext.moveBuffer, searchContext.firstNonMove);
                    regionEnd = searchContext.firstNonMove;
                    regionIndex = regionStart;
                    MoveOrder.scoreLoudMoves(position, searchContext, regionStart, regionEnd);
                    stage = STAGE_CAPTURES;
                    break;

                case STAGE_CAPTURES:
                    while (regionIndex < regionEnd) {
                        MoveOrder.bestMoveFirst(searchContext, regionIndex, regionEnd);
                        int capMove = searchContext.moveBuffer[regionIndex];
                        regionIndex++;
                        if (capMove == ttMove) continue; // already yielded
                        moveCount++;
                        return capMove;
                    }
                    stage = STAGE_DONE;
                    break;

                case STAGE_DONE:
                    return 0;

                // =============== IN-CHECK PATH ===============

                case STAGE_CHECK_TT_MOVE:
                    stage = STAGE_GEN_EVASIONS;
                    if (ttMove != 0 && MoveGenerator.isMoveLegal(position, ttMove)) {
                        moveCount++;
                        return ttMove;
                    }
                    break;

                case STAGE_GEN_EVASIONS:
                    regionStart = searchContext.firstNonMove;
                    searchContext.firstNonMove = MoveGenerator.generateAllMovesNoPins(
                            position, searchContext.moveBuffer, searchContext.firstNonMove);
                    regionEnd = searchContext.firstNonMove;
                    regionIndex = regionStart;
                    MoveOrder.scoreEvasionMoves(position, searchContext, regionStart, regionEnd);
                    stage = STAGE_EVASIONS;
                    break;

                case STAGE_EVASIONS:
                    while (regionIndex < regionEnd) {
                        MoveOrder.bestMoveFirst(searchContext, regionIndex, regionEnd);
                        int evasionMove = searchContext.moveBuffer[regionIndex];
                        regionIndex++;
                        if (evasionMove == ttMove) continue; // already yielded
                        moveCount++;
                        return evasionMove;
                    }
                    stage = STAGE_CHECK_DONE;
                    break;

                case STAGE_CHECK_DONE:
                    return 0;
            }
        }
    }

    /**
     * Resets {@code searchContext.firstNonMove} to the value it had before this picker
     * started using the buffer. Must be called when the node is done.
     */
    public void restoreBuffer() {
        searchContext.firstNonMove = initialFirstNonMove;
    }

    /**
     * Returns the total number of legal moves yielded so far.
     * Used for mate detection when {@link #nextMove()} returns 0 and in check.
     */
    public int moveCount() {
        return moveCount;
    }
}
