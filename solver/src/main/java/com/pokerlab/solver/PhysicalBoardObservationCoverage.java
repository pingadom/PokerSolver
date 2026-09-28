package com.pokerlab.solver;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Compares public-observation support on the exact same sampled physical hands and action paths.
 * Uniform actions are a controlled reach policy, not a candidate poker strategy.
 */
public final class PhysicalBoardObservationCoverage {
    public record StreetCoverage(
            PhysicalConnectedStreetDeviationAudit.Street street,
            int reached,
            int fineBuckets,
            int coarseBuckets,
            int fineSupportedStates,
            int coarseSupportedStates) {
        public double fineSupportRate() {
            return reached == 0 ? 0 : (double) fineSupportedStates / reached;
        }

        public double coarseSupportRate() {
            return reached == 0 ? 0 : (double) coarseSupportedStates / reached;
        }
    }

    public record Report(
            String fineGameHash,
            String coarseGameHash,
            long seed,
            int attemptedDeals,
            int minimumObservationsPerBucket,
            List<StreetCoverage> streets) {}

    private PhysicalBoardObservationCoverage() {}

    public static Report assess(int attemptedDeals, int minimumObservationsPerBucket, long seed) {
        if (attemptedDeals < 2 || attemptedDeals > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 attempted deals");
        if (minimumObservationsPerBucket < 2 || minimumObservationsPerBucket > attemptedDeals)
            throw new IllegalArgumentException("Invalid bucket-support threshold");
        var fine = ButtonBigBlindRangeValidationFixture.createBucketed();
        var coarse = ButtonBigBlindRangeValidationFixture.createCoarseBucketed();
        var streets = PhysicalConnectedStreetDeviationAudit.Street.values();
        Map<PhysicalConnectedStreetDeviationAudit.Street, Map<String, Integer>> fineCounts =
                new EnumMap<>(PhysicalConnectedStreetDeviationAudit.Street.class);
        Map<PhysicalConnectedStreetDeviationAudit.Street, Map<String, Integer>> coarseCounts =
                new EnumMap<>(PhysicalConnectedStreetDeviationAudit.Street.class);
        for (var street : streets) {
            fineCounts.put(street, new LinkedHashMap<>());
            coarseCounts.put(street, new LinkedHashMap<>());
        }
        SplittableRandom random = new SplittableRandom(seed);
        for (int attempt = 0; attempt < attemptedDeals; attempt++) {
            var state = fine.initialState();
            while (!fine.isTerminal(state)) {
                int player = fine.currentPlayer(state);
                if (player == -1) {
                    state = fine.sampleChanceOutcome(state, random.nextDouble()).state();
                    continue;
                }
                if (player == 0)
                    for (var street : streets)
                        if (PhysicalConnectedStreetDeviationAudit.firstDecision(state, street)) {
                            fineCounts
                                    .get(street)
                                    .merge(fine.informationSet(state), 1, Integer::sum);
                            coarseCounts
                                    .get(street)
                                    .merge(coarse.informationSet(state), 1, Integer::sum);
                        }
                List<String> actions = fine.legalActions(state);
                state =
                        fine.afterAction(
                                state, actions.get((int) (random.nextDouble() * actions.size())));
            }
        }
        List<StreetCoverage> coverage =
                java.util.Arrays.stream(streets)
                        .map(
                                street -> {
                                    Map<String, Integer> fineStreet = fineCounts.get(street);
                                    Map<String, Integer> coarseStreet = coarseCounts.get(street);
                                    int reached =
                                            fineStreet.values().stream()
                                                    .mapToInt(Integer::intValue)
                                                    .sum();
                                    if (reached
                                            != coarseStreet.values().stream()
                                                    .mapToInt(Integer::intValue)
                                                    .sum())
                                        throw new IllegalStateException(
                                                "Reach differs by observation mode");
                                    return new StreetCoverage(
                                            street,
                                            reached,
                                            fineStreet.size(),
                                            coarseStreet.size(),
                                            supportedStates(
                                                    fineStreet, minimumObservationsPerBucket),
                                            supportedStates(
                                                    coarseStreet, minimumObservationsPerBucket));
                                })
                        .toList();
        return new Report(
                fine.contentHash(),
                coarse.contentHash(),
                seed,
                attemptedDeals,
                minimumObservationsPerBucket,
                coverage);
    }

    private static int supportedStates(Map<String, Integer> counts, int minimum) {
        return counts.values().stream().filter(count -> count >= minimum).mapToInt(i -> i).sum();
    }
}
