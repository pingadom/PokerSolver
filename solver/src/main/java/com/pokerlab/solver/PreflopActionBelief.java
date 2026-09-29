package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explicit likelihood model for the two observed preflop actions in the physical BTN–BB game. These
 * externally specified probabilities are research assumptions, not learned CFR policies.
 */
public record PreflopActionBelief(
        Map<String, Double> buttonOpenProbability, Map<String, Double> bigBlindCallProbability) {
    public enum ObservedAction {
        BUTTON_OPEN,
        BIG_BLIND_CALL
    }

    public PreflopActionBelief {
        buttonOpenProbability = validated(buttonOpenProbability);
        bigBlindCallProbability = validated(bigBlindCallProbability);
    }

    private static Map<String, Double> validated(Map<String, Double> probabilities) {
        if (probabilities == null || probabilities.isEmpty())
            throw new IllegalArgumentException("Action likelihoods must be nonempty");
        for (var entry : probabilities.entrySet())
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || !Double.isFinite(entry.getValue())
                    || entry.getValue() <= 0
                    || entry.getValue() > 1)
                throw new IllegalArgumentException("Action likelihood must be in (0,1]");
        return Map.copyOf(probabilities);
    }

    void validateRanges(List<WeightedCombo> buttonRange, List<WeightedCombo> bigBlindRange) {
        if (!keys(buttonRange).equals(buttonOpenProbability.keySet())
                || !keys(bigBlindRange).equals(bigBlindCallProbability.keySet()))
            throw new IllegalArgumentException("Action likelihoods must cover exact range combos");
    }

    private static Set<String> keys(List<WeightedCombo> range) {
        return range.stream().map(WeightedCombo::key).collect(Collectors.toSet());
    }

    public double likelihood(ObservedAction action, WeightedCombo combo) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(combo, "combo");
        Double value =
                (action == ObservedAction.BUTTON_OPEN
                                ? buttonOpenProbability
                                : bigBlindCallProbability)
                        .get(combo.key());
        if (value == null) throw new IllegalArgumentException("Unknown opponent combo");
        return value;
    }

    /** Prior times likelihood; river board and own-card removal happen in the equity bucket. */
    public List<WeightedCombo> posteriorWeights(
            List<WeightedCombo> opponentPrior, ObservedAction action) {
        List<WeightedCombo> posterior = new ArrayList<>(opponentPrior.size());
        for (WeightedCombo combo : opponentPrior)
            posterior.add(
                    new WeightedCombo(
                            combo.first(),
                            combo.second(),
                            combo.weight() * likelihood(action, combo)));
        return List.copyOf(posterior);
    }

    String contentDefinition() {
        return "button-open:"
                + canonical(buttonOpenProbability)
                + "|big-blind-call:"
                + canonical(bigBlindCallProbability);
    }

    private static String canonical(Map<String, Double> values) {
        return values.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + ":" + Double.toHexString(entry.getValue()))
                .collect(Collectors.joining("|"));
    }
}
