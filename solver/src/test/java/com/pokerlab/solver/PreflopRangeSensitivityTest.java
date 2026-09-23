package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PreflopRangeSensitivityTest {
    private static PreflopSolutionPack exactPack() throws Exception {
        try (var resource =
                PreflopRangeSensitivityTest.class.getResourceAsStream(
                        "/diverse-validation-pack.json")) {
            assertNotNull(resource);
            return PreflopPackJson.read(
                    new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void probesEachRangeWeightInBothDirections() throws Exception {
        PreflopSolutionPack pack = exactPack();
        PreflopRangeSensitivity.Report report = PreflopRangeSensitivity.analyze(pack, 0.25);
        assertEquals(pack.spotHash(), report.spotHash());
        assertEquals(pack.solverVersion(), report.solverVersion());
        assertEquals(pack.estimatedGameGapBb(), report.baselineGameGapBb());
        assertEquals(30, report.scenarioCount());
        assertEquals(8, report.heroResults().size());
        assertTrue(report.maximumPerturbedGameGapBb() < 0.05);
        for (var result : report.heroResults()) {
            assertTrue(result.minimumShoveEdgeBb() <= result.baselineShoveEdgeBb());
            assertTrue(result.maximumShoveEdgeBb() >= result.baselineShoveEdgeBb());
            assertTrue(result.minimumShoveFrequency() <= result.baselineShoveFrequency());
            assertTrue(result.maximumShoveFrequency() >= result.baselineShoveFrequency());
        }
        var byCombo =
                report.heroResults().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        PreflopRangeSensitivity.HeroResult::combo,
                                        result -> result));
        assertTrue(byCombo.get("Ac Ad").minimumShoveEdgeBb() > 80);
        assertTrue(byCombo.get("6h 7h").maximumShoveEdgeBb() < -6);
        assertTrue(byCombo.get("Ks Qs").minimumShoveEdgeBb() < -3);
        assertTrue(byCombo.get("Ks Qs").maximumShoveEdgeBb() > 2);
        assertNotEquals("BASELINE", byCombo.get("Ks Qs").minimumEdgeScenario());
        assertNotEquals("BASELINE", byCombo.get("Ks Qs").maximumEdgeScenario());
        assertTrue(byCombo.get("Ah Kh").minimumShoveFrequency() < 0.3);
        assertTrue(byCombo.get("Ah Kh").maximumShoveFrequency() > 0.9);
        assertThrows(
                IllegalArgumentException.class, () -> PreflopRangeSensitivity.analyze(pack, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> PreflopRangeSensitivity.analyze(pack, Double.NaN));
    }

    @Test
    void requiresExactPayoffTable() {
        PreflopSolutionPack sampled =
                PreflopPackBuilder.generate(
                        ValidationSpot.create(), 100, 1_000, 42, "2026-09-23T12:00:00Z");
        assertThrows(
                IllegalArgumentException.class,
                () -> PreflopRangeSensitivity.analyze(sampled, 0.25));
    }
}
