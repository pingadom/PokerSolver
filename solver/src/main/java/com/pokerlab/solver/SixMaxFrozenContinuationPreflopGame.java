package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact preflop projection with one immutable postflop policy, including off-policy deals. */
public final class SixMaxFrozenContinuationPreflopGame
        implements MultiPlayerCfrGame<SixMaxPreflopCheckdownGame.State> {
    public record TerminalValue(
            int dealIndex,
            String publicHistory,
            List<Double> utilitiesBb,
            List<Double> sourceCheckdownUtilitiesBb,
            List<Double> continuationMinusCheckdownBb) {
        public TerminalValue {
            utilitiesBb = List.copyOf(utilitiesBb);
            sourceCheckdownUtilitiesBb = List.copyOf(sourceCheckdownUtilitiesBb);
            continuationMinusCheckdownBb = List.copyOf(continuationMinusCheckdownBb);
        }
    }

    private final SixMaxPreflopCheckdownGame source;
    private final Map<SixMaxPreflopCheckdownGame.State, List<Double>> values;
    private final List<TerminalValue> terminalValues;
    private final String frozenSolutionHash;

    public SixMaxFrozenContinuationPreflopGame(
            SixMaxConnectedPreflopGame connected,
            CfrSolution frozen,
            SixMaxContinuationStudyBudget budget) {
        Objects.requireNonNull(connected, "connected");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(budget, "budget").validate(connected);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        connected, frozen, budget.maximumCompleteTreeStates());
        if (complete.addedInformationSets() != 0)
            throw new IllegalArgumentException("Frozen continuation requires a complete policy");
        source = connected.source();
        frozenSolutionHash = SixMaxConnectedPostflopAudit.solutionHash(frozen);
        var payoffs = new LinkedHashMap<SixMaxPreflopCheckdownGame.State, List<Double>>();
        var report = new ArrayList<TerminalValue>();
        // Do not condition on learned reach: every original private deal is needed when an
        // earlier player deviates. Public history also determines the pot and sunk commitments.
        for (var selection : connected.selections()) {
            for (var root : source.chanceOutcomes(source.initialState())) {
                var state = connected.replayPreflop(selection.history(), root.state().dealIndex());
                double[] utility =
                        MultiPlayerStrategyEvaluator.utilitiesFrom(connected, frozen, state);
                if (Arrays.stream(utility).anyMatch(u -> !Double.isFinite(u))
                        || Math.abs(Arrays.stream(utility).sum()) > 1e-8)
                    throw new IllegalArgumentException(
                            "Frozen utility must be finite and zero-sum");
                var immutable = Arrays.stream(utility).boxed().toList();
                if (payoffs.put(state.preflop(), immutable) != null)
                    throw new IllegalStateException("Duplicate frozen preflop terminal");
                var checkdown = source.terminalUtilities(state.preflop());
                var changes = new ArrayList<Double>();
                for (int player = 0; player < playerCount(); player++)
                    changes.add(utility[player] - checkdown[player]);
                report.add(
                        new TerminalValue(
                                state.preflop().dealIndex(),
                                state.preflop().publicHistory(),
                                immutable,
                                Arrays.stream(checkdown).boxed().toList(),
                                changes));
            }
        }
        values = Map.copyOf(payoffs);
        terminalValues = List.copyOf(report);
    }

    public String frozenSolutionHash() {
        return frozenSolutionHash;
    }

    public List<TerminalValue> terminalValues() {
        return terminalValues;
    }

    @Override
    public int playerCount() {
        return source.playerCount();
    }

    @Override
    public SixMaxPreflopCheckdownGame.State initialState() {
        return source.initialState();
    }

    @Override
    public boolean isTerminal(SixMaxPreflopCheckdownGame.State state) {
        return source.isTerminal(state);
    }

    @Override
    public double[] terminalUtilities(SixMaxPreflopCheckdownGame.State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Expected a preflop terminal");
        var utility = values.get(state);
        return utility == null
                ? source.terminalUtilities(state)
                : utility.stream().mapToDouble(Double::doubleValue).toArray();
    }

    @Override
    public int currentPlayer(SixMaxPreflopCheckdownGame.State state) {
        return source.currentPlayer(state);
    }

    @Override
    public List<String> legalActions(SixMaxPreflopCheckdownGame.State state) {
        return source.legalActions(state);
    }

    @Override
    public String informationSet(SixMaxPreflopCheckdownGame.State state) {
        return source.informationSet(state);
    }

    @Override
    public SixMaxPreflopCheckdownGame.State afterAction(
            SixMaxPreflopCheckdownGame.State state, String action) {
        return source.afterAction(state, action);
    }

    @Override
    public List<ChanceOutcome<SixMaxPreflopCheckdownGame.State>> chanceOutcomes(
            SixMaxPreflopCheckdownGame.State state) {
        return source.chanceOutcomes(state);
    }
}
