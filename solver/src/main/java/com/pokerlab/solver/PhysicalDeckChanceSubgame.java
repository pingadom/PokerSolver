package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finite quadrature of the physical-deck game. Each non-root chance node uses declared quantiles of
 * its own legal-card distribution; repeated physical outcomes are combined. This is a separate
 * chance game, not an exact representation of the full deck.
 */
public final class PhysicalDeckChanceSubgame
        implements CfrGame<ButtonBigBlindPhysicalDeckGame.State> {
    private final ButtonBigBlindPhysicalDeckGame physical;
    private final List<Double> flopQuantiles;
    private final List<Double> turnQuantiles;
    private final List<Double> riverQuantiles;

    public PhysicalDeckChanceSubgame(
            ButtonBigBlindPhysicalDeckGame physical,
            List<Double> flopQuantiles,
            List<Double> turnQuantiles,
            List<Double> riverQuantiles) {
        this.physical = Objects.requireNonNull(physical, "physical");
        this.flopQuantiles = validated(flopQuantiles);
        this.turnQuantiles = validated(turnQuantiles);
        this.riverQuantiles = validated(riverQuantiles);
    }

    public String physicalGameHash() {
        return physical.contentHash();
    }

    public List<Double> flopQuantiles() {
        return flopQuantiles;
    }

    public List<Double> turnQuantiles() {
        return turnQuantiles;
    }

    public List<Double> riverQuantiles() {
        return riverQuantiles;
    }

    @Override
    public ButtonBigBlindPhysicalDeckGame.State initialState() {
        return physical.initialState();
    }

    @Override
    public boolean isTerminal(ButtonBigBlindPhysicalDeckGame.State state) {
        return physical.isTerminal(state);
    }

    @Override
    public double terminalUtility(ButtonBigBlindPhysicalDeckGame.State state) {
        return physical.terminalUtility(state);
    }

    @Override
    public int currentPlayer(ButtonBigBlindPhysicalDeckGame.State state) {
        return physical.currentPlayer(state);
    }

    @Override
    public List<String> legalActions(ButtonBigBlindPhysicalDeckGame.State state) {
        return physical.legalActions(state);
    }

    @Override
    public String informationSet(ButtonBigBlindPhysicalDeckGame.State state) {
        return physical.informationSet(state);
    }

    @Override
    public ButtonBigBlindPhysicalDeckGame.State afterAction(
            ButtonBigBlindPhysicalDeckGame.State state, String action) {
        return physical.afterAction(state, action);
    }

    @Override
    public List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> chanceOutcomes(
            ButtonBigBlindPhysicalDeckGame.State state) {
        if (state.bigBlind() == null) return physical.chanceOutcomes(state);
        if (physical.currentPlayer(state) != -1)
            throw new IllegalArgumentException("Not a physical chance node");
        List<Double> quantiles =
                state.flop() == null
                        ? flopQuantiles
                        : state.turn() == null ? turnQuantiles : riverQuantiles;
        Map<ButtonBigBlindPhysicalDeckGame.State, Integer> counts = new LinkedHashMap<>();
        for (double quantile : quantiles) {
            var outcome = physical.sampleChanceOutcome(state, quantile);
            counts.merge(outcome.state(), 1, Integer::sum);
        }
        List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> outcomes = new ArrayList<>();
        counts.forEach(
                (next, count) ->
                        outcomes.add(new ChanceOutcome<>(next, (double) count / quantiles.size())));
        return List.copyOf(outcomes);
    }

    private static List<Double> validated(List<Double> quantiles) {
        Objects.requireNonNull(quantiles, "quantiles");
        if (quantiles.isEmpty() || quantiles.size() > 4)
            throw new IllegalArgumentException("Expected 1-4 chance quantiles per street");
        for (Double value : quantiles)
            if (value == null || !Double.isFinite(value) || value < 0 || value >= 1)
                throw new IllegalArgumentException("Chance quantiles must be in [0, 1)");
        return List.copyOf(quantiles);
    }
}
