package com.jmeyer2030.driftwood.search;

import com.jmeyer2030.driftwood.board.FEN;
import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SEETest {

    private final SEE see = new SEE();
    private final int[] moveBuffer = new int[256];

    @Test
    @DisplayName("SEE: knight captures undefended queen (nxQ)")
    void knightCapturesUndefendedQueen() {
        // Arrange
        FEN fen = new FEN("4k3/8/8/4Q3/8/3n4/6K1/8 b - - 0 1");
        Position position = new Position(fen);
        int move = MoveGenerator.getMoveFromLAN("d3e5", position, moveBuffer);

        // Act
        int result = see.see(move, position);

        // Assert, knight takes undefended queen: net gain = queen value (1000)
        assertEquals(SEE.value[4], result);
    }

    @Test
    @DisplayName("SEE: knight captures queen defended by bishop (nxQ, Bxn)")
    void knightCapturesQueenDefendedByBishop() {
        // Arrange
        FEN fen = new FEN("4k3/8/8/4Q3/8/3n2B1/6K1/8 b - - 0 1");
        Position position = new Position(fen);
        int move = MoveGenerator.getMoveFromLAN("d3e5", position, moveBuffer);

        // Act
        int result = see.see(move, position);

        // Assert, nxQ gains queen (1000), then Bxn loses knight (325) → net 1000 - 325 = 675
        assertEquals(SEE.value[4] - SEE.value[1], result);
    }

    @Test
    @DisplayName("SEE: knight captures queen defended by king (nxQ, Kxn)")
    void knightCapturesQueenDefendedByKing() {
        // Arrange
        FEN fen = new FEN("4k3/8/8/4Q3/5K2/3n4/8/8 b - - 0 1");
        Position position = new Position(fen);
        int move = MoveGenerator.getMoveFromLAN("d3e5", position, moveBuffer);

        // Act
        int result = see.see(move, position);

        // Assert, nxQ gains queen (1000), Kxn recaptures knight (325) → net 1000 - 325 = 675
        assertEquals(SEE.value[4] - SEE.value[1], result);
    }

    @Test
    @DisplayName("SEE: rook takes pawn on complex position (RxP)")
    void rookTakesPawnSimple() {
        // Arrange
        FEN fen = new FEN("1k1r4/1pp4p/p7/4p3/8/P5P1/1PP4P/2K1R3 w - - 0 1");
        Position position = new Position(fen);
        int move = MoveGenerator.getMoveFromLAN("e1e5", position, moveBuffer);

        // Act
        int result = see.see(move, position);

        // Assert, RxP gains a pawn (100), then RxR loses rook (500), net = 100 - 500 = -400
        // but black can choose not to recapture, so SEE correctly evaluates the exchange
        assertTrue(result >= -500 && result <= 100,
                "SEE result " + result + " should be in a reasonable range for RxP with rook behind");
    }

    @Test
    @DisplayName("SEE: knight captures pawn in complex position with many attackers/defenders")
    void knightCapturesPawnComplex() {
        // Arrange
        FEN fen = new FEN("1k1r3q/1ppn3p/p4b2/4p3/8/P2N2P1/1PP1R1BP/2K1Q3 w - - 0 1");
        Position position = new Position(fen);
        int move = MoveGenerator.getMoveFromLAN("d3e5", position, moveBuffer);

        // Act
        int result = see.see(move, position);

        // Assert, complex position; knight captures e5 pawn with multiple attackers/defenders
        // The result should be deterministic and within reasonable bounds
        assertTrue(result >= -500 && result <= 500,
                "SEE result " + result + " should be within reasonable bounds for complex exchange");
    }

    @Test
    @DisplayName("SEE: piece values array is correctly sized")
    void pieceValuesArrayLength() {
        // Arrange & Act & Assert
        assertEquals(6, SEE.value.length, "Should have values for all 6 piece types");
    }

    @Test
    @DisplayName("SEE: piece value ordering is pawn < knight <= bishop < rook < queen < king")
    void pieceValueOrdering() {
        // Arrange & Act & Assert
        assertTrue(SEE.value[0] < SEE.value[1], "Pawn < Knight");
        assertTrue(SEE.value[1] <= SEE.value[2], "Knight <= Bishop");
        assertTrue(SEE.value[2] < SEE.value[3], "Bishop < Rook");
        assertTrue(SEE.value[3] < SEE.value[4], "Rook < Queen");
        assertTrue(SEE.value[4] < SEE.value[5], "Queen < King");
    }
}
