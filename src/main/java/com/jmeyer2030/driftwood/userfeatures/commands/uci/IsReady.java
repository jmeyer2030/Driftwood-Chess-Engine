package com.jmeyer2030.driftwood.userfeatures.commands.uci;

import com.jmeyer2030.driftwood.userfeatures.ChessEngine;
import com.jmeyer2030.driftwood.userfeatures.commands.Command;

public class IsReady implements Command {
    public ChessEngine chessEngine;

    public IsReady(ChessEngine chessEngine) {
        this.chessEngine = chessEngine;
    }

    @Override
    public void execute(String[] arguments) {
        while (!chessEngine.isReady) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted while waiting for initialization.");
                return;
            }
        }
        chessEngine.uciOutput.line("readyok");
    }
}
