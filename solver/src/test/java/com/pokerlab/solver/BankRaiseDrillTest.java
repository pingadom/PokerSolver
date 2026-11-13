package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class BankRaiseDrillTest {
    static final String PIN = SixMaxRootHandBankRaiseDrillLoader.STUDY_HASH;
    static final List<String> SOURCES =
            List.of(
                    "supplied-bank-raise-drill-manifest.json",
                    "supplied-bank-raise-feedback-request.json",
                    "supplied-bank-raise-feedback-report.json.gz",
                    "supplied-bank-raise-sequence-report.json.gz",
                    "supplied-root-hand-bank-report.json.gz");
    @TempDir static Path temp;
    static SixMaxRootHandBankRaiseDrill.Result cache;

    @BeforeAll
    static void ownedStartup() throws Exception {
        for (String n : SOURCES) Files.copy(Path.of("../docs/data", n), temp.resolve(n));
        cache = SixMaxRootHandBankRaiseDrillLoader.load(temp.resolve(SOURCES.getFirst()), PIN);
    }

    @Test
    void completeSparseInventoryAndSixCachedGrades() {
        assertEquals(
                List.of(0, 2),
                cache.snapshot().questions().stream()
                        .map(SixMaxRootHandBankRaiseDrill.Question::sourceCaseIndex)
                        .toList());
        assertFalse(cache.snapshot().trainerAdmission());
        for (int i : List.of(0, 2))
            for (String a : SixMaxRootHandBankRaiseDrill.ACTIONS) {
                assertSame(cache.grade(PIN, i, a), cache.grade(PIN, i, a));
                assertEquals(i, cache.grade(PIN, i, a).sourceCaseIndex());
            }
        assertEquals("Ac Jc", cache.question(PIN, 2).heroCards());
    }

    @Test
    void everyPublicMenuMatchesOwnedCaptureAndContainsNoHiddenInformation() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var fixture =
                mapper.readTree(
                        Path.of("../docs/data/supplied-bank-raise-drill-fixture.json").toFile());
        assertEquals(
                cache.snapshot(),
                mapper.treeToValue(
                        fixture.get("snapshot"), SixMaxRootHandBankRaiseDrill.Snapshot.class));
        for (var q : cache.snapshot().questions()) {
            assertTrue(q.board().isEmpty());
            assertEquals(6, q.context().players().size());
            assertEquals(4.5, q.context().potBb());
            assertEquals(2., q.context().toCallBb());
            String text = mapper.writeValueAsString(q);
            for (String hidden :
                    List.of(
                            "sourceMinimum",
                            "references",
                            "worlds",
                            "dealtCombos",
                            "opponentCards",
                            "strategy",
                            "informationSet",
                            "6d",
                            "Qc",
                            "Td")) assertFalse(text.contains("\"" + hidden + "\""), hidden);
        }
    }

    @Test
    void evShiftIsAppliedOnceAndLossesStayIdentical() throws Exception {
        var report =
                SixMaxRootHandBankRaiseFeedback.readReport(
                        Path.of("../docs/data/supplied-bank-raise-feedback-report.json.gz"));
        for (var a : report.assessments())
            for (var m : a.moves()) {
                var shifted = cache.grade(PIN, a.sourceQuestionIndex(), m.action()).move();
                assertEquals(m.lowerEvBb() + 1, shifted.lowerEvBb());
                assertEquals(m.upperEvBb() + 1, shifted.upperEvBb());
                assertEquals(m.lowerLossBb(), shifted.lowerLossBb());
                assertEquals(m.upperLossBb(), shifted.upperLossBb());
                assertEquals(m.classification(), shifted.classification());
            }
        assertEquals(1.1078040536882738, cache.grade(PIN, 0, "raise:9.0").move().upperLossBb());
        assertEquals(3.595333327470523, cache.grade(PIN, 2, "raise:9.0").move().upperLossBb());
    }

    @Test
    void mixedRepeatedReviewsKeepOriginalIdsAndGradeIdentity() {
        var attempts =
                List.of(
                        new SixMaxRootHandBankRaiseDrill.Attempt(2, "call"),
                        new SixMaxRootHandBankRaiseDrill.Attempt(0, "raise:9.0"),
                        new SixMaxRootHandBankRaiseDrill.Attempt(2, "fold"),
                        new SixMaxRootHandBankRaiseDrill.Attempt(2, "raise:9.0"));
        var review = cache.review(PIN, attempts);
        assertEquals(new SixMaxRootHandBankRaiseDrill.Counts(2, 1, 1, 2), review.counts());
        for (int i = 0; i < attempts.size(); i++) {
            var row = review.attempts().get(i);
            assertEquals(i, row.ordinal());
            assertEquals(attempts.get(i), row.attempt());
            assertSame(
                    cache.grade(PIN, row.attempt().sourceCaseIndex(), row.attempt().action()),
                    row.grade());
        }
    }

    @Test
    void unknownSparseIdsIllegalActionsAndWrongPinsCannotSelectOldBank() {
        for (int i : List.of(-1, 1, 3)) {
            assertThrows(IllegalArgumentException.class, () -> cache.question(PIN, i));
            assertThrows(IllegalArgumentException.class, () -> cache.grade(PIN, i, "fold"));
        }
        for (String pin :
                List.of(
                        "0".repeat(64),
                        SixMaxRootHandBankDrillLoader.STUDY_HASH,
                        PIN.toUpperCase(Locale.ROOT))) {
            assertThrows(IllegalArgumentException.class, () -> cache.grade(pin, 0, "call"));
            assertThrows(IllegalArgumentException.class, () -> cache.review(pin, List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> cache.grade(PIN, 2, "check"));
    }

    @Test
    void emptyMaximumOverflowAndNullReviewsAreBounded() {
        assertEquals(
                new SixMaxRootHandBankRaiseDrill.Counts(0, 0, 0, 0),
                cache.review(PIN, List.of()).counts());
        var a = new SixMaxRootHandBankRaiseDrill.Attempt(2, "raise:9.0");
        assertEquals(
                new SixMaxRootHandBankRaiseDrill.Counts(1, 0, 0, 128),
                cache.review(PIN, Collections.nCopies(128, a)).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.review(PIN, Collections.nCopies(129, a)));
        assertThrows(
                NullPointerException.class,
                () -> cache.review(PIN, Collections.singletonList(null)));
    }

    @Test
    void cachedCollectionsAndReviewRowsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class, () -> cache.snapshot().questions().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> cache.question(PIN, 2).moves().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> cache.question(PIN, 2).board().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> cache.review(PIN, List.of()).attempts().clear());
    }

    @Test
    void parsedReportOrSnapshotCannotMintCacheAuthority() throws Exception {
        assertTrue(
                Arrays.stream(SixMaxRootHandBankRaiseDrill.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertEquals(
                List.of(SixMaxRootHandBankRaiseFeedback.Result.class),
                Arrays.asList(
                        SixMaxRootHandBankRaiseDrill.class
                                .getMethod("create", SixMaxRootHandBankRaiseFeedback.Result.class)
                                .getParameterTypes()));
        assertThrows(
                NoSuchMethodException.class,
                () ->
                        SixMaxRootHandBankRaiseDrill.class.getMethod(
                                "create", SixMaxRootHandBankRaiseFeedback.Report.class));
        assertThrows(
                NoSuchMethodException.class,
                () ->
                        SixMaxRootHandBankRaiseDrill.class.getMethod(
                                "create", SixMaxRootHandBankStudy.Result.class));
        var mapper = SixMaxTexturePayoffTable.mapper();
        String json = mapper.writeValueAsString(cache.snapshot());
        assertThrows(
                Exception.class,
                () -> mapper.readValue(json, SixMaxRootHandBankRaiseDrill.Result.class));
    }

    @Test
    void allOperationsWorkAfterAllSourcesDisappear() throws Exception {
        try {
            for (String n : SOURCES) Files.move(temp.resolve(n), temp.resolve(n + ".held"));
            assertEquals("Ac Jc", cache.question(PIN, 2).heroCards());
            assertEquals(0, cache.grade(PIN, 0, "fold").sourceCaseIndex());
            assertEquals(
                    1,
                    cache.review(PIN, List.of(new SixMaxRootHandBankRaiseDrill.Attempt(2, "call")))
                            .counts()
                            .withinLimit());
        } finally {
            for (String n : SOURCES)
                if (Files.exists(temp.resolve(n + ".held")))
                    Files.move(temp.resolve(n + ".held"), temp.resolve(n));
        }
    }

    Path copied(Path folder) throws Exception {
        for (String n : SOURCES) Files.copy(Path.of("../docs/data", n), folder.resolve(n));
        return folder.resolve(SOURCES.getFirst());
    }

    @Test
    void everySourceBytePinFailsBeforeAnyNumericalReplay(@TempDir Path folder) throws Exception {
        var manifest = copied(folder);
        for (String n : SOURCES.subList(1, SOURCES.size())) {
            var path = folder.resolve(n);
            byte[] old = Files.readAllBytes(path);
            Files.write(path, new byte[] {1, 2, 3});
            try {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxRootHandBankRaiseDrillLoader.load(manifest, PIN));
            } finally {
                Files.write(path, old);
            }
        }
    }

    @Test
    void manifestRejectsChangedPinsNamesBudgetAndCoercions(@TempDir Path folder) throws Exception {
        Path path = copied(folder);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var original = mapper.readTree(path.toFile());
        for (String name :
                List.of(
                        "studyHash",
                        "requestSha256",
                        "reportStoredSha256",
                        "minimumStoredSha256",
                        "bankStoredSha256",
                        "schemaVersion",
                        "minimumFile")) {
            var changed = original.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed)
                    .put(name, name.endsWith("File") ? "../escape.gz" : "wrong");
            Files.write(path, mapper.writeValueAsBytes(changed));
            assertThrows(Exception.class, () -> SixMaxRootHandBankRaiseDrillLoader.load(path, PIN));
        }
        var changed = original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get("startupBudget"))
                .put("maximumEndpointLpSolves", 1);
        Files.write(path, mapper.writeValueAsBytes(changed));
        assertThrows(Exception.class, () -> SixMaxRootHandBankRaiseDrillLoader.load(path, PIN));
        for (String json :
                List.of(
                        mapper.writeValueAsString(original)
                                .replace("\"physicalGames\":1", "\"physicalGames\":\"1\""),
                        "{\"schemaVersion\":1}",
                        "null")) {
            Files.writeString(path, json);
            assertThrows(Exception.class, () -> SixMaxRootHandBankRaiseDrillLoader.load(path, PIN));
        }
    }

    @Test
    void actualHardLinkAliasesAreRejected(@TempDir Path folder) throws Exception {
        var path = copied(folder);
        var request = folder.resolve(SOURCES.get(1));
        Files.delete(request);
        Files.createLink(request, folder.resolve(SOURCES.get(2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseDrillLoader.load(path, PIN));
    }

    @Test
    void manifestCapAndMissingInputsRejectBeforeNumericalWork(@TempDir Path folder)
            throws Exception {
        var path = copied(folder);
        Files.writeString(
                path, " ".repeat(SixMaxRootHandBankRaiseDrillLoader.MAX_MANIFEST_BYTES + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseDrillLoader.load(path, PIN));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseDrillLoader.load(path, "0".repeat(64)));
        assertThrows(
                Exception.class,
                () -> SixMaxRootHandBankRaiseDrillLoader.load(folder.resolve("missing"), PIN));
    }
}
