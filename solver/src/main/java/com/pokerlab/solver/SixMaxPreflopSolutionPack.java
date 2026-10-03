package com.pokerlab.solver;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Saved policy and complete subset-payoff table for the full bounded six-seat preflop round. */
public record SixMaxPreflopSolutionPack(
        String schemaVersion,
        String solverVersion,
        String publicationStatus,
        String generatedAt,
        SixMaxPreflopResearchSpot spot,
        String spotHash,
        String payoffMethod,
        long payoffSeed,
        CfrSolution solution,
        List<MultiwaySolutionPack.PayoffEntry> payoffs,
        double nashConvBb,
        double maxTerminalPayoffSEBb) {
    public static final String SCHEMA_VERSION = "six-max-preflop-checkdown-pack/v1";
    public static final String SHARED_BOARD_MONTE_CARLO = "SHARED_BOARD_MONTE_CARLO";
    public static final long EXACT_BOARDS_PER_DEAL = 658_008;
    private static final double TOLERANCE = 1e-8;

    private record PayoffKey(List<String> hands, int mask) {}

    public SixMaxPreflopSolutionPack {
        if (spot == null
                || solution == null
                || payoffs == null
                || payoffs.stream().anyMatch(Objects::isNull)
                || payoffs.size() > 57 * SixMaxPreflopCheckdownGame.MAX_JOINT_DEALS)
            throw new IllegalArgumentException("Spot, solution and bounded payoffs are required");
        payoffs =
                payoffs.stream()
                        .sorted(
                                Comparator.comparing(
                                                (MultiwaySolutionPack.PayoffEntry entry) ->
                                                        String.join("|", entry.dealtCombos()))
                                        .thenComparingInt(
                                                MultiwaySolutionPack.PayoffEntry::activeMask))
                        .toList();
    }

    public void validate() {
        rebuildGame();
    }

    /** Reconstructs and audits using saved data only, without sampling boards or solving. */
    public SixMaxPreflopCheckdownGame rebuildGame() {
        validateMetadata();
        Map<PayoffKey, MultiwayShowdownEstimate> estimates = new HashMap<>();
        Long trials = null;
        for (var entry : payoffs) {
            validateEstimate(entry);
            if (trials != null && trials != entry.estimate().trials())
                throw new IllegalArgumentException("Mixed payoff trial counts");
            trials = entry.estimate().trials();
            if (estimates.putIfAbsent(
                            new PayoffKey(entry.dealtCombos(), entry.activeMask()),
                            entry.estimate())
                    != null) throw new IllegalArgumentException("Duplicate payoff key");
        }
        Set<PayoffKey> used = new HashSet<>();
        var game =
                spot.game(
                        (hands, mask) -> {
                            var key =
                                    new PayoffKey(
                                            hands.stream().map(WeightedCombo::key).toList(), mask);
                            var estimate = estimates.get(key);
                            if (estimate == null)
                                throw new IllegalArgumentException(
                                        "Missing payoff for an unblocked joint deal");
                            used.add(key);
                            return estimate;
                        });
        if (!used.equals(estimates.keySet()))
            throw new IllegalArgumentException("Payoffs contain extra or blocked deals");
        Set<String> informationSets = new HashSet<>();
        for (var outcome : game.chanceOutcomes(game.initialState()))
            collectInformationSets(game, outcome.state(), informationSets);
        if (!informationSets.equals(solution.strategy().keySet()))
            throw new IllegalArgumentException("Solution information sets do not match game");
        var report = MultiPlayerInformationSetBestResponse.assess(game, solution);
        if (Math.abs(report.nashConvBb() - nashConvBb) > TOLERANCE
                || Math.abs(game.maximumTerminalPayoffStandardErrorBb() - maxTerminalPayoffSEBb)
                        > TOLERANCE)
            throw new IllegalArgumentException("Saved quality metrics do not match the artifact");
        return game;
    }

    private void validateMetadata() {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || (!MultiwaySolutionPack.SOLVER_VERSION.equals(solverVersion)
                        && !MultiwaySolutionPack.CFR_PLUS_SOLVER_VERSION.equals(solverVersion))
                || !MultiwaySolutionPack.VALIDATION_ONLY.equals(publicationStatus))
            throw new IllegalArgumentException("Unsupported pack, solver or publication status");
        try {
            Instant.parse(generatedAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid generation timestamp", exception);
        }
        if (!spot.contentHash().equals(spotHash))
            throw new IllegalArgumentException("Spot hash does not match inputs");
        boolean exact = MultiwaySolutionPack.EXACT_ENUMERATION.equals(payoffMethod);
        if ((!exact && !SHARED_BOARD_MONTE_CARLO.equals(payoffMethod))
                || (exact && payoffSeed != 0)
                || !Double.isFinite(nashConvBb)
                || nashConvBb < 0
                || !Double.isFinite(maxTerminalPayoffSEBb)
                || maxTerminalPayoffSEBb < 0)
            throw new IllegalArgumentException("Invalid payoff or quality metadata");
    }

    private void validateEstimate(MultiwaySolutionPack.PayoffEntry entry) {
        int mask = entry.activeMask();
        var estimate = entry.estimate();
        double[] shares = estimate.shares();
        double[] errors = estimate.standardErrors();
        boolean exact = MultiwaySolutionPack.EXACT_ENUMERATION.equals(payoffMethod);
        if (entry.dealtCombos().size() != 6
                || shares.length != 6
                || errors.length != 6
                || Integer.bitCount(mask) < 2
                || (mask & ~63) != 0
                || (exact ? estimate.trials() != EXACT_BOARDS_PER_DEAL : estimate.trials() < 2))
            throw new IllegalArgumentException("Invalid payoff seats, active mask or trial count");
        double total = 0;
        for (int seat = 0; seat < 6; seat++) {
            double share = shares[seat];
            double error = errors[seat];
            if (!Double.isFinite(share)
                    || share < 0
                    || share > 1
                    || !Double.isFinite(error)
                    || error < 0
                    || ((mask & (1 << seat)) == 0 && (share != 0 || error != 0))
                    || (exact && error != 0)
                    || (!exact
                            && error > Math.sqrt(share * (1 - share) / estimate.trials()) + 1e-12))
                throw new IllegalArgumentException("Invalid payoff share or sampling error");
            total += share;
        }
        if (Math.abs(total - 1) > 1e-9)
            throw new IllegalArgumentException("Payoff shares must sum to one");
    }

    private static void collectInformationSets(
            SixMaxPreflopCheckdownGame game,
            SixMaxPreflopCheckdownGame.State state,
            Set<String> result) {
        if (game.isTerminal(state)) return;
        result.add(game.currentPlayer(state) + ":" + game.informationSet(state));
        for (String action : game.legalActions(state))
            collectInformationSets(game, game.afterAction(state, action), result);
    }
}
