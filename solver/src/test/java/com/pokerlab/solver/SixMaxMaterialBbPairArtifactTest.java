package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Declared BB-only changes retain exact physical payoffs and use fresh matched-budget policies. */
class SixMaxMaterialBbPairArtifactTest {
    @ParameterizedTest
    @CsvSource({"half,0.5,0,0.14080329949137046", "double,2.0,1,0.0738012851222563"})
    void physicalSupportWeightsAndFreshPolicyComparisonsAreBoundToDeclaredInputs(
            String label, double pairWeight, int index, double expectedPolicyTv) throws Exception {
        var baseline = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        var model = "staged-bb-pair-" + label;
        var source = SixMaxPreflopPayoffReuseArtifactTest.pack(model);
        var input =
                MultiwayPackJson.readFullRoundSpot(
                        Files.readString(Path.of("../docs/data/sixmax-" + model + "-spot.json")));
        assertEquals(input, source.spot());
        assertEquals(input.contentHash(), source.spotHash());
        assertEquals(baseline.spot().rules(), input.rules());
        assertEquals(baseline.spot().rake(), input.rake());
        assertEquals(baseline.spot().continuationModel(), input.continuationModel());
        assertEquals(500, source.solution().iterations());
        assertNotEquals(baseline.solution(), source.solution());
        for (int seat = 0; seat < 6; seat++) {
            var before = baseline.spot().ranges().get(seat);
            var after = input.ranges().get(seat);
            assertEquals(before.size(), after.size());
            for (int combo = 0; combo < before.size(); combo++) {
                assertEquals(before.get(combo).key(), after.get(combo).key());
                double expected =
                        seat == 5 && before.get(combo).key().equals("7d 7h")
                                ? pairWeight
                                : before.get(combo).weight();
                assertEquals(expected, after.get(combo).weight());
            }
        }
        assertEquals(684, source.payoffs().size());
        for (int i = 0; i < source.payoffs().size(); i++) {
            var before = baseline.payoffs().get(i);
            var after = source.payoffs().get(i);
            assertEquals(before.dealtCombos(), after.dealtCombos());
            assertEquals(before.activeMask(), after.activeMask());
            assertArrayEquals(before.estimate().shares(), after.estimate().shares());
            assertArrayEquals(
                    before.estimate().standardErrors(), after.estimate().standardErrors());
            assertEquals(before.estimate().trials(), after.estimate().trials());
        }
        var reuse = SixMaxPreflopPayoffReuseArtifactTest.report(model, "payoff-reuse");
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(baseline),
                reuse.provenance().sourcePackHash());
        assertEquals(MultiwayPackJson.fullRoundContentHash(source), reuse.targetPackHash());
        assertEquals(684, reuse.provenance().reusedPayoffEntries());
        assertNull(
                reuse.targetMenuSearch()); // The all-board preflight stopped further seed searches.
        var saved =
                new ObjectMapper()
                        .readValue(
                                Path.of("../docs/data/sixmax-staged-bb-pair-sensitivity.json")
                                        .toFile(),
                                SixMaxPreflopRangeWeightSensitivityMain.Artifact.class);
        assertEquals("VALIDATION_ONLY", saved.publicationStatus());
        assertEquals(2, saved.comparisons().size());
        var expected = saved.comparisons().get(index);
        var comparison = SixMaxPreflopRangeWeightSensitivity.assess(baseline, source);
        assertEquals(expected, comparison);
        assertEquals(1.0 / 6, comparison.jointDealTotalVariation(), 1e-12);
        assertEquals(
                expectedPolicyTv,
                comparison.policyDifference().reachWeightedTotalVariation(),
                1e-12);
        assertEquals(12, comparison.jointDeals());
        assertEquals(9161, comparison.policyDifference().informationSets());
        assertEquals(138793, comparison.policyDifference().visitedStates());
    }
}
