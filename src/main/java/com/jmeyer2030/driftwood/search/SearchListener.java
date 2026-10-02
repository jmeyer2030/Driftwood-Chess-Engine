package com.jmeyer2030.driftwood.search;

/** Receives completed iterations on the thread coordinating the search. */
@FunctionalInterface
public interface SearchListener {
    SearchListener NONE = iteration -> {};

    void onIterationCompleted(SearchIteration iteration);
}
