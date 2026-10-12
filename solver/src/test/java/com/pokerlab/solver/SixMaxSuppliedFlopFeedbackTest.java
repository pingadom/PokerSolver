package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedFlopFeedbackTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-four-button-hand-flop-feedback-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-four-button-hand-flop-feedback-report.json.gz");
    static final Path DRILL = Path.of("../docs/data/supplied-four-button-hand-flop-drill.json");
    static SixMaxSuppliedFlopFeedback.Request request;
    static SixMaxSuppliedFlopBettingGame game;
    static SixMaxSuppliedFlopActionBounds.Result bounds;
    static SixMaxSuppliedFlopFeedback.Result result;
    static SixMaxSuppliedFlopTrainer trainer;

    @BeforeAll
    static void build() throws Exception {
        request = SixMaxSuppliedFlopFeedback.readRequest(REQUEST);
        var c = request.connected();
        game = new SixMaxSuppliedFlopBettingGame(c.input(), c.flop(), c.betPotFraction());
        bounds = SixMaxSuppliedFlopActionBounds.solve(c, game);
        result = SixMaxSuppliedFlopFeedback.qualify(request, game, bounds);
        trainer = new SixMaxSuppliedFlopTrainer(result);
    }

    static SixMaxSuppliedFlopFeedback.Request select(int hero, String key, double limit) {
        return new SixMaxSuppliedFlopFeedback.Request(
                SixMaxSuppliedFlopFeedback.REQUEST_SCHEMA, request.connected(), hero, key, limit);
    }

    @Test
    void physicalSevensQuestionQualifiesWithOriginalFaceAndAllFreshChecks() throws Exception {
        assertEquals(SixMaxSuppliedFlopFeedback.readReport(REPORT), result.report());
        assertTrue(result.qualifiedFeedback().isPresent());
        assertFalse(result.report().trainerAdmission());
        assertEquals(bounds.report(), result.report().bounds());
        assertEquals(
                List.of("WITHIN_LIMIT", "OUTSIDE_LIMIT"),
                result.report().moves().stream().map(m -> m.classification().name()).toList());
        assertTrue(result.report().moves().getLast().lowerLossBb() > .09);
        for (var check : result.report().references()) {
            assertTrue(check.insideDeclaredFace());
            assertTrue(check.boundsChecked());
            assertEquals(0, check.maximumBoundViolationBb());
            assertTrue(check.inclusionMarginBb() > 0);
            assertEquals(1.0 / 3, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-14);
            assertTrue(check.actualQuestionRootReach() < .0001);
            assertTrue(check.actualQuestionReachGivenPhysicalFlop() > .0001);
            assertTrue(check.fullParentQuality().nashConvBb() < .001);
            assertTrue(check.reachedFlopQuality().conditionalBestResponse().nashConvBb() < .001);
            assertTrue(check.failures().isEmpty());
        }
    }

    @Test
    void contextIsOwnedAndIncludesSixSeatsBoardAndBetAmount() {
        var c = trainer.question().context();
        assertEquals("BB", c.hero().name());
        assertEquals("7d 7h", c.heroCards());
        assertEquals(List.of("2s", "8s", "Ts"), c.board());
        assertEquals(6.5, c.potBb());
        assertEquals(3.25, c.betSizeBb());
        assertEquals(0, c.toCallBb());
        assertEquals(6, c.players().size());
        assertEquals(
                List.of("FOLDED", "FOLDED", "FOLDED", "ACTIVE", "FOLDED", "ACTING"),
                c.players().stream().map(SixMaxSuppliedFlopFeedback.Player::status).toList());
        assertEquals(8, c.preflopHistory().size());
        assertEquals(List.of("check", "bet"), c.legalActions());
    }

    @Test
    void ambiguousHandsAndOvertightLimitCannotCreateTrainerCapability() throws Exception {
        for (var d : bounds.report().decisions().subList(1, 3)) {
            var rejected =
                    SixMaxSuppliedFlopFeedback.qualify(
                            select(d.hero(), d.informationSet(), .01), game, bounds);
            assertFalse(rejected.report().qualifiedForOfflineDrill());
            assertTrue(
                    rejected.report().rejectionReasons().contains("NO_ROBUST_MOVE_OUTSIDE_LIMIT"));
            assertTrue(rejected.qualifiedFeedback().isEmpty());
            assertThrows(
                    IllegalArgumentException.class, () -> new SixMaxSuppliedFlopTrainer(rejected));
        }
        var tight =
                SixMaxSuppliedFlopFeedback.qualify(
                        select(5, request.informationSet(), 1e-5), game, bounds);
        assertTrue(tight.report().rejectionReasons().contains("NO_ROBUST_MOVE_WITHIN_LIMIT"));
        assertTrue(tight.qualifiedFeedback().isEmpty());
    }

    @Test
    void wrongActorUnknownHandAndLaterHistoriesRejected() {
        for (var bad :
                List.of(
                        select(3, request.informationSet(), .01),
                        select(5, "unknown", .01),
                        select(5, request.informationSet() + "check|bet", .01),
                        select(5, request.informationSet().replace("7d 7h", "Kd Kh"), .01)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedFlopFeedback.qualify(bad, game, bounds));
        for (double limit : new double[] {0, -.01, .011, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> select(5, request.informationSet(), limit));
    }

    @Test
    void realOutsideFacePolicyNeverClaimsItsBoundsWereChecked() {
        var strategy = new TreeMap<String, Map<String, Double>>();
        bounds.report()
                .freshReferences()
                .getFirst()
                .policy()
                .strategy()
                .forEach(
                        (key, row) -> {
                            var changed = new TreeMap<>(row);
                            if (key.startsWith("3:")) {
                                changed.replaceAll((a, p) -> 0.0);
                                changed.put(
                                        changed.containsKey("fold") ? "fold" : changed.firstKey(),
                                        1.0);
                            }
                            strategy.put(key, changed);
                        });
        var handoff = game.handoffs().getFirst();
        var states =
                handoff.worlds().stream()
                        .map(
                                w ->
                                        new SixMaxSuppliedFlopBettingGame.State(
                                                w.originalDealIndex(),
                                                handoff.publicHistory(),
                                                SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                                                ""))
                        .filter(
                                s ->
                                        (game.currentPlayer(s) + ":" + game.informationSet(s))
                                                .equals(request.informationSet()))
                        .toList();
        var check =
                SixMaxSuppliedFlopFeedback.assess(
                        game,
                        handoff,
                        request,
                        states,
                        bounds.report().decisions().getFirst(),
                        new CfrSolution(2000, strategy));
        assertFalse(check.insideDeclaredFace());
        assertFalse(check.boundsChecked());
        assertNull(check.maximumBoundViolationBb());
        assertTrue(check.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
    }

    @Test
    void cachedGradesAndReviewAreExactBoundedAndImmutable(@TempDir Path dir) throws Exception {
        var q = trainer.question();
        var review = trainer.review(q, List.of("check", "bet", "check"));
        assertEquals(new SixMaxSuppliedFlopTrainer.Counts(2, 1, 0), review.counts());
        assertEquals(review, trainer.replay(review));
        assertEquals(trainer.review(q, List.of("check", "bet")), trainer.replay(DRILL));
        for (String name : List.of("drill.json", "drill.json.gz")) {
            var path = dir.resolve(name);
            trainer.write(path, review);
            assertEquals(review, trainer.replay(path));
            assertThrows(FileAlreadyExistsException.class, () -> trainer.write(path, review));
        }
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(q, "call"));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(q, Collections.nCopies(101, "check")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        trainer.replay(
                                new SixMaxSuppliedFlopTrainer.Review(
                                        SixMaxSuppliedFlopTrainer.SCHEMA,
                                        q,
                                        review.attempts(),
                                        new SixMaxSuppliedFlopTrainer.Counts(3, 0, 0))));
        assertThrows(UnsupportedOperationException.class, () -> q.context().board().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.report().references().getFirst().actionLossBb().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> trainer.grade(q, "bet").alternatives().clear());
        for (var type :
                List.of(
                        SixMaxSuppliedFlopFeedback.Result.class,
                        SixMaxSuppliedFlopFeedback.QualifiedFeedback.class))
            for (var constructor : type.getDeclaredConstructors())
                assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void pinAndWholeQuestionContextAreCheckedBeforeAnyGrade(@TempDir Path dir) throws Exception {
        String hash = result.qualifiedFeedback().orElseThrow().reportHash();
        assertEquals(SixMaxHeadsUpPreflopGame.hash(result.report()), hash);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedFlopTrainerLoader.load(REQUEST, REPORT, "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedFlopTrainerLoader.load(REQUEST, REPORT, "bad"));
        var node = SixMaxTexturePayoffTable.mapper().valueToTree(trainer.question());
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("context")).put("potBb", 100);
        var forged =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(node, SixMaxSuppliedFlopTrainer.Question.class);
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(forged, "check"));
        assertThrows(IllegalArgumentException.class, () -> trainer.review(forged, List.of()));
        Files.writeString(
                dir.resolve("oversized.json"), " ".repeat(SixMaxSuppliedFlopTrainer.MAX_BYTES + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedFlopTrainer.read(dir.resolve("oversized.json")));
    }

    @Test
    void fullPhysicalStartupReplayAndRehashedTampering(@TempDir Path dir) throws Exception {
        String hash = result.qualifiedFeedback().orElseThrow().reportHash();
        var loaded = SixMaxSuppliedFlopTrainerLoader.load(REQUEST, REPORT, hash);
        assertEquals(trainer.question(), loaded.question());
        var node = SixMaxTexturePayoffTable.mapper().valueToTree(result.report());
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("moves").get(1))
                .put("lowerLossBb", 0);
        var changed =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(node, SixMaxSuppliedFlopFeedback.Report.class);
        var path = dir.resolve("rehashed.json");
        Files.writeString(path, SixMaxTextureStudy.json(changed));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopTrainerLoader.load(
                                REQUEST, path, SixMaxHeadsUpPreflopGame.hash(changed)));
        for (String name : List.of("qualification.json", "qualification.json.gz")) {
            var output = dir.resolve(name);
            SixMaxSuppliedFlopFeedback.write(output, result);
            assertEquals(result.report(), SixMaxSuppliedFlopFeedback.readReport(output));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxSuppliedFlopFeedback.write(output, result));
        }
    }
}
