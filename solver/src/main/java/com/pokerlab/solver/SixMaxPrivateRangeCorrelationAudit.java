package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Offline pairwise diagnostics of the physical joint prior, independent of any policy. */
public final class SixMaxPrivateRangeCorrelationAudit {
    public record ComboPair(String firstCombo, String secondCombo, double probability) {}

    public record SeatPair(
            Seat firstSeat,
            Seat secondSeat,
            int marginalProductPairs,
            int supportedPairs,
            double unsupportedIndependentMass,
            double mutualInformationBits,
            double totalVariationFromIndependent,
            List<ComboPair> jointProbabilities) {
        public SeatPair {
            jointProbabilities = List.copyOf(jointProbabilities);
        }
    }

    public record Report(
            String interpretation, String chanceModel, int jointDeals, List<SeatPair> pairs) {
        public Report {
            pairs = List.copyOf(pairs);
        }
    }

    private SixMaxPrivateRangeCorrelationAudit() {}

    public static Report assess(SixMaxPreflopCheckdownGame game) {
        var roots = game.chanceOutcomes(game.initialState());
        var pairs = new ArrayList<SeatPair>();
        for (Seat first : Seat.values()) {
            for (Seat second : Seat.values()) {
                if (first.ordinal() >= second.ordinal()) continue;
                var firstMass = new LinkedHashMap<String, Double>();
                var secondMass = new LinkedHashMap<String, Double>();
                var joint = new LinkedHashMap<List<String>, Double>();
                for (var root : roots) {
                    var hands = game.dealtHands(root.state());
                    String a = hands.get(first.ordinal()).key();
                    String b = hands.get(second.ordinal()).key();
                    firstMass.merge(a, root.probability(), Double::sum);
                    secondMass.merge(b, root.probability(), Double::sum);
                    joint.merge(List.of(a, b), root.probability(), Double::sum);
                }
                var entries = new ArrayList<ComboPair>();
                double mutualInformation = 0, variation = 0, unsupported = 0;
                // Sort keys so diagnostics do not depend on private-root enumeration order.
                for (String a : firstMass.keySet().stream().sorted().toList()) {
                    for (String b : secondMass.keySet().stream().sorted().toList()) {
                        double probability = joint.getOrDefault(List.of(a, b), 0.0);
                        double independent = firstMass.get(a) * secondMass.get(b);
                        entries.add(new ComboPair(a, b, probability));
                        variation += Math.abs(probability - independent);
                        if (probability == 0) unsupported += independent;
                        else {
                            // Logs avoid underflow in the product of two small marginals.
                            mutualInformation +=
                                    probability
                                            * (Math.log(probability)
                                                    - Math.log(firstMass.get(a))
                                                    - Math.log(secondMass.get(b)))
                                            / Math.log(2);
                        }
                    }
                }
                pairs.add(
                        new SeatPair(
                                first,
                                second,
                                entries.size(),
                                joint.size(),
                                unsupported,
                                Math.max(0, mutualInformation),
                                variation / 2,
                                entries));
            }
        }
        return new Report(
                "Offline physical joint prior only; never expose concealed joint hands to a trainer. Zero-probability pairs are absent from this joint support; only an exact range-product game establishes physical impossibility. Pairwise independence does not establish full joint independence. Metrics compare against the product of the physical marginals, not the original input range weights or a reached policy.",
                game.chanceModel().name(),
                roots.size(),
                pairs);
    }
}
