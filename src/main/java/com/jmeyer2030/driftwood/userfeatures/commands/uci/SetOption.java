package com.jmeyer2030.driftwood.userfeatures.commands.uci;

import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.search.TranspositionTable;
import com.jmeyer2030.driftwood.userfeatures.ChessEngine;
import com.jmeyer2030.driftwood.userfeatures.commands.Command;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SetOption implements Command {
    public ChessEngine chessEngine;
    private final Map<String, OptionHandler> optionHandlers;

    public SetOption(ChessEngine chessEngine) {
        this.chessEngine = chessEngine;
        this.optionHandlers = new HashMap<>();
        optionHandlers.put(normalizeOptionName("Hash"), this::setHashOption);
    }

    @Override
    public void execute(String[] arguments) {
        ParsedOption option = parseOption(arguments);
        if (option == null) {
            return;
        }

        OptionHandler optionHandler = optionHandlers.get(normalizeOptionName(option.name));
        if (optionHandler != null) {
            optionHandler.handle(option.value);
        }
    }

    private void setHashOption(String value) {
        int hashSizeMb;
        try {
            hashSizeMb = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return;
        }

        if (hashSizeMb < TranspositionTable.MIN_MB_SIZE || hashSizeMb > TranspositionTable.MAX_MB_SIZE) {
            return;
        }

        // Case that this is used mid-game
        chessEngine.hashSizeMb = hashSizeMb;
        if (chessEngine.sharedTables != null) {
            chessEngine.sharedTables = new SharedTables(hashSizeMb, chessEngine.sharedTables.threeFoldTable);
        }
    }

    private static ParsedOption parseOption(String[] arguments) {
        if (arguments == null || arguments.length < 2 || !"name".equalsIgnoreCase(arguments[0])) {
            return null;
        }

        int valueIndex = -1;
        for (int i = 1; i < arguments.length; i++) {
            if ("value".equalsIgnoreCase(arguments[i])) {
                valueIndex = i;
                break;
            }
        }

        if (valueIndex == 1 || valueIndex == arguments.length - 1) {
            return null;
        }

        int nameEnd = valueIndex == -1 ? arguments.length : valueIndex;
        String name = String.join(" ", java.util.Arrays.copyOfRange(arguments, 1, nameEnd));
        String value = valueIndex == -1
                ? ""
                : String.join(" ", java.util.Arrays.copyOfRange(arguments, valueIndex + 1, arguments.length));
        return new ParsedOption(name, value);
    }

    private static String normalizeOptionName(String optionName) {
        return optionName.toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface OptionHandler {
        void handle(String value);
    }

    private record ParsedOption(String name, String value) {
    }
}
