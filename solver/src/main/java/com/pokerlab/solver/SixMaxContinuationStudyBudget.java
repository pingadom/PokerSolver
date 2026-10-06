package com.pokerlab.solver;

/** Explicit offline cost limits; requests fail rather than prune private or public chance. */
public record SixMaxContinuationStudyBudget(
        int maximumCompatibleDealFlops, long maximumCompleteTreeStates) {
    public record Cost(int compatibleDealFlops, long completeTreeStates) {}

    public static final class Exceeded extends IllegalArgumentException {
        private final String resource;
        private final long required;
        private final long limit;

        Exceeded(String resource, long required, long limit) {
            super(
                    resource.equals("COMPLETE_TREE_STATES")
                            ? "Complete tree needs "
                                    + required
                                    + " states, exceeding study budget "
                                    + limit
                            : "Study needs "
                                    + required
                                    + " compatible deal/flop pairs, exceeding budget "
                                    + limit);
            this.resource = resource;
            this.required = required;
            this.limit = limit;
        }

        public String resource() {
            return resource;
        }

        public long required() {
            return required;
        }

        public long limit() {
            return limit;
        }
    }

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
            throw new Exceeded("COMPLETE_TREE_STATES", states, maximumCompleteTreeStates);
        return new Cost(pairs, states);
    }

    void requireCompatiblePairs(int pairs) {
        if (pairs > maximumCompatibleDealFlops)
            throw new Exceeded("COMPATIBLE_DEAL_FLOPS", pairs, maximumCompatibleDealFlops);
    }
}
