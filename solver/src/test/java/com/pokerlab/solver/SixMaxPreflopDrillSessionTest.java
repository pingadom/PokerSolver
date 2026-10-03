package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopDrillSessionTest {
    private static final class Fixture {
        static final SixMaxPreflopSolutionPack PACK = load();
        static final SixMaxPreflopDrillSession SESSION = new SixMaxPreflopDrillSession(PACK);

        private static SixMaxPreflopSolutionPack load() {
            try (var input =
                    SixMaxPreflopDrillSessionTest.class.getResourceAsStream(
                            "/six-seat-full-round-pack.json")) {
                assertNotNull(input);
                return MultiwayPackJson.readFullRound(
                        new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    @Test
    void exactSavedFullRoundFixtureHasReplayableTenDecisionReview() {
        var pack = Fixture.PACK;
        var session = Fixture.SESSION;
        assertEquals(114, pack.payoffs().size());
        assertEquals(7089, pack.solution().strategy().size());
        assertEquals(0, pack.maxTerminalPayoffSEBb());
        assertTrue(pack.nashConvBb() < SixMaxPreflopDrillSession.MAX_NASH_CONV_BB);
        long seed = Long.MAX_VALUE;
        List<String> actions = new ArrayList<>();
        double total = 0;
        for (int index = 0; index < 10; index++) {
            var question = session.question(seed, index);
            assertEquals(question, session.question(seed, index));
            assertEquals("VALIDATION_ONLY", question.decision().publicationStatus());
            String action = question.decision().legalActions().getFirst();
            actions.add(action);
            var attempt = session.grade(question, action);
            assertTrue(attempt.feedback().evLossBb() >= 0);
            assertTrue(
                    attempt.feedback().actionPayoffStandardErrorBb().values().stream()
                            .allMatch(error -> error == 0));
            total += attempt.feedback().evLossBb();
        }
        String hash = session.question(seed, 0).packHash();
        var review = session.review(seed, hash, actions);
        assertEquals(10, review.attempts().size());
        assertEquals(total, review.totalEvLossBb(), 1e-12);
        assertEquals(total / 10, review.averageEvLossBb(), 1e-12);
        assertEquals(review, session.review(seed, hash, actions));
        assertEquals(session.question(Long.MIN_VALUE, 9), session.question(Long.MIN_VALUE, 9));
    }

    @Test
    void rejectsDifferentArtifactTamperedQuestionAndIllegalOrIncompleteAnswers() {
        var session = Fixture.SESSION;
        var question = session.question(711, 0);
        var differentArtifact =
                new SixMaxPreflopDrillSession.Question(
                        question.sessionSeed(),
                        question.index(),
                        "0".repeat(64),
                        question.decision());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        session.grade(
                                differentArtifact, question.decision().legalActions().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> session.grade(question, "teleport"));
        assertThrows(IllegalArgumentException.class, () -> session.question(711, -1));
        assertThrows(IllegalArgumentException.class, () -> session.question(711, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> session.review(711, question.packHash(), List.of("fold")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        session.review(
                                711, "0".repeat(64), java.util.Collections.nCopies(10, "fold")));
        var swappedDecision =
                new SixMaxPreflopDrillSession.Question(
                        question.sessionSeed(),
                        question.index(),
                        question.packHash(),
                        session.question(711, 1).decision());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        session.grade(
                                swappedDecision, question.decision().legalActions().getFirst()));
    }
}
