package com.jmeyer2030.driftwood.userfeatures.commands.uci;

import com.jmeyer2030.driftwood.userfeatures.ChessEngine;
import com.jmeyer2030.driftwood.userfeatures.commands.Command;

public class Register implements Command {
    public ChessEngine chessEngine;

    public Register(ChessEngine chessEngine) {
        this.chessEngine = chessEngine;
    }

    @Override
    public void execute(String[] arguments) {
        chessEngine.uciOutput.line("register later");
    }
}
