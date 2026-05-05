package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;

/**
 * Staged move picker that lazily produces moves in phases for a single search node.
 * If an early move (e.g. TT move or capture) causes a beta cutoff, later stages
 * (quiet generation, quiet scoring) are never executed.
 *
 * <h3>Stages (not in check)</h3>
 * <ol>
 *   <li>TT_MOVE, yield TT best move (validated with {@link MoveGenerator#isMoveLegal})</li>
 *   <li>GEN_CAPTURES, generate captures into buffer, score with SEE</li>
 *   <li>CAPTURES, selection-sort yield one capture at a time (skip TT move)</li>
 *   <li>KILLER_1, yield killer[0][ply] if legal, quiet, and not TT move</li>
 *   <li>KILLER_2, yield killer[1][ply] if legal, quiet, and not TT/killer1</li>
 *   <li>GEN_QUIETS, generate quiets into buffer, score with history</li>
 *   <li>QUIETS, selection-sort yield one quiet at a time (skip TT/killers)</li>
 *   <li>DONE, return 0</li>
 * </ol>
 *
 * <h3>Stages (in check)</h3>
 * <ol>
 *   <li>TT_MOVE, same as above</li>
 *   <li>GEN_EVASIONS, generate all evasions, score with full scoring</li>
 *   <li>EVASIONS, selection-sort yield one at a time (skip TT move)</li>
 *   <li>DONE, return 0</li>
 * </ol>
 *
 * <h3>Pin preservation</h3>
 * <p>{@code position.pinnedBB} is saved/restored on a {@code FixedSizeLongStack} inside
 * {@code Position.makeMove}/{@code unmakeMove}, so pins computed once in {@link #init}
 * survive child searches without recomputation.</p>
 */
public class MovePicker {

    // ===== Not-in-check stages =====
    private static final int STAGE_TT_MOVE        = 0;
    private static final int STAGE_GEN_CAPTURES   = 1;
    private static final int STAGE_CAPTURES       = 2;
    private static final int STAGE_KILLER_1       = 3;
    private static final int STAGE_KILLER_2       = 4;
    private static final int STAGE_GEN_QUIETS     = 5;
    private static final int STAGE_QUIETS         = 6;
    private static final int STAGE_DONE           = 7;

    // ===== In-check stages =====
    private static final int STAGE_CHECK_TT_MOVE  = 10;
    private static final int STAGE_GEN_EVASIONS   = 11;
    private static final int STAGE_EVASIONS       = 12;
    private static final int STAGE_CHECK_DONE     = 13;

    // ===== Per-node state =====
    private Position position;
    private SearchContext searchContext;
    private SharedTables sharedTables;
    private int ply;
    private int ttMove;
    private int killer1;
    private int killer2;

    private int stage;
    private int initialFirstNonMove;

    // Buffer region tracking for captures
    private int captureStart;
    private int captureEnd;
    private int captureIndex;

    // Buffer region tracking for quiets (or evasions in check path)
    private int quietStart;
    private int quietEnd;
    private int quietIndex;

    // Total legal moves yielded (for mate/stalemate detection)
    private int moveCount;


    /**
     * No-arg constructor for per-ply pooling. Call {@link #init} before first use.
     */
    public MovePicker() {}

    /**
     * Convenience constructor that calls {@link #init}.
     */
    public MovePicker(Position position, SearchContext searchContext, SharedTables sharedTables,
                      int ply, int ttMove, boolean inCheck) {
        init(position, searchContext, sharedTables, ply, ttMove, inCheck);
    }

    /**
     * Initializes this picker for one search node.
     * Calls {@link MoveGenerator#computePins} once, pins survive child searches
     * because {@code Position.makeMove}/{@code unmakeMove} save/restore pinnedBB on a stack.
     */
    public void init(Position position, SearchContext searchContext, SharedTables sharedTables,
                     int ply, int ttMove, boolean inCheck) {
        this.position = position;
        this.searchContext = searchContext;
        this.sharedTables = sharedTables;
        this.ply = ply;
        this.ttMove = ttMove;
        this.initialFirstNonMove = searchContext.firstNonMove;
        this.moveCount = 0;

        // Compute pins once, survives child searches via Position's pinnedBBStack
        MoveGenerator.computePins(position);

        // Get killer moves for this ply
        this.killer1 = searchContext.killerMoves.killerMoves[0][ply];
        this.killer2 = searchContext.killerMoves.killerMoves[1][ply];

        // Set initial stage based on check status
        this.stage = inCheck ? STAGE_CHECK_TT_MOVE : STAGE_TT_MOVE;
    }


    /**
     * Returns the next move to search, or 0 when all moves are exhausted.
     * Advances through stages internally.
     */
    public int nextMove() {
        while (true) {
            switch (stage) {

                // =============== NOT-IN-CHECK PATH ===============

                case STAGE_TT_MOVE:
                    stage = STAGE_GEN_CAPTURES;
                    if (ttMove != 0 && MoveGenerator.isMoveLegal(position, ttMove)) {
                        moveCount++;
                        return ttMove;
                    }
                    break;

                case STAGE_GEN_CAPTURES:
                    captureStart = searchContext.firstNonMove;
                    searchContext.firstNonMove = MoveGenerator.generateCapturesNoPins(
                            position, searchContext.moveBuffer, searchContext.firstNonMove);
                    captureEnd = searchContext.firstNonMove;
                    captureIndex = captureStart;
                    MoveOrder.scoreLoudMoves(position, searchContext, captureStart, captureEnd);
                    stage = STAGE_CAPTURES;
                    break;

                case STAGE_CAPTURES:
                    while (captureIndex < captureEnd) {
                        MoveOrder.bestMoveFirst(searchContext, captureIndex, captureEnd);
                        int capMove = searchContext.moveBuffer[captureIndex];
                        captureIndex++;
                        if (capMove == ttMove) continue; // already yielded
                        moveCount++;
                        return capMove;
                    }
                    stage = STAGE_KILLER_1;
                    break;

                case STAGE_KILLER_1:
                    stage = STAGE_KILLER_2;
                    if (killer1 != 0
                            && killer1 != ttMove
                            && !MoveEncoding.getIsCapture(killer1)
                            && !MoveEncoding.getIsEP(killer1)
                            && MoveGenerator.isMoveLegal(position, killer1)) {
                        moveCount++;
                        return killer1;
                    }
                    break;

                case STAGE_KILLER_2:
                    stage = STAGE_GEN_QUIETS;
                    if (killer2 != 0
                            && killer2 != ttMove
                            && killer2 != killer1
                            && !MoveEncoding.getIsCapture(killer2)
                            && !MoveEncoding.getIsEP(killer2)
                            && MoveGenerator.isMoveLegal(position, killer2)) {
                        moveCount++;
                        return killer2;
                    }
                    break;

                case STAGE_GEN_QUIETS:
                    quietStart = searchContext.firstNonMove;
                    searchContext.firstNonMove = MoveGenerator.generateQuietsNoPins(
                            position, searchContext.moveBuffer, searchContext.firstNonMove);
                    quietEnd = searchContext.firstNonMove;
                    quietIndex = quietStart;
                    MoveOrder.scoreQuietMoves(position, searchContext, quietStart, quietEnd);
                    stage = STAGE_QUIETS;
                    break;

                case STAGE_QUIETS:
                    while (quietIndex < quietEnd) {
                        MoveOrder.bestMoveFirst(searchContext, quietIndex, quietEnd);
                        int quietMove = searchContext.moveBuffer[quietIndex];
                        quietIndex++;
                        // Skip moves already yielded in earlier stages
                        if (quietMove == ttMove || quietMove == killer1 || quietMove == killer2) continue;
                        moveCount++;
                        return quietMove;
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
                    captureStart = searchContext.firstNonMove;
                    searchContext.firstNonMove = MoveGenerator.generateAllMovesNoPins(
                            position, searchContext.moveBuffer, searchContext.firstNonMove);
                    captureEnd = searchContext.firstNonMove;
                    captureIndex = captureStart;
                    MoveOrder.scoreEvasionMoves(position, searchContext,
                            captureStart, captureEnd);
                    stage = STAGE_EVASIONS;
                    break;

                case STAGE_EVASIONS:
                    while (captureIndex < captureEnd) {
                        MoveOrder.bestMoveFirst(searchContext, captureIndex, captureEnd);
                        int evasionMove = searchContext.moveBuffer[captureIndex];
                        captureIndex++;
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
     * Used for mate/stalemate detection when {@link #nextMove()} returns 0.
     */
    public int moveCount() {
        return moveCount;
    }
}
