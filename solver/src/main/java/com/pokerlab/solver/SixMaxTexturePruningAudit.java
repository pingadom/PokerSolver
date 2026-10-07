package com.pokerlab.solver;

/** Read-only comparison of complete same-game, same-budget original and pruned checkpoints. */
public final class SixMaxTexturePruningAudit {
    public static final double TOLERANCE = 1e-10;

    public record Report(
            String schemaVersion,
            String publicationStatus,
            String gameHash,
            String originalAlgorithm,
            String prunedAlgorithm,
            String originalSolutionHash,
            String prunedSolutionHash,
            int iterations,
            long completeTreeStates,
            int informationSets,
            double maximumActionFrequencyDifference,
            double uniformInformationSetMeanTotalVariation,
            double roundingTolerance,
            boolean frequenciesWithinTolerance,
            SixMaxConnectedPreflopAudit.Quality originalQuality,
            SixMaxConnectedPreflopAudit.Quality prunedQuality,
            double maximumProfileUtilityDifferenceBb,
            double maximumDeviationGainDifferenceBb) {}

    private SixMaxTexturePruningAudit() {}

    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table,
            SixMaxTextureStudy.Checkpoint original,
            SixMaxTextureStudy.Checkpoint pruned)
            throws Exception {
        var game = SixMaxTextureStudy.rebuild(source, table, original);
        SixMaxTextureStudy.rebuild(source, table, pruned);
        if (!"CFR_PLUS".equals(original.algorithm())
                || !SixMaxTextureStudy.PRUNED_ALGORITHM.equals(pruned.algorithm())
                || !original.gameHash().equals(pruned.gameHash())
                || original.solution().iterations() != pruned.solution().iterations()
                || !original.solution()
                        .strategy()
                        .keySet()
                        .equals(pruned.solution().strategy().keySet()))
            throw new IllegalArgumentException(
                    "Require original and pruned complete policies for the same game and iteration budget");
        double maximum = 0, totalVariation = 0;
        for (var row : original.solution().strategy().entrySet()) {
            var other = pruned.solution().strategy().get(row.getKey());
            if (!row.getValue().keySet().equals(other.keySet()))
                throw new IllegalArgumentException("Pruning changed legal action support");
            for (var action : row.getValue().entrySet()) {
                double difference = Math.abs(action.getValue() - other.get(action.getKey()));
                maximum = Math.max(maximum, difference);
                totalVariation += .5 * difference;
            }
        }
        var before =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, original.solution()));
        var after =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, pruned.solution()));
        double profileDifference = 0, gainDifference = 0;
        for (int player = 0; player < 6; player++) {
            profileDifference =
                    Math.max(
                            profileDifference,
                            Math.abs(
                                    before.profileUtilitiesBb().get(player)
                                            - after.profileUtilitiesBb().get(player)));
            gainDifference =
                    Math.max(
                            gainDifference,
                            Math.abs(
                                    before.deviationGainsBb().get(player)
                                            - after.deviationGainsBb().get(player)));
        }
        return new Report(
                "six-max-texture-pruning-comparison/v1",
                "VALIDATION_ONLY",
                original.gameHash(),
                original.algorithm(),
                pruned.algorithm(),
                original.solutionHash(),
                pruned.solutionHash(),
                original.solution().iterations(),
                game.completeTreeStates(),
                original.solution().strategy().size(),
                maximum,
                totalVariation / original.solution().strategy().size(),
                TOLERANCE,
                maximum <= TOLERANCE,
                before,
                after,
                profileDifference,
                gainDifference);
    }
}
