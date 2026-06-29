package com.jmeyer2030.driftwood.userfeatures;

import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.search.SearchContext;

/**
* Main class for the chess engine.
* Configures options, and starts accepting commands
*/
public class ChessEngine {

    // Engine Information:
    public final String ENGINE_NAME = "DriftWood 5.0";
    public final String AUTHOR = "Joshua Meyer";

    // Engine Settings:
    public boolean debugActive;
    public int hashSizeMb = 256;

    // Engine state
    public boolean isReady = true; // if ready to search or add a position, this should be true.

    // Position state:
    public Position position;
    public SharedTables sharedTables;
    public SearchContext searchContext;

    public ChessEngine() {
    }

    /**
    * Begins chess engine, usable from console
    */
    public static void main(String[] args) {
        ChessEngine chessEngine = new ChessEngine();
        chessEngine.run();
    }

    /**
    * Begins input loop
    */
    private void run() {
        CommandHandler commandHandler = new CommandHandler(this);

        commandHandler.inputLoop();
    }

    /**
    * Returns chess engine information
    */
    public String getID() {
        return "id name " + ENGINE_NAME + "\nid author " + AUTHOR + "\n";
    }
}
