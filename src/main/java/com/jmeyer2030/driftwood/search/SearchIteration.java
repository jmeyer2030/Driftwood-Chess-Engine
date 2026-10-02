package com.jmeyer2030.driftwood.search;

import java.util.List;

/** Immutable snapshot; subsequent iterations cannot change the reported PV. */
public record SearchIteration(int depth, int score, List<Integer> principalVariation) {
    public SearchIteration {
        principalVariation = List.copyOf(principalVariation);
    }
}
