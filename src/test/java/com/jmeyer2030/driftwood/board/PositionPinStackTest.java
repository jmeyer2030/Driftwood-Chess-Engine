package com.jmeyer2030.driftwood.board;

import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pinnedBB stack in {@link Position}.
 * Validates that pinnedBB is correctly saved/restored across makeMove/unmakeMove
 * and makeNullMove/unMakeNullMove, so that pins computed at a parent node survive
 * child searches.
 */
class PositionPinStackTest {

    @Test
    @DisplayName("pinnedBB restored after makeMove/unmakeMove when position has a pin")
    void pinnedBBRestoredAfterMakeMoveUnmakeMove() {
        // Arrange, Black bishop on b4 pins white knight on c3 to white king on e1
        Position position = new Position(new FEN("rnbqk1nr/pppp1ppp/4p3/8/1b6/2NP4/PPP1PPPP/R1BQKBNR w KQkq - 2 3"));

        // Act, compute pins, save a copy, then make a move and unmake
        MoveGenerator.computePins(position);
        long pinsBefore = position.pinnedBB;

        // Verify there IS a pin (knight on c3 = square 18 should be pinned)
        assertNotEquals(0L, pinsBefore & (1L << 18),
                "Knight on c3 (sq 18) should be pinned by bishop on b4");

        // Make a legal move (pawn a2-a3 = sq 8 to sq 16), which doesn't affect the pin
        int[] moveBuffer = new int[256];
        int move = MoveGenerator.getMoveFromLAN("a2a3", position, moveBuffer);
        position.makeMove(move);

        // Child: compute pins for child position (overwrites pinnedBB)
        MoveGenerator.computePins(position);

        // Unmake the move
        position.unMakeMove(move);

        // Assert, pinnedBB should be restored to parent's value
        assertEquals(pinsBefore, position.pinnedBB,
                "pinnedBB should be restored to parent value after unmakeMove");
    }

    @Test
    @DisplayName("pinnedBB restored after makeNullMove/unMakeNullMove when position has a pin")
    void pinnedBBRestoredAfterNullMoveUnmakeNullMove() {
        // Arrange, same pinned position as above
        Position position = new Position(new FEN("rnbqk1nr/pppp1ppp/4p3/8/1b6/2NP4/PPP1PPPP/R1BQKBNR w KQkq - 2 3"));

        // Act, compute pins, save a copy, then null move and un-null-move
        MoveGenerator.computePins(position);
        long pinsBefore = position.pinnedBB;

        position.makeNullMove();

        // Child: compute pins for the new active player (overwrites pinnedBB)
        MoveGenerator.computePins(position);

        // Undo the null move
        position.unMakeNullMove();

        // Assert, pinnedBB should be restored to parent's value
        assertEquals(pinsBefore, position.pinnedBB,
                "pinnedBB should be restored to parent value after unMakeNullMove");
    }

    @Test
    @DisplayName("pinnedBB restored across multiple nested makeMove/unmakeMove calls")
    void pinnedBBRestoredAcrossNestedMakeMoves() {
        // Arrange, position with pin
        Position position = new Position(new FEN("rnbqk1nr/pppp1ppp/4p3/8/1b6/2NP4/PPP1PPPP/R1BQKBNR w KQkq - 2 3"));

        MoveGenerator.computePins(position);
        long pinsAtRoot = position.pinnedBB;

        // Act, make move 1 (white a2a3)
        int[] moveBuffer = new int[256];
        int move1 = MoveGenerator.getMoveFromLAN("a2a3", position, moveBuffer);
        position.makeMove(move1);
        MoveGenerator.computePins(position);
        long pinsAtPly1 = position.pinnedBB;

        // Make move 2 (black d7d5)
        int move2 = MoveGenerator.getMoveFromLAN("d7d5", position, moveBuffer);
        position.makeMove(move2);
        MoveGenerator.computePins(position);

        // Unmake move 2
        position.unMakeMove(move2);

        // Assert, ply 1 pins restored
        assertEquals(pinsAtPly1, position.pinnedBB,
                "pinnedBB should be restored to ply 1 value after unmaking move 2");

        // Unmake move 1
        position.unMakeMove(move1);

        // Assert, root pins restored
        assertEquals(pinsAtRoot, position.pinnedBB,
                "pinnedBB should be restored to root value after unmaking move 1");
    }

    @Test
    @DisplayName("pinnedBB restored when pin is created and destroyed by moves")
    void pinnedBBRestoredWhenPinChanges() {
        // Arrange, starting position (no pins initially)
        Position position = new Position();

        MoveGenerator.computePins(position);
        long pinsAtRoot = position.pinnedBB;

        // Verify no pins in starting position
        assertEquals(0L, pinsAtRoot,
                "Starting position should have no pinned pieces");

        // Act, make some moves
        int[] moveBuffer = new int[256];
        int move1 = MoveGenerator.getMoveFromLAN("e2e4", position, moveBuffer);
        position.makeMove(move1);
        MoveGenerator.computePins(position);

        // Unmake
        position.unMakeMove(move1);

        // Assert, root pins restored (all 0)
        assertEquals(pinsAtRoot, position.pinnedBB,
                "pinnedBB should be restored to initial no-pins state");
    }
}
