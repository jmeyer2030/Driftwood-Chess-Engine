package com.jmeyer2030.driftwood.movegeneration;

import com.jmeyer2030.driftwood.board.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
* Static magic bitboard based rook attacks generator:
*
* Implementation of the computation of magicNumbers was removed in an earlier commit, and we just
* keep the computed magic numbers.
*
* @TODO: Reintroduce magic number computation separately, maybe a util folder or something.
*/
public class RookLogic {
    // @formatter:off
    // Masks for blocker locations on each square
    private static final long[] blockerMasks = new long[64];

    // Move boards corresponding with potential blockers
    private static final long[][] moveBoards = new long[64][];

    // Pre-computed magic numbers
    private static final long[] magicNumbers =
            {144133057304076800L,   4701793197762887680L,  -9151279258172126976L, 144124009940783169L,   5872698314287351808L,
             144132788852099073L,   288230930236575748L,   36047111917142144L,    290622915605434400L,   148759662735393409L,
             -8070168987475902208L, 3477060456026800161L,  2306124518567052304L,  2814819694805505L,     -9217038776862113664L,
             9147941040177280L,     1807213936532013060L,  141837553647616L,      9007749555818496L,     4931441867922867200L,
             5332827107867762704L,  18155685820760576L,    1229483802079003136L,  -9219956953197576063L, 585538322449858600L,
             2598664983503970310L,  4503670496428160L,     79171280699520L,       146371388084060288L,   216810533238611984L,
             283691180363777L,      10134207263178852L,    2323857958561120288L,  2310346746284212290L,  141020989755424L,
             4611827065178558464L,  4900479413352663200L,  -9150610746776747008L, 37417822200144L,       4756927385616519300L,
             -8358644071538786288L, -9223301665946271712L, 40541193343344640L,    2958873751543906432L,  72066390198091904L,
             5188357945750454560L,  577041603716448257L,   39584704626697L,       -9180306225826231808L, 2954396542078813312L,
             9008299036918016L,     -3458623707612183936L, 11822533675320832L,    365354554215105024L,   4591569286005760L,
             216190934457583104L,   108158963120351874L,   18036811809030402L,    1143539341729793L,     281492425023497L,
             -9204794550683958270L, 72339086195032577L,    4629702650590470276L,  8071017881330266370L};

    // Number of potential blockers for each square
    private static final int[] numBits = new int[]
            {12, 11, 11, 11, 11, 11, 11, 12,
             11, 10, 10, 10, 10, 10, 10, 11,
             11, 10, 10, 10, 10, 10, 10, 11,
             11, 10, 10, 10, 10, 10, 10, 11,
             11, 10, 10, 10, 10, 10, 10, 11,
             11, 10, 10, 10, 10, 10, 10, 11,
             11, 10, 10, 10, 10, 10, 10, 11,
             12, 11, 11, 11, 11, 11, 11, 12};
    // @formatter:on

    static {
        generateBlockerMasks();
        populateMoveBoards();
    }

    // TODO: Is this needed? can we just use get attack board?
    /**
     * Returns a move board given a square and position
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
     * @param occupancy occupancy
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

    /**
     * Given a blocker main.java.board and square, returns the index of the corresponding move main.java.board
     *
     * @param blockerBoard blockerboard
     * @param square       square
     * @return index of moveBoards that corresponds with the blockerboard for a bishop on that square
     */
    private static int getIndexForBlocker(long blockerBoard, int square) {
        // Function to get the index
        return (int) ((magicNumbers[square] * blockerBoard) >> (64 - numBits[square])
                & ((1 << numBits[square]) - 1));
    }

//Implemented Methods:

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
        long moveBoard;
        int index;

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
    protected static List<List<Long>> generateAllMoveBoards(List<List<Long>> blockerBoards) {
        List<List<Long>> moveBoards = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            moveBoards.add(generateMoveBoards(i, blockerBoards.get(i)));
        }

        return moveBoards;
    }


    /**
     * generates all potential locations of blockers for each square
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

        for (int i = 0; i < 8; i++) {
            int sameRankBitLoc = rankLoc * 8 + i;//bit to flip within the same rank
            int sameFileBitLoc = i * 8 + fileLoc;//bit to flip within the same file
            result |= (1L << sameRankBitLoc);
            result |= (1L << sameFileBitLoc);
        }

        result &= ~(1L << rankLoc * 8);
        result &= ~(1L << rankLoc * 8 + 7);
        result &= ~(1L << fileLoc);
        result &= ~(1L << 7 * 8 + fileLoc);
        result &= ~(1L << square);

        return result;
    }

    /**
     * Returns a move main.java.board for a bishop blockerBoard
     *
     * @param blockerBoard, a bishop blockerBoard
     * @param square,       the square associated with the blockerBoard
     * @return the moveBoard for that blockerBoard
     */
    private static long generateMoveBoard(long blockerBoard, int square) {
        int rankLoc = square / 8;
        int fileLoc = square % 8;

        long moveBoard = 0;

        //iterate right
        for (int file = fileLoc + 1; file < 8; file++) {
            int currentLoc = 8 * rankLoc + file;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }

        //iterate left
        for (int file = fileLoc - 1; file >= 0; file--) {
            int currentLoc = 8 * rankLoc + file;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }
        //iterate up
        for (int rank = rankLoc + 1; rank < 8; rank++) {
            int currentLoc = 8 * rank + fileLoc;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }
        //iterate down
        for (int rank = rankLoc - 1; rank >= 0; rank--) {
            int currentLoc = 8 * rank + fileLoc;
            moveBoard |= (1L << currentLoc);
            long bitMask = 1L << currentLoc;
            if ((blockerBoard & bitMask) != 0) {
                break;
            }
        }

        return moveBoard;
    }
}
