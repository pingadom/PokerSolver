package com.pokerlab.solver;

/** Explicit offline cost limits; requests fail rather than prune private or public chance. */
public record SixMaxContinuationStudyBudget(
        int maximumCompatibleDealFlops, long maximumCompleteTreeStates) {
    public record Cost(int compatibleDealFlops, long completeTreeStates) {}

    public SixMaxContinuationStudyBudget {
        if (maximumCompatibleDealFlops < 1
                || maximumCompatibleDealFlops > 16
                || maximumCompleteTreeStates < 1
                || maximumCompleteTreeStates > 2_000_000)
            throw new IllegalArgumentException(
                    "Study budgets require 1–16 compatible deal/flop pairs and 1–2000000 states");
    }

    public static SixMaxContinuationStudyBudget standard() {
        return new SixMaxContinuationStudyBudget(8, 2_000_000);
    }

    public static SixMaxContinuationStudyBudget widerFlops() {
        return new SixMaxContinuationStudyBudget(16, 2_000_000);
    }

    public Cost validate(SixMaxConnectedPreflopGame game) {
        int pairs =
                game.coverage().stream()
                        .flatMap(c -> c.legalSelectedFlopsByDeal().stream())
                        .mapToInt(Integer::intValue)
                        .sum();
        requireCompatiblePairs(pairs);
        long states = game.completeTreeStateCount();
        if (states > maximumCompleteTreeStates)
            throw new IllegalArgumentException(
                    "Complete tree needs "
                            + states
                            + " states, exceeding study budget "
                            + maximumCompleteTreeStates);
        return new Cost(pairs, states);
    }

    void requireCompatiblePairs(int pairs) {
        if (pairs > maximumCompatibleDealFlops)
            throw new IllegalArgumentException(
                    "Study needs "
                            + pairs
                            + " compatible deal/flop pairs, exceeding budget "
                            + maximumCompatibleDealFlops);
    }
}
