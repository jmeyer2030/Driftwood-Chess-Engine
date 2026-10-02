package com.jmeyer2030.driftwood.userfeatures.commands.uci;

import com.jmeyer2030.driftwood.search.Ponder;
import com.jmeyer2030.driftwood.search.Search;
import com.jmeyer2030.driftwood.search.TimeManagement;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;
import com.jmeyer2030.driftwood.userfeatures.ChessEngine;
import com.jmeyer2030.driftwood.userfeatures.commands.Command;
import com.jmeyer2030.driftwood.board.Position;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


public class Go implements Command {

    // List of subcommands, e.g. ponder, wtime, btime, etc.
    public HashMap<String, String> subCommands;

    // List of moves if "searchmoves" is used
    public List<String> searchMoves;

    public ChessEngine chessEngine;

    @SuppressWarnings("SpellCheckingInspection")
    public Go(ChessEngine chessEngine) {
        this.chessEngine = chessEngine;

        // initialize starting fields
        this.startingParams();
    }

    /**
     * Executes the command
     */
    @Override
    public void execute(String[] arguments) {
        // Add arguments to fields
        this.fillSearchTerms(arguments);

        this.executeCommand();

        // Reset fields to initial state
        this.startingParams();
    }

    /**
     * Parses arguments so that they can be used for search
     *
     * @param arguments from the input
     */
    public void fillSearchTerms(String[] arguments) {
        String currentSubCommand = null;
        for (String argument : arguments) {
            if (subCommands.containsKey(argument)) { // If we are currently on a subcommand
                currentSubCommand = argument;

                // Handle "infinite" with 0 parameters
                if (argument.equals("infinite")) {
                    subCommands.put("infinite", "true");
                }

                if (argument.equals("ponder")) {
                    subCommands.put("ponder", "true");
                }
            } else if ("searchmoves".equals(currentSubCommand)) {
                searchMoves.add(argument);
            } else {
                subCommands.put(currentSubCommand, argument);
            }
        }
    }

    /**
     * Resets internally stored command parameters
     * For use on initialization and after command is executed
     */
    private void startingParams() {
        this.searchMoves = new LinkedList<String>();
        this.subCommands = new HashMap<String, String>();

        subCommands.put("searchmoves", null);
        subCommands.put("ponder", null);
        subCommands.put("wtime", null);
        subCommands.put("btime", null);
        subCommands.put("winc", null);
        subCommands.put("binc", null);
        subCommands.put("movestogo", null);
        subCommands.put("depth", null);
        subCommands.put("nodes", null);
        subCommands.put("mate", null);
        subCommands.put("movetime", null);
        subCommands.put("infinite", null);
    }

    public void executeCommand() {
        if ("true".equals(subCommands.get("ponder"))) {
            executePonder();
        } else {
            executeSearch();
        }
    }

    /**
     * Executes the search based on stored parameters
     */
    public void executeSearch() {
        // Apply move to chessEngine's position
        applySearchMoves();
        Position position = chessEngine.position;

        // Get the time we are allocating to the search
        String activeTimeStr;
        if (position.activePlayer == 0) {
            activeTimeStr = subCommands.get("wtime");
        } else {
            activeTimeStr = subCommands.get("btime");
        }
        long time = Long.parseLong(activeTimeStr);
        long computeTime = TimeManagement.millisForMove(time, 0);

        Search.MoveValue moveValue = Search.iterativeDeepening(position, computeTime, chessEngine.searchContext, chessEngine.sharedTables, chessEngine.uciOutput);

        chessEngine.uciOutput.bestMove(moveValue.bestMove, chessEngine.searchContext.pvTable.getBestResponse());
    }

    public void executePonder() {
        Ponder.startPondering(chessEngine.position, chessEngine.searchContext, chessEngine.sharedTables);
    }

    public void applySearchMoves() {
        Position position = chessEngine.position;
        for (String lan : searchMoves) {
            int move = MoveGenerator.getMoveFromLAN(lan, position, chessEngine.searchContext.moveBuffer);
            chessEngine.sharedTables.applyMove(move, position);
        }
    }
}
