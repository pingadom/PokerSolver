package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TurnRiverSolutionPackTest {
    private static final String GENERATED_AT = "2026-09-25T12:00:00Z";

    @Test
    void roundTripsAndHashesDeterministically() {
        TurnRiverSolutionPack pack =
                TurnRiverPackBuilder.generate(TurnRiverValidationSpot.create(), 100, GENERATED_AT);
        String json = TurnRiverPackJson.write(pack);
        TurnRiverSolutionPack loaded = TurnRiverPackJson.read(json);
        assertEquals(pack, loaded);
        assertEquals(json, TurnRiverPackJson.write(loaded));
        assertEquals(TurnRiverPackJson.contentHash(pack), TurnRiverPackJson.contentHash(loaded));
        assertEquals(1_112, loaded.solution().strategy().size());
        assertEquals(pack.spotHash(), loaded.spot().contentHash());
    }

    @Test
    void committedFixtureLoadsWithPinnedIdentityAndGap() throws Exception {
        try (var resource = getClass().getResourceAsStream("/turn-river-validation-pack.json")) {
            assertNotNull(resource);
            TurnRiverSolutionPack pack =
                    TurnRiverPackJson.read(
                            new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(TurnRiverValidationSpot.create(), pack.spot());
            assertEquals(3_000, pack.iterations());
            assertEquals(
                    "219b632c95ebb85be08cc3649ed2e005848e8012907be683557bc407d2b3bd6e",
                    TurnRiverPackJson.contentHash(pack));
            assertTrue(pack.gameGapBb() < 0.001);
        }
    }

    @Test
    void refusesChangedInputsIncompleteStrategiesAndFalseQuality() {
        TurnRiverSolutionPack pack =
                TurnRiverPackBuilder.generate(TurnRiverValidationSpot.create(), 100, GENERATED_AT);
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(pack, "wrong-hash", pack.gameGapBb(), pack.solution()).validate());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        copy(pack, pack.spotHash(), pack.gameGapBb() + 1, pack.solution())
                                .validate());
        Map<String, Map<String, Double>> missing = new HashMap<>(pack.solution().strategy());
        missing.remove(missing.keySet().iterator().next());
        CfrSolution incomplete = new CfrSolution(pack.iterations(), missing);
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(pack, pack.spotHash(), pack.gameGapBb(), incomplete).validate());
        Map<String, Map<String, Double>> altered = new HashMap<>(pack.solution().strategy());
        String key = altered.keySet().iterator().next();
        altered.put(key, Map.of("k", 1.0, "b", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        copy(
                                        pack,
                                        pack.spotHash(),
                                        pack.gameGapBb(),
                                        new CfrSolution(pack.iterations(), altered))
                                .validate());
    }

    private static TurnRiverSolutionPack copy(
            TurnRiverSolutionPack source, String spotHash, double gap, CfrSolution solution) {
        return new TurnRiverSolutionPack(
                source.schemaVersion(),
                source.solverVersion(),
                source.publicationStatus(),
                source.generatedAt(),
                source.spot(),
                spotHash,
                source.iterations(),
                gap,
                solution);
    }
}
