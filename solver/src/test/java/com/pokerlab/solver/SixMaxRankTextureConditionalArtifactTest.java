package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Saved conditional evidence is recomputed, including independently evaluated parent witnesses. */
class SixMaxRankTextureConditionalArtifactTest {
    private static final String PREFIX = "../docs/data/sixmax-staged-rank-texture";
    private static SixMaxPreflopSolutionPack source;
    private static SixMaxRankTexturePayoffTable.Artifact table;

    @BeforeAll
    static void load() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        table = SixMaxRankTexturePayoffTable.read(Path.of(PREFIX + "-payoffs.json.gz"), source);
    }

    private record Case(
            SixMaxRankTextureConditionalAudit.HistoryAudit history,
            SixMaxRankTextureConditionalAudit.SignalAudit signal) {}

    @ParameterizedTest
    @CsvSource({
        "100,2998,2.6234998999849584,0.005327399895458462",
        "500,2677,2.62184060906943,0.00022738979429135858"
    })
    void everyReachedCaseAndParentWitnessReplaysAndWorstFiveMatchPureEnumeration(
            int iterations, int aboveThreshold, double largest, double weighted) throws Exception {
        var cp =
                SixMaxRankTextureStudy.read(
                        Path.of(PREFIX + "-policy-" + iterations + ".json.gz"), source, table);
        var report =
                SixMaxRankTextureConditionalAudit.replay(
                        Path.of(PREFIX + "-conditional-" + iterations + ".json.gz"),
                        source,
                        table,
                        cp);
        assertEquals(SixMaxRankTextureConditionalAudit.SCHEMA, report.schemaVersion());
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        assertEquals(SixMaxRankTextureConditionalAudit.SCOPE, report.qualityScope());
        assertEquals(cp.solutionHash(), report.solutionHash());
        assertEquals(cp.gameHash(), report.gameHash());
        assertEquals(cp.completeTreeStates(), report.completeTreeStates());
        assertEquals(cp.algorithm(), report.algorithm());
        assertEquals(iterations, report.iterations());
        assertEquals(7092, report.summary().auditedSignals());
        assertEquals(0, report.summary().zeroReachHistories());
        assertEquals(0, report.summary().signalsWithoutReachedPrivateSupport());
        assertEquals(aboveThreshold, report.summary().signalsAboveGapThreshold());
        assertEquals(largest, report.summary().largestConditionalGapBb(), 1e-10);
        assertEquals(weighted, report.parentWitness().reachWeightedLocalNashConvBb(), 1e-12);
        var study =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Path.of(PREFIX + "-study-" + iterations + ".json").toFile(),
                                SixMaxRankTextureStudy.Report.class);
        assertEquals(
                study.jointlySolvedInRankTextureGame(), report.parentWitness().parentQuality());
        double[] recomputed = new double[6];
        for (var history : report.histories()) {
            assertEquals("AUDITED", history.status());
            assertTrue(history.historyProbability() > 0);
            assertEquals(1182, history.signals().size());
            assertEquals(1, history.signalProbabilitiesSum(), 1e-12);
            for (var signal : history.signals()) {
                assertEquals("AUDITED", signal.status());
                assertEquals(
                        0,
                        signal.quality().profileUtilitiesBb().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-10);
                assertEquals(
                        1,
                        signal.firstMarginal().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-12);
                assertEquals(
                        1,
                        signal.secondMarginal().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-12);
                for (int player = 0; player < 6; player++)
                    recomputed[player] +=
                            history.historyProbability()
                                    * signal.signalProbabilityGivenHistory()
                                    * signal.quality().deviationGainsBb().get(player);
            }
        }
        for (int player = 0; player < 6; player++) {
            assertEquals(
                    recomputed[player],
                    report.parentWitness().reachWeightedLocalGainsBb().get(player),
                    1e-12);
            assertEquals(
                    recomputed[player],
                    report.parentWitness().embeddedPostflopResponseGainsBb().get(player),
                    1e-9);
            assertTrue(
                    report.parentWitness().embeddedPostflopResponseGainsBb().get(player)
                            <= report.parentWitness().parentQuality().deviationGainsBb().get(player)
                                    + 1e-9);
            assertTrue(Math.abs(report.parentWitness().embeddingErrorsBb().get(player)) < 1e-9);
        }
        var worst =
                report.histories().stream()
                        .flatMap(h -> h.signals().stream().map(s -> new Case(h, s)))
                        .sorted(
                                Comparator.comparingDouble(
                                                (Case c) -> c.signal().quality().nashConvBb())
                                        .reversed())
                        .limit(5)
                        .toList();
        assertTrue(
                worst.stream()
                        .anyMatch(
                                c ->
                                        c.signal().firstCombosAtFivePercent() >= 2
                                                && c.signal().secondCombosAtFivePercent() >= 2));
        var game = SixMaxRankTextureStudy.rebuild(source, table, cp);
        for (var candidate : worst)
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    game, cp.solution(), candidate.history(), candidate.signal());
        // The original complete learned policy is unchanged by audits and pure-response witnesses.
        assertEquals(cp.solutionHash(), SixMaxConnectedPostflopAudit.solutionHash(cp.solution()));
    }
}
