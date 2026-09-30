package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * BTN river call probability from a connected CFR average strategy. Missing information sets use an
 * explicit fallback and are counted; sparse coverage is never represented as a solved action.
 */
public final class ConnectedRiverCallPolicy implements RiverCallPolicy {
    public record Coverage(
            long queries,
            long learnedQueries,
            int queriedInformationSets,
            int learnedInformationSets) {
        public double querySupportRate() {
            return queries == 0 ? 0 : (double) learnedQueries / queries;
        }

        public double informationSetSupportRate() {
            return queriedInformationSets == 0
                    ? 0
                    : (double) learnedInformationSets / queriedInformationSets;
        }
    }

    private final ButtonBigBlindPhysicalDeckGame game;
    private final CfrSolution solution;
    private final RiverCallPolicy fallback;
    private final List<WeightedCombo> bigBlindRange;
    private final String definition;
    private final Set<String> queriedKeys = new HashSet<>();
    private final Set<String> learnedKeys = new HashSet<>();
    private long queries;
    private long learnedQueries;

    public ConnectedRiverCallPolicy(
            ButtonBigBlindPhysicalDeckGame game, CfrSolution solution, RiverCallPolicy fallback) {
        this.game = Objects.requireNonNull(game, "game");
        this.solution = Objects.requireNonNull(solution, "solution");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.bigBlindRange =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(deal -> deal.state().bigBlind())
                        .distinct()
                        .toList();
        StringBuilder strategy = new StringBuilder();
        solution.strategy().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            strategy.append(entry.getKey()).append(':');
                            entry.getValue().entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .forEach(
                                            action ->
                                                    strategy.append(action.getKey())
                                                            .append('=')
                                                            .append(
                                                                    Double.toHexString(
                                                                            action.getValue()))
                                                            .append(','));
                            strategy.append('|');
                        });
        this.definition =
                "connected-river-call/v1|"
                        + game.contentHash()
                        + '|'
                        + solution.iterations()
                        + '|'
                        + MultiwayCallSpot.sha256(strategy.toString())
                        + '|'
                        + fallback.definition();
    }

    @Override
    public String definition() {
        return definition;
    }

    public Coverage coverage() {
        return new Coverage(queries, learnedQueries, queriedKeys.size(), learnedKeys.size());
    }

    @Override
    public double callProbability(
            WeightedCombo button, List<Card> board, PublicRiverHistory publicRiverState) {
        OptionalDouble learned = learnedCallProbability(button, board, publicRiverState);
        return learned.isPresent()
                ? learned.getAsDouble()
                : fallback.callProbability(button, board, publicRiverState);
    }

    /** Returns no value for an unvisited BTN information set; never applies the fallback. */
    public OptionalDouble learnedCallProbability(
            WeightedCombo button, List<Card> board, PublicRiverHistory publicRiverState) {
        if (button == null
                || board == null
                || publicRiverState == null
                || !board.equals(publicRiverState.board())
                || !publicRiverState.riverHistory().isEmpty())
            throw new IllegalArgumentException("Expected a BTN combo at the first BB river bet");
        WeightedCombo placeholder =
                bigBlindRange.stream()
                        .filter(
                                combo ->
                                        !combo.conflictsWith(button)
                                                && !board.contains(combo.first())
                                                && !board.contains(combo.second()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No legal BB combo on public river"));
        var facingBet =
                new ButtonBigBlindPhysicalDeckGame.State(
                        placeholder,
                        button,
                        publicRiverState.preflopHistory(),
                        publicRiverState.flop(),
                        publicRiverState.flopHistory(),
                        publicRiverState.turn(),
                        publicRiverState.turnHistory(),
                        publicRiverState.river(),
                        "b");
        String key = game.informationSet(facingBet);
        Map<String, Double> actions = solution.at(1, key);
        queries++;
        queriedKeys.add(key);
        if (actions == null) return OptionalDouble.empty();
        if (actions.size() != 2
                || actions.get("c") == null
                || actions.get("f") == null
                || !Double.isFinite(actions.get("c"))
                || !Double.isFinite(actions.get("f"))
                || actions.get("c") < 0
                || actions.get("f") < 0
                || Math.abs(actions.get("c") + actions.get("f") - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid learned BTN river call policy: " + key);
        learnedQueries++;
        learnedKeys.add(key);
        return OptionalDouble.of(actions.get("c"));
    }
}
