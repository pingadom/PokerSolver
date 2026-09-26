package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlopTurnRiverCommittedPackTest {
    @Test
    void committedFullDeckPackPassesIndependentValidation() throws IOException {
        byte[] compressed;
        try (var resource = getClass().getResourceAsStream("/flop-full-validation-pack.json.gz")) {
            assertNotNull(resource);
            compressed = resource.readAllBytes();
        }
        FlopTurnRiverSolutionPack pack = FlopTurnRiverPackJson.gunzip(compressed);
        assertEquals("VALIDATION_ONLY", pack.publicationStatus());
        assertEquals(1_200, pack.iterations());
        assertEquals(156_224, pack.solution().strategy().size());
        assertEquals(0.007446216, pack.gameGapBb(), 1e-8);
        assertEquals(pack.spot().withFullTurnDeck().contentHash(), pack.spotHash());
        assertEquals(
                "5f66691ff4f3b1d61671249ad9986041a2a044a396f19f0dccf91ad9a235753b",
                FlopTurnRiverPackJson.contentHash(pack));
        FlopTurnRiverHandSession session = new FlopTurnRiverHandSession(pack);
        FlopTurnRiverHandSession.Snapshot initial =
                session.replay(42, session.packHash(), 0, List.of());
        assertEquals("FLOP", initial.street());
        assertNull(initial.opponentCombo());
        FlopTurnRiverHandSession.Snapshot afterCheck =
                session.replay(42, session.packHash(), 0, List.of("k"));
        assertEquals(1, afterCheck.feedback().size());
        assertTrue(Double.isFinite(afterCheck.feedback().getFirst().selectedEvBb()));
    }
}
