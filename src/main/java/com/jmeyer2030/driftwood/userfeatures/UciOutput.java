package com.jmeyer2030.driftwood.userfeatures;

import java.io.PrintStream;
import java.util.Objects;
import java.util.stream.Collectors;

import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.search.Search;
import com.jmeyer2030.driftwood.search.SearchIteration;
import com.jmeyer2030.driftwood.search.SearchListener;

/** Writes protocol responses independently of diagnostic logging. Does not own the stream. */
public final class UciOutput implements SearchListener {
    private final PrintStream out;

    public UciOutput(PrintStream out) {
        this.out = Objects.requireNonNull(out);
    }

    public synchronized void line(String response) {
        out.println(response);
        out.flush();
    }

    @Override
    public void onIterationCompleted(SearchIteration iteration) {
        int score = iteration.score();
        String formattedScore;
        if (Math.abs(score) >= Search.MATED_SCORE) {
            int moves = (Search.MATED_VALUE - Math.abs(score) + 1) / 2;
            formattedScore = "mate " + (score < 0 ? -moves : moves);
        } else {
            formattedScore = "cp " + score;
        }
        String pv = iteration.principalVariation().stream()
                .map(MoveEncoding::getLAN).collect(Collectors.joining(" "));
        line("info depth " + iteration.depth() + " score " + formattedScore
                + (pv.isEmpty() ? "" : " pv " + pv));
    }

    public void bestMove(int move, int ponder) {
        line("bestmove " + (move == 0 ? "0000" : MoveEncoding.getLAN(move))
                + (ponder == 0 ? "" : " ponder " + MoveEncoding.getLAN(ponder)));
    }
}
