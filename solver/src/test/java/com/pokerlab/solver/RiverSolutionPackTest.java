package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RiverSolutionPackTest {
    private static final String GENERATED_AT = "2026-09-24T18:00:00Z";

    @TempDir Path temporaryDirectory;

    @Test
    void offlineBuilderProducesDeterministicValidatedPack() {
        RiverSolutionPack pack =
                RiverPackBuilder.generate(RiverValidationSpot.create(), 3_000, GENERATED_AT);
        String json = RiverPackJson.write(pack);
        RiverSolutionPack loaded = RiverPackJson.read(json);
        assertEquals(pack, loaded);
        assertEquals(json, RiverPackJson.write(loaded));
        assertEquals(RiverPackJson.contentHash(pack), RiverPackJson.contentHash(loaded));
        assertEquals(64, RiverPackJson.contentHash(pack).length());
        assertEquals(RiverSolutionPack.VALIDATION_ONLY, loaded.publicationStatus());
        assertTrue(loaded.gameGapBb() < 0.05);
    }

    @Test
    void rejectsAlteredSpotGapAndIncompleteStrategies() {
        RiverSolutionPack pack =
                RiverPackBuilder.generate(RiverValidationSpot.create(), 100, GENERATED_AT);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiverSolutionPack(
                                        pack.schemaVersion(),
                                        pack.solverVersion(),
                                        pack.publicationStatus(),
                                        pack.generatedAt(),
                                        pack.spot(),
                                        "0".repeat(64),
                                        pack.iterations(),
                                        pack.gameGapBb(),
                                        pack.solution())
                                .validate());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiverSolutionPack(
                                        pack.schemaVersion(),
                                        pack.solverVersion(),
                                        pack.publicationStatus(),
                                        pack.generatedAt(),
                                        pack.spot(),
                                        pack.spotHash(),
                                        pack.iterations(),
                                        pack.gameGapBb() + 1,
                                        pack.solution())
                                .validate());
        Map<String, Map<String, Double>> incomplete = new HashMap<>(pack.solution().strategy());
        incomplete.remove(incomplete.keySet().iterator().next());
        CfrSolution missingStrategy = new CfrSolution(pack.iterations(), incomplete);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiverSolutionPack(
                                        pack.schemaVersion(),
                                        pack.solverVersion(),
                                        pack.publicationStatus(),
                                        pack.generatedAt(),
                                        pack.spot(),
                                        pack.spotHash(),
                                        pack.iterations(),
                                        pack.gameGapBb(),
                                        missingStrategy)
                                .validate());
    }

    @Test
    void cliWritesOnceAndSavedFixtureRevalidates() throws Exception {
        Path output = temporaryDirectory.resolve("river-pack.json");
        GenerateRiverPack.main(new String[] {output.toString(), "3000", GENERATED_AT});
        RiverSolutionPack generated =
                RiverPackJson.read(Files.readString(output, StandardCharsets.UTF_8));
        assertEquals(RiverValidationSpot.create().contentHash(), generated.spotHash());
        assertThrows(
                java.nio.file.FileAlreadyExistsException.class,
                () ->
                        GenerateRiverPack.main(
                                new String[] {output.toString(), "3000", GENERATED_AT}));

        String fixture;
        try (var resource = getClass().getResourceAsStream("/river-validation-pack.json")) {
            assertNotNull(resource);
            fixture = new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        RiverSolutionPack committed = RiverPackJson.read(fixture);
        assertEquals(fixture, RiverPackJson.write(committed));
        assertEquals(committed, generated);
        assertEquals(
                "8581679721c36ded761fabf5b36780b4515b243a23a361da52815f6589039e70",
                RiverPackJson.contentHash(committed));
    }
}
