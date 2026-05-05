package com.jmeyer2030.driftwood.staticevaluation;

import com.jmeyer2030.driftwood.board.Position;
import com.jmeyer2030.driftwood.config.GlobalConstants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents an Efficiently Updatable Neural Network
 * - Trained using Stock Fish training data using Bullet Lib
 * - Contains and loads network weights and sizes statically
 * - Instantiations contain a ply-indexed accumulator stack for incremental updates
 *
 * <p>Uses a copy-on-make accumulator stack approach:
 * <ul>
 *   <li>{@link #pushAccumulator()} copies the current ply's accumulators to the next ply (called at start of makeMove)</li>
 *   <li>{@link #addFeature}/{@link #removeFeature} eagerly apply weight deltas to the current ply's accumulators</li>
 *   <li>{@link #popAccumulator()} restores the parent ply's accumulators instantly (called at end of unmakeMove)</li>
 *   <li>{@link #computeOutput} reads from the current ply's accumulators, no deferred processing</li>
 * </ul>
 * This eliminates redundant undo+redo work that the previous lazy-batching approach performed.</p>
 */
public class NNUE implements Evaluator {
    private static final int INPUT_SIZE = 768;
    private static final int HIDDEN_LAYER_SIZE = 128;

    private static final int QA = 255;
    private static final int QB = 64;
    private static final int SCALE = 400;

    // Maximum accumulator stack depth (game moves + search depth)
    private static final int MAX_ACCUM_PLY = GlobalConstants.MAX_GAME_MOVES;

    // Flat int[] for cache-friendly access and JVM auto-vectorization (avoids short→int widening)
    private static final int[] HIDDEN_LAYER_WEIGHTS;  // flat [INPUT_SIZE * HIDDEN_LAYER_SIZE]
    private static final int[] HIDDEN_LAYER_BIAS;

    private static final int[] OUTPUT_WEIGHTS;
    private static final int OUTPUT_BIAS;

    // Ply-indexed accumulator stack: each ply has its own white and black accumulator
    private final int[][] whiteAccumulatorStack = new int[MAX_ACCUM_PLY][HIDDEN_LAYER_SIZE];
    private final int[][] blackAccumulatorStack = new int[MAX_ACCUM_PLY][HIDDEN_LAYER_SIZE];
    private int accumulatorPly = 0;

    private int evaluation;
    private boolean evaluationIsCurrent;
    private int precomputeActivePlayer;


    /* Initializes weights and biases from quantised.bin on class load */
    static {
        NetworkData networkData = getNetworkData();
        HIDDEN_LAYER_WEIGHTS = networkData.hiddenWeights();
        HIDDEN_LAYER_BIAS = networkData.hiddenBias();
        OUTPUT_WEIGHTS = networkData.outputWeights();
        OUTPUT_BIAS = networkData.outputBias();
    }


    /**
     * Creates the nn and fills its accumulators at ply 0
     */
    public NNUE(Position position) {
        fillAccumulators(position);
    }

    /**
     * Copies the current ply's accumulators to the next ply and increments the ply pointer.
     * Called at the start of makeMove, before any addFeature/removeFeature calls.
     */
    @Override
    public void pushAccumulator() {
        int nextPly = accumulatorPly + 1;
        System.arraycopy(whiteAccumulatorStack[accumulatorPly], 0, whiteAccumulatorStack[nextPly], 0, HIDDEN_LAYER_SIZE);
        System.arraycopy(blackAccumulatorStack[accumulatorPly], 0, blackAccumulatorStack[nextPly], 0, HIDDEN_LAYER_SIZE);
        accumulatorPly = nextPly;
        evaluationIsCurrent = false;
    }

    /**
     * Restores the parent ply's accumulator by decrementing the ply pointer.
     * Called at the end of unmakeMove, no accumulator work needed.
     */
    @Override
    public void popAccumulator() {
        accumulatorPly--;
        evaluationIsCurrent = false;
    }

    /**
     * Eagerly adds a feature to the current ply's accumulators.
     *
     * @param piece  piece to add
     * @param color  color of the piece
     * @param square square of the piece
     */
    public void addFeature(int piece, int color, int square) {
        int whitePerspectiveVal = piece + (color == 0 ? 0 : 6); // If piece is white, 0-5
        int blackPerspectiveVal = piece + (color == 0 ? 6 : 0); // If piece is white, 6-11

        int whitePerspectiveIndex = 64 * whitePerspectiveVal + square;
        int blackPerspectiveIndex = 64 * blackPerspectiveVal + (square ^ 0b111000);

        // Apply directly to current ply's accumulators, separate loops for JVM auto-vectorization
        int whiteBase = whitePerspectiveIndex * HIDDEN_LAYER_SIZE;
        int[] whiteAcc = whiteAccumulatorStack[accumulatorPly];
        for (int j = 0; j < HIDDEN_LAYER_SIZE; j++) {
            whiteAcc[j] += HIDDEN_LAYER_WEIGHTS[whiteBase + j];
        }

        int blackBase = blackPerspectiveIndex * HIDDEN_LAYER_SIZE;
        int[] blackAcc = blackAccumulatorStack[accumulatorPly];
        for (int j = 0; j < HIDDEN_LAYER_SIZE; j++) {
            blackAcc[j] += HIDDEN_LAYER_WEIGHTS[blackBase + j];
        }

        evaluationIsCurrent = false;
    }

    /**
     * Eagerly removes a feature from the current ply's accumulators.
     *
     * @param piece  piece to remove
     * @param color  color of the piece
     * @param square square of the piece
     */
    public void removeFeature(int piece, int color, int square) {
        int whitePieceVal = piece + (color == 0 ? 0 : 6); // If piece is white, 0-5
        int blackPieceVal = piece + (color == 0 ? 6 : 0); // If piece is white, 6-11

        int whitePerspectiveIndex = 64 * whitePieceVal + square;
        int blackPerspectiveIndex = 64 * blackPieceVal + (square ^ 0b111000);

        // Apply directly to current ply's accumulators, separate loops for JVM auto-vectorization
        int whiteBase = whitePerspectiveIndex * HIDDEN_LAYER_SIZE;
        int[] whiteAcc = whiteAccumulatorStack[accumulatorPly];
        for (int j = 0; j < HIDDEN_LAYER_SIZE; j++) {
            whiteAcc[j] -= HIDDEN_LAYER_WEIGHTS[whiteBase + j];
        }

        int blackBase = blackPerspectiveIndex * HIDDEN_LAYER_SIZE;
        int[] blackAcc = blackAccumulatorStack[accumulatorPly];
        for (int j = 0; j < HIDDEN_LAYER_SIZE; j++) {
            blackAcc[j] -= HIDDEN_LAYER_WEIGHTS[blackBase + j];
        }

        evaluationIsCurrent = false;
    }

    /**
     * Fills accumulators at ply 0 by iterating over pieces and adding them as features
     *
     * @param position to fill accumulators from
     */
    private void fillAccumulators(Position position) {
        // First add biases at ply 0
        int[] whiteAcc = whiteAccumulatorStack[0];
        int[] blackAcc = blackAccumulatorStack[0];
        for (int i = 0; i < HIDDEN_LAYER_SIZE; i++) {
            whiteAcc[i] = HIDDEN_LAYER_BIAS[i];
            blackAcc[i] = HIDDEN_LAYER_BIAS[i];
        }

        // Add features to the accumulators (applied eagerly at ply 0)
        for (int color = 0; color <= 1; color++) {
            for (int piece = 0; piece <= 5; piece++) {
                // Get bitboard corresponding with a piece and color
                long pieceColorBB = position.pieces[piece] & position.pieceColors[color];

                // Iterate over these pieces and add features to accumulator
                while (pieceColorBB != 0) {
                    int square = Long.numberOfTrailingZeros(pieceColorBB);
                    pieceColorBB &= (pieceColorBB - 1);

                    addFeature(piece, color, square);
                }
            }
        }
    }

    /**
     * Computes the output from the current ply's accumulators.
     * No deferred processing needed, accumulators are always up-to-date.
     */
    public int computeOutput(int activePlayer) {
        if (evaluationIsCurrent && activePlayer == precomputeActivePlayer) {
            return evaluation;
        }

        int[] whiteAcc = whiteAccumulatorStack[accumulatorPly];
        int[] blackAcc = blackAccumulatorStack[accumulatorPly];

        long outputActivation = 0;

        int whiteOffset = (activePlayer == 0) ? 0 : HIDDEN_LAYER_SIZE;
        int blackOffset = HIDDEN_LAYER_SIZE - whiteOffset;

        for (int i = 0; i < HIDDEN_LAYER_SIZE; i++) {
            int wClipped = Math.min(Math.max(whiteAcc[i], 0), QA);
            outputActivation += (long) (wClipped * wClipped) * OUTPUT_WEIGHTS[whiteOffset + i];
        }
        for (int i = 0; i < HIDDEN_LAYER_SIZE; i++) {
            int bClipped = Math.min(Math.max(blackAcc[i], 0), QA);
            outputActivation += (long) (bClipped * bClipped) * OUTPUT_WEIGHTS[blackOffset + i];
        }

        outputActivation /= QA;

        outputActivation += OUTPUT_BIAS;

        outputActivation *= SCALE;

        outputActivation /= QB * QA;

        evaluation = (int) outputActivation;
        evaluationIsCurrent = true;
        precomputeActivePlayer = activePlayer;

        return evaluation;
    }


    /**
     * Retrieves the binary network data and returns it as a short array
     *
     * @return shortArray of weights and biases of the network
     */
    private static short[] getNetworkBytes() {
        byte[] dataAsByteArr;

        try (InputStream inputStream = NNUE.class.getResourceAsStream("/quantised.bin")) {
            if (inputStream == null) {
                throw new RuntimeException("Resource quanised.bin not found");
            }

            dataAsByteArr = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read quantised.bin", e);
        }

        short[] shortArray = new short[dataAsByteArr.length / 2];
        ByteBuffer buffer = ByteBuffer.wrap(dataAsByteArr).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < shortArray.length; i++) {
            shortArray[i] = buffer.getShort();
        }

        return shortArray;

    }

    /**
     * Initializes network weights and biases
     * Expected Data format:
     * input weights -> input Biases -> hidden weights -> hidden biases -> Output bias -> padding
     */
    private static NetworkData getNetworkData() {
        short[] nnShorts = getNetworkBytes();

        // Flat int[] for contiguous memory + vectorization (widen short→int during load)
        int[] hiddenWeights = new int[INPUT_SIZE * HIDDEN_LAYER_SIZE];
        int[] hiddenBias = new int[HIDDEN_LAYER_SIZE];
        int[] outputWeights = new int[HIDDEN_LAYER_SIZE * 2];
        int outputBias;

        // Get feature weights, stored flat as [inputFeature * HIDDEN_LAYER_SIZE + hiddenNeuron]
        for (int i = 0; i < INPUT_SIZE * HIDDEN_LAYER_SIZE; i++) {
            hiddenWeights[i] = nnShorts[i];
        }

        // Get feature biases
        int startShort = HIDDEN_LAYER_SIZE * INPUT_SIZE;
        for (int hlNeuron = 0; hlNeuron < HIDDEN_LAYER_SIZE; hlNeuron++) {
            hiddenBias[hlNeuron] = nnShorts[startShort + hlNeuron];
        }

        // Get output weights
        startShort += HIDDEN_LAYER_SIZE;
        for (int hlNeuron = 0; hlNeuron < HIDDEN_LAYER_SIZE * 2; hlNeuron++) {
            outputWeights[hlNeuron] = nnShorts[startShort + hlNeuron];
        }

        // Get output bias
        startShort += HIDDEN_LAYER_SIZE * 2;
        outputBias = nnShorts[startShort];

        return new NetworkData(hiddenWeights, hiddenBias, outputWeights, outputBias);
    }

    private record NetworkData(
        int[] hiddenWeights,
        int[] hiddenBias,
        int[] outputWeights,
        int outputBias
    ) {}
}