package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Measures traversal savings, explicit missing-policy coverage and exact declared-game quality. */
public final class SixMaxFlopCoverageAudit {
    private static final long COMPLETION_STATE_BUDGET = 2_000_000;

    public record Run(
            long seed,
            int iterations,
            double uniformProposalMixture,
            boolean checkdownControlVariate,
            int visitedInformationSets,
            int uniformlyCompletedInformationSets,
            long completeTreeVisits,
            long exhaustiveVisitsAtSameIterations,
            MultiPlayerCfrSolver.Statistics sampledTraversal,
            double traversalReductionFactor,
            String sampledSolutionHash,
            String completedSolutionHash,
            SixMaxConnectedPreflopAudit.Quality quality,
            double bettingContinuationProbability,
            List<SixMaxConnectedPreflopAudit.ConditionalPostflop> conditionalPostflop) {
        public Run {
            conditionalPostflop = List.copyOf(conditionalPostflop);
        }
    }

    public record Case(
            int selectedFlops,
            int connectedDealFlops,
            List<SixMaxConnectedPreflopGame.Coverage> coverage,
            double sourceBettingContinuationProbability,
            List<Run> runs) {
        public Case {
            coverage = List.copyOf(coverage);
            runs = List.copyOf(runs);
        }
    }

    public record Report(
            String continuationModel,
            String algorithm,
            String chanceTraversal,
            long flopSelectionSeed,
            List<Case> cases) {
        public Report {
            cases = List.copyOf(cases);
        }
    }

    private SixMaxFlopCoverageAudit() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            long flopSeed,
            List<Long> trainingSeeds,
            int iterations,
            int maximumFlops,
            List<Double> mixtures) {
        return assess(
                base,
                source,
                flopSeed,
                trainingSeeds,
                iterations,
                maximumFlops,
                mixtures,
                (width, run) -> {});
    }

    public static Report assess(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            long flopSeed,
            List<Long> trainingSeeds,
            int iterations,
            int maximumFlops,
            List<Double> mixtures,
            BiConsumer<Integer, Run> progress) {
        return assess(
                base,
                source,
                flopSeed,
                trainingSeeds,
                iterations,
                maximumFlops,
                mixtures,
                MultiPlayerCfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                false,
                progress);
    }

    public static Report assess(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            long flopSeed,
            List<Long> trainingSeeds,
            int iterations,
            int maximumFlops,
            List<Double> mixtures,
            MultiPlayerCfrSolver.ChanceMode chanceMode,
            boolean linearWeighting,
            BiConsumer<Integer, Run> progress) {
        Objects.requireNonNull(progress, "progress");
        if (chanceMode != MultiPlayerCfrSolver.ChanceMode.SAMPLED_AFTER_ROOT
                && chanceMode != MultiPlayerCfrSolver.ChanceMode.SAMPLED_RUNOUTS)
            throw new IllegalArgumentException("Coverage audit requires enumerated private deals");
        trainingSeeds = List.copyOf(trainingSeeds);
        mixtures = List.copyOf(mixtures);
        if (trainingSeeds.isEmpty()
                || trainingSeeds.size() > 3
                || trainingSeeds.stream().distinct().count() != trainingSeeds.size())
            throw new IllegalArgumentException("Select 1–3 distinct training seeds");
        if (iterations < 1 || iterations > 3000 || maximumFlops < 1 || maximumFlops > 4)
            throw new IllegalArgumentException("Budget requires 1–3000 iterations and 1–4 flops");
        if (mixtures.isEmpty()
                || mixtures.size() > 3
                || mixtures.stream().distinct().count() != mixtures.size()
                || mixtures.stream().anyMatch(m -> !Double.isFinite(m) || m < 0 || m > .95))
            throw new IllegalArgumentException("Select 1–3 distinct mixtures in [0, .95]");
        var example =
                SixMaxPreflopContinuationAudit.assess(base, source, flopSeed, 1).examples().stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Source has no reached heads-up continuation"));
        var support = SixMaxPolicyFlopTransition.counterfactualSupport(base, example.history());
        var boards = new ArrayList<List<Card>>();
        boards.add(example.sampledFlop().stream().map(Card::parse).toList());
        for (int attempt = 1; boards.size() < maximumFlops && attempt <= 1000; attempt++) {
            var board = support.sampleFlop(flopSeed + attempt).board();
            if (!boards.contains(board)) boards.add(board);
        }
        if (boards.size() < maximumFlops)
            throw new IllegalArgumentException("Insufficient distinct flops");
        double flopBet = Math.min(support.potBb() * .5, support.remainingStackBb());
        double turnBet = (support.potBb() + 2 * flopBet) * .5;
        double riverBet =
                (support.potBb()
                                + 2 * flopBet
                                + 2
                                        * Math.min(
                                                turnBet,
                                                Math.max(0, support.remainingStackBb() - flopBet)))
                        * .5;
        var cases = new ArrayList<Case>();
        var widths = maximumFlops == 1 ? List.of(1) : List.of(1, maximumFlops);
        for (int width : widths) {
            var selection =
                    new SixMaxConnectedPreflopGame.Selection(
                            example.history(),
                            boards.subList(0, width),
                            flopBet,
                            turnBet,
                            riverBet);
            var game = new SixMaxConnectedPreflopGame(base, List.of(selection));
            int dealFlops =
                    game.coverage().getFirst().legalSelectedFlopsByDeal().stream()
                            .mapToInt(Integer::intValue)
                            .sum();
            if (dealFlops > 8)
                throw new IllegalArgumentException(
                        "Exact coverage audit supports at most eight deal/flop pairs");
            var runs = new ArrayList<Run>();
            for (long seed : trainingSeeds)
                for (double mixture : mixtures) {
                    var controls =
                            width == maximumFlops && mixture > 0 && seed == trainingSeeds.getFirst()
                                    ? List.of(true, false)
                                    : List.of(true);
                    for (boolean centered : controls) {
                        var solver =
                                new MultiPlayerCfrSolver<>(
                                        game,
                                        CfrSolver.Variant.VANILLA,
                                        chanceMode,
                                        seed,
                                        mixture,
                                        centered,
                                        linearWeighting);
                        var sampled = solver.solve(iterations);
                        var complete =
                                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                        game, sampled, COMPLETION_STATE_BUDGET);
                        var statistics = solver.statistics();
                        long exhaustiveVisits =
                                Math.multiplyExact(
                                        Math.multiplyExact(
                                                complete.visitedStates(), game.playerCount()),
                                        iterations);
                        var quality =
                                MultiPlayerInformationSetBestResponse.assess(
                                        game, complete.solution());
                        runs.add(
                                new Run(
                                        seed,
                                        iterations,
                                        mixture,
                                        centered,
                                        sampled.strategy().size(),
                                        complete.addedInformationSets(),
                                        complete.visitedStates(),
                                        exhaustiveVisits,
                                        statistics,
                                        (double) exhaustiveVisits / statistics.visitedNodes(),
                                        SixMaxConnectedPostflopAudit.solutionHash(sampled),
                                        SixMaxConnectedPostflopAudit.solutionHash(
                                                complete.solution()),
                                        SixMaxConnectedPreflopAudit.Quality.of(quality),
                                        SixMaxConnectedPreflopAudit.bettingProbability(
                                                game, complete.solution()),
                                        SixMaxConnectedPreflopAudit.conditionalPostflop(
                                                game, complete.solution())));
                        progress.accept(width, runs.getLast());
                    }
                }
            cases.add(
                    new Case(
                            width,
                            dealFlops,
                            game.coverage(),
                            SixMaxConnectedPreflopAudit.bettingProbability(game, source),
                            runs));
        }
        return new Report(
                "SPARSE_PHYSICAL_FLOPS_CONNECTED_PREFLOP_FLOP_TURN_RIVER",
                linearWeighting
                        ? "LINEAR_CFR_IMPORTANCE_WEIGHTED_CHANCE_SAMPLING"
                        : "VANILLA_CFR_IMPORTANCE_WEIGHTED_CHANCE_SAMPLING",
                chanceMode.name(),
                flopSeed,
                cases);
    }
}
