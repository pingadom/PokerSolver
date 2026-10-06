package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Replays fixed-source all-board bounds without retraining or evaluating board equities. */
class SixMaxMaterialContinuationFeasibilityArtifactTest {
    @ParameterizedTest
    @CsvSource({
        "three-open,NOT_RULED_OUT,0.6665808038634596",
        "three-nine,INFEASIBLE_UNDER_FIXED_POLICY,0.000009561631689740277",
        "staged-three-nine,INFEASIBLE_UNDER_FIXED_POLICY,0.2003511339891511",
        "staged-button-weak-half,INFEASIBLE_UNDER_FIXED_POLICY,0.0003314839089403609",
        "staged-button-weak-double,INFEASIBLE_UNDER_FIXED_POLICY,0.000008519580189753689"
    })
    void savedBoundsBindSourcesAndReplayEveryBoard(String model, String status, double fraction)
            throws Exception {
        var source = SixMaxPreflopPayoffReuseArtifactTest.pack(model);
        var artifact =
                new ObjectMapper()
                        .readValue(
                                Path.of(
                                                "../docs/data/sixmax-"
                                                        + model
                                                        + "-material-feasibility.json")
                                        .toFile(),
                                SixMaxMaterialContinuationFeasibilityMain.Artifact.class);
        assertEquals("six-max-material-continuation-feasibility/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(MultiwayPackJson.fullRoundContentHash(source), artifact.sourcePackHash());
        assertEquals(source.spotHash(), artifact.sourceSpotHash());
        var report =
                SixMaxMaterialContinuationFeasibility.assess(
                        source.rebuildGame(), source.solution(), artifact.feasibility().settings());
        assertEquals(artifact.feasibility(), report);
        assertEquals(status, report.status());
        assertEquals(fraction, report.optimisticHeadsUpFraction(), 1e-12);
        assertEquals(12, report.rootPrivateDeals());
        assertEquals(20, report.histories().size());
        assertFalse(report.numericReachUnresolved());
        assertTrue(report.unexaminedHistoryProbability() > 0);
        assertEquals(
                SixMaxMaterialContinuationFeasibility.Settings.researchDefault(),
                report.settings());
        if (model.equals("staged-three-nine")) {
            var first = report.histories().get(2);
            assertEquals(8296, first.optimisticMaterialFlops());
            assertEquals(4, first.minimumCompatibleCounterfactualDeals());
            assertEquals(java.util.List.of("2c", "8h", "Jh"), first.exampleFlop());
            assertEquals(
                    java.util.List.of(3, 7),
                    report.histories().stream()
                            .filter(
                                    SixMaxMaterialContinuationFeasibility.History
                                            ::eligibleForUpperBound)
                            .map(SixMaxMaterialContinuationFeasibility.History::reachRank)
                            .toList());
        }
        var evidence = SixMaxPreflopPayoffReuseArtifactTest.report(model, "payoff-reuse");
        assertEquals(
                evidence.targetReach().headsUpContinuationProbability(),
                report.headsUpProbability(),
                1e-10);
        if (model.equals("three-open")) {
            // A real passing search cannot be rejected by an optimistic preflight bound.
            assertEquals(
                    "MENU_FOUND",
                    SixMaxPreflopPayoffReuseArtifactTest.report(model, "single-history-search")
                            .targetMenuSearch()
                            .status());
            assertNotEquals("INFEASIBLE_UNDER_FIXED_POLICY", report.status());
        }
    }
}
