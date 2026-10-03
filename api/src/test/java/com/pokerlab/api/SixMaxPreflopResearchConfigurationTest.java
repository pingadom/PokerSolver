package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxPreflopResearchConfigurationTest {
    @TempDir Path temporary;

    @Test
    void requiresAnExplicitBoundedFileAndRejectsSampledPayoffs() throws Exception {
        var configuration = new SixMaxPreflopResearchConfiguration();
        assertThrows(
                IllegalStateException.class, () -> configuration.sixMaxPreflopResearchService(""));
        var oversized = temporary.resolve("oversized.json");
        try (var file = new java.io.RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(16L * 1024 * 1024 + 1);
        }
        assertThrows(
                IllegalStateException.class,
                () -> configuration.sixMaxPreflopResearchService(oversized.toString()));
        var original = load();
        var spot =
                new SixMaxPreflopResearchSpot(
                        "sampled-test",
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        original.spot().ranges(),
                        CashRakeRule.none(),
                        SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN);
        var sampled =
                SixMaxPreflopPackBuilder.build(
                        spot,
                        4,
                        CfrSolver.Variant.CFR_PLUS,
                        new SharedBoardMultiwayShowdownOracle(100, 711),
                        SixMaxPreflopSolutionPack.SHARED_BOARD_MONTE_CARLO,
                        711,
                        original.generatedAt());
        var file = temporary.resolve("sampled.json");
        Files.writeString(file, MultiwayPackJson.writeFullRound(sampled));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> configuration.sixMaxPreflopResearchService(file.toString()));
        assertTrue(error.getMessage().contains("exact-board payoffs"));
    }

    @Test
    void rejectsAnUnconvergedExactPolicyAndBindsIdentityToTheWholeArtifact() throws Exception {
        var original = load();
        var game = original.rebuildGame();
        var early = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1);
        var weak =
                new SixMaxPreflopSolutionPack(
                        original.schemaVersion(),
                        original.solverVersion(),
                        original.publicationStatus(),
                        original.generatedAt(),
                        original.spot(),
                        original.spotHash(),
                        original.payoffMethod(),
                        original.payoffSeed(),
                        early,
                        original.payoffs(),
                        MultiPlayerInformationSetBestResponse.assess(game, early).nashConvBb(),
                        0);
        weak.validate();
        assertTrue(weak.nashConvBb() > SixMaxPreflopDrillSession.MAX_NASH_CONV_BB);
        assertThrows(IllegalArgumentException.class, () -> new SixMaxPreflopResearchService(weak));
        var newer =
                new SixMaxPreflopSolutionPack(
                        original.schemaVersion(),
                        original.solverVersion(),
                        original.publicationStatus(),
                        "2026-10-03T18:00:00Z",
                        original.spot(),
                        original.spotHash(),
                        original.payoffMethod(),
                        original.payoffSeed(),
                        original.solution(),
                        original.payoffs(),
                        original.nashConvBb(),
                        original.maxTerminalPayoffSEBb());
        var firstService = new SixMaxPreflopResearchService(original);
        var newerService = new SixMaxPreflopResearchService(newer);
        assertEquals(original.spotHash(), newer.spotHash());
        assertNotEquals(firstService.metadata().packHash(), newerService.metadata().packHash());
        String action = newerService.question(711, 0).legalActions().getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () -> newerService.grade(711, 0, firstService.metadata().packHash(), action));
    }

    private static SixMaxPreflopSolutionPack load() throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(SixMaxPreflopResearchControllerTest.fixture()));
    }
}
