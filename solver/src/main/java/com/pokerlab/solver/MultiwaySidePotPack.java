package com.pokerlab.solver;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Separate v1 artifact for unequal-stack research; never interpreted as an equal-stack pack. */
public record MultiwaySidePotPack(
        String schemaVersion,
        String solverVersion,
        String publicationStatus,
        String generatedAt,
        MultiwaySidePotSpot spot,
        String spotHash,
        String payoffMethod,
        long payoffSeed,
        CfrSolution solution,
        List<MultiwaySolutionPack.PayoffEntry> payoffs,
        double nashConvBb,
        double maxTerminalPayoffSEBb) {
    public static final String SCHEMA_VERSION = "multiway-side-pot-pack/v1";
    private static final double TOLERANCE = 1e-8;

    private record PayoffKey(List<String> dealtCombos, int activeMask) {}

    public MultiwaySidePotPack {
        if (spot == null
                || solution == null
                || payoffs == null
                || payoffs.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Spot, solution and payoffs are required");
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

    /** Reconstructs payoffs without board enumeration, then rechecks the strategy and metrics. */
    public MultiwayPreflopCallGame rebuildGame() {
        validateMetadata();
        Map<PayoffKey, MultiwayShowdownEstimate> estimates = new HashMap<>();
        Long sampledTrials = null;
        for (var entry : payoffs) {
            validateEstimate(entry);
            if (MultiwaySolutionPack.SEEDED_MONTE_CARLO.equals(payoffMethod)) {
                if (sampledTrials != null && sampledTrials != entry.estimate().trials())
                    throw new IllegalArgumentException("Mixed payoff trial counts");
                sampledTrials = entry.estimate().trials();
            }
            if (estimates.putIfAbsent(
                            new PayoffKey(entry.dealtCombos(), entry.activeMask()),
                            entry.estimate())
                    != null) throw new IllegalArgumentException("Duplicate payoff key");
        }
        Set<PayoffKey> used = new HashSet<>();
        MultiwayPreflopCallGame game =
                spot.game(
                        (dealt, mask) -> {
                            PayoffKey key =
                                    new PayoffKey(
                                            dealt.stream().map(WeightedCombo::key).toList(), mask);
                            MultiwayShowdownEstimate estimate = estimates.get(key);
                            if (estimate == null)
                                throw new IllegalArgumentException("Missing payoff for joint deal");
                            used.add(key);
                            return estimate;
                        });
        if (!used.equals(estimates.keySet()))
            throw new IllegalArgumentException("Extra or blocked payoff entries");
        Set<String> expectedInformationSets = new HashSet<>();
        for (var outcome : game.chanceOutcomes(game.initialState()))
            collectInformationSets(game, outcome.state(), expectedInformationSets);
        if (!expectedInformationSets.equals(solution.strategy().keySet()))
            throw new IllegalArgumentException("Solution information sets do not match game");
        double recomputedNashConv = MultiwayCallBestResponse.assess(game, solution).nashConvBb();
        if (Math.abs(recomputedNashConv - nashConvBb) > TOLERANCE
                || Math.abs(game.maximumTerminalPayoffStandardErrorBb() - maxTerminalPayoffSEBb)
                        > TOLERANCE)
            throw new IllegalArgumentException("Quality metrics do not match saved game");
        return game;
    }

    private void validateMetadata() {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || (!MultiwaySolutionPack.SOLVER_VERSION.equals(solverVersion)
                        && !MultiwaySolutionPack.CFR_PLUS_SOLVER_VERSION.equals(solverVersion))
                || !MultiwaySolutionPack.VALIDATION_ONLY.equals(publicationStatus))
            throw new IllegalArgumentException("Unsupported schema, solver or publication status");
        try {
            Instant.parse(generatedAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid generation timestamp", exception);
        }
        if (!spot.contentHash().equals(spotHash))
            throw new IllegalArgumentException("Spot hash does not match inputs");
        if ((!MultiwaySolutionPack.EXACT_ENUMERATION.equals(payoffMethod)
                        && !MultiwaySolutionPack.SEEDED_MONTE_CARLO.equals(payoffMethod))
                || (MultiwaySolutionPack.EXACT_ENUMERATION.equals(payoffMethod) && payoffSeed != 0)
                || !Double.isFinite(nashConvBb)
                || nashConvBb < 0
                || !Double.isFinite(maxTerminalPayoffSEBb)
                || maxTerminalPayoffSEBb < 0)
            throw new IllegalArgumentException("Invalid payoff method, seed or quality metadata");
    }

    private void validateEstimate(MultiwaySolutionPack.PayoffEntry entry) {
        int count = spot.seats().size();
        int mask = entry.activeMask();
        MultiwayShowdownEstimate estimate = entry.estimate();
        double[] shares = estimate.shares();
        double[] errors = estimate.standardErrors();
        boolean exact = MultiwaySolutionPack.EXACT_ENUMERATION.equals(payoffMethod);
        if (entry.dealtCombos().size() != count
                || shares.length != count
                || errors.length != count
                || (mask & 1) == 0
                || Integer.bitCount(mask) < 2
                || (mask & ~((1 << count) - 1)) != 0
                || (exact ? estimate.trials() != boardCount(count) : estimate.trials() < 2))
            throw new IllegalArgumentException("Invalid payoff seats, mask or trial count");
        double total = 0;
        for (int seat = 0; seat < count; seat++) {
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
                throw new IllegalArgumentException("Invalid payoff shares or sampling error");
            total += share;
        }
        if (Math.abs(total - 1) > 1e-9)
            throw new IllegalArgumentException("Payoff shares must sum to one");
    }

    private static long boardCount(int seats) {
        long count = 1;
        for (int index = 1; index <= 5; index++)
            count = count * (52 - 2 * seats - index + 1) / index;
        return count;
    }

    private static void collectInformationSets(
            MultiwayPreflopCallGame game, MultiwayPreflopCallGame.State state, Set<String> result) {
        if (game.isTerminal(state)) return;
        result.add(game.currentPlayer(state) + ":" + game.informationSet(state));
        for (String action : game.legalActions(state))
            collectInformationSets(game, game.afterAction(state, action), result);
    }
}
