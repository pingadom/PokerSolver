package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SequencePreflopDrillTest {
    static final List<String> SOURCES =
            List.of(
                    "supplied-sequence-preflop-drill-manifest.json",
                    "supplied-root-sequence-feedback-request.json",
                    "supplied-root-sequence-feedback-report.json.gz",
                    "supplied-root-envelope-feedback-report.json.gz",
                    "supplied-root-sequence-minimum-report.json.gz",
                    "supplied-root-minimum-report.json.gz");
    static final String PIN = SixMaxSequencePreflopDrillLoader.STUDY_HASH;
    @TempDir static Path temp;
    static SixMaxSequencePreflopDrill.Result cache;

    @BeforeAll
    static void oneCompleteOwnedLoaderReplay() throws Exception {
        for (String name : SOURCES) Files.copy(Path.of("../docs/data", name), temp.resolve(name));
        cache = SixMaxSequencePreflopDrillLoader.load(temp.resolve(SOURCES.getFirst()), PIN);
    }

    @Test
    void ownedCacheMatchesPublicHttpFixtureAndHasNoFutureOrVillainCards() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var fixture =
                mapper.readTree(
                        Path.of("../docs/data/supplied-sequence-preflop-drill-fixture.json")
                                .toFile());
        var q = cache.question(PIN, 0);
        assertEquals(
                q,
                mapper.treeToValue(
                        fixture.get("questions").get(0),
                        SixMaxSequencePreflopDrill.Question.class));
        assertEquals("9d 9h", q.heroCards());
        assertTrue(q.board().isEmpty());
        assertEquals(6, q.context().players().size());
        assertEquals(4.5, q.context().potBb());
        assertEquals(2, q.context().toCallBb());
        assertEquals("BB", q.context().actingSeat().toString());
        String text = mapper.writeValueAsString(q);
        for (String hidden :
                List.of(
                        "dealtCombos",
                        "opponentCards",
                        "strategy",
                        "informationSet",
                        "6d",
                        "Qc",
                        "Td")) assertFalse(text.contains("\"" + hidden + "\""));
    }

    @Test
    void currentDecisionEvsShiftOnceWhileLossAndAmbiguityStayUnchanged() throws Exception {
        var report =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-root-sequence-feedback-report.json.gz"),
                                        8 * 1024 * 1024),
                                SixMaxRootSequenceFeedback.Report.class);
        for (var original : report.moves()) {
            var grade = cache.grade(PIN, 0, original.action());
            var move = grade.move();
            assertEquals(SixMaxSequencePreflopDrill.EV_SCOPE, grade.evScope());
            assertEquals(original.lowerEvBb() + 1, move.lowerEvBb());
            assertEquals(original.upperEvBb() + 1, move.upperEvBb());
            assertEquals(original.lowerLossBb(), move.lowerLossBb());
            assertEquals(original.upperLossBb(), move.upperLossBb());
            assertEquals(original.classification(), move.classification());
        }
        assertEquals(0, cache.grade(PIN, 0, "fold").move().lowerEvBb(), 2e-8);
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                cache.grade(PIN, 0, "raise:9.0").move().classification());
    }

    @Test
    void allThreeGradesAreCachedAndBoundToTheExactStudy() {
        assertFalse(cache.snapshot().trainerAdmission());
        assertEquals(1, cache.snapshot().questions().size());
        for (String action : SixMaxSequencePreflopDrill.ACTIONS)
            assertSame(cache.grade(PIN, 0, action), cache.grade(PIN, 0, action));
        assertSame(cache.question(PIN, 0), cache.question(PIN, 0));
        assertThrows(IllegalArgumentException.class, () -> cache.grade("0".repeat(64), 0, "fold"));
        assertThrows(IllegalArgumentException.class, () -> cache.question(PIN, 1));
    }

    @Test
    void orderedAndRepeatedReviewsPreserveEveryClassificationAndOrdinal() {
        var attempts =
                List.of(
                        new SixMaxSequencePreflopDrill.Attempt(0, "raise:9.0"),
                        new SixMaxSequencePreflopDrill.Attempt(0, "call"),
                        new SixMaxSequencePreflopDrill.Attempt(0, "fold"),
                        new SixMaxSequencePreflopDrill.Attempt(0, "raise:9.0"));
        var review = cache.review(PIN, attempts);
        assertEquals(new SixMaxSequencePreflopDrill.Counts(1, 1, 1, 2), review.counts());
        assertEquals(attempts, review.attempts().stream().map(a -> a.attempt()).toList());
        for (int i = 0; i < attempts.size(); i++) {
            assertEquals(i, review.attempts().get(i).ordinal());
            assertSame(
                    cache.grade(PIN, 0, attempts.get(i).action()),
                    review.attempts().get(i).grade());
        }
    }

    @Test
    void emptyMaximumOverflowNullAndUnknownChoicesCannotInventGrades() {
        assertEquals(
                new SixMaxSequencePreflopDrill.Counts(0, 0, 0, 0),
                cache.review(PIN, List.of()).counts());
        var repeated =
                Collections.nCopies(128, new SixMaxSequencePreflopDrill.Attempt(0, "raise:9.0"));
        assertEquals(
                new SixMaxSequencePreflopDrill.Counts(1, 0, 0, 128),
                cache.review(PIN, repeated).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.review(PIN, Collections.nCopies(129, repeated.getFirst())));
        assertThrows(
                NullPointerException.class,
                () -> cache.review(PIN, Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> cache.grade(PIN, 0, "raise:9x0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSequencePreflopDrill.Attempt(0, null));
    }

    @Test
    void parsedReportOrSnapshotCannotConstructCacheAuthority() throws Exception {
        assertTrue(
                Arrays.stream(SixMaxSequencePreflopDrill.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var create =
                SixMaxSequencePreflopDrill.class.getMethod(
                        "create", SixMaxRootSequenceFeedback.Result.class);
        assertEquals(
                List.of(SixMaxRootSequenceFeedback.Result.class),
                Arrays.asList(create.getParameterTypes()));
        var mapper = SixMaxTexturePayoffTable.mapper();
        String text = mapper.writeValueAsString(cache.snapshot());
        for (String value :
                List.of(text, "{\"snapshot\":" + text + "}", "{\"studyHash\":\"" + PIN + "\"}"))
            assertThrows(
                    Exception.class,
                    () -> mapper.readValue(value, SixMaxSequencePreflopDrill.Result.class));
        assertThrows(
                NoSuchMethodException.class,
                () ->
                        SixMaxSequencePreflopDrill.class.getMethod(
                                "create", SixMaxRootMinimumFeedback.Result.class));
    }

    @Test
    void cacheAndAllPublicNestedCollectionsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class, () -> cache.snapshot().questions().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> cache.question(PIN, 0).moves().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> cache.question(PIN, 0).board().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> cache.review(PIN, List.of()).attempts().clear());
    }

    @Test
    void everyOperationStillWorksWhenStartupSourcesAreUnavailable() throws Exception {
        var before = cache.snapshot();
        try {
            for (String name : SOURCES)
                Files.move(temp.resolve(name), temp.resolve(name + ".held"));
            assertSame(before, cache.snapshot());
            assertEquals(0, cache.question(PIN, 0).sourceCaseIndex());
            var attempts =
                    SixMaxSequencePreflopDrill.ACTIONS.stream()
                            .map(a -> new SixMaxSequencePreflopDrill.Attempt(0, a))
                            .toList();
            assertEquals(
                    new SixMaxSequencePreflopDrill.Counts(1, 1, 1, 1),
                    cache.review(PIN, attempts).counts());
        } finally {
            for (String name : SOURCES)
                if (Files.exists(temp.resolve(name + ".held")))
                    Files.move(temp.resolve(name + ".held"), temp.resolve(name));
        }
    }
}
