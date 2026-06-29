package com.jmeyer2030.driftwood.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranspositionTableTest {

    // -- Constructor tests --

    @Test
    @DisplayName("Constructor creates table when numBits is within valid range")
    void constructorCreatesTable_whenNumBitsValid() {
        // Arrange
        int numBits = 4;

        // Act
        TranspositionTable tt = new TranspositionTable(numBits);

        // Assert - table exists and accepts operations without error
        assertNotNull(tt);
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException when numBits is zero")
    void constructorThrows_whenNumBitsIsZero() {
        // Arrange
        int numBits = 0;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(numBits));
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException when numBits is negative")
    void constructorThrows_whenNumBitsIsNegative() {
        // Arrange
        int numBits = -1;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(numBits));
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException when numBits exceeds 29")
    void constructorThrows_whenNumBitsExceedsMax() {
        // Arrange
        int numBits = 31;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(numBits));
    }

    @Test
    @DisplayName("Constructor accepts boundary value numBits = 1")
    void constructorAccepts_whenNumBitsIsOne() {
        // Arrange
        int numBits = 1;

        // Act
        TranspositionTable tt = new TranspositionTable(numBits);

        // Assert
        assertNotNull(tt);
    }

    // -- constructByMBSize tests --

    @Test
    @DisplayName("constructByMBSize creates expected index range when given one megabyte")
    void constructByMBSizeCreatesExpectedIndexRange_whenGivenOneMegabyte() {
        // Arrange
        int mb = 1;
        long highestBucketHash = (1L << 15) - 1;

        // Act
        TranspositionTable tt = TranspositionTable.constructByMBSize(mb);

        // Assert
        assertEquals(65534, tt.getIndex(highestBucketHash));
    }

    @Test
    @DisplayName("constructByMBSize throws IllegalArgumentException when megabytes exceeds max index bits")
    void constructByMBSizeThrows_whenMegabytesExceedsMaxIndexBits() {
        // Arrange
        int mb = 16_385;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> TranspositionTable.constructByMBSize(mb));
    }

    // -- getIndex tests --

    @Test
    @DisplayName("getIndex returns even value within array bounds when given a large hash")
    void getIndexReturnsWithinBounds_whenGivenLargeHash() {
        // Arrange
        int numBits = 4; // 16 buckets × 2 = 32 array slots, base indices 0,2,4,...,30
        TranspositionTable tt = new TranspositionTable(numBits);
        long hash = 0xDEAD_BEEF_CAFE_BABEL;

        // Act
        int index = tt.getIndex(hash);

        // Assert - must be an even number in [0, 32)
        assertTrue(index >= 0 && index < 32);
        assertEquals(0, index % 2);
    }

    @Test
    @DisplayName("getIndex returns zero when hash is zero")
    void getIndexReturnsZero_whenHashIsZero() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);

        // Act
        int index = tt.getIndex(0L);

        // Assert
        assertEquals(0, index);
    }

    // -- Unpack round-trip tests --

    @Test
    @DisplayName("Unpack returns stored bestMove after addElement and probe")
    void unpackBestMove_returnsCorrectValue_afterStore() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        int bestMove = 0x0000_1A2B;
        int depth = 10;
        int score = 500;
        int nodeType = NodeType.EXACT;

        // Act
        tt.addElement(hash, bestMove, depth, score, nodeType);
        long packed = tt.probe(hash, depth);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(bestMove, TranspositionTable.unpackBestMove(packed));
    }

    @Test
    @DisplayName("Unpack returns stored depth after addElement and probe")
    void unpackDepth_returnsCorrectValue_afterStore() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        int bestMove = 42;
        int depth = 15;
        int score = -100;
        int nodeType = NodeType.LOWER_BOUND;

        // Act
        tt.addElement(hash, bestMove, depth, score, nodeType);
        long packed = tt.probe(hash, depth);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(depth, TranspositionTable.unpackDepth(packed));
    }

    @Test
    @DisplayName("Unpack returns stored nodeType after addElement and probe")
    void unpackNodeType_returnsCorrectValue_afterStore() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 99999L;
        int bestMove = 7;
        int depth = 5;
        int score = 0;
        int nodeType = NodeType.UPPER_BOUND;

        // Act
        tt.addElement(hash, bestMove, depth, score, nodeType);
        long packed = tt.probe(hash, depth);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(nodeType, TranspositionTable.unpackNodeType(packed));
    }

    @Test
    @DisplayName("Unpack returns stored positive score after addElement and probe")
    void unpackScore_returnsCorrectPositiveValue_afterStore() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 55555L;
        int bestMove = 1;
        int depth = 8;
        int score = 12345;
        int nodeType = NodeType.EXACT;

        // Act
        tt.addElement(hash, bestMove, depth, score, nodeType);
        long packed = tt.probe(hash, depth);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(score, TranspositionTable.unpackScore(packed));
    }

    @Test
    @DisplayName("Unpack returns stored negative score after addElement and probe")
    void unpackScore_returnsCorrectNegativeValue_afterStore() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 77777L;
        int bestMove = 3;
        int depth = 6;
        int score = -9876;
        int nodeType = NodeType.EXACT;

        // Act
        tt.addElement(hash, bestMove, depth, score, nodeType);
        long packed = tt.probe(hash, depth);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(score, TranspositionTable.unpackScore(packed));
    }

    @Test
    @DisplayName("Unpack round-trips all fields correctly for a single stored entry")
    void allFieldsRoundTrip_afterStoreAndProbe() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(10);
        long hash = 0xABCD_1234_5678_9ABCL;
        int bestMove = 0b0000_0000_0000_1010_0011_1100_0101_0110; // arbitrary 32-bit move
        int depth = 20;
        int score = -500;
        int nodeType = NodeType.LOWER_BOUND;

        // Act
        tt.addElement(hash, bestMove, depth, score, nodeType);
        long packed = tt.probe(hash, depth);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(bestMove, TranspositionTable.unpackBestMove(packed));
        assertEquals(depth, TranspositionTable.unpackDepth(packed));
        assertEquals(score, TranspositionTable.unpackScore(packed));
        assertEquals(nodeType, TranspositionTable.unpackNodeType(packed));
    }

    // -- Probe miss scenarios --

    @Test
    @DisplayName("Probe returns zero when table is empty")
    void probeReturnsZero_whenTableIsEmpty() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;

        // Act
        long result = tt.probe(hash, 1);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Probe returns zero when hash does not match stored entry")
    void probeReturnsZero_whenHashDoesNotMatch() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long storedHash = 12345L;
        long queryHash = 67890L;
        tt.addElement(storedHash, 1, 10, 100, NodeType.EXACT);

        // Act
        long result = tt.probe(queryHash, 1);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Probe returns zero when stored depth is less than required depth")
    void probeReturnsZero_whenDepthInsufficient() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        int storedDepth = 5;
        int requiredDepth = 10;
        tt.addElement(hash, 1, storedDepth, 100, NodeType.EXACT);

        // Act
        long result = tt.probe(hash, requiredDepth);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Probe returns packed data when stored depth equals required depth")
    void probeReturnsPacked_whenDepthEqualsRequired() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        int depth = 10;
        tt.addElement(hash, 1, depth, 100, NodeType.EXACT);

        // Act
        long result = tt.probe(hash, depth);

        // Assert
        assertNotEquals(0, result);
    }

    @Test
    @DisplayName("Probe returns packed data when stored depth exceeds required depth")
    void probeReturnsPacked_whenDepthExceedsRequired() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        tt.addElement(hash, 1, 15, 100, NodeType.EXACT);

        // Act
        long result = tt.probe(hash, 10);

        // Assert
        assertNotEquals(0, result);
    }

    @Test
    @DisplayName("Probe returns zero when bestMove is zero")
    void probeReturnsZero_whenBestMoveIsZero() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        tt.addElement(hash, 0, 10, 100, NodeType.EXACT);

        // Act
        long result = tt.probe(hash, 1);

        // Assert
        assertEquals(0, result);
    }

    // -- checkedGetBestMove tests --

    @Test
    @DisplayName("checkedGetBestMove returns stored move when hash matches")
    void checkedGetBestMoveReturnsMove_whenHashMatches() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        int bestMove = 42;
        tt.addElement(hash, bestMove, 10, 100, NodeType.EXACT);

        // Act
        int result = tt.checkedGetBestMove(hash);

        // Assert
        assertEquals(bestMove, result);
    }

    @Test
    @DisplayName("checkedGetBestMove returns zero when hash does not match")
    void checkedGetBestMoveReturnsZero_whenHashDoesNotMatch() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        tt.addElement(12345L, 42, 10, 100, NodeType.EXACT);

        // Act
        int result = tt.checkedGetBestMove(67890L);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("checkedGetBestMove returns move regardless of depth")
    void checkedGetBestMoveReturnsMove_regardlessOfDepth() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        int bestMove = 99;
        tt.addElement(hash, bestMove, 1, 0, NodeType.EXACT);

        // Act
        int result = tt.checkedGetBestMove(hash);

        // Assert - returns the move even though depth is shallow
        assertEquals(bestMove, result);
    }

    // -- Overwrite / replacement tests --

    @Test
    @DisplayName("addElement overwrites previous entry at the same index")
    void addElementOverwrites_whenSameHash() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        tt.addElement(hash, 1, 5, 100, NodeType.EXACT);

        // Act - overwrite with a new entry
        int newBestMove = 99;
        int newDepth = 20;
        int newScore = -300;
        int newNodeType = NodeType.UPPER_BOUND;
        tt.addElement(hash, newBestMove, newDepth, newScore, newNodeType);
        long packed = tt.probe(hash, newDepth);

        // Assert - second write is what we read back
        assertNotEquals(0, packed);
        assertEquals(newBestMove, TranspositionTable.unpackBestMove(packed));
        assertEquals(newDepth, TranspositionTable.unpackDepth(packed));
        assertEquals(newScore, TranspositionTable.unpackScore(packed));
        assertEquals(newNodeType, TranspositionTable.unpackNodeType(packed));
    }

    @Test
    @DisplayName("addElement stores both entries when two different hashes map to the same bucket")
    void addElementStoresBoth_whenIndexCollides() {
        // Arrange - two hashes that map to the same bucket in a 2-bucket table
        TranspositionTable tt = new TranspositionTable(1); // 2 buckets × 2 entries = 4 slots
        long hash1 = 0b00L; // bucket 0
        long hash2 = 0b10L; // also bucket 0
        tt.addElement(hash1, 1, 10, 100, NodeType.EXACT);

        // Act - store second entry in same bucket
        tt.addElement(hash2, 2, 10, 200, NodeType.EXACT);

        // Assert - both entries survive in the bucket
        assertNotEquals(0, tt.probe(hash1, 1));
        assertNotEquals(0, tt.probe(hash2, 1));
    }

    // -- Edge case: score sign preservation --

    @Test
    @DisplayName("Unpack preserves zero score after round-trip")
    void unpackScore_preservesZero_afterRoundTrip() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 11111L;
        int score = 0;
        tt.addElement(hash, 1, 5, score, NodeType.EXACT);

        // Act
        long packed = tt.probe(hash, 5);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(0, TranspositionTable.unpackScore(packed));
    }

    // -- Edge case: max depth --

    @Test
    @DisplayName("Unpack returns max depth 255 after round-trip")
    void unpackDepth_returnsMaxDepth_afterRoundTrip() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 22222L;
        int maxDepth = 255;
        tt.addElement(hash, 1, maxDepth, 50, NodeType.EXACT);

        // Act
        long packed = tt.probe(hash, maxDepth);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(maxDepth, TranspositionTable.unpackDepth(packed));
    }

    // -- Edge case: all node types --

    @Test
    @DisplayName("Unpack returns EXACT node type after round-trip")
    void unpackNodeType_returnsExact_afterRoundTrip() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 33333L;
        tt.addElement(hash, 1, 5, 0, NodeType.EXACT);

        // Act
        long packed = tt.probe(hash, 5);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(NodeType.EXACT, TranspositionTable.unpackNodeType(packed));
    }

    @Test
    @DisplayName("Unpack returns LOWER_BOUND node type after round-trip")
    void unpackNodeType_returnsLowerBound_afterRoundTrip() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 44444L;
        tt.addElement(hash, 1, 5, 0, NodeType.LOWER_BOUND);

        // Act
        long packed = tt.probe(hash, 5);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(NodeType.LOWER_BOUND, TranspositionTable.unpackNodeType(packed));
    }

    @Test
    @DisplayName("Unpack returns UPPER_BOUND node type after round-trip")
    void unpackNodeType_returnsUpperBound_afterRoundTrip() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 55555L;
        tt.addElement(hash, 1, 5, 0, NodeType.UPPER_BOUND);

        // Act
        long packed = tt.probe(hash, 5);

        // Assert
        assertNotEquals(0, packed);
        assertEquals(NodeType.UPPER_BOUND, TranspositionTable.unpackNodeType(packed));
    }

    // -- Replacement policy tests --

    @Test
    @DisplayName("addElement replaces entry when new depth is greater than old depth for same hash")
    void addElementReplaces_whenNewDepthIsGreater() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        tt.addElement(hash, 1, 5, 100, NodeType.EXACT);

        // Act - store deeper entry at same hash
        tt.addElement(hash, 2, 10, 200, NodeType.LOWER_BOUND);
        long packed = tt.probe(hash, 10);

        // Assert - deeper entry replaced the shallower one
        assertNotEquals(0, packed);
        assertEquals(2, TranspositionTable.unpackBestMove(packed));
        assertEquals(10, TranspositionTable.unpackDepth(packed));
        assertEquals(200, TranspositionTable.unpackScore(packed));
    }

    @Test
    @DisplayName("addElement replaces entry when new depth equals old depth for same hash")
    void addElementReplaces_whenNewDepthEquals() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        tt.addElement(hash, 1, 10, 100, NodeType.EXACT);

        // Act - store entry at same depth
        tt.addElement(hash, 2, 10, 200, NodeType.LOWER_BOUND);
        long packed = tt.probe(hash, 10);

        // Assert - new entry replaced the old one
        assertNotEquals(0, packed);
        assertEquals(2, TranspositionTable.unpackBestMove(packed));
    }

    @Test
    @DisplayName("addElement overwrites same-hash entry even when new depth is shallower")
    void addElementOverwritesSameHash_whenNewIsShallower() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        tt.addElement(hash, 1, 15, 100, NodeType.EXACT);

        // Act - store shallower entry at same hash (same-position refresh)
        tt.addElement(hash, 2, 5, 200, NodeType.LOWER_BOUND);
        long packed = tt.probe(hash, 5);

        // Assert - new entry replaced the old one (same hash always refreshes)
        assertNotEquals(0, packed);
        assertEquals(2, TranspositionTable.unpackBestMove(packed));
        assertEquals(5, TranspositionTable.unpackDepth(packed));
        assertEquals(200, TranspositionTable.unpackScore(packed));
    }

    @Test
    @DisplayName("addElement writes deeper entry to depth-preferred slot 0 when bucket is full in the same generation")
    void addElementReplacesShallowest_whenBucketFull() {
        // Arrange - fill both bucket slots with different hashes (all same generation)
        TranspositionTable tt = new TranspositionTable(1); // 2 buckets × 2 entries
        long hash1 = 0b00L; // bucket 0, depth 5 (shallowest)
        long hash2 = 0b10L; // bucket 0, depth 15
        long hash3 = 0b100L; // bucket 0
        tt.addElement(hash1, 1, 5, 100, NodeType.EXACT);
        tt.addElement(hash2, 2, 15, 200, NodeType.EXACT);

        // Act - third entry evicts the shallowest
        tt.addElement(hash3, 3, 10, 300, NodeType.LOWER_BOUND);

        // Assert - hash1 (depth 5) was evicted; hash2 (depth 15) and hash3 survive
        assertEquals(0, tt.probe(hash1, 1));
        assertNotEquals(0, tt.probe(hash2, 1));
        assertNotEquals(0, tt.probe(hash3, 1));
    }

    @Test
    @DisplayName("addElement sends shallow entry to always-replace slot 1 when slot 0 is deeper in the same generation")
    void addElementPreservesDeeper_whenBucketFull() {
        // Arrange - fill bucket: slot 0 = deep, slot 1 = shallow (all same generation)
        TranspositionTable tt = new TranspositionTable(1);
        long hash1 = 0b00L; // bucket 0, depth 20 (deepest)
        long hash2 = 0b10L; // bucket 0, depth 3 (shallowest)
        tt.addElement(hash1, 1, 20, 100, NodeType.EXACT);
        tt.addElement(hash2, 2, 3, 200, NodeType.EXACT);

        // Act - third entry evicts the shallowest (hash2)
        long hash3 = 0b100L;
        tt.addElement(hash3, 3, 8, 300, NodeType.LOWER_BOUND);

        // Assert - hash1 (depth 20) survives, hash2 (depth 3) was evicted
        assertNotEquals(0, tt.probe(hash1, 1));
        assertEquals(0, tt.probe(hash2, 1));
        assertNotEquals(0, tt.probe(hash3, 1));
    }

    @Test
    @DisplayName("checkedGetBestMove returns move from second bucket slot when first slot holds different hash")
    void checkedGetBestMoveReturnsMove_whenEntryInSecondSlot() {
        // Arrange - place two entries in the same bucket
        TranspositionTable tt = new TranspositionTable(1);
        long hash1 = 0b00L;
        long hash2 = 0b10L;
        tt.addElement(hash1, 11, 5, 100, NodeType.EXACT);
        tt.addElement(hash2, 22, 5, 200, NodeType.EXACT);

        // Act
        int bestMove1 = tt.checkedGetBestMove(hash1);
        int bestMove2 = tt.checkedGetBestMove(hash2);

        // Assert - both best moves are retrievable
        assertEquals(11, bestMove1);
        assertEquals(22, bestMove2);
    }

    // -- Aging tests --

    @Test
    @DisplayName("newSearch advances the generation counter by one")
    void newSearchAdvancesGeneration_whenCalled() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(4);
        byte genBefore = tt.getCurrentGeneration();

        // Act
        tt.newSearch();

        // Assert
        assertEquals((byte)(genBefore + 1), tt.getCurrentGeneration());
    }

    @Test
    @DisplayName("newSearch wraps generation counter from max byte value back to min")
    void newSearchWrapsGeneration_whenByteOverflows() {
        // Arrange - advance to Byte.MAX_VALUE
        TranspositionTable tt = new TranspositionTable(4);
        for (int i = 0; i < 127; i++) {
            tt.newSearch();
        }
        assertEquals(Byte.MAX_VALUE, tt.getCurrentGeneration());

        // Act - one more increment wraps around
        tt.newSearch();

        // Assert - wraps to Byte.MIN_VALUE
        assertEquals(Byte.MIN_VALUE, tt.getCurrentGeneration());
    }

    @Test
    @DisplayName("addElement evicts stale entry in depth-preferred slot when incoming depth overcomes age penalty")
    void addElementEvictsStaleFromSlot0_whenIncomingDepthExceedsAgedDepth() {
        // Arrange - store entry at gen 0, advance, store entry at gen 1
        TranspositionTable tt = new TranspositionTable(1);
        long hashStale   = 0b00L;  // bucket 0, slot 0, depth 5, gen 0 → adjusted depth = 5 - AGE_BONUS = -3
        long hashCurrent = 0b10L;  // bucket 0, slot 1, depth 5, gen 1
        tt.addElement(hashStale, 1, 5, 100, NodeType.EXACT);
        tt.newSearch(); // advance generation
        tt.addElement(hashCurrent, 2, 5, 200, NodeType.EXACT);

        // Act - new entry (depth 4, gen 1): 4 >= -3, so it replaces stale slot 0
        long hashNew = 0b100L;
        tt.addElement(hashNew, 3, 4, 300, NodeType.LOWER_BOUND);

        // Assert - stale entry evicted from slot 0, current entry in slot 1 untouched
        assertEquals(0, tt.probe(hashStale, 1));
        assertNotEquals(0, tt.probe(hashCurrent, 1));
        assertNotEquals(0, tt.probe(hashNew, 1));
    }

    @Test
    @DisplayName("addElement evicts stale deep entry from slot 0 when depth difference is within AGE_BONUS")
    void addElementEvictsStaleDeepFromSlot0_whenDepthDiffWithinBonus() {
        // Arrange - stale entry in slot 0 with depth 10 (adjusted = 10 - 8 = 2),
        //           current entry in slot 1 with depth 5
        TranspositionTable tt = new TranspositionTable(1);
        long hashStale   = 0b00L;
        long hashCurrent = 0b10L;
        tt.addElement(hashStale, 1, 10, 100, NodeType.EXACT);
        tt.newSearch();
        tt.addElement(hashCurrent, 2, 5, 200, NodeType.EXACT);

        // Act - new entry depth 4 >= adjusted 2 → replaces slot 0
        long hashNew = 0b100L;
        tt.addElement(hashNew, 3, 4, 300, NodeType.LOWER_BOUND);

        // Assert - stale depth-10 evicted; current depth-5 in slot 1 untouched
        assertEquals(0, tt.probe(hashStale, 1));
        assertNotEquals(0, tt.probe(hashCurrent, 1));
        assertNotEquals(0, tt.probe(hashNew, 1));
    }

    @Test
    @DisplayName("addElement sends entry to always-replace slot 1 when stale slot 0 depth exceeds AGE_BONUS advantage")
    void addElementWritesToSlot1_whenStaleSlot0TooDeep() {
        // Arrange - stale entry in slot 0 with depth 20 (adjusted = 20 - 8 = 12),
        //           current entry in slot 1 with depth 5
        TranspositionTable tt = new TranspositionTable(1);
        long hashStale   = 0b00L;
        long hashCurrent = 0b10L;
        tt.addElement(hashStale, 1, 20, 100, NodeType.EXACT);
        tt.newSearch();
        tt.addElement(hashCurrent, 2, 5, 200, NodeType.EXACT);

        // Act - new entry depth 4 < adjusted 12 → goes to always-replace slot 1
        long hashNew = 0b100L;
        tt.addElement(hashNew, 3, 4, 300, NodeType.LOWER_BOUND);

        // Assert - stale depth-20 preserved in slot 0; current depth-5 evicted from slot 1
        assertNotEquals(0, tt.probe(hashStale, 1));
        assertEquals(0, tt.probe(hashCurrent, 1));
        assertNotEquals(0, tt.probe(hashNew, 1));
    }

    @Test
    @DisplayName("Slot 1 always accepts a new entry even when slot 0 has a deep current entry")
    void slot1AlwaysAcceptsNewEntry_whenSlot0HasDeepCurrentEntry() {
        // Arrange - slot 0: current depth 20, slot 1: current depth 15
        TranspositionTable tt = new TranspositionTable(1);
        long hash1 = 0b00L;  // slot 0, depth 20
        long hash2 = 0b10L;  // slot 1, depth 15
        tt.addElement(hash1, 1, 20, 100, NodeType.EXACT);
        tt.addElement(hash2, 2, 15, 200, NodeType.EXACT);

        // Act - very shallow entry (depth 1): 1 < 20, so rejected by slot 0 → always-replace slot 1
        long hashShallow = 0b100L;
        tt.addElement(hashShallow, 3, 1, 50, NodeType.UPPER_BOUND);

        // Assert - shallow entry stored (slot 1), deep entry preserved (slot 0)
        assertNotEquals(0, tt.probe(hash1, 1));       // deep entry survived in slot 0
        assertEquals(0, tt.probe(hash2, 1));            // evicted from slot 1
        assertNotEquals(0, tt.probe(hashShallow, 1));   // shallow entry got in via slot 1
    }

    @Test
    @DisplayName("Deeper incoming entry displaces slot 0 and preserves slot 1 entry")
    void deeperIncomingDisplacesSlot0_preservesSlot1() {
        // Arrange - slot 0: current depth 5, slot 1: current depth 10
        TranspositionTable tt = new TranspositionTable(1);
        long hash1 = 0b00L;  // slot 0, depth 5
        long hash2 = 0b10L;  // slot 1, depth 10
        tt.addElement(hash1, 1, 5, 100, NodeType.EXACT);
        tt.addElement(hash2, 2, 10, 200, NodeType.EXACT);

        // Act - deep entry (depth 15): 15 >= 5, so it replaces slot 0
        long hashDeep = 0b100L;
        tt.addElement(hashDeep, 3, 15, 300, NodeType.LOWER_BOUND);

        // Assert - new deep entry in slot 0, slot 1 preserved
        assertNotEquals(0, tt.probe(hashDeep, 1));  // new deep entry in slot 0
        assertNotEquals(0, tt.probe(hash2, 1));     // slot 1 preserved
        assertEquals(0, tt.probe(hash1, 1));        // old slot 0 entry evicted
    }

    @Test
    @DisplayName("Stale entries remain probe-able after generation advances")
    void staleEntriesRemainProbeable_afterNewSearch() {
        // Arrange
        TranspositionTable tt = new TranspositionTable(8);
        long hash = 12345L;
        tt.addElement(hash, 42, 10, 500, NodeType.EXACT);

        // Act - advance generation (entry becomes stale)
        tt.newSearch();
        long packed = tt.probe(hash, 10);

        // Assert - stale entry is still returned by probe
        assertNotEquals(0, packed);
        assertEquals(42, TranspositionTable.unpackBestMove(packed));
        assertEquals(500, TranspositionTable.unpackScore(packed));
    }

    @Test
    @DisplayName("Same-position overwrite refreshes generation to current")
    void samePositionOverwrite_refreshesGeneration() {
        // Arrange - store entry at gen 0, advance to gen 1
        TranspositionTable tt = new TranspositionTable(1);
        long hashEntry   = 0b00L;
        long hashOther   = 0b10L;
        tt.addElement(hashEntry, 1, 5, 100, NodeType.EXACT);
        tt.newSearch();
        // Overwrite same-position (refreshes to gen 1)
        tt.addElement(hashEntry, 1, 5, 100, NodeType.EXACT);
        // Fill other slot at gen 1
        tt.addElement(hashOther, 2, 5, 200, NodeType.EXACT);

        // Act - advance again to gen 2. Both entries are now stale (gen 1).
        //       Store a new entry that must evict one of them.
        tt.newSearch();
        long hashNew = 0b100L;
        tt.addElement(hashNew, 3, 5, 300, NodeType.LOWER_BOUND);

        // Assert - both were same generation (stale). Slot 0 (hashEntry) has adjusted depth 5-8=-3;
        //          new depth 5 >= -3, so slot 0 is overwritten. hashOther in slot 1 and hashNew in slot 0 survive.
        assertNotEquals(0, tt.probe(hashNew, 1));
    }
}
