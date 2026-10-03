package com.jmeyer2030.driftwood.userfeatures.perft;

/**
 * Bounded perft cache with allocation-free probes and stores. Counts are reusable
 * only for the exact full hash and remaining depth. Deeper subtrees take priority
 * over shallower entries that collide with them.
 */
public class PerftTable {
    public static final long MISS = -1L;

    private final int indexMask;
    private final long[] keys;
    private final long[] counts;
    private final int[] depths;

    public PerftTable(int numBits) {
        if (numBits < 1 || numBits > 30) {
            throw new IllegalArgumentException();
        }

        int size = 1 << numBits;
        this.indexMask = size - 1;
        this.keys = new long[size];
        this.counts = new long[size];
        this.depths = new int[size]; // Zero marks an empty slot; only positive depths are stored.
    }

    public int getIndex(long zobristHash) {
        return (int) zobristHash & indexMask;
    }

    /** Returns the cached count, including zero, or {@link #MISS} when absent. */
    public long probe(long zobristHash, int depth) {
        int index = getIndex(zobristHash);
        if (keys[index] == zobristHash && depths[index] == depth) {
            return counts[index];
        }
        return MISS;
    }

    /** Allocates a snapshot for callers that need an entry; perft uses {@link #probe}. */
    public PerftElement getElement(long zobristHash, int depth) {
        long count = probe(zobristHash, depth);
        return count == MISS ? null : new PerftElement(zobristHash, depth, count);
    }

    public void addElement(long zobristHash, int depth, long perftResult) {
        int index = getIndex(zobristHash);
        keys[index] = zobristHash;
        counts[index] = perftResult;
        depths[index] = depth;
    }
}
