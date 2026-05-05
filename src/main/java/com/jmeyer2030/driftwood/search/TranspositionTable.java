package com.jmeyer2030.driftwood.search;

/**
 * Transposition table using two {@code long[]} arrays for cache-friendly,
 * lockless-safe storage. Uses a <b>two-entry bucket</b> scheme: each logical
 * slot holds a pair of entries stored at adjacent array indices for good
 * cache-line utilisation.
 *
 * <h3>Encoding</h3>
 * <pre>
 * keys[i] = zobristHash ^ data[i]          (XOR verification trick)
 *
 * data[i] bit layout (64 bits):
 *   0-31  bestMove   (32 bits, full move encoding)
 *  32-39  depth      ( 8 bits, 0-255)
 *  40-41  nodeType   ( 2 bits, 0=EXACT, 1=LOWER, 2=UPPER)
 *  42-63  score      (22 bits, signed via arithmetic shift)
 * </pre>
 *
 * <h3>Aging</h3>
 * <p>Each entry is stamped with a generation counter when written. The counter
 * is advanced by calling {@link #newSearch()} at the start of each UCI
 * {@code go} command. Stale entries (from a previous generation) remain
 * probe-able but are easier to evict: an entry's "keep score" is
 * {@code depth + AGE_BONUS} when current, or just {@code depth} when stale.</p>
 *
 * <h3>Replacement policy</h3>
 * <p>On store, the incoming entry is placed by priority:</p>
 * <ol>
 *   <li>Overwrite the slot whose hash matches (same position, always refresh)</li>
 *   <li>Use an empty slot</li>
 *   <li>Asymmetric eviction: slot 0 is <b>depth-preferred</b> (only overwritten when the
 *       incoming depth ≥ the existing depth, with stale entries penalised by
 *       {@code AGE_BONUS}); slot 1 is <b>always-replace</b> (unconditionally overwritten
 *       when slot 0 rejects the entry).</li>
 * </ol>
 *
 * <p>Total: 32 bytes per bucket (2 × 16 bytes), 2–4 array accesses per probe.</p>
 */
public class TranspositionTable {
    // -- Bit layout constants --
    private static final long BEST_MOVE_MASK       = 0xFFFF_FFFFL;
    private static final int  DEPTH_SHIFT          = 32;
    private static final int  DEPTH_FIELD_MASK     = 0xFF;   // 8 bits (0-255)
    private static final int  NODE_TYPE_SHIFT      = 40;
    private static final int  NODE_TYPE_FIELD_MASK = 0x3;    // 2 bits (0-3)
    private static final int  SCORE_SHIFT          = 42;
    // Upper 22 bits - no explicit mask needed, arithmetic right-shift handles sign.

    // -- Constructor bounds --
    private static final int MIN_INDEX_BITS = 1;
    private static final int MAX_INDEX_BITS = 29;

    // -- Aging --
    /**
     * Bonus added to an entry's depth when computing its keep score, if the
     * entry belongs to the current generation. A stale depth-12 entry has the
     * same keep score as a current depth-4 entry when AGE_BONUS = 8.
     */
    static final int AGE_BONUS = 8;

    private final long indexMask;

    private final long[] keys;
    private final long[] data;
    private final byte[] generations;
    private byte currentGeneration;

    /**
     * Initialises a new transposition table.
     *
     * @param numBits number of bucket-index bits.
     *                Table has 2^numBits buckets × 2 entries = 2^(numBits+1) entries × 16 bytes.
     */
    public TranspositionTable(int numBits) {
        if (numBits < MIN_INDEX_BITS || numBits > MAX_INDEX_BITS) {
            throw new IllegalArgumentException("numBits must be between " + MIN_INDEX_BITS + " and " + MAX_INDEX_BITS);
        }

        this.indexMask = (1L << numBits) - 1;
        int size = 2 << numBits;  // 2 entries per bucket
        this.keys = new long[size];
        this.data = new long[size];
        this.generations = new byte[size];
        this.currentGeneration = 0;
    }

    /**
     * Advances the generation counter. Call once at the start of each new
     * search (Iterative deepening) so that entries from previous searches
     * become "stale" and are easier to evict during replacement.
     */
    public void newSearch() {
        currentGeneration++;
    }

    /** Returns the current generation counter (package-private, for testing). */
    byte getCurrentGeneration() {
        return currentGeneration;
    }

    /** Gets the base array index of the two-entry bucket for a zobristHash. */
    public int getIndex(long zobristHash) {
        return (int) (indexMask & zobristHash) << 1;
    }

    /** Packs bestMove, depth, nodeType, and score into a long */
    private static long packData(int bestMove, int depth, int nodeType, int score) {
        return (bestMove & BEST_MOVE_MASK)
             | ((long)(depth    & DEPTH_FIELD_MASK)     << DEPTH_SHIFT)
             | ((long)(nodeType & NODE_TYPE_FIELD_MASK) << NODE_TYPE_SHIFT)
             | ((long) score                            << SCORE_SHIFT);
    }

    /** Extracts bestMove from packed data. */
    public static int unpackBestMove(long packed) {
        return (int)(packed & BEST_MOVE_MASK);
    }

    /** Extracts depth from packed data. */
    public static int unpackDepth(long packed) {
        return (int)((packed >>> DEPTH_SHIFT) & DEPTH_FIELD_MASK);
    }

    /** Extracts nodeType from packed data. */
    public static int unpackNodeType(long packed) {
        return (int)((packed >>> NODE_TYPE_SHIFT) & NODE_TYPE_FIELD_MASK);
    }

    /** Extracts score from packed data (arithmetic shift preserves sign). */
    public static int unpackScore(long packed) {
        return (int)(packed >> SCORE_SHIFT);
    }

    /**
     * Probes the table for a matching entry at sufficient depth.
     * Checks both slots of the bucket.
     *
     * @param zobristHash   hash of the position to look up
     * @param requiredDepth minimum depth for the entry to be useful
     * @return packed data word, or {@code 0} on miss
     */
    public long probe(long zobristHash, int requiredDepth) {
        int index = getIndex(zobristHash);

        // Check slot 0
        long packed = data[index];
        if ((keys[index] ^ packed) == zobristHash
                && unpackDepth(packed) >= requiredDepth
                && (packed & BEST_MOVE_MASK) != 0) {
            return packed;
        }

        // Check slot 1
        packed = data[index + 1];
        if ((keys[index + 1] ^ packed) == zobristHash
                && unpackDepth(packed) >= requiredDepth
                && (packed & BEST_MOVE_MASK) != 0) {
            return packed;
        }

        return 0;
    }

    /**
     * Returns the best move for the given position, or 0 on miss.
     * Checks both bucket slots. Does NOT require a depth check, useful for move ordering.
     */
    public int checkedGetBestMove(long zobristHash) {
        int index = getIndex(zobristHash);

        // Check slot 0
        long packed = data[index];
        if ((keys[index] ^ packed) == zobristHash) return unpackBestMove(packed);

        // Check slot 1
        packed = data[index + 1];
        if ((keys[index + 1] ^ packed) == zobristHash) return unpackBestMove(packed);

        return 0;
    }

    /**
     * Stores an entry using the two-entry bucket replacement policy with aging.
     * <ol>
     *   <li>If either slot's hash matches, overwrite it (same position, always refresh).</li>
     *   <li>If either slot is empty, use it.</li>
     *   <li>Asymmetric eviction: slot 0 is depth-preferred (overwritten only when the incoming
     *       depth ≥ the existing depth, with stale entries penalised by {@code AGE_BONUS});
     *       slot 1 is always-replace.</li>
     * </ol>
     */
    public void addElement(long zobristHash, int bestMove, int depth, int score, int nodeType) {
        int index = getIndex(zobristHash);
        long packed = packData(bestMove, depth, nodeType, score);

        long packed0 = data[index];
        long packed1 = data[index + 1];

        // 1. Same-position match → always overwrite (keeps data fresh)
        if ((keys[index] ^ packed0) == zobristHash) {
            writeSlot(index, zobristHash, packed);
            return;
        }
        if ((keys[index + 1] ^ packed1) == zobristHash) {
            writeSlot(index + 1, zobristHash, packed);
            return;
        }

        // 2. Empty slot
        if (packed0 == 0) {
            writeSlot(index, zobristHash, packed);
            return;
        }
        if (packed1 == 0) {
            writeSlot(index + 1, zobristHash, packed);
            return;
        }

        // 3. Asymmetric eviction.
        //    Slot 0 = depth-preferred: only overwritten when new depth ≥ existing depth
        //    (stale entries are penalised by AGE_BONUS, making them easier to displace).
        //    Slot 1 = always-replace: unconditionally overwritten when slot 0 rejects.
        int existingDepth0 = unpackDepth(packed0);
        if (generations[index] != currentGeneration) {
            existingDepth0 -= AGE_BONUS; // stale entries are easier to beat
        }
        if (depth >= existingDepth0) {
            writeSlot(index, zobristHash, packed);
        } else {
            writeSlot(index + 1, zobristHash, packed);
        }
    }


    /** Writes a packed entry and its XOR-verification key to the given slot, stamping the current generation. */
    private void writeSlot(int slot, long zobristHash, long packed) {
        data[slot] = packed;
        keys[slot] = zobristHash ^ packed;
        generations[slot] = currentGeneration;
    }
}
