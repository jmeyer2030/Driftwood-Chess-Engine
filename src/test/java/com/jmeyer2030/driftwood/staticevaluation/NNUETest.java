package com.jmeyer2030.driftwood.staticevaluation;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NNUETest {

    @Test
    @DisplayName("computeOutput returns consistent value for starting position from white perspective")
    void returnsConsistentValueForStartingPosition() {
        // Arrange
        Position position = new Position();
        NNUE nnue = (NNUE) position.evaluator;

        // Act
        int evalWhite = nnue.computeOutput(0);

        // Assert, starting position should be roughly symmetric, eval near zero
        assertTrue(Math.abs(evalWhite) < 200,
                "Starting position eval should be near zero, was: " + evalWhite);
    }

    @Test
    @DisplayName("computeOutput returns opposite-sign values for white vs black perspective in asymmetric position")
    void returnsOppositeSignsForWhiteVsBlackPerspective() {
        // Arrange, white has an extra queen, heavily favors white
        FEN fen = new FEN("4k3/8/8/8/8/8/8/3QK3 w - - 0 1");
        Position position = new Position(fen);
        NNUE nnue = (NNUE) position.evaluator;

        // Act
        int evalWhite = nnue.computeOutput(0);
        int evalBlack = nnue.computeOutput(1);

        // Assert, white perspective should be positive, black perspective should be negative
        assertTrue(evalWhite > 0, "White perspective should be positive, was: " + evalWhite);
        assertTrue(evalBlack < 0, "Black perspective should be negative, was: " + evalBlack);
    }

    @Test
    @DisplayName("computeOutput returns cached value when called twice without feature changes")
    void returnsCachedValueOnSecondCall() {
        // Arrange
        Position position = new Position();
        NNUE nnue = (NNUE) position.evaluator;

        // Act
        int first = nnue.computeOutput(0);
        int second = nnue.computeOutput(0);

        // Assert
        assertEquals(first, second, "Cached call should return the same value");
    }

    @Test
    @DisplayName("computeOutput reflects incremental updates after make/unmake move")
    void reflectsIncrementalUpdatesAfterMakeUnmake() {
        // Arrange
        Position position = new Position();
        NNUE nnue = (NNUE) position.evaluator;
        int evalBefore = nnue.computeOutput(0);

        // Act, generate a legal move and make/unmake it
        int[] moveBuffer = new int[256];
        int numMoves = com.jmeyer2030.driftwood.movegeneration.MoveGenerator.generateAllMoves(position, moveBuffer, 0);
        assertTrue(numMoves > 0, "Should have legal moves from starting position");
        int move = moveBuffer[0];

        position.makeMove(move);
        position.unMakeMove(move);

        int evalAfter = nnue.computeOutput(0);

        // Assert, eval should be identical after make/unmake round-trip
        assertEquals(evalBefore, evalAfter,
                "Eval should be identical after make/unmake round-trip");
    }

    @Test
    @DisplayName("computeOutput returns original eval after multi-ply make/unmake round-trip")
    void returnsOriginalEvalAfterMultiPlyRoundTrip() {
        // Arrange
        Position position = new Position();
        NNUE nnue = (NNUE) position.evaluator;
        int evalBefore = nnue.computeOutput(0);

        // Act, make 3 moves deep, then unmake all 3
        int[] moveBuffer = new int[256];
        int[] moves = new int[3];
        for (int depth = 0; depth < 3; depth++) {
            int numMoves = com.jmeyer2030.driftwood.movegeneration.MoveGenerator.generateAllMoves(position, moveBuffer, 0);
            assertTrue(numMoves > 0, "Should have legal moves at depth " + depth);
            moves[depth] = moveBuffer[0];
            position.makeMove(moves[depth]);
        }
        for (int depth = 2; depth >= 0; depth--) {
            position.unMakeMove(moves[depth]);
        }

        int evalAfter = nnue.computeOutput(0);

        // Assert — eval should be identical after full round-trip
        assertEquals(evalBefore, evalAfter,
                "Eval should be identical after 3-ply make/unmake round-trip");
    }

    @Test
    @DisplayName("computeOutput preserves parent accumulator when evaluating sibling moves")
    void preservesParentAccumulatorForSiblingMoves() {
        // Arrange
        Position position = new Position();
        NNUE nnue = (NNUE) position.evaluator;
        int evalBefore = nnue.computeOutput(0);

        // Act — make move A, evaluate, unmake; then make move B, evaluate, unmake
        int[] moveBuffer = new int[256];
        int numMoves = com.jmeyer2030.driftwood.movegeneration.MoveGenerator.generateAllMoves(position, moveBuffer, 0);
        assertTrue(numMoves >= 2, "Need at least 2 legal moves");

        int moveA = moveBuffer[0];
        int moveB = moveBuffer[1];

        // Sibling A
        position.makeMove(moveA);
        int evalAfterA = nnue.computeOutput(position.activePlayer);
        position.unMakeMove(moveA);

        // Sibling B
        position.makeMove(moveB);
        int evalAfterB = nnue.computeOutput(position.activePlayer);
        position.unMakeMove(moveB);

        int evalAfter = nnue.computeOutput(0);

        // Assert — parent eval should be preserved after both sibling explorations
        assertEquals(evalBefore, evalAfter,
                "Parent eval should be identical after exploring two sibling moves");
        // Sibling evals should differ (different moves produce different positions)
        // but this isn't guaranteed for all move pairs, so we just verify they computed without error
    }
}
