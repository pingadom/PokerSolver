package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PreflopDrillSessionTest {
    @Test
    void replaysEveryQuestionAndRecomputesCompleteReview() throws Exception {
        var pack = fixture();
        var trainer = new PreflopTrainer(pack);
        var session = new PreflopDrillSession(trainer);
        var combos = new HashSet<String>();
        for (int index = 0; index < 10; index++) {
            var question = session.question(Long.MAX_VALUE, index);
            assertEquals(question, session.question(Long.MAX_VALUE, index));
            assertEquals("VALIDATION_ONLY", question.publicationStatus());
            combos.add(question.heroCombo());
        }
        assertTrue(combos.size() > 1);
        var actions = Collections.nCopies(10, PreflopTrainer.Action.FOLD);
        var review = session.review(Long.MAX_VALUE, actions);
        assertEquals(10, review.attempts().size());
        double expectedTotal = 0;
        for (int index = 0; index < 10; index++) {
            var single = session.grade(Long.MAX_VALUE, index, actions.get(index));
            assertEquals(single, review.attempts().get(index));
            expectedTotal += single.feedback().evLossBb();
        }
        assertEquals(expectedTotal, review.totalEvLossBb());
        assertEquals(expectedTotal / 10, review.averageEvLossBb());
        assertThrows(IllegalArgumentException.class, () -> session.question(42, -1));
        assertThrows(IllegalArgumentException.class, () -> session.question(42, 10));
        assertThrows(
                IllegalArgumentException.class, () -> session.review(42, actions.subList(0, 9)));
    }

    @Test
    void packHashChangesOnResolvingIdenticalSpot() {
        var spot = DiverseValidationSpot.create();
        var first =
                PreflopPackBuilder.generate(
                        spot, 10, 100, 42, "2026-09-24T00:00:00Z", CfrSolver.Variant.CFR_PLUS);
        var second =
                PreflopPackBuilder.generate(
                        spot, 100, 100, 42, "2026-09-24T00:00:00Z", CfrSolver.Variant.CFR_PLUS);
        assertEquals(first.spotHash(), second.spotHash());
        assertNotEquals(PreflopPackJson.contentHash(first), PreflopPackJson.contentHash(second));
        assertEquals(
                PreflopPackJson.contentHash(first),
                PreflopPackJson.contentHash(PreflopPackJson.read(PreflopPackJson.write(first))));
    }

    private static PreflopSolutionPack fixture() throws Exception {
        try (var stream =
                PreflopDrillSessionTest.class.getResourceAsStream(
                        "/diverse-validation-pack.json")) {
            assertNotNull(stream);
            return PreflopPackJson.read(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
