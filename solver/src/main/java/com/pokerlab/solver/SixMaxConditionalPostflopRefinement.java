package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Offline conditional re-solving. Frozen posteriors do not make replacements safe in the parent
 * game.
 */
public final class SixMaxConditionalPostflopRefinement {
    public record Branch(
            String publicHistory,
            List<String> flop,
            String status,
            double historyReachProbability,
            double flopProbabilityGivenHistory,
            int posteriorJointDeals,
            int replacedInformationSets,
            HeadsUpBestResponse.Report before,
            HeadsUpBestResponse.Report after) {
        public Branch {
            flop = List.copyOf(flop);
        }
    }

    public record Report(
            int refinementIterations,
            int inputInformationSets,
            int replacedInformationSets,
            int preservedInformationSets,
            String originalSolutionHash,
            String candidateSolutionHash,
            SixMaxConnectedPreflopAudit.Quality originalQuality,
            SixMaxConnectedPreflopAudit.Quality candidateQuality,
            double nashConvChangeBb,
            double bettingContinuationProbability,
            List<Branch> branches) {
        public Report {
            branches = List.copyOf(branches);
        }
    }

    public record Result(CfrSolution candidate, Report report) {}

    private SixMaxConditionalPostflopRefinement() {}

    public static Result refine(
            SixMaxConnectedPreflopGame game,
            CfrSolution original,
            int iterations,
            Consumer<Branch> progress) {
        if (iterations < 1 || iterations > 500)
            throw new IllegalArgumentException("Conditional refinement requires 1–500 iterations");
        Objects.requireNonNull(progress, "progress");
        if (game.coverage().stream()
                        .flatMap(c -> c.legalSelectedFlopsByDeal().stream())
                        .mapToInt(Integer::intValue)
                        .sum()
                > 8)
            throw new IllegalArgumentException(
                    "Refinement supports at most eight compatible deal/flop pairs");
        var validated = MultiPlayerStrategyCompletion.uniformAtUnseen(game, original, 2_000_000);
        if (validated.addedInformationSets() != 0)
            throw new IllegalArgumentException(
                    "Refinement requires an explicitly completed input policy");
        var originalQuality =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, original));
        var updated = new LinkedHashMap<>(original.strategy());
        var branches = new ArrayList<Branch>();
        int replaced = 0;
        for (var selection : game.selections()) {
            String publicHistory =
                    game.coverage().stream()
                            .filter(c -> c.actions().equals(selection.history()))
                            .findFirst()
                            .orElseThrow()
                            .publicHistory();
            double reach = SixMaxConnectedPreflopAudit.historyReach(game, original, selection);
            var transition =
                    reach == 0
                            ? null
                            : new SixMaxPolicyFlopTransition(
                                    game.source(), original, selection.history());
            for (var board : selection.flops()) {
                double probability = transition == null ? 0 : transition.flopProbability(board);
                Branch branch;
                if (probability == 0) {
                    branch =
                            new Branch(
                                    publicHistory,
                                    board.stream().map(Card::compact).toList(),
                                    transition == null ? "ZERO_HISTORY_REACH" : "ZERO_FLOP_REACH",
                                    reach,
                                    probability,
                                    0,
                                    0,
                                    null,
                                    null);
                } else {
                    var flop = transition.conditionOnFlop(board);
                    var post =
                            new SixMaxHeadsUpPostflopGame(
                                    flop,
                                    selection.flopBetBb(),
                                    selection.turnBetBb(),
                                    selection.riverBetBb());
                    var before =
                            HeadsUpBestResponse.assess(
                                    post,
                                    SixMaxConnectedPreflopAudit.postflopPolicy(post, original));
                    var solved =
                            new CfrSolver<>(post, CfrSolver.Variant.CFR_PLUS).solve(iterations);
                    var after = HeadsUpBestResponse.assess(post, solved);
                    for (var row : solved.strategy().entrySet()) {
                        int localPlayer =
                                Integer.parseInt(
                                        row.getKey().substring(0, row.getKey().indexOf(':')));
                        String actualKey =
                                post.seat(localPlayer).ordinal()
                                        + ":postflop:"
                                        + row.getKey().substring(row.getKey().indexOf(':') + 1);
                        if (!updated.containsKey(actualKey))
                            throw new IllegalStateException(
                                    "Refined policy is outside the parent game");
                        updated.put(actualKey, row.getValue());
                    }
                    replaced += solved.strategy().size();
                    branch =
                            new Branch(
                                    publicHistory,
                                    board.stream().map(Card::compact).toList(),
                                    "REFINED",
                                    reach,
                                    probability,
                                    flop.deals().size(),
                                    solved.strategy().size(),
                                    before,
                                    after);
                }
                branches.add(branch);
                progress.accept(branch);
            }
        }
        var candidate = new CfrSolution(original.iterations(), updated);
        MultiPlayerStrategyCompletion.uniformAtUnseen(game, candidate, 2_000_000);
        var candidateQuality =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, candidate));
        return new Result(
                candidate,
                new Report(
                        iterations,
                        original.strategy().size(),
                        replaced,
                        original.strategy().size() - replaced,
                        SixMaxConnectedPostflopAudit.solutionHash(original),
                        SixMaxConnectedPostflopAudit.solutionHash(candidate),
                        originalQuality,
                        candidateQuality,
                        candidateQuality.nashConvBb() - originalQuality.nashConvBb(),
                        SixMaxConnectedPreflopAudit.bettingProbability(game, candidate),
                        branches));
    }
}
