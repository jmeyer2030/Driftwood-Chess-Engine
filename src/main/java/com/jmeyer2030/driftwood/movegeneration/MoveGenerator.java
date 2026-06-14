package com.jmeyer2030.driftwood.movegeneration;

import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.board.PositionConstants;
import com.jmeyer2030.driftwood.board.MoveEncoding;
import com.jmeyer2030.driftwood.board.Piece;

import java.util.LinkedList;
import java.util.List;

public class MoveGenerator {

    // Mode constants for move generation
    static final int MODE_ALL = 0;
    static final int MODE_CAPTURES = 1;
    static final int MODE_QUIETS = 2;

    /**
     * Returns the move object from a description in long algebraic notation (LAN)
     *
     * @param lan      move described in Long Algebraic Notation
     * @param position position that the move would be played on
     * @return the described move object
     */
    public static int getMoveFromLAN(String lan, Position position, int[] moveBuffer) {
        int firstNonMove = generateAllMoves(position, moveBuffer, 0);

        for (int i = 0; i < firstNonMove; i++) {
            int move = moveBuffer[i];
            if (MoveEncoding.getLAN(move).equals(lan)) return move;
        }

        // Case that no generated move matches the lan description
        throw new IllegalArgumentException();
    }

    /**
    * Generates and returns a list of moves for a position. This must ONLY be used by tests,
    * because it allocates its own move buffer.
    */
    public static List<String> debugGenerateMoveList(Position position) {
        int[] moveBuffer = new int[256];

        List<String> moves = new LinkedList<>();

        int firstNonMove = generateAllMoves(position, moveBuffer, 0);

        for (int i = 0; i < firstNonMove; i++) {
            moves.add(MoveEncoding.getLAN(moveBuffer[i]));
        }

        return moves;
    }

    /**
     * Generates all legal moves and puts them in the move buffer starting at firstNonMove
     * Returns the index of the new firstNonMove
     *
     * @param position     position to find moves for
     * @param moveBuffer   place to put moves
     * @param firstNonMove location to put the first move
     * @return new first non move
     */
    public static int generateAllMoves(Position position, int[] moveBuffer, int firstNonMove) {
        computePins(position);

        int numCheckers = Long.bitCount(position.checkers);

        if (numCheckers == 0) {
            return generateMovesNoChecks(position, moveBuffer, firstNonMove);
        } else if (numCheckers == 1) {
            int checkerSquare = Long.numberOfTrailingZeros(position.checkers);
            int checkerType = position.getPieceType(checkerSquare);

            // Capturing the piece is allowed
            long validDestinations = 1L << checkerSquare;
            // If sliding, intercepting the attack is allowed
            if (checkerType == 2 || checkerType == 3 || checkerType == 4) {
                validDestinations |= AbsolutePins.inBetween[position.kingLocs[position.activePlayer]][checkerSquare];
            }
            return generateMovesOneCheck(position, moveBuffer, firstNonMove, validDestinations);
        } else {
            return generateMovesDoubleCheck(position, moveBuffer, firstNonMove);
        }
    }

    private static int generateMovesNoChecks(Position position, int[] moveBuffer, int firstNonMove) {
        firstNonMove = generatePawnMoves(position, moveBuffer, firstNonMove, ~0L, MODE_ALL);
        firstNonMove = generateKnightMoves(position, moveBuffer, firstNonMove, ~0L, MODE_ALL);
        firstNonMove = generateBishopMoves(position, moveBuffer, firstNonMove, ~0L, MODE_ALL);
        firstNonMove = generateRookMoves(position, moveBuffer, firstNonMove, ~0L, MODE_ALL);
        firstNonMove = generateQueenMoves(position, moveBuffer, firstNonMove, ~0L, MODE_ALL);
        firstNonMove = generateKingMoves(position, moveBuffer, firstNonMove, false, MODE_ALL);
        return firstNonMove;
    }

    private static int generateMovesOneCheck(Position position, int[] moveBuffer, int firstNonMove, long checkHandleMask) {
        firstNonMove = generatePawnMoves(position, moveBuffer, firstNonMove, checkHandleMask, MODE_ALL);
        firstNonMove = generateKnightMoves(position, moveBuffer, firstNonMove, checkHandleMask, MODE_ALL);
        firstNonMove = generateBishopMoves(position, moveBuffer, firstNonMove, checkHandleMask, MODE_ALL);
        firstNonMove = generateRookMoves(position, moveBuffer, firstNonMove, checkHandleMask, MODE_ALL);
        firstNonMove = generateQueenMoves(position, moveBuffer, firstNonMove, checkHandleMask, MODE_ALL);
        firstNonMove = generateKingMoves(position, moveBuffer, firstNonMove, true, MODE_ALL);
        return firstNonMove;
    }

    private static int generateMovesDoubleCheck(Position position, int[] moveBuffer, int firstNonMove) {
        return generateKingMoves(position, moveBuffer, firstNonMove, true, MODE_ALL);
    }

    /**
     * Specialized move generation function for Quiescence search
     * IF in check:
     * - Generate all moves (we want to consider all evasions)
     * ELSE:
     * - Generate only captures
     * <p>
     * We can generate
     */
    public static int generateQSearchMoves(Position position, int[] moveBuffer, int firstNonMove) {
        int numCheckers = Long.bitCount(position.checkers);

        if (numCheckers == 0) {
            computePins(position);
            firstNonMove = generatePawnMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
            firstNonMove = generateKnightMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
            firstNonMove = generateBishopMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
            firstNonMove = generateRookMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
            firstNonMove = generateQueenMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
            firstNonMove = generateKingMoves(position, moveBuffer, firstNonMove, false, MODE_CAPTURES);
            return firstNonMove;
        } else {
            return generateAllMoves(position, moveBuffer, firstNonMove);
        }
    }

    /**
     * Generates a bitboard representing the locations of checkers of the active player's king
     */
    public static long computeCheckers(Position position) {
        int kingSquare = position.kingLocs[position.activePlayer];
        // Get intersections of attacks from king and enemy pieces of the correct type
        long pawnAttacks = PawnLogic.getAttackBoard(kingSquare, position.activePlayer) & position.pieces[0] & position.pieceColors[1 - position.activePlayer];
        long knightAttacks = KnightLogic.getAttackBoard(kingSquare, position) & position.pieces[1] & position.pieceColors[1 - position.activePlayer];
        long bishopAttacks = BishopLogic.getAttackBoard(kingSquare, position.occupancy) & (position.pieces[2] | position.pieces[4]) & position.pieceColors[1 - position.activePlayer];
        long rookAttacks = RookLogic.getAttackBoard(kingSquare, position.occupancy) & (position.pieces[3] | position.pieces[4]) & position.pieceColors[1 - position.activePlayer];

        return pawnAttacks | knightAttacks | bishopAttacks | rookAttacks;
    }

    /**
     * Generates a bitboard representing the locations of attackers of a square.
     *
     * @param position position to check
     * @param square   to check for attackers
     * @return bitboard representing pieces of attackerColor that are attacking square in position
     */
    public static long getSEEAttackers(Position position, int square) {
        // Get intersections of attacks from king and enemy pieces of the correct type
        long pawnAttacks = PawnLogic.getAttackBoard(square, 0) & position.pieces[0] & position.pieceColors[1];
        pawnAttacks |= PawnLogic.getAttackBoard(square, 1) & position.pieces[0] & position.pieceColors[0];
        long knightAttacks = KnightLogic.getAttackBoard(square, position) & position.pieces[1];
        long bishopAttacks = BishopLogic.getAttackBoard(square, position.occupancy) & (position.pieces[2] | position.pieces[4]);
        long rookAttacks = RookLogic.getAttackBoard(square, position.occupancy) & (position.pieces[3] | position.pieces[4]);
        long kingAttacks = KingLogic.getKingAttacks(square) & position.pieces[5];

        return pawnAttacks | knightAttacks | bishopAttacks | rookAttacks | kingAttacks;
    }

    /**
     * Returns true if the king would be attacked if it moved to the move's destination
     *
     * @param move     move to check
     * @param position the move is applied to
     * @return if the move puts themselves in check
     */
    public static boolean kingMoveSelfInCheck(int move, Position position, int kingColor) {
        int kingLoc = Long.numberOfTrailingZeros(position.pieceColors[kingColor] & position.pieces[5]);
        long removedKingOccupancy = position.occupancy & ~(1L << kingLoc);
        long potentialAttackers = position.pieceColors[1 - kingColor];
        int destination = MoveEncoding.getDestination(move);

        return ((PawnLogic.getAttackBoard(destination, kingColor) & potentialAttackers & position.pieces[0]) != 0) || // Look at king color pawn move FROM the king's loc
                ((BishopLogic.getAttackBoard(destination, removedKingOccupancy) & potentialAttackers & (position.pieces[2] | position.pieces[4])) != 0) || ((RookLogic.getAttackBoard(destination, removedKingOccupancy) & potentialAttackers & (position.pieces[3] | position.pieces[4])) != 0) || ((KnightLogic.getAttackBoard(destination, position) & potentialAttackers & position.pieces[1]) != 0) || ((KingLogic.getKingAttacks(destination) & potentialAttackers & position.pieces[5]) != 0);
    }

    /**
     * Updates checks on the move and sees if its legal. If it's valid, it is added to the move buffer
     * and the new firstNonMove of the array is returned.
     *
     * @param move         move to add
     * @param moveBuffer   buffer to add to
     * @param firstNonMove index to add to
     * @param position     position move is applied to
     * @return new buffer location
     */
    private static int addAndValidateMove(int move, int[] moveBuffer, int firstNonMove, Position position) {
        moveBuffer[firstNonMove] = move;
        return ++firstNonMove;
    }

    /**
     * Checks if an EP move puts self in check
     * Simulates it and sees if self in check by discovery or
     */
    static boolean epIsValid(int start, int destination, Position position, long checkHandleMask) {
        int epCaptureSquare = position.enPassant - 8 + 16 * position.activePlayer;
        long modifiedOccupancy = position.occupancy ^ (1L << epCaptureSquare) ^ (1L << start) | (1L << destination); // Simulate the move

        // First check if the move causes a bishop check
        long kingBishopView = BishopLogic.getAttackBoard(position.kingLocs[position.activePlayer], modifiedOccupancy); // get attacks from the king (as bishop)
        long attackers = (position.pieces[2] | position.pieces[4]) & position.pieceColors[1 - position.activePlayer];
        if ((attackers & kingBishopView) != 0) {
            return false;
        }

        // then check if it causes a rook check
        long kingRookView = RookLogic.getAttackBoard(position.kingLocs[position.activePlayer], modifiedOccupancy);
        attackers = (position.pieces[3] | position.pieces[4]) & position.pieceColors[1 - position.activePlayer];
        if ((attackers & kingRookView) != 0) {
            return false;
        }

        // fail if checker is non-slider AND it isn't captured
        return Long.bitCount(checkHandleMask) != 1 || Long.numberOfTrailingZeros(checkHandleMask) == epCaptureSquare;

        // resultant isn't attacked by a slider, or the attacker was stopped by
    }


    /**
     * Generates and returns all pawn moves
     * En Passant moves have the issue that they remove two pieces on the same rank which can lead to a discovered attack
     * Additionally the captured piece isn't the square that the pawn moves to, leading to not seeing an ep as capturing a checker
     * These are handled manually in the ep generation section
     * <p>
     * For EP, we need to make sure:
     * - new occupancy doesn't cause a discovery on the same rank
     * - removing the captured pawn doesn't cause a bishop discovery
     * - capturing the pawn is considered removing a checker
     *
     * @param position     to generate moves for
     * @param moveBuffer   to fill moves
     * @param firstNonMove index of moveBuffer to start adding
     * @return firstNonMove updated
     */
    private static int generatePawnMoves(Position position, int[] moveBuffer, int firstNonMove, long checkHandleMask, int mode) {
        long pawnList = position.pieces[Piece.PAWN] & position.pieceColors[position.activePlayer];

        while (pawnList != 0L) {
            // Get location of the first pawn on the pawn bitboard, and remove it.
            int start = Long.numberOfTrailingZeros(pawnList);
            pawnList &= (pawnList - 1);

            // Pin mask is a bitboard that represents the moves that are legal given the piece's pinned status.
            long pinMask = ~0L;
            if ((position.pinnedBB & (1L << start)) != 0) {
                pinMask = AbsolutePins.pinRay[position.kingLocs[position.activePlayer]][start];
            }

            // ===== Captures (including capture-promotions and EP) =====
            if (mode != MODE_QUIETS) {
                // Handle captures
                long destinations = PawnLogic.getCaptures(start, position) & pinMask & checkHandleMask;
                while (destinations != 0) {
                    int destination = Long.numberOfTrailingZeros(destinations);
                    destinations &= (destinations - 1);

                    if (destination / 8 == 0 || destination / 8 == 7) {
                        int moveN = MoveShortcuts.generatePawnPromotionCapture(start, destination, 1, position);
                        int moveB = MoveShortcuts.generatePawnPromotionCapture(start, destination, 2, position);
                        int moveR = MoveShortcuts.generatePawnPromotionCapture(start, destination, 3, position);
                        int moveQ = MoveShortcuts.generatePawnPromotionCapture(start, destination, 4, position);

                        firstNonMove = addAndValidateMove(moveN, moveBuffer, firstNonMove, position);
                        firstNonMove = addAndValidateMove(moveB, moveBuffer, firstNonMove, position);
                        firstNonMove = addAndValidateMove(moveR, moveBuffer, firstNonMove, position);
                        firstNonMove = addAndValidateMove(moveQ, moveBuffer, firstNonMove, position);

                    } else {
                        int move = MoveShortcuts.generatePawnCapture(start, destination, position);
                        firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                    }
                }

                // Handle En Passant
                long enPassantMoves = PawnLogic.getEnPassant(start, position) & pinMask;
                while (enPassantMoves != 0) {
                    int destination = Long.numberOfTrailingZeros(enPassantMoves);
                    enPassantMoves &= (enPassantMoves - 1);
                    int move = MoveShortcuts.generatePawnEnPassant(start, destination, position);
                    if (epIsValid(start, destination, position, checkHandleMask)) {
                        firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                    }
                }
            }

            // ===== Quiet Moves =====
            if (mode != MODE_CAPTURES) {
                long quietMoves = PawnLogic.getQuietMoves(start, position) & pinMask & checkHandleMask;
                while (quietMoves != 0) {
                    int destination = Long.numberOfTrailingZeros(quietMoves);
                    quietMoves &= (quietMoves - 1);

                    if (destination / 8 == 0 || destination / 8 == 7) {
                        int moveN = MoveShortcuts.generatePawnPromotionNoCapture(start, destination, 1, position);
                        int moveB = MoveShortcuts.generatePawnPromotionNoCapture(start, destination, 2, position);
                        int moveR = MoveShortcuts.generatePawnPromotionNoCapture(start, destination, 3, position);
                        int moveQ = MoveShortcuts.generatePawnPromotionNoCapture(start, destination, 4, position);

                        firstNonMove = addAndValidateMove(moveN, moveBuffer, firstNonMove, position);
                        firstNonMove = addAndValidateMove(moveB, moveBuffer, firstNonMove, position);
                        firstNonMove = addAndValidateMove(moveR, moveBuffer, firstNonMove, position);
                        firstNonMove = addAndValidateMove(moveQ, moveBuffer, firstNonMove, position);
                    } else if (Math.abs(destination - start) == 16) {
                        int move = MoveShortcuts.generatePawnDoublePush(start, destination, position);
                        firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                    } else {
                        int move = MoveShortcuts.generatePawnSinglePush(start, destination, position);
                        firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                    }
                }
            }
        }
        return firstNonMove;
    }

    /**
     * Generates and returns all Rook moves
     *
     * @param position     to generate moves for
     * @param moveBuffer   to fill moves
     * @param firstNonMove index of moveBuffer to start adding
     * @return updated firstNonMove
     */
    private static int generateRookMoves(Position position, int[] moveBuffer, int firstNonMove, long checkHandleMask, int mode) {
        long rookList = (position.pieceColors[position.activePlayer]) & position.pieces[3];
        // Iterate over the rook positions using bitwise manipulation
        while (rookList != 0L) {
            int start = Long.numberOfTrailingZeros(rookList);
            rookList &= (rookList - 1);

            long pinMask = ~0L;
            if ((position.pinnedBB & (1L << start)) != 0) {
                pinMask = AbsolutePins.pinRay[position.kingLocs[position.activePlayer]][start];
            }

            long moves = RookLogic.getMoveBoard(start, position) & pinMask & checkHandleMask;

            // Process capture moves
            if (mode != MODE_QUIETS) {
                long captureDestinations = moves & position.pieceColors[1 - position.activePlayer];
                while (captureDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(captureDestinations);
                    captureDestinations &= (captureDestinations - 1);

                    int move = MoveShortcuts.generateRookCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }

            // Process quiet moves
            if (mode != MODE_CAPTURES) {
                long quietDestinations = moves & ~position.occupancy;
                while (quietDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(quietDestinations);
                    quietDestinations &= (quietDestinations - 1);

                    int move = MoveShortcuts.generateRookNoCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }
        }
        return firstNonMove;
    }

    /**
     * Generates and returns all bishop moves
     *
     * @param position to generate moves for
     * @return Move list
     */
    private static int generateBishopMoves(Position position, int[] moveBuffer, int firstNonMove, long checkHandleMask, int mode) {
        long bishopList = position.pieceColors[position.activePlayer] & position.pieces[2];
        // Iterate over bishop positions using bitwise manipulation
        while (bishopList != 0L) {
            int start = Long.numberOfTrailingZeros(bishopList);
            bishopList &= (bishopList - 1);

            long pinMask = ~0L;
            if ((position.pinnedBB & (1L << start)) != 0) {
                pinMask = AbsolutePins.pinRay[position.kingLocs[position.activePlayer]][start];
            }

            long moves = BishopLogic.getMoveBoard(start, position) & pinMask & checkHandleMask;

            // Process capture moves
            if (mode != MODE_QUIETS) {
                long captureDestinations = moves & position.pieceColors[1 - position.activePlayer];
                while (captureDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(captureDestinations);
                    captureDestinations &= (captureDestinations - 1);

                    int move = MoveShortcuts.generateBishopCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }

            // Process quiet moves
            if (mode != MODE_CAPTURES) {
                long quietDestinations = moves & ~position.occupancy;
                while (quietDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(quietDestinations);
                    quietDestinations &= (quietDestinations - 1);

                    int move = MoveShortcuts.generateBishopNoCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }
        }
        return firstNonMove;
    }

    /**
     * Generates and returns all knight moves
     *
     * @param position to generate moves for
     * @return Move list
     */
    private static int generateKnightMoves(Position position, int[] moveBuffer, int firstNonMove, long checkHandleMask, int mode) {
        long knightList = position.pieceColors[position.activePlayer] & position.pieces[1];

        // Iterate over knight positions using bitwise manipulation
        while (knightList != 0L) {
            int start = Long.numberOfTrailingZeros(knightList);
            knightList &= (knightList - 1);

            long pinMask = ~0L;
            if ((position.pinnedBB & (1L << start)) != 0) {
                pinMask = AbsolutePins.pinRay[position.kingLocs[position.activePlayer]][start];
            }

            // Process capture moves
            if (mode != MODE_QUIETS) {
                long captureDestinations = KnightLogic.getCaptures(start, position) & pinMask & checkHandleMask;
                while (captureDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(captureDestinations);
                    captureDestinations &= (captureDestinations - 1);
                    int move = MoveShortcuts.generateKnightCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }

            // Process quiet moves
            if (mode != MODE_CAPTURES) {
                long quietDestinations = KnightLogic.getQuietMoves(start, position) & pinMask & checkHandleMask;
                while (quietDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(quietDestinations);
                    quietDestinations &= (quietDestinations - 1);

                    int move = MoveShortcuts.generateKnightNoCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }
        }
        return firstNonMove;
    }

    /**
     * Generates and returns all king moves
     *
     * @param position position to generate moves for
     * @return Move list
     */
    private static int generateKingMoves(Position position, int[] moveBuffer, int firstNonMove, boolean inCheck, int mode) {
        long kingList = position.pieceColors[position.activePlayer] & position.pieces[5];

        // Iterate over king positions using bitwise manipulation
        while (kingList != 0L) {
            int start = Long.numberOfTrailingZeros(kingList);
            kingList &= (kingList - 1);

            // Process capture moves
            if (mode != MODE_QUIETS) {
                long captureDestinations = KingLogic.getCaptures(start, position);
                while (captureDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(captureDestinations);
                    captureDestinations &= (captureDestinations - 1);

                    int move = MoveShortcuts.generateKingCapture(start, destination, position);
                    if (!kingMoveSelfInCheck(move, position, position.activePlayer))
                        firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }

            // Process quiet moves and castling
            if (mode != MODE_CAPTURES) {
                long quietDestinations = KingLogic.getQuietMoves(start, position);
                while (quietDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(quietDestinations);
                    quietDestinations &= (quietDestinations - 1);

                    int move = MoveShortcuts.generateKingNoCapture(start, destination, position);

                    if (!kingMoveSelfInCheck(move, position, position.activePlayer))
                        firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }

                // Process castling moves
                long castleDestinations = KingLogic.generateCastles(start, position);
                while (castleDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(castleDestinations);
                    castleDestinations &= (castleDestinations - 1);

                    // Check if they move through or are in check
                    if (castleSquaresAttacked(position, destination, inCheck)) continue;

                    int move;

                    if (destination == PositionConstants.CASTLE_DEST_WQ || destination == PositionConstants.CASTLE_DEST_BQ) {
                        move = MoveShortcuts.generateKingQueenSideCastle(start, destination, position);
                    } else {
                        move = MoveShortcuts.generateKingKingSideCastle(start, destination, position);
                    }

                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }
        }

        return firstNonMove;
    }

    /**
     * Generates and returns all queen moves
     *
     * @param position to generate moves for
     * @return Move list
     */
    private static int generateQueenMoves(Position position, int[] moveBuffer, int firstNonMove, long checkHandleMask, int mode) {
        long queenList = position.pieceColors[position.activePlayer] & position.pieces[4];

        // Iterate over queen positions using bitwise manipulation
        while (queenList != 0L) {
            int start = Long.numberOfTrailingZeros(queenList);
            queenList &= (queenList - 1);

            long pinMask = ~0L;
            if ((position.pinnedBB & (1L << start)) != 0) {
                pinMask = AbsolutePins.pinRay[position.kingLocs[position.activePlayer]][start];
            }

            long moves = (RookLogic.getMoveBoard(start, position) | BishopLogic.getMoveBoard(start, position)) & pinMask & checkHandleMask;

            // Process capture moves
            if (mode != MODE_QUIETS) {
                long captureDestinations = moves & position.pieceColors[1 - position.activePlayer];
                while (captureDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(captureDestinations);
                    captureDestinations &= (captureDestinations - 1);

                    int move = MoveShortcuts.generateQueenCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }

            // Process quiet moves
            if (mode != MODE_CAPTURES) {
                long quietDestinations = moves & ~position.occupancy;
                while (quietDestinations != 0) {
                    int destination = Long.numberOfTrailingZeros(quietDestinations);
                    quietDestinations &= (quietDestinations - 1);

                    int move = MoveShortcuts.generateQueenNoCapture(start, destination, position);
                    firstNonMove = addAndValidateMove(move, moveBuffer, firstNonMove, position);
                }
            }
        }

        return firstNonMove;
    }


    // ======================== Staged Move Generation Methods ========================

    /**
     * Generates only captures (including EP and capture-promotions) for the no-check case.
     * Caller MUST have called computePins() first.
     */
    public static int generateCapturesNoPins(Position position, int[] moveBuffer, int firstNonMove) {
        firstNonMove = generatePawnMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
        firstNonMove = generateKnightMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
        firstNonMove = generateBishopMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
        firstNonMove = generateRookMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
        firstNonMove = generateQueenMoves(position, moveBuffer, firstNonMove, ~0L, MODE_CAPTURES);
        firstNonMove = generateKingMoves(position, moveBuffer, firstNonMove, false, MODE_CAPTURES);
        return firstNonMove;
    }

    /**
     * Generates only quiet moves (non-captures, including quiet promotions and castling) for the no-check case.
     * Caller MUST have called computePins() first.
     */
    public static int generateQuietsNoPins(Position position, int[] moveBuffer, int firstNonMove) {
        firstNonMove = generatePawnMoves(position, moveBuffer, firstNonMove, ~0L, MODE_QUIETS);
        firstNonMove = generateKnightMoves(position, moveBuffer, firstNonMove, ~0L, MODE_QUIETS);
        firstNonMove = generateBishopMoves(position, moveBuffer, firstNonMove, ~0L, MODE_QUIETS);
        firstNonMove = generateRookMoves(position, moveBuffer, firstNonMove, ~0L, MODE_QUIETS);
        firstNonMove = generateQueenMoves(position, moveBuffer, firstNonMove, ~0L, MODE_QUIETS);
        firstNonMove = generateKingMoves(position, moveBuffer, firstNonMove, false, MODE_QUIETS);
        return firstNonMove;
    }

    /**
     * Generates all legal moves without calling computePins (caller must have done so).
     * Used by MovePicker for the in-check path where pins are already computed.
     */
    public static int generateAllMovesNoPins(Position position, int[] moveBuffer, int firstNonMove) {
        int numCheckers = Long.bitCount(position.checkers);

        if (numCheckers == 0) {
            return generateMovesNoChecks(position, moveBuffer, firstNonMove);
        } else if (numCheckers == 1) {
            int checkerSquare = Long.numberOfTrailingZeros(position.checkers);
            int checkerType = position.getPieceType(checkerSquare);

            long validDestinations = 1L << checkerSquare;
            if (checkerType == Piece.BISHOP || checkerType == Piece.ROOK || checkerType == Piece.QUEEN) {
                validDestinations |= AbsolutePins.inBetween[position.kingLocs[position.activePlayer]][checkerSquare];
            }
            return generateMovesOneCheck(position, moveBuffer, firstNonMove, validDestinations);
        } else {
            return generateMovesDoubleCheck(position, moveBuffer, firstNonMove);
        }
    }

    // ======================== Single-Move Legality Check ========================

    /**
     * Checks if a single move is legal in the current position.
     * Used for validating TT moves and killer moves without full move generation.
     * <p>
     * REQUIRES: computePins() has been called on the position.
     *
     * @param position the current position (with pins computed)
     * @param move     the int-encoded move to validate
     * @return true if the move is legal
     */
    public static boolean isMoveLegal(Position position, int move) {
        if (move == 0) return false;

        int start = MoveEncoding.getStart(move);
        int dest = MoveEncoding.getDestination(move);
        int movedPiece = MoveEncoding.getMovedPiece(move);

        // Start square must have the correct piece and color
        long startBit = 1L << start;
        if ((position.pieceColors[position.activePlayer] & startBit) == 0) return false;
        if (position.getPieceType(start) != movedPiece) return false;

        long destBit = 1L << dest;
        int numCheckers = Long.bitCount(position.checkers);
        boolean isEP = MoveEncoding.getIsEP(move);
        boolean isCastle = MoveEncoding.getIsCastle(move);

        // Double check: only king moves legal
        if (numCheckers >= 2 && movedPiece != Piece.KING) return false;

        // Compute check handle mask for single check (non-king pieces)
        long checkHandleMask = ~0L;
        if (numCheckers == 1) {
            int checkerSquare = Long.numberOfTrailingZeros(position.checkers);
            int checkerType = position.getPieceType(checkerSquare);
            checkHandleMask = 1L << checkerSquare;
            if (checkerType == Piece.BISHOP || checkerType == Piece.ROOK || checkerType == Piece.QUEEN) {
                checkHandleMask |= AbsolutePins.inBetween[position.kingLocs[position.activePlayer]][checkerSquare];
            }
        }

        // ===== King moves =====
        if (movedPiece == Piece.KING) {
            if (isCastle) {
                if (numCheckers > 0) return false; // can't castle in check
                // Check castle conditions (rights, path clear, rook present)
                long castleDestinations = KingLogic.generateCastles(start, position);
                if ((destBit & castleDestinations) == 0) return false;
                // Check not through check
                return !castleSquaresAttacked(position, dest, false);
            } else {
                // Destination must be in king attack set
                if ((KingLogic.getKingAttacks(start) & destBit) == 0) return false;
                // Destination must not have own piece
                if ((position.pieceColors[position.activePlayer] & destBit) != 0) return false;
                // If capture, must have enemy piece with correct type; if quiet, must be empty
                if (MoveEncoding.getIsCapture(move)) {
                    if ((position.pieceColors[1 - position.activePlayer] & destBit) == 0) return false;
                    if (position.getPieceType(dest) != MoveEncoding.getCapturedPiece(move)) return false;
                } else {
                    if ((position.occupancy & destBit) != 0) return false;
                }
                // Check self-in-check
                return !kingMoveSelfInCheck(move, position, position.activePlayer);
            }
        }

        // ===== Non-king pieces: capture/quiet board state validation =====
        // TT hash collisions can produce moves with capture flags that don't match reality.
        // If we make such a move, the position state gets corrupted during make/unmake.
        if (!isEP) {
            boolean isCapture = MoveEncoding.getIsCapture(move);
            if (isCapture) {
                // Must have an enemy piece at the destination
                if ((position.pieceColors[1 - position.activePlayer] & destBit) == 0) return false;
                // Captured piece type must match what's actually on the board
                if (position.getPieceType(dest) != MoveEncoding.getCapturedPiece(move)) return false;
            } else {
                // Quiet move: destination must be empty
                if ((position.occupancy & destBit) != 0) return false;
            }
        }

        // ===== Non-king pieces: pin constraint =====
        if ((position.pinnedBB & (1L << start)) != 0) {
            long pinMask = AbsolutePins.pinRay[position.kingLocs[position.activePlayer]][start];
            if ((destBit & pinMask) == 0) return false;
        }

        // ===== Piece-specific reachability =====
        switch (movedPiece) {
            case Piece.PAWN:
                if (isEP) {
                    // EP: check EP square matches and pawn can attack it
                    if ((PawnLogic.getEnPassant(start, position) & destBit) == 0) return false;
                    // Validate EP (discovery checks, check handling)
                    return epIsValid(start, dest, position, checkHandleMask);
                } else if (MoveEncoding.getIsCapture(move)) {
                    // Pawn capture: destination must be reachable and on checkHandleMask
                    return (PawnLogic.getCaptures(start, position) & destBit & checkHandleMask) != 0;
                } else {
                    // Quiet pawn push: destination must be reachable and on checkHandleMask
                    return (PawnLogic.getQuietMoves(start, position) & destBit & checkHandleMask) != 0;
                }

            case Piece.KNIGHT:
                // getMoveBoard filters own pieces; combine with checkHandleMask
                return (KnightLogic.getMoveBoard(start, position) & destBit & checkHandleMask) != 0;

            case Piece.BISHOP:
                return (BishopLogic.getMoveBoard(start, position) & destBit & checkHandleMask) != 0;

            case Piece.ROOK:
                return (RookLogic.getMoveBoard(start, position) & destBit & checkHandleMask) != 0;

            case Piece.QUEEN:
                return ((BishopLogic.getMoveBoard(start, position) | RookLogic.getMoveBoard(start, position))
                        & destBit & checkHandleMask) != 0;

            default:
                return false;
        }
    }


    /**
     * Returns if castle squares are attacked
     * Only checks starting square and move-through square
     *
     * @param position    position to check
     * @param destination destination of the king
     * @return if the castle move is valid
     */
    static boolean castleSquaresAttacked(Position position, int destination, boolean inCheck) {
        boolean squareAttacked = false;
        if (inCheck) {
            return true;
        }
        if (destination == PositionConstants.CASTLE_DEST_WQ) {
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_PASSTHROUGH_WQ, 1);
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_DEST_WQ, 1);
        } else if (destination == PositionConstants.CASTLE_DEST_WK) {
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_PASSTHROUGH_WK, 1);
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_DEST_WK, 1);
        } else if (destination == PositionConstants.CASTLE_DEST_BQ) {
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_PASSTHROUGH_BQ, 0);
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_DEST_BQ, 0);
        } else if (destination == PositionConstants.CASTLE_DEST_BK) {
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_PASSTHROUGH_BK, 0);
            squareAttacked |= squareAttackedBy(position, PositionConstants.CASTLE_DEST_BK, 0);
        }
        return squareAttacked;
    }


    /**
     * Returns if a square is attacked by the specified color
     *
     * @param position    position to check
     * @param square      square to check
     * @param attackColor color of attacking pieces
     * @return squareAttacked if square is attacked by specified color
     */
    public static boolean squareAttackedBy(Position position, int square, int attackColor) {
        long potentialAttackers = position.pieceColors[attackColor];
        return ((PawnLogic.getAttackBoard(square, 1 - attackColor) & potentialAttackers & position.pieces[0]) != 0) || ((BishopLogic.getAttackBoard(square, position) & potentialAttackers & (position.pieces[2] | position.pieces[4])) != 0) || ((RookLogic.getAttackBoard(square, position) & potentialAttackers & (position.pieces[3] | position.pieces[4])) != 0) || ((KnightLogic.getAttackBoard(square, position) & potentialAttackers & position.pieces[1]) != 0) || ((KingLogic.getKingAttacks(square) & potentialAttackers & position.pieces[5]) != 0);
    }

    /**
     * Returns if a king of the specified color is attacked
     *
     * @param position  position to check
     * @param kingColor color of the king to check
     * @return true if the king is attacked
     */
    public static boolean kingAttacked(Position position, int kingColor) {
        int square = position.kingLocs[kingColor];
        int attackColor = 1 - kingColor;
        return squareAttackedBy(position, square, attackColor);
    }

    /**
     * A pinned piece is a piece that is movement restricted because it is intercepting an attack that would otherwise
     * hit the active player's king.
     * Sets position.pinnedBB, a bitboard with bits set for each pinned piece.
     *
     * @param position position to compute pins on
     */
    public static void computePins(Position position) {
        long pinnedBB = 0L;

        // Get potential pinners that move like a rook
        long rookAttackPieces = (position.pieces[3] | position.pieces[4]) & position.pieceColors[1 - position.activePlayer];

        // Get xray attacks if the king moved like a rook
        long rookXRay = RookLogic.xrayAttacks(position.kingLocs[position.activePlayer], position);

        // Get intersection of rooks and xray
        long pinningPieces = rookAttackPieces & rookXRay;

        while (pinningPieces != 0) {
            int rookAttackPiece = Long.numberOfTrailingZeros(pinningPieces);
            pinningPieces &= (pinningPieces - 1);

            int blockerPiece = Long.numberOfTrailingZeros(AbsolutePins.inBetween[rookAttackPiece][position.kingLocs[position.activePlayer]] & position.pieceColors[position.activePlayer]);

            // Case that the attack isn't blocked
            if (blockerPiece == 64) {
                continue;
            }

            pinnedBB |= (1L << blockerPiece);
        }

        long bishopAttackPieces = (position.pieces[2] | position.pieces[4]) & position.pieceColors[1 - position.activePlayer];

        long bishopXRay = BishopLogic.xrayAttacks(position.kingLocs[position.activePlayer], position);

        pinningPieces = bishopAttackPieces & bishopXRay;

        while (pinningPieces != 0) {
            int bishopAttackPiece = Long.numberOfTrailingZeros(pinningPieces);
            int blockerPiece = Long.numberOfTrailingZeros(AbsolutePins.inBetween[bishopAttackPiece][position.kingLocs[position.activePlayer]] & position.pieceColors[position.activePlayer]);

            pinningPieces &= (pinningPieces - 1);
            if (blockerPiece == 64) {
                continue;
            }

            pinnedBB |= (1L << blockerPiece);
        }

        position.pinnedBB = pinnedBB;
    }
}
