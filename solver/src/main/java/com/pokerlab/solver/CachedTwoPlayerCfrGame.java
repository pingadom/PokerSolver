package com.pokerlab.solver;

import java.util.*;

/**
 * Private traversal cache for bounded, checked two-decision-maker reference games. Construction
 * preserves the snapshot's action/chance order and utility bits; it does not solve or qualify a
 * strategy. Each CFR run still owns fresh regrets and chooses its existing pruning mode.
 */
final class CachedTwoPlayerCfrGame implements MultiPlayerCfrGame<CachedTwoPlayerCfrGame.Node> {
    static final class Node {
        private final Object owner;
        private final int actor;
        private final String key;
        private final List<String> actions;
        private final List<Node> children;
        private final List<ChanceOutcome<Node>> chance;
        private final double[] utilities;

        private Node(Object owner, FiniteTwoPlayerSequenceForm.Node source) {
            this.owner = owner;
            actor = source.actor();
            key = source.key().substring(source.key().indexOf(':') + 1);
            actions = List.copyOf(source.actions());
            children = source.children().stream().map(child -> new Node(owner, child)).toList();
            var outcomes = new ArrayList<ChanceOutcome<Node>>();
            if (actor == -1)
                for (int i = 0; i < children.size(); i++)
                    outcomes.add(
                            new ChanceOutcome<>(children.get(i), source.probabilities().get(i)));
            chance = List.copyOf(outcomes);
            utilities = source.utilities().stream().mapToDouble(Double::doubleValue).toArray();
        }
    }

    private final Object owner = new Object();
    private final Node root;
    private final int players;
    private final int nodes;
    private final Map<Integer, Double> constants;

    <S> CachedTwoPlayerCfrGame(MultiPlayerCfrGame<S> game) {
        // Always validate the input; never trust supplied inactive constants or validation counts.
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        players = checked.snapshot().playerCount();
        nodes = checked.treeNodes();
        var fixed = new TreeMap<Integer, Double>();
        for (int player = 0; player < players; player++) {
            if (player == checked.firstActor() || player == checked.secondActor()) continue;
            fixed.put(
                    player,
                    SixMaxOwnedFlopReferences.validate(checked.snapshot().initialState(), player));
        }
        constants = Collections.unmodifiableMap(fixed);
        root = new Node(owner, checked.snapshot().initialState());
    }

    int validatedTreeNodes() {
        return nodes;
    }

    Map<Integer, Double> inactiveUtilities() {
        return constants;
    }

    private void own(Node state) {
        if (state == null || state.owner != owner)
            throw new IllegalArgumentException("Foreign cached state");
    }

    public int playerCount() {
        return players;
    }

    public Node initialState() {
        return root;
    }

    public boolean isTerminal(Node state) {
        own(state);
        return state.actor == -2;
    }

    public double[] terminalUtilities(Node state) {
        own(state);
        if (state.actor != -2) throw new IllegalArgumentException("Terminal state required");
        return state.utilities.clone();
    }

    public int currentPlayer(Node state) {
        own(state);
        return state.actor;
    }

    public List<String> legalActions(Node state) {
        own(state);
        if (state.actor < 0) throw new IllegalArgumentException("Decision state required");
        return state.actions;
    }

    public String informationSet(Node state) {
        legalActions(state);
        return state.key;
    }

    public Node afterAction(Node state, String action) {
        legalActions(state);
        if (action == null) throw new IllegalArgumentException("Action required");
        int index = state.actions.indexOf(action);
        if (index < 0) throw new IllegalArgumentException("Illegal cached action");
        return state.children.get(index);
    }

    public List<ChanceOutcome<Node>> chanceOutcomes(Node state) {
        own(state);
        if (state.actor != -1) throw new IllegalArgumentException("Chance state required");
        return state.chance;
    }

    public OptionalDouble inactivePlayerUtility(Node state, int player) {
        own(state);
        if (player < 0 || player >= players) throw new IllegalArgumentException("Invalid player");
        var fixed = constants.get(player);
        return fixed == null ? OptionalDouble.empty() : OptionalDouble.of(fixed);
    }
}
