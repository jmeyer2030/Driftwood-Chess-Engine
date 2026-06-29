package com.jmeyer2030.driftwood.userfeatures.commands.uci;

import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.search.SearchContext;
import com.jmeyer2030.driftwood.userfeatures.ChessEngine;
import com.jmeyer2030.driftwood.userfeatures.commands.Command;

public class UCINewGame implements Command {
    public ChessEngine chessEngine;

    public UCINewGame(ChessEngine chessEngine) {
        this.chessEngine = chessEngine;
    }

    @Override
    public void execute(String[] arguments) {
        chessEngine.position = new Position();
        chessEngine.sharedTables = new SharedTables(chessEngine.hashSizeMb);
        chessEngine.searchContext = new SearchContext();
        if (chessEngine.debugActive) {
            System.err.println("info string New game created successfully");
        }
    }
}
