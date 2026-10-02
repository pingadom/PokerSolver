package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates one frozen six-seat policy on independently estimated showdown payoffs. The chance
 * support, chance probabilities, betting tree and rake must be identical across the two games; only
 * the terminal showdown estimates may change.
 */
public final class SixMaxPreflopPolicyHoldoutAudit {
    public record Report(
            MultiPlayerInformationSetBestResponse.Report training,
            MultiPlayerInformationSetBestResponse.Report holdout,
            List<Double> profileUtilityDriftBb,
            List<Double> deviationGainDriftBb,
            double maximumAbsoluteProfileUtilityDriftBb,
            double maximumAbsoluteDeviationGainDriftBb,
            double trainingMaximumTerminalPayoffStandardErrorBb,
            double holdoutMaximumTerminalPayoffStandardErrorBb) {
        public Report {
            profileUtilityDriftBb = List.copyOf(profileUtilityDriftBb);
            deviationGainDriftBb = List.copyOf(deviationGainDriftBb);
        }
    }

    private SixMaxPreflopPolicyHoldoutAudit() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame training,
            SixMaxPreflopCheckdownGame holdout,
            CfrSolution frozenPolicy) {
        Objects.requireNonNull(training, "training");
        Objects.requireNonNull(holdout, "holdout");
        Objects.requireNonNull(frozenPolicy, "frozenPolicy");
        requireSameGame(training, holdout);
        return assess(
                training,
                holdout,
                frozenPolicy,
                MultiPlayerInformationSetBestResponse.assess(training, frozenPolicy));
    }

    static Report assess(
            SixMaxPreflopCheckdownGame training,
            SixMaxPreflopCheckdownGame holdout,
            CfrSolution frozenPolicy,
            MultiPlayerInformationSetBestResponse.Report trainingReport) {
        Objects.requireNonNull(training, "training");
        Objects.requireNonNull(holdout, "holdout");
        Objects.requireNonNull(frozenPolicy, "frozenPolicy");
        Objects.requireNonNull(trainingReport, "trainingReport");
        requireSameGame(training, holdout);
        var validation = MultiPlayerInformationSetBestResponse.assess(holdout, frozenPolicy);
        List<Double> utilityDrift = new ArrayList<>();
        List<Double> gainDrift = new ArrayList<>();
        double maxUtilityDrift = 0;
        double maxGainDrift = 0;
        for (int seat = 0; seat < training.playerCount(); seat++) {
            double utility =
                    validation.profileUtilitiesBb().get(seat)
                            - trainingReport.profileUtilitiesBb().get(seat);
            double gain =
                    validation.deviationGainsBb().get(seat)
                            - trainingReport.deviationGainsBb().get(seat);
            utilityDrift.add(utility);
            gainDrift.add(gain);
            maxUtilityDrift = Math.max(maxUtilityDrift, Math.abs(utility));
            maxGainDrift = Math.max(maxGainDrift, Math.abs(gain));
        }
        return new Report(
                trainingReport,
                validation,
                utilityDrift,
                gainDrift,
                maxUtilityDrift,
                maxGainDrift,
                training.maximumTerminalPayoffStandardErrorBb(),
                holdout.maximumTerminalPayoffStandardErrorBb());
    }

    private static void requireSameGame(
            SixMaxPreflopCheckdownGame training, SixMaxPreflopCheckdownGame holdout) {
        if (!training.rules().equals(holdout.rules())
                || !training.rakeRule().equals(holdout.rakeRule())
                || !training.treeSummary().equals(holdout.treeSummary())
                || training.chanceModel() != holdout.chanceModel()
                || training.chanceSamples() != holdout.chanceSamples())
            throw new IllegalArgumentException(
                    "Holdout must use the same rules, rake and chance model");
        var trainDeals = training.chanceOutcomes(training.initialState());
        var validationDeals = holdout.chanceOutcomes(holdout.initialState());
        if (trainDeals.size() != validationDeals.size())
            throw new IllegalArgumentException("Holdout chance support differs");
        for (int deal = 0; deal < trainDeals.size(); deal++) {
            var first = trainDeals.get(deal);
            var second = validationDeals.get(deal);
            if (Math.abs(first.probability() - second.probability()) > 1e-12)
                throw new IllegalArgumentException("Holdout chance probabilities differ");
            var trainHands = training.dealtHands(first.state());
            var validationHands = holdout.dealtHands(second.state());
            for (int seat = 0; seat < training.playerCount(); seat++)
                if (!trainHands.get(seat).key().equals(validationHands.get(seat).key()))
                    throw new IllegalArgumentException("Holdout physical deals differ");
        }
    }
}
