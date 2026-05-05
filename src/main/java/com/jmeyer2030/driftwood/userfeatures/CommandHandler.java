package com.jmeyer2030.driftwood.userfeatures;

import com.jmeyer2030.driftwood.userfeatures.commands.Command;
import com.jmeyer2030.driftwood.userfeatures.commands.initial.PerftCMD;
import com.jmeyer2030.driftwood.userfeatures.commands.initial.SayHello;
import com.jmeyer2030.driftwood.userfeatures.commands.initial.UCIMode;
import com.jmeyer2030.driftwood.userfeatures.commands.uci.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
/*

*/
public class CommandHandler {
    private final Map<String, Command> initialCommands;
    private final Map<String, Command> uciCommands;
    private final Map<String, Command> currentCommands;

    /**
    * Initializes lists of commands
    */
    @SuppressWarnings("SpellCheckingInspection")
    public CommandHandler(ChessEngine chessEngine) {
        uciCommands = new HashMap<>();
        uciCommands.put("debug", new Debug(chessEngine));
        uciCommands.put("go", new Go(chessEngine));
        uciCommands.put("isready", new IsReady(chessEngine));
        uciCommands.put("ponderhit", new PonderHit(chessEngine));
        uciCommands.put("quit", new Quit(chessEngine));
        uciCommands.put("register", new Register(chessEngine));
        uciCommands.put("setoption", new SetOption(chessEngine));
        uciCommands.put("position", new SetPosition(chessEngine));
        uciCommands.put("stop", new Stop(chessEngine));
        uciCommands.put("ucinewgame", new UCINewGame(chessEngine));

        initialCommands = new HashMap<String, Command>();
        initialCommands.put("sayhello", new SayHello());
        initialCommands.put("uci", new UCIMode(chessEngine, this));
        initialCommands.put("perft", new PerftCMD(chessEngine));

        currentCommands = initialCommands;
    }


    /**
    * Loops infinitely reading from console input and executing these commands
    */
    public void inputLoop() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            // Read input and separate by spaces
            String input;
            try {
                input = scanner.nextLine();
            } catch (NoSuchElementException e) {
                // stdin was closed (e.g. parent process terminated), exit gracefully
                break;
            }
            String[] parts = input.split("\\s+");

            String[] arguments;

            // Handle different sizes of inputs
            if (parts.length == 0 ) {
                System.out.println("No Input parts detected");
                continue;
            } else if (parts.length == 1) {
                arguments = new String[0];
            } else {
                arguments = Arrays.copyOfRange(parts, 1, parts.length);
            }

            // Execute the command. First word is the command, following are arguments which are passed to the command
            executeCommand(parts[0], arguments);
        }
    }

    /**
    * Executes a command if the command is contained in our command list
    * @param command first token of the input
    * @param arguments all other tokens
    */
    public void executeCommand(String command, String[] arguments) {
        Command action = currentCommands.get(command.toLowerCase());
        if (action != null) {
            action.execute(arguments);
        } else {
            System.out.println("Unknown command: " + command);
        }
    }

    /**
    * Modifies the list of acceptable commands to allow UCI
    */
    public void acceptUCICommands() {
        currentCommands.putAll(uciCommands);
    }
}
