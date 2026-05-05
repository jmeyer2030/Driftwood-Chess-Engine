package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.config.GlobalConstants;

/**
 * Per-search scratch space and heuristic tables. Created once per {@code ucinewgame} and
 * reused across iterative-deepening iterations. Contains:
 * <ul>
 *   <li>Move buffer and parallel score array (shared across the search tree via index windows)</li>
 *   <li>Best-move array (one slot per ply)</li>
 *   <li>Killer moves, history heuristic, and triangular PV table</li>
 * </ul>
 *
 * <p>{@code firstNonMove} tracks the next free slot in {@code moveBuffer}. It is saved and
 * restored manually at each search node to implement move-window scoping.</p>
 */
public class SearchContext {
    public final int[] moveBuffer;
    public final int[] moveScores;
    public final int[] bestMoves;
    public int firstNonMove;
    public final KillerMoves killerMoves;
    public final HistoryHeuristic historyHeuristic;
    public final CaptureHistory captureHistory;
    public final TriangularPVTable pvTable;
    public final SEE see;

    // Per-ply pooled objects, avoids per-node heap allocation in the hot search loop
    public final MovePicker[] movePickers;
    public final QSearchMovePicker[] qSearchMovePickers;

    // Per-ply pooled quietsSearched tracking, avoids per-node array allocation in pvSearch
    public final int[][] quietsSearched;
    public final int[] numQuietsSearched;

    // Per-ply pooled capturesSearched tracking, for capture history penalization on beta cutoff
    public final int[][] capturesSearched;
    public final int[] numCapturesSearched;

    public SearchContext() {
        this.moveBuffer = new int[2048];
        this.moveScores = new int[2048];
        this.bestMoves = new int[GlobalConstants.MAX_PLY];
        this.firstNonMove = 0;
        this.killerMoves = new KillerMoves();
        this.historyHeuristic = new HistoryHeuristic();
        this.captureHistory = new CaptureHistory();
        this.pvTable = new TriangularPVTable();
        this.see = new SEE();

        this.movePickers = new MovePicker[GlobalConstants.MAX_PLY];
        this.qSearchMovePickers = new QSearchMovePicker[GlobalConstants.MAX_PLY];
        for (int i = 0; i < GlobalConstants.MAX_PLY; i++) {
            this.movePickers[i] = new MovePicker();
            this.qSearchMovePickers[i] = new QSearchMovePicker();
        }

        this.quietsSearched = new int[GlobalConstants.MAX_PLY][128];
        this.numQuietsSearched = new int[GlobalConstants.MAX_PLY];

        this.capturesSearched = new int[GlobalConstants.MAX_PLY][64];
        this.numCapturesSearched = new int[GlobalConstants.MAX_PLY];
    }
}

