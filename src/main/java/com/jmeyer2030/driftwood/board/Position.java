package com.jmeyer2030.driftwood.board;

import com.jmeyer2030.driftwood.movegeneration.MoveGenerator;
import com.jmeyer2030.driftwood.config.GlobalConstants;
import com.jmeyer2030.driftwood.staticevaluation.DummyNNUE;
import com.jmeyer2030.driftwood.staticevaluation.NNUE;
import com.jmeyer2030.driftwood.staticevaluation.Evaluator;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * Represents a game state with Bitboards
 */
public final class Position {


    // Stores information that could be lost when making a move so that it can be recovered in unmake
    public FixedSizeIntStack hmcStack;
    public FixedSizeIntStack epStack;
    public FixedSizeIntStack castleRightsStack;
    public FixedSizeLongStack checkersStack;

    public long zobristHash;

    // Piece Locations
    public long occupancy;
    public long[] pieceColors;
    public long[] pieces;
    public int[] kingLocs;

    // State:
    public int activePlayer; // 0 is white, 1 is black
    public byte castleRights; //Castle Rights: 0b0000(whiteQueen)(whiteKing)(blackQueen)(blackKing)
    public int enPassant; //Same as fen, is the location where the pawn would be if it advanced one square.
    public int halfMoveCount;
    public int fullMoveCount;

    // Bitboard of pinned pieces: bit set if the piece on that square is pinned.
    // Pin ray is derived from pinRay[kingLoc][pinnedSquare], no pinner square needed.
    public long pinnedBB;

    // Stack for saving/restoring pinnedBB across makeMove/unmakeMove.
    // Eliminates the need to recompute pins after child searches corrupt position.pinnedBB.
    public FixedSizeLongStack pinnedBBStack;

    public int[][] pieceCounts; // Indexed as: pieceCounts[color][piece], for use in NMP and draw eval

    public long checkers;
    public boolean inCheck;

    // Static Evaluator
    public Evaluator evaluator;

    /*
     * Constructors
     */

    /**
     * Build starting position
     */
    public Position() {
        pinnedBB = 0L;
        pinnedBBStack = new FixedSizeLongStack();

        hmcStack = new FixedSizeIntStack();
        epStack = new FixedSizeIntStack();
        castleRightsStack = new FixedSizeIntStack();
        checkersStack = new FixedSizeLongStack();

        // Piece Locations:
        occupancy = 0b11111111_11111111_00000000_00000000_00000000_00000000_11111111_11111111L;
        pieceColors = new long[2];
        pieceColors[0] = 0b00000000_00000000_00000000_00000000_00000000_00000000_11111111_11111111L;
        pieceColors[1] = 0b11111111_11111111_00000000_00000000_00000000_00000000_00000000_00000000L;

        pieces = new long[6];
        pieces[Piece.PAWN] = 0b00000000_11111111_00000000_00000000_00000000_00000000_11111111_00000000L;
        pieces[Piece.KNIGHT] = 0b01000010_00000000_00000000_00000000_00000000_00000000_00000000_01000010L;
        pieces[Piece.BISHOP] = 0b00100100_00000000_00000000_00000000_00000000_00000000_00000000_00100100L;
        pieces[Piece.ROOK] = 0b10000001_00000000_00000000_00000000_00000000_00000000_00000000_10000001L;
        pieces[Piece.QUEEN] = 0b00001000_00000000_00000000_00000000_00000000_00000000_00000000_00001000L;
        pieces[Piece.KING] = 0b00010000_00000000_00000000_00000000_00000000_00000000_00000000_00010000L;

        kingLocs = new int[]{Long.numberOfTrailingZeros(pieces[5] & pieceColors[0]), Long.numberOfTrailingZeros(pieces[5] & pieceColors[1])};


        //State:
        activePlayer = 0;
        castleRights = PositionConstants.ALL_CASTLE_RIGHTS;
        enPassant = GlobalConstants.NO_EP;
        halfMoveCount = 0;
        fullMoveCount = 1;
        inCheck = false;

        zobristHash = Hashing.computeZobrist(this);

        pieceCounts = new int[][]{new int[]{8, 2, 2, 2, 1, 1}, new int[]{8, 2, 2, 2, 1, 1}};
        checkers = 0;

        this.evaluator = new NNUE(this);
    }

    /**
     * Copy a position
     */
    public Position(Position position) {
        hmcStack = position.hmcStack.copy();
        epStack = position.epStack.copy();
        castleRightsStack = position.castleRightsStack.copy();
        checkersStack = position.checkersStack.copy();

        this.occupancy = position.occupancy;
        this.pieceColors = Arrays.copyOf(position.pieceColors, 2);
        this.pieces = Arrays.copyOf(position.pieces, 6);
        this.kingLocs = Arrays.copyOf(position.kingLocs, 2);
        this.activePlayer = position.activePlayer;
        this.castleRights = position.castleRights;
        this.enPassant = position.enPassant;
        this.halfMoveCount = position.halfMoveCount;
        this.fullMoveCount = position.fullMoveCount;
        this.inCheck = position.inCheck;
        this.zobristHash = position.zobristHash;

        this.pinnedBB = position.pinnedBB;
        this.pinnedBBStack = position.pinnedBBStack.copy();

        this.pieceCounts = new int[][]{Arrays.copyOf(position.pieceCounts[0], 6), Arrays.copyOf(position.pieceCounts[1], 6)};
        this.checkers = position.checkers;

        this.evaluator = new NNUE(this);
    }

    /**
     * Build from FEN
     */
    public Position(FEN fen) {
        pieceCounts = new int[][]{new int[6], new int[6]};

        pinnedBB = 0L;
        pinnedBBStack = new FixedSizeLongStack();

        hmcStack = new FixedSizeIntStack();
        epStack = new FixedSizeIntStack();
        castleRightsStack = new FixedSizeIntStack();
        checkersStack = new FixedSizeLongStack();
        long occupancy = 0L;
        long whitePieces = 0L;
        long blackPieces = 0L;

        long pawns = 0L;
        long rooks = 0L;
        long knights = 0L;
        long bishops = 0L;
        long queens = 0L;
        long kings = 0L;

        // Parse piece placement
        String[] ranks = fen.piecePlacement.split("/");
        for (int rank = 7; rank >= 0; rank--) { // Iterate over ranks from 8th to 1st
            String currentRank = ranks[rank];
            int file = 0;

            for (char c : currentRank.toCharArray()) {
                if (Character.isDigit(c)) {
                    // Empty squares
                    file += c - '0';
                } else {
                    // Piece on the square
                    int square = (7 - rank) * 8 + file; // Convert rank and file to square index
                    long squareBit = 1L << square;
                    occupancy |= squareBit;

                    switch (c) {
                        case 'P':
                            pieceCounts[0][0] += 1;
                            pawns |= squareBit;
                            whitePieces |= squareBit;
                            break;
                        case 'p':
                            pieceCounts[1][0] += 1;
                            pawns |= squareBit;
                            blackPieces |= squareBit;
                            break;
                        case 'R':
                            pieceCounts[0][3] += 1;
                            rooks |= squareBit;
                            whitePieces |= squareBit;
                            break;
                        case 'r':
                            pieceCounts[1][3] += 1;
                            rooks |= squareBit;
                            blackPieces |= squareBit;
                            break;
                        case 'N':
                            pieceCounts[0][1] += 1;
                            knights |= squareBit;
                            whitePieces |= squareBit;
                            break;
                        case 'n':
                            pieceCounts[1][1] += 1;
                            knights |= squareBit;
                            blackPieces |= squareBit;
                            break;
                        case 'B':
                            pieceCounts[0][2] += 1;
                            bishops |= squareBit;
                            whitePieces |= squareBit;
                            break;
                        case 'b':
                            pieceCounts[1][2] += 1;
                            bishops |= squareBit;
                            blackPieces |= squareBit;
                            break;
                        case 'Q':
                            pieceCounts[0][4] += 1;
                            queens |= squareBit;
                            whitePieces |= squareBit;
                            break;
                        case 'q':
                            pieceCounts[1][4] += 1;
                            queens |= squareBit;
                            blackPieces |= squareBit;
                            break;
                        case 'K':
                            pieceCounts[0][5] += 1;
                            kings |= squareBit;
                            whitePieces |= squareBit;
                            break;
                        case 'k':
                            pieceCounts[1][5] += 1;
                            kings |= squareBit;
                            blackPieces |= squareBit;
                            break;
                    }
                    file++;
                }
            }

        }

        // Parse active color
        int activePlayer = fen.activeColor == 'w' ? 0 : 1;

        // Parse castling rights
        byte castleRights = 0;
        if (fen.castlingAvailable.contains("K")) castleRights |= PositionConstants.CASTLE_RIGHT_WK;
        if (fen.castlingAvailable.contains("Q")) castleRights |= PositionConstants.CASTLE_RIGHT_WQ;
        if (fen.castlingAvailable.contains("k")) castleRights |= PositionConstants.CASTLE_RIGHT_BK;
        if (fen.castlingAvailable.contains("q")) castleRights |= PositionConstants.CASTLE_RIGHT_BQ;

        // Parse en passant square
        int enPassant = GlobalConstants.NO_EP;
        if (!fen.enPassant.equals("-")) {
            char fileChar = fen.enPassant.charAt(0);
            char rankChar = fen.enPassant.charAt(1);
            int file = fileChar - 'a';
            int rank = rankChar - '1';
            enPassant = rank * 8 + file;
        }

        //Game Status
        fullMoveCount = fen.fullMoves;
        halfMoveCount = fen.halfMoves;

        //Piece Locations:
        this.occupancy = occupancy;
        this.pieceColors = new long[]{whitePieces, blackPieces};
        this.pieces = new long[]{pawns, knights, bishops, rooks, queens, kings};

        //State:
        this.activePlayer = activePlayer;
        this.castleRights = castleRights;
        this.enPassant = enPassant;

        this.kingLocs = new int[]{Long.numberOfTrailingZeros(pieces[5] & pieceColors[0]), Long.numberOfTrailingZeros(pieces[5] & pieceColors[1])};

        this.inCheck = MoveGenerator.kingAttacked(this, this.activePlayer);

        this.zobristHash = Hashing.computeZobrist(this);

        this.checkers = MoveGenerator.computeCheckers(this);

        this.evaluator = new NNUE(this);
    }

    /**
    * Creates a fen position with a no-op NNUE
    */
    public static Position getPerftPosition(FEN fen) {
        Position position;
        if (fen == null) {
            position = new Position();
        } else {
            position = new Position(fen);
        }
        position.evaluator = new DummyNNUE();
        return position;
    }

    /*
     * Make and unMake
     */

    /**
     * Removes a piece updating board state only (Zobrist, bitboards, piece counts).
     * Does NOT update the NNUE evaluator. Used by unmakeMove where popAccumulator
     * restores the accumulator instead.
     */
    private void removePieceBoardOnly(int square, int pieceType, int color) {
        this.zobristHash ^= Hashing.PIECE_SQUARE[square][color][pieceType];
        this.occupancy &= ~(1L << square);
        this.pieceColors[color] &= ~(1L << square);
        this.pieces[pieceType] &= ~(1L << square);
        this.pieceCounts[color][pieceType]--;
    }

    /**
     * Adds a piece updating board state only (Zobrist, bitboards, piece counts).
     * Does NOT update the NNUE evaluator. Used by unmakeMove where popAccumulator
     * restores the accumulator instead.
     */
    private void addPieceBoardOnly(int square, int pieceType, int color) {
        this.zobristHash ^= Hashing.PIECE_SQUARE[square][color][pieceType];
        this.occupancy |= (1L << square);
        this.pieceColors[color] |= (1L << square);
        this.pieces[pieceType] |= (1L << square);
        this.pieceCounts[color][pieceType]++;
    }

    /**
     * Removes a piece Updating:
     * - Zobrist Hash
     * - occupancy, piece colors, pieceCounts, and pieces
     * - NNUE feature (eagerly applied to current accumulator ply)
     *
     * @param square    to remove the piece
     * @param pieceType of the piece
     * @param color     of the removed piece
     */
    private void removePiece(int square, int pieceType, int color) {
        removePieceBoardOnly(square, pieceType, color);
        this.evaluator.removeFeature(pieceType, color, square);
    }

    /**
     * Adds a piece Updating:
     * - Zobrist Hash
     * - occupancy, piece colors, pieceCounts, and pieces
     * - NNUE feature (eagerly applied to current accumulator ply)
     *
     * @param square    of the piece
     * @param pieceType of the piece
     * @param color     of the piece
     */
    private void addPiece(int square, int pieceType, int color) {
        addPieceBoardOnly(square, pieceType, color);
        evaluator.addFeature(pieceType, color, square);
    }

    /**
     * Makes a passing move and updates related fields
     */
    public void makeNullMove() {
        // Push stored things
        hmcStack.push(this.halfMoveCount);
        epStack.push(this.enPassant);
        castleRightsStack.push(this.castleRights);
        pinnedBBStack.push(this.pinnedBB);

        // Undo ep hash
        if (enPassant != GlobalConstants.NO_EP) {
            zobristHash ^= Hashing.EN_PASSANT[enPassant % 8];
        }

        // Increment HMC
        this.halfMoveCount++;

        // Set ep to none
        this.enPassant = GlobalConstants.NO_EP;

        // If black moving, increment FMC
        this.fullMoveCount += activePlayer;

        // Switch active player
        this.activePlayer = 1 - activePlayer;

        // Switch active player hash
        this.zobristHash ^= Hashing.SIDE_TO_MOVE[Color.WHITE];
        this.zobristHash ^= Hashing.SIDE_TO_MOVE[Color.BLACK];
    }

    /**
     * Unmakes a passing move and updates related fields
     */
    public void unMakeNullMove() {
        // Switch active player
        this.activePlayer = 1 - activePlayer;

        //zobristHash ^= Hashing.castleRights[castleRights];

        // Pop stored things
        halfMoveCount = hmcStack.pop();
        enPassant = epStack.pop();
        castleRights = (byte) castleRightsStack.pop();
        pinnedBB = pinnedBBStack.pop();

        // If black moving, increment FMC
        this.fullMoveCount -= activePlayer;

        // Switch active player hash
        this.zobristHash ^= Hashing.SIDE_TO_MOVE[0];
        this.zobristHash ^= Hashing.SIDE_TO_MOVE[1];
        //zobristHash ^= Hashing.castleRights[castleRights];

        if (enPassant != GlobalConstants.NO_EP) { // Move before was double push
            zobristHash ^= Hashing.EN_PASSANT[enPassant % 8];
        }
    }

    /**
     * Applies a move
     */
    public void makeMove(int move) {
        // Case null move:
        if (move == 0) {
            makeNullMove();
            return;
        }

        // Get Encoded data
        int start = MoveEncoding.getStart(move);
        int destination = MoveEncoding.getDestination(move);
        int movedPiece = MoveEncoding.getMovedPiece(move);
        int capturedPiece = MoveEncoding.getCapturedPiece(move);
        int promotionType = MoveEncoding.getPromotionType(move);
        boolean isCapture = MoveEncoding.getIsCapture(move);
        boolean isEP = MoveEncoding.getIsEP(move);
        boolean isPromotion = MoveEncoding.getIsPromotion(move);
        boolean isCastle = MoveEncoding.getIsCastle(move);
        boolean isDoublePush = MoveEncoding.getIsDoublePush(move);
        boolean isReversible = MoveEncoding.getIsReversible(move);
        int castleSide = MoveEncoding.getCastleSide(move);

        // Store state information
        hmcStack.push(this.halfMoveCount);
        epStack.push(this.enPassant);
        castleRightsStack.push(this.castleRights);
        checkersStack.push(this.checkers);
        pinnedBBStack.push(this.pinnedBB);

        // Copy current accumulator to next ply before applying feature deltas
        evaluator.pushAccumulator();

        zobristHash ^= Hashing.CASTLE_RIGHTS[castleRights];

        if (enPassant != GlobalConstants.NO_EP) {
            zobristHash ^= Hashing.EN_PASSANT[enPassant % 8];
        }

        // Increment hmc
        halfMoveCount++;

        // Remove capture
        if (isCapture) {
            removePiece(destination, capturedPiece, 1 - activePlayer);
        }

        // Remove start, add destination
        removePiece(start, movedPiece, activePlayer);
        addPiece(destination, movedPiece, activePlayer);

        // Handle specific move types
        if (!isReversible) {
            halfMoveCount = 0;
        }

        if (isEP) {
            // Remove pawn captured en Passant
            int enPassantCaptureSquare = enPassant - 8 + 16 * activePlayer; // if white - 8, else + 8
            removePiece(enPassantCaptureSquare, Piece.PAWN, 1 - activePlayer);
        }

        if (isPromotion) {
            // Remove pawn, add promotion piece
            removePiece(destination, movedPiece, activePlayer);
            addPiece(destination, promotionType, activePlayer);
        }

        if (isCastle) {
            // Move the rook
            int rookStart = PositionConstants.CASTLE_ROOK_STARTS[activePlayer][castleSide];
            int rookDestination = PositionConstants.CASTLE_ROOK_DESTINATIONS[activePlayer][castleSide];
            removePiece(rookStart, Piece.ROOK, activePlayer);
            addPiece(rookDestination, Piece.ROOK, activePlayer);
        }

        if (movedPiece == Piece.KING) {
            kingLocs[activePlayer] = destination;
            // Change castle rights
            castleRights &= PositionConstants.CASTLE_RIGHTS_MASK[activePlayer];
        }

        if (movedPiece == Piece.ROOK) {
            // Change castle rights
            if (start == PositionConstants.ROOK_START_WQ) {
                castleRights &= PositionConstants.REVOKE_WQ;
            } else if (start == PositionConstants.ROOK_START_WK) {
                castleRights &= PositionConstants.REVOKE_WK;
            } else if (start == PositionConstants.ROOK_START_BQ) {
                castleRights &= PositionConstants.REVOKE_BQ;
            } else if (start == PositionConstants.ROOK_START_BK) {
                castleRights &= PositionConstants.REVOKE_BK;
            }
        }

        if (isDoublePush) {
            // Set EP square
            enPassant = destination - 8 + 16 * activePlayer;
            zobristHash ^= Hashing.EN_PASSANT[enPassant % 8];
        } else {
            enPassant = GlobalConstants.NO_EP;
        }

        //increment moveCounter if black moved
        fullMoveCount += activePlayer;

        // Switch active player
        activePlayer = 1 - activePlayer;

        this.zobristHash ^= Hashing.SIDE_TO_MOVE[1 - activePlayer];
        this.zobristHash ^= Hashing.SIDE_TO_MOVE[activePlayer];
        this.zobristHash ^= Hashing.CASTLE_RIGHTS[castleRights];

        this.checkers = MoveGenerator.computeCheckers(this);
        this.inCheck = Long.numberOfTrailingZeros(checkers) != 64;
    }

    /**
     * Unmakes a move.
     * Uses board-only piece manipulation (no evaluator calls) because
     * popAccumulator restores the parent ply's accumulator state.
     */
    public void unMakeMove(int move) {
        if (move == 0) {
            unMakeNullMove();
            return;
        }
        // Get Encoded data
        int start = MoveEncoding.getStart(move);
        int destination = MoveEncoding.getDestination(move);
        int movedPiece = MoveEncoding.getMovedPiece(move);
        int capturedPiece = MoveEncoding.getCapturedPiece(move);
        int promotionType = MoveEncoding.getPromotionType(move);
        boolean isCapture = MoveEncoding.getIsCapture(move);
        boolean isEP = MoveEncoding.getIsEP(move);
        boolean isPromotion = MoveEncoding.getIsPromotion(move);
        boolean isCastle = MoveEncoding.getIsCastle(move);
        int castleSide = MoveEncoding.getCastleSide(move);

        // Change active player
        this.activePlayer = 1 - activePlayer;

        zobristHash ^= Hashing.CASTLE_RIGHTS[castleRights];
        if (enPassant != GlobalConstants.NO_EP) {
            zobristHash ^= Hashing.EN_PASSANT[enPassant % 8];
        }

        // Board-only piece manipulation, accumulator restored by popAccumulator below
        if (isPromotion) {
            removePieceBoardOnly(destination, promotionType, activePlayer);
        } else {
            removePieceBoardOnly(destination, movedPiece, activePlayer);
        }

        addPieceBoardOnly(start, movedPiece, activePlayer);

        if (isCapture) {
            addPieceBoardOnly(destination, capturedPiece, 1 - activePlayer);
        }

        if (isEP) {
            int enPassantCaptureSquare = destination - 8 + 16 * activePlayer;
            addPieceBoardOnly(enPassantCaptureSquare, Piece.PAWN, 1 - activePlayer);
        }

        if (isCastle) {
            int rookStart = PositionConstants.CASTLE_ROOK_STARTS[activePlayer][castleSide];
            int rookDestination = PositionConstants.CASTLE_ROOK_DESTINATIONS[activePlayer][castleSide];
            removePieceBoardOnly(rookDestination, Piece.ROOK, activePlayer);
            addPieceBoardOnly(rookStart, Piece.ROOK, activePlayer);
        }

        if (movedPiece == Piece.KING) {
            kingLocs[activePlayer] = start;
        }

        this.castleRights = (byte) castleRightsStack.pop();
        this.halfMoveCount = hmcStack.pop();
        this.enPassant = epStack.pop();
        this.checkers = checkersStack.pop();
        this.inCheck = checkers != 0;
        this.pinnedBB = pinnedBBStack.pop();

        fullMoveCount -= activePlayer; // if black moved, decrement


        this.zobristHash ^= Hashing.SIDE_TO_MOVE[1 - activePlayer];
        this.zobristHash ^= Hashing.SIDE_TO_MOVE[activePlayer];
        this.zobristHash ^= Hashing.CASTLE_RIGHTS[castleRights];

        if (enPassant != GlobalConstants.NO_EP) {
            zobristHash ^= Hashing.EN_PASSANT[enPassant % 8];
        }

        // Restore parent ply's accumulator, no undo work needed
        evaluator.popAccumulator();
    }


    /**
     * Returns the PieceType on a square
     *
     * @param square square
     * @return pieceType
     */
    public int getPieceType(int square) {
        long squareMask = (1L << square);
        if ((this.pieces[Piece.PAWN] & squareMask) != 0) {
            return Piece.PAWN;
        } else if ((this.pieces[Piece.ROOK] & squareMask) != 0) {
            return Piece.ROOK;
        } else if ((this.pieces[Piece.KNIGHT] & squareMask) != 0) {
            return Piece.KNIGHT;
        } else if ((this.pieces[Piece.BISHOP] & squareMask) != 0) {
            return Piece.BISHOP;
        } else if ((this.pieces[Piece.QUEEN] & squareMask) != 0) {
            return Piece.QUEEN;
        } else if ((this.pieces[Piece.KING] & squareMask) != 0) {
            return Piece.KING;
        }

        throw new RuntimeException("A piece was expected to exist on square: " + square + ", but did not.");
    }

    /**
     * Returns if this position is equal to another. Debug feature.
     *
     * @param position position
     * @return true if they are equal
     */
    public boolean equals(Position position) {
        boolean equal = true;
        equal &= compareValues(this.occupancy, position.occupancy, "Occupancy");
        equal &= compareValues(this.pieces[Piece.PAWN], position.pieces[Piece.PAWN], "Pawns");
        equal &= compareValues(this.pieces[Piece.KNIGHT], position.pieces[Piece.KNIGHT], "Knight");
        equal &= compareValues(this.pieces[Piece.BISHOP], position.pieces[Piece.BISHOP], "Bishop");
        equal &= compareValues(this.pieces[Piece.ROOK], position.pieces[Piece.ROOK], "Rook");
        equal &= compareValues(this.pieces[Piece.QUEEN], position.pieces[Piece.QUEEN], "Queen");
        equal &= compareValues(this.pieces[Piece.KING], position.pieces[Piece.KING], "King");
        equal &= compareValues(this.pieceColors[Color.WHITE], position.pieceColors[Color.WHITE], "White Pieces");
        equal &= compareValues(this.pieceColors[Color.BLACK], position.pieceColors[Color.BLACK], "Black Pieces");
        equal &= compareValues(this.castleRights, position.castleRights, "Castle Rights");
        equal &= compareValues(this.halfMoveCount, position.halfMoveCount, "Rule 50");
        equal &= compareValues(this.enPassant, position.enPassant, "En Passant");
        equal &= compareValues(this.activePlayer, position.activePlayer, "Active Player");
        equal &= compareValues(this.fullMoveCount, position.fullMoveCount, "Full Move Count");
        return equal;
    }

    /**
     * Returns true if values are equal, else false
     *
     * @param value1    first value
     * @param value2    value to compare to
     * @param fieldName field name of these values
     * @return if they are equal
     */
    private boolean compareValues(Object value1, Object value2, String fieldName) {
        if (!value1.equals(value2)) {
            System.out.println(fieldName + " different");
            return false;
        }
        return true;
    }

    /**
     * Prints a position in a human-readable format
     *
     * <pre>
     *      +---+---+---+---+---+---+---+---+
     *    8 | r | n | b | q | k | b | n | r |
     *      +---+---+---+---+---+---+---+---+
     *    7 | p | p | p | p | p | p | p | p |
     *      +---+---+---+---+---+---+---+---+
     *    6 |   |   |   |   |   |   |   |   |
     *      +---+---+---+---+---+---+---+---+
     *    5 |   |   |   |   |   |   |   |   |
     *      +---+---+---+---+---+---+---+---+
     *    4 |   |   |   |   |   |   |   |   |
     *      +---+---+---+---+---+---+---+---+
     *    3 |   |   |   |   |   |   |   |   |
     *      +---+---+---+---+---+---+---+---+
     *    2 | P | P | P | P | P | P | P | P |
     *      +---+---+---+---+---+---+---+---+
     *    1 | R | N | B | Q | K | B | N | R |
     *      +---+---+---+---+---+---+---+---+
     *        A   B   C   D   E   F   G   H
     * </pre>
     */
    public String getDisplayBoard() {
        try {
            validPosition();
        } catch (InvalidPositionException ipe) {
            return "Position isn't valid!";
        }

        char[] board = new char[64];
        char[] pieceSymbols = {'p', 'n', 'b', 'r', 'q', 'k'};


        // fill main.java.board with correct piece symbol
        Arrays.fill(board, ' ');
        for (int i = 0; i < 6; i++) {
            long currentPieceType = pieces[i];
            while (currentPieceType != 0L) {
                int loc = Long.numberOfTrailingZeros(currentPieceType);
                currentPieceType &= (currentPieceType - 1);
                board[loc] = pieceSymbols[i];
            }
        }

        // Shift case of the board array
        long shiftCase = pieceColors[0];
        while (shiftCase != 0L) {
            int loc = Long.numberOfTrailingZeros(shiftCase);
            shiftCase &= (shiftCase - 1);
            board[loc] = Character.toUpperCase(board[loc]);
        }
        char[][] boardTemplate = new char[][]{
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  8 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  7 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  6 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  5 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  4 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  3 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  2 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "  1 |   |   |   |   |   |   |   |   |\n".toCharArray(),
                "    +---+---+---+---+---+---+---+---+\n".toCharArray(),
                "      A   B   C   D   E   F   G   H  \n".toCharArray()};

        int firstSquare = 6;
        int squareIncrement = 4;

        // Replace characters in template with their piece
        for (int i = 0; i < 64; i++) {
            int boardRow = 1 + (i / 8) * 2; // boardRow of boardTemplate
            int boardCol = firstSquare + ((i % 8) * squareIncrement);

            int row = 7 - i / 8;
            int col = i % 8;
            int index = row * 8 + col;

            boardTemplate[boardRow][boardCol] = board[index];
        }

        // Convert char[][] to a list
        List<Character> result = new LinkedList<>();
        for (char[] arr : boardTemplate) {
            for (char c : arr) {
                result.add(c);
            }
        }

        StringBuilder bldr = new StringBuilder();

        for (Character c : result) {
            bldr.append(c);
        }

        return bldr.toString();
    }

    /**
     * A position is "valid" iff:
     * - pieceColors[0-1] have no overlap AND
     * - pieces[0-5] have no overlap AND
     * - pieceColors[0-1] (with OR operator) is equivalent to occupancy
     * - pieces[0-5] (with OR operator) is equivalent to occupancy
     */
    public void validPosition() {
        // Test pieceColors no overlap
        long pieceColorsAND = pieceColors[Color.WHITE] & pieceColors[Color.BLACK];
        if (pieceColorsAND != 0)
            throw new InvalidPositionException("Piece colors overlap");

        // Test pieces no overlap
        long piecesAND = pieces[Piece.PAWN] & pieces[Piece.KNIGHT] & pieces[Piece.BISHOP] & pieces[Piece.ROOK] & pieces[Piece.QUEEN] & pieces[Piece.KING];
        if (piecesAND != 0)
            throw new InvalidPositionException("Piece types overlap");

        // Test pieceColors OR == occupancy
        long pieceColorsOR = pieceColors[Color.WHITE] | pieceColors[Color.BLACK];
        if (occupancy != pieceColorsOR)
            throw new InvalidPositionException("Piece colors aren't consistent with occupancy");

        // Test pieces OR == occupancy
        long piecesOR = pieces[Piece.PAWN] | pieces[Piece.KNIGHT] | pieces[Piece.BISHOP] | pieces[Piece.ROOK] | pieces[Piece.QUEEN] | pieces[Piece.KING];
        if (occupancy != piecesOR)
            throw new InvalidPositionException("Piece types aren't consistent with occupancy");
    }
}
