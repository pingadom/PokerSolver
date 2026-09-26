package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlopTurnRiverSolutionPackTest {
    private static final String GENERATED_AT = "2026-09-26T12:00:00Z";

    @Test
    void fullDeckPackRoundTripsWithValidatedStrategyAndContentHash() {
        FlopTurnRiverSpot fixture = FlopTurnRiverValidationSpot.create().withFullTurnDeck();
        FlopTurnRiverSpot small =
                new FlopTurnRiverSpot(
                        fixture.flop(),
                        fixture.potBb(),
                        fixture.remainingStackBb(),
                        fixture.flopBetBb(),
                        fixture.turnBetBb(),
                        fixture.riverBetBb(),
                        List.of(fixture.firstRange().getFirst()),
                        List.of(fixture.secondRange().getFirst()),
                        fixture.turnCandidates());
        FlopTurnRiverSolutionPack pack =
                FlopTurnRiverPackBuilder.generate(small, 3, GENERATED_AT, 100);
        String hash = FlopTurnRiverPackJson.contentHash(pack);
        String json = FlopTurnRiverPackJson.write(pack);
        FlopTurnRiverSolutionPack restored = FlopTurnRiverPackJson.read(json);
        assertEquals(hash, FlopTurnRiverPackJson.contentHash(restored));
        assertEquals(pack.gameGapBb(), restored.gameGapBb());
        assertEquals(pack.solution().strategy().size(), restored.solution().strategy().size());
        FlopTurnRiverSolutionPack compressed =
                FlopTurnRiverPackJson.gunzip(FlopTurnRiverPackJson.gzip(pack));
        assertEquals(hash, FlopTurnRiverPackJson.contentHash(compressed));
        assertThrows(
                IllegalArgumentException.class,
                () -> FlopTurnRiverPackJson.gunzip(new byte[] {1, 2, 3}));
    }

    @Test
    void rejectsRestrictedChanceBeforeSolving() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FlopTurnRiverPackBuilder.generate(
                                FlopTurnRiverValidationSpot.create(), 3, GENERATED_AT, 100));
    }

    @Test
    void rejectsCorruptIdentityOrStoredGap() {
        FlopTurnRiverSpot fixture = FlopTurnRiverValidationSpot.create().withFullTurnDeck();
        FlopTurnRiverSpot small =
                new FlopTurnRiverSpot(
                        fixture.flop(),
                        fixture.potBb(),
                        fixture.remainingStackBb(),
                        fixture.flopBetBb(),
                        fixture.turnBetBb(),
                        fixture.riverBetBb(),
                        List.of(fixture.firstRange().getFirst()),
                        List.of(fixture.secondRange().getFirst()),
                        fixture.turnCandidates());
        FlopTurnRiverSolutionPack pack =
                FlopTurnRiverPackBuilder.generate(small, 3, GENERATED_AT, 100);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FlopTurnRiverSolutionPack(
                                        pack.schemaVersion(),
                                        pack.solverVersion(),
                                        pack.publicationStatus(),
                                        pack.generatedAt(),
                                        pack.spot(),
                                        "wrong",
                                        pack.iterations(),
                                        pack.gameGapBb(),
                                        pack.solution())
                                .validate());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FlopTurnRiverSolutionPack(
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
    }
}
