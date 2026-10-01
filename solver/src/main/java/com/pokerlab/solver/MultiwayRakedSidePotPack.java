package com.pokerlab.solver;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Validation-only raked solution with an embedded, independently validated exact showdown table. It
 * does not confer admission to a public cash-game trainer.
 */
public record MultiwayRakedSidePotPack(
        String schemaVersion,
        String solverVersion,
        String publicationStatus,
        String generatedAt,
        MultiwaySidePotPack sourcePack,
        String sourcePackHash,
        CashRakeRule rakeRule,
        CfrSolution solution,
        double nashConvBb,
        double maxTerminalPayoffSEBb,
        double expectedRakeBb) {
    public static final String SCHEMA_VERSION = "multiway-raked-side-pot-pack/v1";
    private static final double TOLERANCE = 1e-8;

    public MultiwayRakedSidePotPack {
        if (sourcePack == null || rakeRule == null || solution == null)
            throw new IllegalArgumentException("Source pack, rake rule and solution are required");
    }

    public void validate() {
        rebuildGame();
    }

    /** Rechecks all source payoffs, the rake rule, the full strategy and its reported metrics. */
    public MultiwayPreflopCallGame rebuildGame() {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || !MultiwaySolutionPack.VALIDATION_ONLY.equals(publicationStatus)
                || (!MultiwaySolutionPack.SOLVER_VERSION.equals(solverVersion)
                        && !MultiwaySolutionPack.CFR_PLUS_SOLVER_VERSION.equals(solverVersion)))
            throw new IllegalArgumentException("Unsupported raked pack metadata");
        try {
            Instant.parse(generatedAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid generation timestamp", exception);
        }
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(sourcePack.payoffMethod())
                || rakeRule.fraction() <= 0
                || rakeRule.capBb() <= 0)
            throw new IllegalArgumentException("Raked packs require exact payoffs and active rake");
        if (!MultiwayPackJson.sidePotContentHash(sourcePack).equals(sourcePackHash))
            throw new IllegalArgumentException("Source pack hash does not match payoffs");
        var game = MultiwayRakedGameFactory.fromPack(sourcePack, rakeRule);
        if (!sourcePack.solution().strategy().keySet().equals(solution.strategy().keySet()))
            throw new IllegalArgumentException("Raked strategy information sets do not match game");
        var report = MultiwayCallBestResponse.assess(game, solution);
        double rake =
                sourcePack.spot().deadMoneyBb()
                        - report.profileUtilitiesBb().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum();
        if (!Double.isFinite(nashConvBb)
                || nashConvBb < 0
                || !Double.isFinite(maxTerminalPayoffSEBb)
                || maxTerminalPayoffSEBb < 0
                || !Double.isFinite(expectedRakeBb)
                || expectedRakeBb < 0
                || expectedRakeBb > rakeRule.capBb() + TOLERANCE
                || Math.abs(report.nashConvBb() - nashConvBb) > TOLERANCE
                || Math.abs(game.maximumTerminalPayoffStandardErrorBb() - maxTerminalPayoffSEBb)
                        > TOLERANCE
                || Math.abs(rake - expectedRakeBb) > TOLERANCE)
            throw new IllegalArgumentException("Raked quality or house-share metrics do not match");
        return game;
    }
}
