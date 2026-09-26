package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiwayResearchConfigurationTest {
    @TempDir Path temporary;
    private final MultiwayResearchConfiguration configuration = new MultiwayResearchConfiguration();

    @Test
    void requiresExplicitPathAndExactPayoffs() throws Exception {
        assertThrows(IllegalStateException.class, () -> configuration.multiwayResearchService(""));
        var sampled =
                MultiwayPackBuilder.build(
                        SixSeatValidationSpot.create(),
                        10,
                        CfrSolver.Variant.CFR_PLUS,
                        new SeededMultiwayShowdownOracle(2, 42),
                        "SEEDED_MONTE_CARLO",
                        42,
                        "2026-09-24T00:00:00Z");
        Path file = temporary.resolve("sampled.json");
        Files.writeString(file, MultiwayPackJson.write(sampled));
        var exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> configuration.multiwayResearchService(file.toString()));
        assertTrue(exception.getMessage().contains("requires exact payoffs"));
    }

    @Test
    void rejectsUnconvergedStrategyEvenWithExactPayoffs() throws Exception {
        var saved =
                MultiwayPackJson.read(Files.readString(MultiwayResearchControllerTest.fixture()));
        var game = saved.rebuildGame();
        var early = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1);
        var weak =
                new MultiwaySolutionPack(
                        saved.schemaVersion(),
                        saved.solverVersion(),
                        saved.publicationStatus(),
                        saved.generatedAt(),
                        saved.spot(),
                        saved.spotHash(),
                        saved.payoffMethod(),
                        saved.payoffSeed(),
                        early,
                        saved.payoffs(),
                        MultiwayCallBestResponse.assess(game, early).nashConvBb(),
                        saved.maxTerminalPayoffSEBb());
        assertTrue(weak.nashConvBb() > 0.05);
        weak.validate();
        var exception =
                assertThrows(IllegalStateException.class, () -> new MultiwayResearchService(weak));
        assertTrue(exception.getMessage().contains("deviation threshold"));
    }

    @Test
    void packIdentityChangesWhenSameSpotIsSolvedAgain() throws Exception {
        var saved =
                MultiwayPackJson.read(Files.readString(MultiwayResearchControllerTest.fixture()));
        var game = saved.rebuildGame();
        var revised = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(100);
        var updated =
                new MultiwaySolutionPack(
                        saved.schemaVersion(),
                        saved.solverVersion(),
                        saved.publicationStatus(),
                        saved.generatedAt(),
                        saved.spot(),
                        saved.spotHash(),
                        saved.payoffMethod(),
                        saved.payoffSeed(),
                        revised,
                        saved.payoffs(),
                        MultiwayCallBestResponse.assess(game, revised).nashConvBb(),
                        saved.maxTerminalPayoffSEBb());
        assertEquals(saved.spotHash(), updated.spotHash());
        var originalService = new MultiwayResearchService(saved);
        var updatedService = new MultiwayResearchService(updated);
        assertNotEquals(
                originalService.metadata().packHash(), updatedService.metadata().packHash());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        updatedService.grade(
                                42,
                                0,
                                0,
                                originalService.metadata().packHash(),
                                MultiwayCallTrainer.Action.CALL));
    }
}
