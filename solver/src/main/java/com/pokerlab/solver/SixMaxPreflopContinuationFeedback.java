package com.pokerlab.solver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Alternating experiment: re-solve all preflop decisions against fixed continuation values. */
public final class SixMaxPreflopContinuationFeedback {
    public enum Algorithm {
        CFR_PLUS,
        LINEAR_VANILLA
    }

    public record Report(
            int preflopIterations,
            String preflopAlgorithm,
            String preflopChanceTraversal,
            MultiPlayerCfrSolver.Statistics preflopTraversal,
            int replacedPreflopInformationSets,
            int preservedPostflopInformationSets,
            double maximumPreflopActionFrequencyChange,
            String originalSolutionHash,
            String candidateSolutionHash,
            List<SixMaxFrozenContinuationPreflopGame.TerminalValue> frozenTerminalValues,
            SixMaxConnectedPreflopAudit.Quality originalProjectedQuality,
            SixMaxConnectedPreflopAudit.Quality candidateProjectedQuality,
            SixMaxConnectedPreflopAudit.Quality originalParentQuality,
            SixMaxConnectedPreflopAudit.Quality candidateParentQuality,
            double parentNashConvChangeBb,
            boolean parentNashConvDidNotIncrease,
            SixMaxReachedContinuationStudy.Reach originalReach,
            SixMaxReachedContinuationStudy.Reach candidateReach,
            List<SixMaxConnectedPreflopAudit.ConditionalPostflop> originalConditionalPostflop,
            List<SixMaxConnectedPreflopAudit.ConditionalPostflop> candidateConditionalPostflop) {
        public Report {
            frozenTerminalValues = List.copyOf(frozenTerminalValues);
            originalConditionalPostflop = List.copyOf(originalConditionalPostflop);
            candidateConditionalPostflop = List.copyOf(candidateConditionalPostflop);
        }
    }

    public record Result(CfrSolution candidate, Report report) {}

    private SixMaxPreflopContinuationFeedback() {}

    public static Result solve(
            SixMaxConnectedPreflopGame game,
            CfrSolution original,
            int iterations,
            SixMaxContinuationStudyBudget budget) {
        return solve(game, original, iterations, budget, Algorithm.CFR_PLUS);
    }

    public static Result solve(
            SixMaxConnectedPreflopGame game,
            CfrSolution original,
            int iterations,
            SixMaxContinuationStudyBudget budget,
            Algorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm");
        if (iterations < 1 || iterations > 3000)
            throw new IllegalArgumentException("Preflop feedback requires 1–3000 iterations");
        Objects.requireNonNull(original, "original");
        var projected = new SixMaxFrozenContinuationPreflopGame(game, original, budget);
        var oldPre = preflopPolicy(original);
        var solver =
                algorithm == Algorithm.CFR_PLUS
                        ? new MultiPlayerCfrSolver<>(projected, CfrSolver.Variant.CFR_PLUS)
                        : new MultiPlayerCfrSolver<>(
                                projected,
                                CfrSolver.Variant.VANILLA,
                                MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                                0,
                                0,
                                true,
                                true);
        var newPre = solver.solve(iterations);
        if (!newPre.strategy().keySet().equals(oldPre.strategy().keySet()))
            throw new IllegalStateException(
                    "Preflop information support changed during projection");
        var candidate = liftPreflop(original, newPre);
        var before =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, original));
        var after =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, candidate));
        var projectedBefore =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(projected, oldPre));
        var projectedAfter =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(projected, newPre));
        requireProjectionAgreement(before, projectedBefore);
        requireProjectionAgreement(after, projectedAfter);
        double maximumChange = 0;
        for (var row : oldPre.strategy().entrySet())
            for (var action : row.getValue().entrySet())
                maximumChange =
                        Math.max(
                                maximumChange,
                                Math.abs(
                                        action.getValue()
                                                - newPre.strategy()
                                                        .get(row.getKey())
                                                        .get(action.getKey())));
        double change = after.nashConvBb() - before.nashConvBb();
        return new Result(
                candidate,
                new Report(
                        iterations,
                        algorithm.name(),
                        "EXHAUSTIVE",
                        solver.statistics(),
                        newPre.strategy().size(),
                        original.strategy().size() - newPre.strategy().size(),
                        maximumChange,
                        projected.frozenSolutionHash(),
                        SixMaxConnectedPostflopAudit.solutionHash(candidate),
                        projected.terminalValues(),
                        projectedBefore,
                        projectedAfter,
                        before,
                        after,
                        change,
                        change <= 1e-9,
                        SixMaxReachedContinuationStudy.reach(game, original),
                        SixMaxReachedContinuationStudy.reach(game, candidate),
                        SixMaxConnectedPreflopAudit.conditionalPostflop(game, original),
                        SixMaxConnectedPreflopAudit.conditionalPostflop(game, candidate)));
    }

    static CfrSolution preflopPolicy(CfrSolution joint) {
        var rows = new LinkedHashMap<>(joint.strategy());
        rows.keySet().removeIf(key -> key.contains(":postflop:"));
        return new CfrSolution(joint.iterations(), rows);
    }

    private static void requireProjectionAgreement(
            SixMaxConnectedPreflopAudit.Quality parent,
            SixMaxConnectedPreflopAudit.Quality projected) {
        for (int player = 0; player < 6; player++) {
            if (Math.abs(
                            parent.profileUtilitiesBb().get(player)
                                    - projected.profileUtilitiesBb().get(player))
                    > 1e-9)
                throw new IllegalStateException("Frozen projection changed profile utility");
            if (projected.bestResponseUtilitiesBb().get(player)
                    > parent.bestResponseUtilitiesBb().get(player) + 1e-9)
                throw new IllegalStateException(
                        "Preflop-only best response exceeds full-game response");
        }
    }

    static CfrSolution liftPreflop(CfrSolution original, CfrSolution preflop) {
        if (!preflop.strategy().keySet().equals(preflopPolicy(original).strategy().keySet()))
            throw new IllegalArgumentException(
                    "Replacement must have exactly the original preflop support");
        var rows = new LinkedHashMap<>(original.strategy());
        rows.putAll(preflop.strategy());
        // Keep the original joint-training marker; the independent preflop budget is in Report.
        return new CfrSolution(original.iterations(), rows);
    }
}
