package com.jmeyer2030.driftwood.userfeatures.commands.initial;

import com.jmeyer2030.driftwood.search.TranspositionTable;
import com.jmeyer2030.driftwood.userfeatures.CommandHandler;
import com.jmeyer2030.driftwood.userfeatures.ChessEngine;
import com.jmeyer2030.driftwood.userfeatures.commands.Command;

public class UCIMode implements Command {

    public ChessEngine chessEngine;
    public CommandHandler handler;

    public UCIMode(ChessEngine chessEngine, CommandHandler handler) {
        this.chessEngine = chessEngine;
        this.handler = handler;
    }

    /**
    * Sets commandMode to UCI
    * Sets handler to accept UCI commands
    * Prints ID
    * Sends uciok, confirming that the main.java.engine is ready for uci
    */
    @Override
    public void execute(String[] arguments) {
        handler.acceptUCICommands();
        chessEngine.getID().lines().forEach(chessEngine.uciOutput::line);
        chessEngine.uciOutput.line(String.format(
                "option name Hash type spin default %d min %d max %d",
                chessEngine.hashSizeMb,
                TranspositionTable.MIN_MB_SIZE,
                TranspositionTable.MAX_MB_SIZE));
        chessEngine.uciOutput.line("uciok");
    }
}
