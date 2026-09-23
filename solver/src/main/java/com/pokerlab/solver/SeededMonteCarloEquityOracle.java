package com.pokerlab.solver;

import com.pokerlab.core.simulation.MonteCarloSimulation;
import com.pokerlab.core.simulation.PlayerHand;
import com.pokerlab.core.simulation.SimulationRequest;
import com.pokerlab.core.simulation.SimulationResult;
import java.util.List;
import java.util.OptionalLong;

/** Reuses PokerLab's evaluator to estimate all-in payoffs; not a strategy solver. */
public final class SeededMonteCarloEquityOracle implements PreflopEquityOracle {
    private final int trials;
    private final long baseSeed;

    public SeededMonteCarloEquityOracle(int trials, long baseSeed) {
        if (trials < 1) throw new IllegalArgumentException("trials must be positive");
        this.trials = trials;
        this.baseSeed = baseSeed;
    }

    @Override
    public EquityEstimate estimate(WeightedCombo first, WeightedCombo second) {
        if (first.conflictsWith(second))
            throw new IllegalArgumentException("Opponent combos share a card");
        long derivedSeed = 31 * (baseSeed ^ first.key().hashCode()) + second.key().hashCode();
        SimulationRequest request =
                SimulationRequest.quickWithSeed(
                        List.of(
                                new PlayerHand("first", first.first(), first.second()),
                                new PlayerHand("second", second.first(), second.second())),
                        List.of(),
                        trials,
                        OptionalLong.of(derivedSeed));
        SimulationResult result = MonteCarloSimulation.run(request);
        double equity = result.equity("first");
        double secondMoment = (result.wins("first") + 0.25 * result.ties("first")) / trials;
        double variance = Math.max(0, secondMoment - equity * equity);
        return new EquityEstimate(equity, Math.sqrt(variance / trials), trials);
    }
}
