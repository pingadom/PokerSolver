package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import java.util.*;

/** Fresh CFR references on the immutable owned game after literal inactive-payoff validation. */
public final class SixMaxOwnedFlopReferences implements MultiPlayerCfrGame<State> {
    static final String ALGORITHM = "FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1";

    public record Audit(
            String algorithm,
            int validatedTreeNodes,
            Map<Integer, Double> inactiveUtilities,
            MultiPlayerCfrSolver.Statistics statistics,
            long prunedSuffixRoots) {
        public Audit {
            inactiveUtilities = Collections.unmodifiableMap(new TreeMap<>(inactiveUtilities));
            Objects.requireNonNull(statistics);
            if (!ALGORITHM.equals(algorithm)
                    || validatedTreeNodes < 1
                    || inactiveUtilities.size() != 4
                    || prunedSuffixRoots < 0)
                throw new IllegalArgumentException("Invalid owned reference work");
        }
    }

    record Run(CfrSolution policy, Audit audit) {}

    private final SixMaxSuppliedFlopBettingGame game;
    private final Map<Integer, Double> constants;
    private final int nodes;
    private final CachedTwoPlayerCfrGame traversal;

    SixMaxOwnedFlopReferences(SixMaxSuppliedFlopBettingGame game) {
        this.game = Objects.requireNonNull(game);
        traversal = new CachedTwoPlayerCfrGame(game);
        nodes = traversal.validatedTreeNodes();
        constants = traversal.inactiveUtilities();
    }

    /** No estimate, range assumption or tolerance may establish this pruning guarantee. */
    static double validate(FiniteTwoPlayerSequenceForm.Node node, int player) {
        if (node.actor() == player)
            throw new IllegalArgumentException("Inactive seat has a decision");
        if (node.actor() == -2) return node.utilities().get(player);
        Double fixed = null;
        for (var child : node.children()) {
            double value = validate(child, player);
            if (fixed != null && Double.doubleToLongBits(fixed) != Double.doubleToLongBits(value))
                throw new IllegalArgumentException("Inactive terminal utility varies");
            fixed = value;
        }
        if (fixed == null) throw new IllegalArgumentException("Inactive proof has no terminals");
        return fixed;
    }

    Run train(int iterations) {
        if (iterations != 80_000 && iterations != 160_000)
            throw new IllegalArgumentException("Unsupported fresh reference iterations");
        return trainBounded(iterations);
    }

    // Small deterministic comparisons exercise the identical production traversal in tests.
    Run trainBounded(int iterations) {
        if (iterations < 1 || iterations > 160_000)
            throw new IllegalArgumentException("Invalid reference iteration bound");
        var solver =
                new MultiPlayerCfrSolver<>(
                        traversal,
                        CfrSolver.Variant.CFR_PLUS,
                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
        var policy = solver.solve(iterations);
        return new Run(
                policy,
                new Audit(
                        ALGORITHM,
                        nodes,
                        constants,
                        solver.statistics(),
                        solver.inactiveUtilityPrunedNodes()));
    }

    public int playerCount() {
        return game.playerCount();
    }

    public State initialState() {
        return game.initialState();
    }

    public boolean isTerminal(State s) {
        return game.isTerminal(s);
    }

    public double[] terminalUtilities(State s) {
        return game.terminalUtilities(s);
    }

    public int currentPlayer(State s) {
        return game.currentPlayer(s);
    }

    public List<String> legalActions(State s) {
        return game.legalActions(s);
    }

    public String informationSet(State s) {
        return game.informationSet(s);
    }

    public State afterAction(State s, String a) {
        return game.afterAction(s, a);
    }

    public List<ChanceOutcome<State>> chanceOutcomes(State s) {
        return game.chanceOutcomes(s);
    }

    public OptionalDouble inactivePlayerUtility(State s, int player) {
        if (player < 0 || player >= playerCount())
            throw new IllegalArgumentException("Invalid player");
        var fixed = constants.get(player);
        if (fixed == null) return OptionalDouble.empty();
        // Pruned paths still reject foreign states; active paths use the game's normal checks.
        game.isTerminal(s);
        return OptionalDouble.of(fixed);
    }
}
