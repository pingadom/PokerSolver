package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Offline content screening, separate from strategy quality and trainer publication. */
public final class SixMaxRetainedContinuationCoverage {
    public record Settings(
            double minimumHistoryProbability,
            double minimumHeadsUpFraction,
            double minimumComboMass,
            int minimumMaterialCombosPerActiveSeat) {
        public Settings {
            for (double threshold :
                    new double[] {
                        minimumHistoryProbability, minimumHeadsUpFraction, minimumComboMass
                    })
                if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1)
                    throw new IllegalArgumentException("Coverage thresholds must be in (0, 1]");
            if (minimumMaterialCombosPerActiveSeat < 1
                    || minimumMaterialCombosPerActiveSeat
                            > SixMaxConnectedPreflopGame.MAX_PRIVATE_DEALS
                    || minimumMaterialCombosPerActiveSeat * minimumComboMass > 1)
                throw new IllegalArgumentException(
                        "Material-combo count and mass cannot fit a probability distribution");
        }

        public static Settings researchDefault() {
            return new Settings(.0001, .25, .05, 2);
        }
    }

    public record HandMix(
            Seat seat,
            Map<String, Double> counterfactualMarginal,
            Map<String, Double> reachedMarginal,
            int materialReachedCombos,
            double largestReachedComboMass,
            double reachedEntropyBits,
            double effectiveReachedCombos) {
        public HandMix {
            counterfactualMarginal = Map.copyOf(counterfactualMarginal);
            reachedMarginal = Map.copyOf(reachedMarginal);
        }
    }

    public record Board(
            List<String> flop,
            int counterfactualJointDeals,
            int reachedJointDeals,
            double probabilityGivenHistory,
            double compatiblePosteriorMass,
            double absolutePhysicalProbability,
            HandMix first,
            HandMix second,
            List<String> failures) {
        public Board {
            flop = List.copyOf(flop);
            failures = List.copyOf(failures);
        }
    }

    public record History(
            String publicHistory,
            double probability,
            Double logProbability,
            String reachStatus,
            List<Board> boards,
            List<String> failures) {
        public History {
            boards = List.copyOf(boards);
            failures = List.copyOf(failures);
        }
    }

    public record Report(
            String interpretation,
            String solutionHash,
            Settings settings,
            String status,
            boolean criteriaMet,
            SixMaxReachedContinuationStudy.Reach reach,
            List<History> histories,
            List<String> failures) {
        public Report {
            histories = List.copyOf(histories);
            failures = List.copyOf(failures);
        }
    }

    private SixMaxRetainedContinuationCoverage() {}

    public static Report assess(
            SixMaxConnectedPreflopGame game, CfrSolution policy, Settings settings) {
        Objects.requireNonNull(settings, "settings");
        var support = SixMaxPrivateSupportAudit.assess(game, policy);
        var reach = SixMaxReachedContinuationStudy.reach(game, policy);
        var histories = new ArrayList<History>();
        for (var coverage : game.coverage()) {
            var evidence =
                    support.boards().stream()
                            .filter(b -> b.publicHistory().equals(coverage.publicHistory()))
                            .toList();
            // Positive likelihood products may underflow in absolute reach. Keep the posterior
            // and its log reach rather than treating it as a zero-policy branch.
            boolean positive =
                    SixMaxPrivateSupportAudit.hasPositiveHistoryReach(
                            game.source(), policy, coverage.actions());
            var transition =
                    positive
                            ? new SixMaxPolicyFlopTransition(
                                    game.source(), policy, coverage.actions())
                            : null;
            double probability = evidence.getFirst().policyHistoryReach();
            var failures = new ArrayList<String>();
            if (!positive) failures.add("ZERO_POLICY_HISTORY_REACH");
            else if (probability == 0) failures.add("ABSOLUTE_HISTORY_REACH_UNDERFLOW");
            if (probability < settings.minimumHistoryProbability())
                failures.add("HISTORY_REACH_BELOW_MINIMUM");
            var boards = new ArrayList<Board>();
            for (var board : evidence) {
                var first = mix(board, coverage.firstToAct(), settings);
                var second = mix(board, coverage.secondToAct(), settings);
                var boardFailures = new ArrayList<String>();
                if (board.reachedJointDeals() == 0) boardFailures.add("BOARD_UNREACHED");
                for (var mix : List.of(first, second)) {
                    if (mix.counterfactualMarginal().size()
                            < settings.minimumMaterialCombosPerActiveSeat())
                        boardFailures.add(mix.seat() + ":COUNTERFACTUAL_HAND_SUPPORT_TOO_NARROW");
                    if (mix.materialReachedCombos() < settings.minimumMaterialCombosPerActiveSeat())
                        boardFailures.add(mix.seat() + ":RETAINED_HAND_MIX_TOO_NARROW");
                }
                double conditional =
                        transition == null
                                ? 0
                                : transition.flopProbability(
                                        board.flop().stream().map(Card::parse).toList());
                boards.add(
                        new Board(
                                board.flop(),
                                board.counterfactualJointDeals(),
                                board.reachedJointDeals(),
                                conditional,
                                conditional * SixMaxPolicyFlopTransition.FLOPS_PER_DEAL,
                                board.reachedPhysicalFlopProbability(),
                                first,
                                second,
                                boardFailures));
            }
            if (boards.stream().anyMatch(b -> !b.failures().isEmpty()))
                failures.add("BOARD_CONTENT_CRITERIA_FAILED");
            histories.add(
                    new History(
                            coverage.publicHistory(),
                            probability,
                            transition == null ? null : transition.logReachProbability(),
                            !positive
                                    ? "ZERO_POLICY_REACH"
                                    : probability == 0 ? "NUMERIC_UNDERFLOW" : "POSITIVE_REACH",
                            boards,
                            failures));
        }
        var failures = new ArrayList<String>();
        if (reach.fractionOfHeadsUpProbabilitySelected() < settings.minimumHeadsUpFraction())
            failures.add("HEADS_UP_HISTORY_COVERAGE_BELOW_MINIMUM");
        if (histories.stream().anyMatch(h -> !h.failures().isEmpty()))
            failures.add("SELECTED_HISTORY_CRITERIA_FAILED");
        boolean passed = failures.isEmpty();
        return new Report(
                "Offline engineering screen, not a convergence or trainer-publication certificate. "
                        + "History reach and fraction of reached heads-up history mass are distinct from physical-board coverage. "
                        + "A physical flop has probability at most 1/9880 given a history; no absolute physical-flop floor is applied. "
                        + "Counterfactual support is preserved. Marginals are descriptive, not independent ranges. "
                        + "Quality acceptance remains a separate decision.",
                support.solutionHash(),
                settings,
                passed ? "CONTENT_CRITERIA_MET" : "COVERAGE_CRITERIA_FAILED",
                passed,
                reach,
                histories,
                failures);
    }

    private static HandMix mix(
            SixMaxPrivateSupportAudit.BoardSupport board, Seat seat, Settings settings) {
        var prior = board.counterfactualMarginals().get(seat);
        var posterior = board.reachedMarginals().getOrDefault(seat, Map.of());
        double entropy =
                posterior.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .mapToDouble(Map.Entry::getValue)
                        .filter(p -> p > 0)
                        .map(p -> -p * StrictMath.log(p) / StrictMath.log(2))
                        .sum();
        return new HandMix(
                seat,
                prior,
                posterior,
                (int)
                        posterior.values().stream()
                                .filter(p -> p >= settings.minimumComboMass())
                                .count(),
                posterior.values().stream().mapToDouble(Double::doubleValue).max().orElse(0),
                entropy,
                posterior.isEmpty() ? 0 : StrictMath.pow(2, entropy));
    }
}
