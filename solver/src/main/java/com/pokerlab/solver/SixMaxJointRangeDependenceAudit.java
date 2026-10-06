package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline full-joint dependence, including dependencies invisible to all seat-pair audits. */
public final class SixMaxJointRangeDependenceAudit {
    public record Report(
            String interpretation,
            String chanceModel,
            int supportedJointDeals,
            long marginalProductDeals,
            double jointEntropyBits,
            Map<Seat, Double> marginalEntropyBits,
            double totalCorrelationBits,
            double unsupportedIndependentMass,
            double totalVariationFromIndependent) {
        public Report {
            marginalEntropyBits = Map.copyOf(marginalEntropyBits);
        }
    }

    private SixMaxJointRangeDependenceAudit() {}

    public static Report assess(SixMaxPreflopCheckdownGame game) {
        var joint = new LinkedHashMap<List<String>, Double>();
        var marginals = new ArrayList<Map<String, Double>>();
        for (Seat ignored : Seat.values()) marginals.add(new LinkedHashMap<>());
        for (var root : game.chanceOutcomes(game.initialState())) {
            if (root.probability() == 0) continue;
            var hands = game.dealtHands(root.state()).stream().map(WeightedCombo::key).toList();
            joint.merge(hands, root.probability(), Double::sum);
            for (Seat seat : Seat.values())
                marginals
                        .get(seat.ordinal())
                        .merge(hands.get(seat.ordinal()), root.probability(), Double::sum);
        }
        var entropies = new LinkedHashMap<Seat, Double>();
        long productDeals = 1;
        for (Seat seat : Seat.values()) {
            var marginal = marginals.get(seat.ordinal());
            productDeals = Math.multiplyExact(productDeals, marginal.size());
            entropies.put(seat, entropy(marginal.values()));
        }
        double correlation = 0, supportedIndependentMass = 0, supportedVariation = 0;
        for (var world : joint.entrySet()) {
            double logIndependent = 0;
            for (Seat seat : Seat.values())
                logIndependent +=
                        Math.log(
                                marginals
                                        .get(seat.ordinal())
                                        .get(world.getKey().get(seat.ordinal())));
            double independent = Math.exp(logIndependent);
            supportedIndependentMass += independent;
            supportedVariation += Math.abs(world.getValue() - independent);
            // Compute the log ratio before multiplication; the independent product may underflow.
            correlation +=
                    world.getValue() * (Math.log(world.getValue()) - logIndependent) / Math.log(2);
        }
        // All independent mass outside the supported worlds contributes its full mass to L1.
        // This avoids enumerating a potentially huge six-seat marginal Cartesian product.
        double unsupported = Math.clamp(1 - supportedIndependentMass, 0, 1);
        return new Report(
                "Offline full physical joint prior, not an action-conditioned belief or strategy-quality score. Total correlation compares the joint prior against the product of its physical marginals, including higher-order dependence missed by seat pairs. Unsupported independent mass is absent from this support; empirical absence does not prove physical impossibility. Concealed joint hands must remain outside trainer questions.",
                game.chanceModel().name(),
                joint.size(),
                productDeals,
                entropy(joint.values()),
                entropies,
                Math.max(0, correlation),
                unsupported,
                Math.clamp((supportedVariation + unsupported) / 2, 0, 1));
    }

    private static double entropy(Iterable<Double> probabilities) {
        double result = 0;
        for (double p : probabilities) if (p > 0) result -= p * Math.log(p) / Math.log(2);
        return result;
    }
}
