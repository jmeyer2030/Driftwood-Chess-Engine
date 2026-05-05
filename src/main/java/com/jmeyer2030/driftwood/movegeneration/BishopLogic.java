package com.jmeyer2030.driftwood.movegeneration;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

import com.jmeyer2030.driftwood.board.Position;

public class BishopLogic {

    // Masks for blocker locations on each square
    private static final long[] blockerMasks = new long[64];

    // Move boards corresponding with potential blockers
    private static final long[][] moveBoards = new long[64][];

    // Pre-computed magic numbers
    private static final long[] magicNumbers =
            {4762574765071009120L, 3035709978020225024L, -8573683670269558392L, -9220943688114896895L,
             1297336034757443584L, 2550944558485632L, 149208143149662272L, 1338112498205186L,
             439171341070125185L, 45222333252352L, 1873655792922535428L, 343439132422310928L,
             353013042521088L, 2305854009439158340L, -8069883179750587120L, 27049637468217344L,
             -8628333643761958654L, 1154082662173008656L, 18577434498828544L, -9058946528189546096L,
             1145837178716186L, 290622914533793800L, 3476858079382085825L, 4630263437925057024L,
             1161937778154948608L, 2320622644582946816L, -9223332452153949152L, 18155273571139616L,
             54324739509862405L, 83884490930262144L, 18165031879837696L, 9572382600626306L,
             72693189070299136L, 9289911334110208L, 577094414598930468L, 2201185943680L,
             81065360228418576L, 22523500137418753L, 288512959230576648L, -9222806887340420094L,
             6922597914221085824L, 576610457717319936L, 864726390270869632L, 4629709350871499012L,
             4613973020062548224L, 602635484790816L, 9715301998433312L, 2308104996826120320L,
             4613094497927045643L, 81139568691150921L, 289357376730955792L, 576496075221893248L,
             1134833477355536L, -9187236303706389498L, 4538955816263680L, 94909848055062536L,
             2288102087338000L, 325109097941772320L, 4971974126610155521L, 4904736654213906962L,
             576531192185225748L, 4629700692083376256L, 1152957805956301832L, 293299211259118089L};

    // Number of potential blockers for each square
    private static final int[] numBits = new int[]
            {6, 5, 5, 5, 5, 5, 5, 6,
             5, 5, 5, 5, 5, 5, 5, 5,
             5, 5, 7, 7, 7, 7, 5, 5,
             5, 5, 7, 9, 9, 7, 5, 5,
             5, 5, 7, 9, 9, 7, 5, 5,
             5, 5, 7, 7, 7, 7, 5, 5,
             5, 5, 5, 5, 5, 5, 5, 5,
             6, 5, 5, 5, 5, 5, 5, 6};

    static {
        generateBlockerMasks();
        populateMoveBoards();
    }

    /*
     * Public move-generation methods
     */

    /**
     * Returns a move main.java.board given a square and position
     *
     * @param square   square
     * @param position position
     * @return bitboard of all moves
     */
    public static long getMoveBoard(int square, Position position) {
        long activePlayerPieces = position.pieceColors[position.activePlayer];
        long blockerBoard = position.occupancy & blockerMasks[square];
        int index = getIndexForBlocker(blockerBoard, square);
        return moveBoards[square][index] & ~activePlayerPieces;
    }

    /**
     * @param square   square
     * @param position position
     * @return bitboard of captures
     */
    public static long getCaptures(int square, Position position) {
        return getMoveBoard(square, position) & position.occupancy;
    }

    /**
     * @param square   square
     * @param position position
     * @return bitboard of quiet moves
     */
    public static long getQuietMoves(int square, Position position) {
        return getMoveBoard(square, position) & ~position.occupancy;
    }

    /**
     * Returns an attack main.java.board given a square and occupancy main.java.board
     *
     * @param square   square
     * @param position position
     * @return attackBoard of all attacks
     */
    public static long getAttackBoard(int square, Position position) {
        return moveBoards[square][getIndexForBlocker(position.occupancy & blockerMasks[square], square)];
    }

    /**
     * @param square    square
     * @param occupancy
     * @return bitboard of attacks
     */
    public static long getAttackBoard(int square, long occupancy) {
        long blockerBoard = occupancy & blockerMasks[square];
        int index = getIndexForBlocker(blockerBoard, square);
        return moveBoards[square][index];
    }

    /**
     * returns a bitboard representing the attacks of a slider behind and including the blocker
     *
     * @param square   square
     * @param position position
     * @return attacks if they see through the first blocker
     */
    public static long xrayAttacks(int square, Position position) {
        // Get the Attacks of the piece on the square
        long attacks = getAttackBoard(square, position);

        // Get the intersection of attacks and active player pieces
        long activePlayerBlockers = (position.pieceColors[position.activePlayer]) & attacks;

        long occupancyWithoutFriendlyBlockers = position.occupancy ^ activePlayerBlockers;

        // Equivalent to (attacksWithoutBlockers ^ attacks) | (attacks & attacksWithoutBlockers)
        return getAttackBoard(square, occupancyWithoutFriendlyBlockers);
    }

    /*
     * Private methods primarily for generating move boards or indexing
     */

    /**
     * Given a blocker board and square, returns the index of the corresponding move board
     *
     * @param blockerBoard blockerboard
     * @param square       square
     * @return index of moveBoards that corresponds with the blockerboard for a bishop on that square
     */
    private static int getIndexForBlocker(long blockerBoard, int square) {
        return (int) ((magicNumbers[square] * blockerBoard) >> (64 - numBits[square])
                & ((1 << numBits[square]) - 1));
    }

    /**
     * populates long[][] with moveboards indexable with the magic numbers
     */
    private static void populateMoveBoards() {
        List<List<Long>> tempBlockerBoards = generateAllBlockerBoards();
        List<List<Long>> unsortedMoveBoards = generateAllMoveBoards(tempBlockerBoards);

        List<List<Long>> moveBoardsList = sortAllMoveBoards(unsortedMoveBoards, tempBlockerBoards);

        for (int i = 0; i < 64; i++) {
            moveBoards[i] = moveBoardsList.get(i).stream().mapToLong(Long::longValue).toArray();
        }
    }

    /**
     * Returns all blockerBoards for a given blockerMask, each
     *
     * @param blockerMask an arbitrary blocker-mask
     * @return list of all blockerBoards for a blockerMask
     */
    private static List<Long> generateBlockerBoards(long blockerMask) {
        String binaryRep = Long.toBinaryString(blockerMask);
        int length = binaryRep.length();

        // Find positions of '1's
        List<Integer> onesPositions = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (binaryRep.charAt(i) == '1') {
                onesPositions.add(i);
            }
        }

        List<Long> permutations = new ArrayList<>();
        // Generate all subsets of onesPositions using bitmasking
        for (int mask = 0; mask < (1 << onesPositions.size()); mask++) {
            long perm = blockerMask;
            for (int i = 0; i < onesPositions.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    // Flip the corresponding bit to 0
                    perm &= ~(1L << (length - onesPositions.get(i) - 1));
                }
            }
            permutations.add(perm);
        }

        return permutations;
    }

    /**
     * Sorts moveBoards according to the computed index that they should be at
     *
     * @param square        square
     * @param moveBoards    moveBoards of square
     * @param blockerBoards blockerBoards of square
     */
    private static List<Long> sortMoveBoards(int square, List<Long> moveBoards, List<Long> blockerBoards) {
        // Combine moveBoards and blockerBoards into pairs
        List<Pair> combinedList = new ArrayList<>();
        for (int i = 0; i < moveBoards.size(); i++) {
            long moveBoard = moveBoards.get(i);
            long blockerBoard = blockerBoards.get(i);
            int index = getIndexForBlocker(blockerBoard, square);  // Get the corresponding index
            combinedList.add(new Pair(moveBoard, index));
        }

        // Sort the combined list by the index (second element of the pair)
        combinedList.sort(Comparator.comparingInt(a -> a.index));

        // Extract the sorted moveBoards
        List<Long> sortedMoveBoards = new ArrayList<>();
        for (Pair pair : combinedList) {
            sortedMoveBoards.add(pair.moveBoard);
        }

        return sortedMoveBoards;
    }


    /**
     * Pair class to hold the moveBoard and its associated index
     */
    private static class Pair {
        final long moveBoard;
        final int index;

        Pair(long moveBoard, int index) {
            this.moveBoard = moveBoard;
            this.index = index;
        }
    }

    /**
     * sorts each moveBoard based on its computed magic index
     *
     * @param unsortedMoveBoards moveBoards
     * @param blockerBoards      blockerBoards
     * @return sorted move boards such that indexing works
     */
    private static List<List<Long>> sortAllMoveBoards(List<List<Long>> unsortedMoveBoards, List<List<Long>> blockerBoards) {
        List<List<Long>> moveBoards = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            moveBoards.add(sortMoveBoards(i, unsortedMoveBoards.get(i), blockerBoards.get(i)));
        }
        return moveBoards;
    }


    /**
     * Generates the corresponding list of moveBoards
     *
     * @param square square
     * @return moveBoardList
     */
    private static List<Long> generateMoveBoards(int square, List<Long> blockerBoards) {
        List<Long> moveBoardList = new ArrayList<>();
        for (Long blockerBoard : blockerBoards) {
            moveBoardList.add(generateMoveBoard(blockerBoard, square));
        }

        return moveBoardList;
    }

    /**
     * Returns an array of all blockerboards on each square
     *
     * @return List<List < Long>> blockerBoards, every blocker configuration for each square
     */
    private static List<List<Long>> generateAllBlockerBoards() {
        List<List<Long>> blockerBoards = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            blockerBoards.add(generateBlockerBoards(blockerMasks[i]));
        }
        return blockerBoards;
    }

    /**
     * Returns a List<List<Long>> of all move boards
     *
     * @param blockerBoards blockerBoards
     * @return moveBoards
     */
    private static List<List<Long>> generateAllMoveBoards(List<List<Long>> blockerBoards) {
        List<List<Long>> moveBoards = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            moveBoards.add(generateMoveBoards(i, blockerBoards.get(i)));
        }

        return moveBoards;
    }


    /**
     * Generates all potential locations of blockers for each square
     */
    private static void generateBlockerMasks() {
        for (int i = 0; i < 64; i++) {
            blockerMasks[i] = generateBlockerMask(i);
        }
    }

    /**
     * Returns a bishop blocker-mask for a given square
     * Blocker Mask: BB of all potential blocker locations
     *
     * @param square an integer between 0 and 63
     * @return a bishop blockerMask if the bishop were on that square
     */
    private static long generateBlockerMask(int square) {
        long result = 0L;

        int rankLoc = square / 8;
        int fileLoc = square % 8;

        // Diagonal (/ direction)
        for (int rank = rankLoc + 1, file = fileLoc + 1; rank < 7 && file < 7; rank++, file++) {
            result |= (1L << (rank * 8 + file));
        }
        for (int rank = rankLoc - 1, file = fileLoc - 1; rank >= 1 && file >= 1; rank--, file--) {
            result |= (1L << (rank * 8 + file));
        }

        // Anti-diagonal (\ direction)
        for (int rank = rankLoc - 1, file = fileLoc + 1; rank >= 1 && file < 7; rank--, file++) {
            result |= (1L << (rank * 8 + file));
        }
        for (int rank = rankLoc + 1, file = fileLoc - 1; rank < 7 && file >= 1; rank++, file--) {
            result |= (1L << (rank * 8 + file));
        }

        return result;
    }

    /**
     * Returns a move board for a bishop blockerBoard
     *
     * @param blockerBoard, a bishop blockerBoard
     * @param square,       the square associated with the blockerBoard
     * @return the moveBoard for that blockerBoard
     */
    private static long generateMoveBoard(long blockerBoard, int square) {
        int rankLoc = square / 8;
        int fileLoc = square % 8;

        long moveBoard = 0;

        // Diagonal (/ direction)
        for (int rank = rankLoc + 1, file = fileLoc + 1; rank < 8 && file < 8; rank++, file++) {
            int currentLoc = 8 * rank + file;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }
        for (int rank = rankLoc - 1, file = fileLoc - 1; rank >= 0 && file >= 0; rank--, file--) {
            int currentLoc = 8 * rank + file;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }
        // Anti-diagonal (\ direction)
        for (int rank = rankLoc - 1, file = fileLoc + 1; rank >= 0 && file < 8; rank--, file++) {
            int currentLoc = 8 * rank + file;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }
        for (int rank = rankLoc + 1, file = fileLoc - 1; rank < 8 && file >= 0; rank++, file--) {
            int currentLoc = 8 * rank + file;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }
        return moveBoard;
    }
}
