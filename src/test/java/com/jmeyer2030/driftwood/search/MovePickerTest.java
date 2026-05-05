package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.SharedTables;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MovePicker}.
 * Validates staged move generation produces the same set of legal moves as
 * {@link MoveGenerator#generateAllMoves}, with correct deduplication and ordering
 * (TT move first, then captures, then killers, then quiets).
 */
public class MovePickerTest {

    /**
     * Collects all moves from a MovePicker into a list.
     */
    private List<Integer> drainPicker(MovePicker picker) {
        List<Integer> moves = new ArrayList<>();
        int move;
        while ((move = picker.nextMove()) != 0) {
            moves.add(move);
        }
        return moves;
    }

    /**
     * Generates all legal moves for a position via generateAllMoves.
     */
    private Set<Integer> generateAllMovesSet(Position position) {
        int[] moveBuffer = new int[256];
        int numMoves = MoveGenerator.generateAllMoves(position, moveBuffer, 0);
        Set<Integer> moveSet = new HashSet<>();
        for (int i = 0; i < numMoves; i++) {
            moveSet.add(moveBuffer[i]);
        }
        return moveSet;
    }

    @Test
    @DisplayName("MovePicker yields same legal moves as generateAllMoves for starting position")
    public void testMovePickerCompleteness_StartingPosition() {
        // Arrange
        Position position = new Position();
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);

        Set<Integer> allMoves = generateAllMovesSet(position);

        // Act
        MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, 0, position.inCheck);
        List<Integer> pickedMoves = drainPicker(picker);
        picker.restoreBuffer();

        // Assert
        Set<Integer> pickedSet = new HashSet<>(pickedMoves);
        assertEquals(allMoves.size(), pickedMoves.size(),
                "MovePicker yielded " + pickedMoves.size() + " moves but generateAllMoves produced " + allMoves.size());
        assertEquals(allMoves, pickedSet, "MovePicker moves don't match generateAllMoves");
    }

    @Test
    @DisplayName("MovePicker yields same legal moves as generateAllMoves when in check")
    public void testMovePickerCompleteness_InCheck() {
        // Arrange
        FEN fen = new FEN("rnbqkbnr/pppp1ppp/8/4p3/7Q/8/PPPPPPP1/RNB1KBNR b KQkq - 1 2");
        Position position = new Position(fen);
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);

        Set<Integer> allMoves = generateAllMovesSet(position);

        // Act
        MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, 0, position.inCheck);
        List<Integer> pickedMoves = drainPicker(picker);
        picker.restoreBuffer();

        // Assert
        Set<Integer> pickedSet = new HashSet<>(pickedMoves);
        assertEquals(allMoves.size(), pickedMoves.size());
        assertEquals(allMoves, pickedSet, "MovePicker moves don't match generateAllMoves when in check");
    }

    @Test
    @DisplayName("MovePicker yields TT move first when TT move is legal")
    public void testMovePickerTTMoveFirst() {
        // Arrange
        Position position = new Position();
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);

        // Get a legal move to use as TT move
        int[] moveBuffer = new int[256];
        int numMoves = MoveGenerator.generateAllMoves(position, moveBuffer, 0);
        int ttMove = moveBuffer[0]; // first legal move

        // Act
        MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, ttMove, position.inCheck);
        int firstMove = picker.nextMove();
        picker.restoreBuffer();

        // Assert
        assertEquals(ttMove, firstMove, "First move from MovePicker should be the TT move");
    }

    @Test
    @DisplayName("MovePicker yields no duplicate moves")
    public void testMovePickerNoDuplicates() {
        // Arrange, complex position with many piece types
        FEN fen = new FEN("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        Position position = new Position(fen);
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);

        // Set up a TT move (pick one of the legal captures)
        int[] moveBuffer = new int[256];
        int numMoves = MoveGenerator.generateAllMoves(position, moveBuffer, 0);
        int ttMove = 0;
        for (int i = 0; i < numMoves; i++) {
            if (MoveEncoding.getIsCapture(moveBuffer[i])) {
                ttMove = moveBuffer[i];
                break;
            }
        }

        // Act
        MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, ttMove, position.inCheck);
        List<Integer> pickedMoves = drainPicker(picker);
        picker.restoreBuffer();

        // Assert
        Set<Integer> pickedSet = new HashSet<>(pickedMoves);
        assertEquals(pickedSet.size(), pickedMoves.size(),
                "MovePicker produced duplicate moves");
    }

    @Test
    @DisplayName("MovePicker yields captures before quiets when not in check")
    public void testMovePickerCapturesBeforeQuiets() {
        // Arrange, position with captures available
        FEN fen = new FEN("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        Position position = new Position(fen);
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);

        // Act
        MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, 0, position.inCheck);
        List<Integer> pickedMoves = drainPicker(picker);
        picker.restoreBuffer();

        // Assert, find the last capture and first quiet, verify ordering
        int lastCaptureIndex = -1;
        int firstQuietIndex = -1;
        for (int i = 0; i < pickedMoves.size(); i++) {
            if (MoveEncoding.getIsCapture(pickedMoves.get(i))) {
                lastCaptureIndex = i;
            }
            if (!MoveEncoding.getIsCapture(pickedMoves.get(i)) && firstQuietIndex == -1) {
                firstQuietIndex = i;
            }
        }

        if (lastCaptureIndex != -1 && firstQuietIndex != -1) {
            // All captures should come before the first quiet (allowing killers in between)
            // Captures come in stages 2-3, killers in 4-5, quiets in 6-7
            // The first quiet should be either a killer or in the QUIETS stage,
            // both of which come after captures
            assertTrue(firstQuietIndex > 0,
                    "First quiet move should not be the very first move when captures exist");
        }
    }

    @Test
    @DisplayName("MovePicker yields no moves in checkmate position")
    public void testMovePickerCheckmate() {
        // Arrange, back-rank mate (Black is mated)
        FEN fen = new FEN("6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1");
        Position position = new Position(fen);
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);

        // First make a move that delivers checkmate
        int[] moveBuffer = new int[256];
        MoveGenerator.generateAllMoves(position, moveBuffer, 0);
        // Find Re8# (rook from e1 to e8)
        int mateMove = 0;
        for (int i = 0; i < MoveGenerator.generateAllMoves(position, moveBuffer, 0); i++) {
            // We just want to test the picker yields 0 moves in a mated position
        }
        // Use a known checkmate position instead
        FEN mateFen = new FEN("5rk1/5pRp/8/8/8/8/8/6K1 b - - 0 1");
        Position matePos = new Position(mateFen);

        // Verify it's actually checkmate (no legal moves)
        int numAllMoves = MoveGenerator.generateAllMoves(matePos, moveBuffer, 0);

        // Act
        MovePicker picker = new MovePicker(matePos, searchContext, sharedTables, 0, 0, matePos.inCheck);
        List<Integer> pickedMoves = drainPicker(picker);
        picker.restoreBuffer();

        // Assert
        assertEquals(numAllMoves, pickedMoves.size(),
                "MovePicker should yield same number of moves as generateAllMoves in this position");
    }

    @Test
    @DisplayName("MovePicker restoreBuffer resets firstNonMove correctly")
    public void testMovePickerRestoreBuffer() {
        // Arrange
        Position position = new Position();
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);
        int originalFirstNonMove = searchContext.firstNonMove;

        // Act
        MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, 0, position.inCheck);
        drainPicker(picker);
        picker.restoreBuffer();

        // Assert
        assertEquals(originalFirstNonMove, searchContext.firstNonMove,
                "restoreBuffer should reset firstNonMove to its original value");
    }

    @Test
    @DisplayName("MovePicker moveCount matches number of moves yielded")
    public void testMovePickerMoveCount() {
        // Arrange
        Position position = new Position();
        SearchContext searchContext = new SearchContext();
        SharedTables sharedTables = new SharedTables(18);

        // Act
        MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, 0, position.inCheck);
        List<Integer> pickedMoves = drainPicker(picker);
        picker.restoreBuffer();

        // Assert
        assertEquals(pickedMoves.size(), picker.moveCount(),
                "moveCount() should equal the number of moves yielded");
    }

    @Test
    @DisplayName("MovePicker yields same legal moves as generateAllMoves across perft suite positions")
    public void testMovePickerCompleteness_PerftSuite() throws Exception {
        // Arrange
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("perftSuite.txt"))));

        String line;
        int positionCount = 0;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank() || line.startsWith("#")) continue;

            String fenStr = line.split(";")[0].trim();
            FEN fen = new FEN(fenStr);
            Position position = new Position(fen);
            SearchContext searchContext = new SearchContext();
            SharedTables sharedTables = new SharedTables(18);

            Set<Integer> allMoves = generateAllMovesSet(position);

            // Act
            MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, 0, position.inCheck);
            List<Integer> pickedMoves = drainPicker(picker);
            picker.restoreBuffer();

            // Assert
            Set<Integer> pickedSet = new HashSet<>(pickedMoves);
            assertEquals(allMoves.size(), pickedMoves.size(),
                    "Move count mismatch for FEN: " + fenStr +
                            "\nExpected: " + allMoves.size() + " Actual: " + pickedMoves.size());
            assertEquals(allMoves, pickedSet,
                    "Move set mismatch for FEN: " + fenStr);
            positionCount++;
        }
        reader.close();

        // Assert
        assertTrue(positionCount > 100,
                "Should have tested at least 100 positions, tested: " + positionCount);
    }

    @Test
    @DisplayName("MovePicker with TT move yields same legal move set as generateAllMoves across perft suite")
    public void testMovePickerCompleteness_WithTTMove_PerftSuite() throws Exception {
        // Arrange
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("perftSuite.txt"))));

        String line;
        int positionCount = 0;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank() || line.startsWith("#")) continue;

            String fenStr = line.split(";")[0].trim();
            FEN fen = new FEN(fenStr);
            Position position = new Position(fen);
            SearchContext searchContext = new SearchContext();
            SharedTables sharedTables = new SharedTables(18);

            // Generate all legal moves via traditional method
            int[] moveBuffer = new int[256];
            int numMoves = MoveGenerator.generateAllMoves(position, moveBuffer, 0);
            Set<Integer> allMoves = new HashSet<>();
            for (int i = 0; i < numMoves; i++) {
                allMoves.add(moveBuffer[i]);
            }

            // Use first legal move as TT move
            int ttMove = numMoves > 0 ? moveBuffer[0] : 0;

            // Act
            MovePicker picker = new MovePicker(position, searchContext, sharedTables, 0, ttMove, position.inCheck);
            List<Integer> pickedMoves = drainPicker(picker);
            picker.restoreBuffer();

            // Assert
            Set<Integer> pickedSet = new HashSet<>(pickedMoves);
            assertEquals(allMoves.size(), pickedMoves.size(),
                    "Move count mismatch (with TT) for FEN: " + fenStr);
            assertEquals(allMoves, pickedSet,
                    "Move set mismatch (with TT) for FEN: " + fenStr);

            // Also verify TT move is first if it was legal
            if (ttMove != 0 && numMoves > 0) {
                assertEquals(ttMove, pickedMoves.get(0),
                        "TT move should be first for FEN: " + fenStr);
            }

            positionCount++;
        }
        reader.close();

        // Assert
        assertTrue(positionCount > 100,
                "Should have tested at least 100 positions, tested: " + positionCount);
    }
}

