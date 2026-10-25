package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class UnpairedFlopSourceIntegrationTest {
    static final Path DATA = Path.of("../docs/data");
    static final Path REQUEST = DATA.resolve("supplied-unpaired-shared-flop-request.json");
    static final Path REPORT = DATA.resolve("supplied-unpaired-shared-flop-report.json.gz");
    static final Path REVIEW = DATA.resolve("supplied-unpaired-shared-flop-review.json");
    static final String PIN = "c5845cabc3a93208520549d5893ea311ad3c5409f792b96729b0d325fab077c9";
    @TempDir static Path directory;
    static SixMaxSharedFlopFeedback.Result generated;
    static SixMaxSharedFlopTrainer cached;
    static SixMaxRefinedFlopCoverageStudy.Report discovery;
    static SixMaxRefinedParentStudy.Report parent;
    static Path generatedReport, generatedReview;

    @BeforeAll
    static void completeDeclaredPhysicalGenerationAndPinnedReplay() throws Exception {
        // Two fresh qualified startups, plus original-parent and eight-case discovery replays.
        // Four game builds, at most 49 parent solves, four fresh runs / 480k iterations.
        generatedReport = directory.resolve("source.json.gz");
        generatedReview = directory.resolve("review.json");
        var choices = new ArrayList<SixMaxSharedFlopTrainer.Choice>();
        for (int i = 0; i < 4; i++)
            for (String action : List.of("fold", "call"))
                choices.add(new SixMaxSharedFlopTrainer.Choice(i, action));
        generated =
                SixMaxSharedFlopFeedbackMain.solveReview(
                        REQUEST, generatedReport, generatedReview, choices);
        cached = SixMaxSharedFlopTrainerLoader.load(REQUEST, REPORT, PIN);
        parent =
                SixMaxRefinedParentStudy.replay(
                                DATA.resolve("supplied-unpaired-flop-parent-request.json"),
                                DATA.resolve("supplied-unpaired-flop-parent-report.json.gz"))
                        .report();
        discovery =
                SixMaxRefinedFlopCoverageStudy.replay(
                                DATA.resolve("supplied-unpaired-flop-coverage-request.json"),
                                DATA.resolve("supplied-unpaired-flop-coverage-report.json.gz"))
                        .report();
    }

    @Test
    void oneOwnedGenerationProducesTheExactSourceAndReview() throws Exception {
        assertEquals(SixMaxSharedFlopFeedback.readReport(REPORT), generated.report());
        assertEquals(generated.report(), SixMaxSharedFlopFeedback.readReport(generatedReport));
        assertEquals(PIN, generated.qualifiedBatch().orElseThrow().reportHash());
        assertArrayEquals(Files.readAllBytes(REVIEW), Files.readAllBytes(generatedReview));
        assertEquals(SixMaxSharedFlopTrainer.read(REVIEW), cached.replay(REVIEW));
    }

    @Test
    void allFourHandsHaveBoardSpecificCachedChoicesAndBounds() {
        assertEquals(4, cached.questions().size());
        var hands = List.of("2c 3c", "Ah Kh", "As Qs", "Qh Th");
        var best = List.of("fold", "call", "call", "call");
        for (int i = 0; i < 4; i++) {
            var q = cached.question(i);
            assertEquals(i, q.index());
            assertEquals(4, q.totalQuestions());
            assertEquals(PIN, q.decision().reportHash());
            assertEquals(hands.get(i), q.decision().heroCards());
            assertEquals(List.of("6d", "Qc", "Td"), q.decision().context().board());
            assertEquals(List.of("fold", "call"), q.decision().context().legalActions());
            assertEquals(9.75, q.decision().context().potBb());
            assertEquals(3.25, q.decision().context().toCallBb());
            assertEquals("BTN", q.decision().hero());
            for (String action : List.of("fold", "call")) {
                var f = cached.grade(q, action);
                assertEquals(action, f.selected().action());
                assertEquals(
                        action.equals(best.get(i))
                                ? SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT
                                : SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                        f.selected().classification());
                assertEquals(2, f.alternatives().size());
            }
        }
    }

    @Test
    void twoFreshReferencesAreAssessedAtEveryQuestionWithoutRelaxedGates() {
        var r = generated.report();
        assertFalse(r.trainerAdmission());
        assertTrue(r.qualifiedForOfflineDrill());
        assertTrue(r.rejectionReasons().isEmpty());
        assertEquals(
                List.of(80000, 160000),
                r.references().stream().map(x -> x.policy().iterations()).toList());
        assertEquals(2, r.reservation().freshReferenceRuns());
        assertEquals(240000, r.reservation().totalReferenceIterations());
        assertEquals(12, r.reservation().selectedPolicyAssessments());
        for (var item : r.cases()) {
            assertEquals(parent, item.conditional().parent());
            assertTrue(item.primary().failures().isEmpty());
            assertEquals(2, item.references().size());
            assertTrue(
                    item.references().stream()
                            .allMatch(
                                    c ->
                                            c.failures().isEmpty()
                                                    && c.insideDeclaredFace()
                                                    && c.boundsChecked()));
        }
    }

    @Test
    void rejectedAfterCheckCasesRemainCompleteNegativeEvidence() {
        assertEquals(new SixMaxRefinedFlopCoverageStudy.Counts(8, 4, 4, 4, 0), discovery.counts());
        assertEquals(0, discovery.reservation().freshReferenceRuns());
        assertEquals(List.of("6d", "Qc", "Td"), parent.binding().flop());
        assertTrue(parent.solve().behavioralQuality().nashConvBb() <= 1e-8);
        for (int i = 0; i < 8; i += 2) {
            var item = discovery.cases().get(i);
            assertEquals("INSUFFICIENT_REACH", item.rejection().reason());
            assertNull(item.conditional());
            assertTrue(item.moves().isEmpty());
            assertEquals(0., item.rejection().reachFailure().minimumReachWitness().questionReach());
            assertEquals(-1e-8, item.rejection().reachFailure().certifiedReachLowerBound());
        }
    }

    @Test
    void repeatedAttemptsDoNotManufactureQuestionCoverage() {
        var choices = Collections.nCopies(100, new SixMaxSharedFlopTrainer.Choice(1, "call"));
        var r = cached.review(choices);
        assertEquals(100, r.attempts().size());
        assertEquals(new SixMaxSharedFlopTrainer.Counts(100, 0, 0, 1, 4), r.counts());
        assertEquals(r, cached.replay(r));
        assertEquals(
                new SixMaxSharedFlopTrainer.Counts(0, 0, 0, 0, 4),
                cached.review(List.of()).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> cached.review(Collections.nCopies(101, choices.getFirst())));
    }

    @Test
    void stalePinsForgedQuestionsAndChangedReviewsCannotGrade() throws Exception {
        var q = cached.question(0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        cached.grade(
                                new SixMaxSharedFlopTrainer.Question(0, 3, q.decision()), "fold"));
        assertThrows(IllegalArgumentException.class, () -> cached.grade(q, "check"));
        assertThrows(IllegalArgumentException.class, () -> cached.question(4));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSharedFlopTrainerLoader.load(REQUEST, REPORT, "0".repeat(64)));
        var old =
                SixMaxSharedFlopTrainer.read(DATA.resolve("supplied-shared-call-flop-review.json"));
        assertThrows(
                IllegalArgumentException.class,
                () -> cached.grade(old.attempts().getFirst().question(), "call"));
        var r = cached.replay(REVIEW);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        cached.replay(
                                new SixMaxSharedFlopTrainer.Review(
                                        r.schemaVersion(),
                                        r.reportHash(),
                                        r.attempts(),
                                        new SixMaxSharedFlopTrainer.Counts(8, 0, 0, 4, 4))));
    }
}
