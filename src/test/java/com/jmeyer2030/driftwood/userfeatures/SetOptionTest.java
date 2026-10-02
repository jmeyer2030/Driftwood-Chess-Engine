package com.jmeyer2030.driftwood.userfeatures;

import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.board.ThreeFoldTable;
import com.jmeyer2030.driftwood.search.TranspositionTable;
import com.jmeyer2030.driftwood.userfeatures.commands.initial.UCIMode;
import com.jmeyer2030.driftwood.userfeatures.commands.uci.SetOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class SetOptionTest {

    @Test
    @DisplayName("UCI mode prints Hash option when uci command is executed")
    void uciModePrintsHashOption_whenUciCommandExecuted() {
        // Arrange
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChessEngine chessEngine = new ChessEngine(new UciOutput(new PrintStream(output)));
        CommandHandler commandHandler = new CommandHandler(chessEngine);
        UCIMode uciMode = new UCIMode(chessEngine, commandHandler);
        uciMode.execute(new String[0]);

        // Assert
        String expectedHashOption = "option name Hash type spin default "
                + chessEngine.hashSizeMb
                + " min "
                + TranspositionTable.MIN_MB_SIZE
                + " max "
                + TranspositionTable.MAX_MB_SIZE;
        assertTrue(output.toString().contains(expectedHashOption));
    }

    @Test
    @DisplayName("SetOption updates hash size and rebuilds TT when Hash value is valid")
    void setOptionUpdatesHashSizeAndRebuildsTT_whenHashValueIsValid() {
        // Arrange
        ChessEngine chessEngine = new ChessEngine();
        chessEngine.sharedTables = new SharedTables(1);
        ThreeFoldTable originalThreeFoldTable = chessEngine.sharedTables.threeFoldTable;
        TranspositionTable originalTT = chessEngine.sharedTables.tt;
        SetOption setOption = new SetOption(chessEngine);
        String[] arguments = "name Hash value 2".split(" ");

        // Act
        setOption.execute(arguments);

        // Assert
        assertEquals(2, chessEngine.hashSizeMb);
        assertNotSame(originalTT, chessEngine.sharedTables.tt);
        assertSame(originalThreeFoldTable, chessEngine.sharedTables.threeFoldTable);
    }

    @Test
    @DisplayName("SetOption leaves hash size unchanged when Hash value is outside spin range")
    void setOptionLeavesHashSizeUnchanged_whenHashValueOutsideSpinRange() {
        // Arrange
        ChessEngine chessEngine = new ChessEngine();
        chessEngine.hashSizeMb = 256;
        SetOption setOption = new SetOption(chessEngine);
        String[] arguments = ("name Hash value " + (TranspositionTable.MAX_MB_SIZE + 1)).split(" ");

        // Act
        setOption.execute(arguments);

        // Assert
        assertEquals(256, chessEngine.hashSizeMb);
    }
}
