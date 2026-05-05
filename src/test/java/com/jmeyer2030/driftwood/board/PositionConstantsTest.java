package com.jmeyer2030.driftwood.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionConstantsTest {

    @Test
    @DisplayName("ALL_CASTLE_RIGHTS equals the OR of all four individual castle rights")
    void allCastleRightsEqualsOrOfIndividualRights() {
        // Arrange
        int expected = PositionConstants.CASTLE_RIGHT_WK
                | PositionConstants.CASTLE_RIGHT_WQ
                | PositionConstants.CASTLE_RIGHT_BK
                | PositionConstants.CASTLE_RIGHT_BQ;

        // Act
        byte actual = PositionConstants.ALL_CASTLE_RIGHTS;

        // Assert
        assertEquals((byte) expected, actual);
    }

    @Test
    @DisplayName("REVOKE mask clears only its target right when ANDed with ALL_CASTLE_RIGHTS")
    void revokeMaskClearsOnlyTargetRight() {
        // Arrange
        byte all = PositionConstants.ALL_CASTLE_RIGHTS;

        // Act & Assert, each revoke mask removes exactly one right
        assertEquals(all & ~PositionConstants.CASTLE_RIGHT_WQ, all & PositionConstants.REVOKE_WQ);
        assertEquals(all & ~PositionConstants.CASTLE_RIGHT_WK, all & PositionConstants.REVOKE_WK);
        assertEquals(all & ~PositionConstants.CASTLE_RIGHT_BQ, all & PositionConstants.REVOKE_BQ);
        assertEquals(all & ~PositionConstants.CASTLE_RIGHT_BK, all & PositionConstants.REVOKE_BK);
    }

    @Test
    @DisplayName("FEN-parsed castle rights match expected bit values for the starting position")
    void fenParsedCastleRightsMatchStartingPosition() {
        // Arrange
        FEN fen = new FEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        // Act
        Position position = Position.getPerftPosition(fen);

        // Assert
        assertEquals(PositionConstants.ALL_CASTLE_RIGHTS, position.castleRights);
    }

    @Test
    @DisplayName("Castle rights are partially set when FEN specifies only some rights")
    void castleRightsPartiallySetFromFen() {
        // Arrange, only white king-side and black queen-side
        FEN fen = new FEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Kq - 0 1");

        // Act
        Position position = Position.getPerftPosition(fen);

        // Assert
        byte expected = (byte) (PositionConstants.CASTLE_RIGHT_WK | PositionConstants.CASTLE_RIGHT_BQ);
        assertEquals(expected, position.castleRights);
    }

    @Test
    @DisplayName("Rook start squares correspond to standard chess starting squares")
    void rookStartSquaresAreCorrect() {
        // Arrange, standard board squares: a1=0, h1=7, a8=56, h8=63

        // Act & Assert
        assertEquals(0, PositionConstants.ROOK_START_WQ, "a1 = white queen-side rook");
        assertEquals(7, PositionConstants.ROOK_START_WK, "h1 = white king-side rook");
        assertEquals(56, PositionConstants.ROOK_START_BQ, "a8 = black queen-side rook");
        assertEquals(63, PositionConstants.ROOK_START_BK, "h8 = black king-side rook");
    }

    @Test
    @DisplayName("Castle destination squares are on the correct file for king-side (g) and queen-side (c)")
    void castleDestinationSquaresOnCorrectFile() {
        // Arrange, king-side = g-file (file 6), queen-side = c-file (file 2)

        // Act & Assert
        assertEquals(6, PositionConstants.CASTLE_DEST_WK % 8, "White king-side dest on g-file");
        assertEquals(2, PositionConstants.CASTLE_DEST_WQ % 8, "White queen-side dest on c-file");
        assertEquals(6, PositionConstants.CASTLE_DEST_BK % 8, "Black king-side dest on g-file");
        assertEquals(2, PositionConstants.CASTLE_DEST_BQ % 8, "Black queen-side dest on c-file");
    }
}

