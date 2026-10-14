package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxFlopIntervalCollectionTest {
    static final String PIN = "fb77bda6bf843e8a2e4ec49c91070e608b240e50e770674601413351ad099436";
    static SixMaxFlopIntervalCollection collection;

    static Path data(String suffix) {
        return Path.of("../docs/data/supplied-two-flop-interval-collection-" + suffix + ".json");
    }

    @BeforeAll
    static void physicallyReplayBothQualifiedSources() throws Exception {
        collection = SixMaxFlopIntervalCollectionLoader.load(data("manifest"), PIN);
    }

    static SixMaxFlopIntervalCollection.Choice choice(int index, String action) {
        return new SixMaxFlopIntervalCollection.Choice(
                index, collection.question(index).decision().reportHash(), action);
    }

    @Test
    void typedQuestionsRetainSeparateProtocolsAndPublicContexts() throws Exception {
        assertEquals(PIN, collection.collectionHash());
        assertEquals(2, collection.questions().size());
        var initial = collection.question(0).decision();
        var later = collection.question(1).decision();
        assertEquals(SixMaxFlopIntervalCollection.Kind.INITIAL_CHECK_BET, initial.kind());
        assertNull(initial.later());
        assertEquals(SixMaxFlopIntervalCollection.Kind.BB_FACING_BET, later.kind());
        assertNull(later.initial());
        assertEquals(SixMaxSuppliedFlopTrainer.SELECTION, initial.initial().selection());
        assertEquals(SixMaxLaterFlopTrainer.SELECTION, later.later().selection());
        assertEquals(6.5, initial.initial().context().potBb());
        assertEquals(9.75, later.later().context().potBb());
        assertEquals(3.25, later.later().context().toCallBb());
        for (String action : List.of("check", "bet"))
            assertEquals(
                    action,
                    collection
                            .grade(collection.question(0), action)
                            .decision()
                            .initial()
                            .selected()
                            .action());
        for (String action : List.of("fold", "call"))
            assertEquals(
                    action,
                    collection
                            .grade(collection.question(1), action)
                            .decision()
                            .later()
                            .selected()
                            .action());
        assertThrows(UnsupportedOperationException.class, () -> collection.questions().clear());
        // Authority requires the two owned Result types; public parsed report/question constructors
        // cannot create it.
        assertEquals(
                List.of(
                        SixMaxSuppliedFlopFeedback.Result.class,
                        SixMaxLaterFlopFeedback.Result.class),
                Arrays.asList(
                        SixMaxFlopIntervalCollection.class.getConstructors()[0]
                                .getParameterTypes()));
        assertEquals(
                PIN,
                SixMaxFlopIntervalCollection.identity(initial.reportHash(), later.reportHash()));
        assertNotEquals(
                PIN,
                SixMaxFlopIntervalCollection.identity(later.reportHash(), initial.reportHash()));
    }

    @Test
    void pinsIndicesAndTypedMenusPreventCrossQuestionGrading() {
        assertThrows(IllegalArgumentException.class, () -> collection.question(-1));
        assertThrows(IllegalArgumentException.class, () -> collection.question(2));
        assertThrows(IllegalArgumentException.class, () -> collection.grade(null, "check"));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(0), "fold"));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(1), "check"));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(1), null));
        var q = collection.question(0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.grade(
                                new SixMaxFlopIntervalCollection.Question(
                                        PIN, 1, 2, q.selection(), q.decision()),
                                "check"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxFlopIntervalCollection.Choice(
                                                1, q.decision().reportHash(), "fold"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxFlopIntervalCollection.Decision(
                                q.decision().kind(),
                                q.decision().initial(),
                                collection.question(1).decision().later()));
    }

    @Test
    void canonicalMixedReviewCountsDistinctContextsInsteadOfRepeatedAttempts(@TempDir Path dir)
            throws Exception {
        var choices =
                List.of(choice(0, "check"), choice(1, "fold"), choice(0, "bet"), choice(1, "call"));
        var review = collection.review(choices);
        assertEquals(new SixMaxFlopIntervalCollection.Counts(2, 2, 2, 0), review.counts());
        assertEquals(review, collection.replay(SixMaxFlopIntervalCollection.read(data("review"))));
        assertEquals(0, collection.review(List.of()).counts().distinctQuestionsAttempted());
        assertEquals(
                new SixMaxFlopIntervalCollection.Counts(1, 100, 0, 0),
                collection.review(Collections.nCopies(100, choice(1, "fold"))).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Collections.nCopies(101, choice(0, "check"))));
        assertThrows(IllegalArgumentException.class, () -> collection.review(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Arrays.asList(choice(0, "check"), null)));
        assertThrows(UnsupportedOperationException.class, () -> review.attempts().clear());
        for (String name : List.of("review.json", "review.json.gz")) {
            var output = dir.resolve(name);
            collection.write(output, review);
            assertEquals(review, collection.replay(SixMaxFlopIntervalCollection.read(output)));
            assertThrows(FileAlreadyExistsException.class, () -> collection.write(output, review));
        }
    }

    @Test
    void completeReplayRejectsEditedQuestionsGradesIndicesCountsAndSourceKinds() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var original = SixMaxFlopIntervalCollection.read(data("review"));
        for (String kind : List.of("question", "grade", "index", "counts", "pin", "typed")) {
            var tree = mapper.valueToTree(original);
            switch (kind) {
                case "question" ->
                        ((ObjectNode) tree.at("/questions/1/decision/later/context"))
                                .put("toCallBb", 0);
                case "grade" ->
                        ((ObjectNode) tree.at("/attempts/0/feedback/decision/initial/selected"))
                                .put("upperEvBb", 100);
                case "index" -> ((ObjectNode) tree.at("/attempts/0")).put("index", 1);
                case "counts" ->
                        ((ObjectNode) tree.get("counts")).put("distinctQuestionsAttempted", 1);
                case "pin" ->
                        ((ObjectNode) tree.at("/attempts/1/feedback/decision/later"))
                                .put("reportHash", "0".repeat(64));
                case "typed" ->
                        ((ObjectNode) tree.at("/questions/1/decision"))
                                .put("kind", "INITIAL_CHECK_BET");
            }
            assertThrows(
                    Exception.class,
                    () ->
                            collection.replay(
                                    mapper.treeToValue(
                                            tree, SixMaxFlopIntervalCollection.Review.class)),
                    kind);
        }
    }

    @Test
    void manifestPinsInventorySiblingPathsAndBoundsFailBeforePhysicalReplay(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = SixMaxFlopIntervalCollectionLoader.readManifest(data("manifest"));
        for (String pin : List.of("bad", "0".repeat(64)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxFlopIntervalCollectionLoader.load(data("manifest"), pin));
        for (String kind : List.of("order", "path", "source-pin", "alias")) {
            var tree = mapper.valueToTree(manifest);
            switch (kind) {
                case "order" -> ((ObjectNode) tree.at("/sources/0")).put("kind", "BB_FACING_BET");
                case "path" ->
                        ((ObjectNode) tree.at("/sources/0")).put("requestFile", "../other.json");
                case "source-pin" ->
                        ((ObjectNode) tree.at("/sources/0")).put("reportHash", "0".repeat(64));
                case "alias" ->
                        ((ObjectNode) tree.at("/sources/0"))
                                .put("reportFile", manifest.sources().getFirst().requestFile());
            }
            var path = dir.resolve(kind + ".json");
            Files.writeString(path, tree.toString());
            assertThrows(
                    Exception.class,
                    () -> SixMaxFlopIntervalCollectionLoader.load(path, PIN),
                    kind);
        }
        var large = dir.resolve("large.json");
        Files.writeString(
                large, " ".repeat(SixMaxFlopIntervalCollectionLoader.MAX_MANIFEST_BYTES + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxFlopIntervalCollectionLoader.load(large, PIN));
        Files.writeString(large, " ".repeat(SixMaxFlopIntervalCollection.MAX_BYTES + 1));
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxFlopIntervalCollection.read(large));
    }

    @Test
    void rehashingEditedSourceAndManifestCannotBypassOwnedPhysicalReplay(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = SixMaxFlopIntervalCollectionLoader.readManifest(data("manifest"));
        for (var source : manifest.sources())
            for (String name : List.of(source.requestFile(), source.reportFile()))
                Files.copy(data("manifest").getParent().resolve(name), dir.resolve(name));
        var source = manifest.sources().getLast();
        var report = SixMaxLaterFlopFeedback.readReport(dir.resolve(source.reportFile()));
        var tree = mapper.valueToTree(report);
        ((ObjectNode) tree.get("context")).put("toCallBb", 0);
        var changed = dir.resolve("changed-report.json");
        Files.writeString(changed, tree.toString());
        var changedPin = SixMaxHeadsUpPreflopGame.hash(SixMaxLaterFlopFeedback.readReport(changed));
        var pin =
                SixMaxFlopIntervalCollection.identity(
                        manifest.sources().getFirst().reportHash(), changedPin);
        var sources =
                List.of(
                        manifest.sources().getFirst(),
                        new SixMaxFlopIntervalCollectionLoader.Source(
                                source.kind(),
                                source.requestFile(),
                                changed.getFileName().toString(),
                                changedPin));
        var input = dir.resolve("manifest.json");
        Files.writeString(
                input,
                SixMaxTextureStudy.json(
                        new SixMaxFlopIntervalCollectionLoader.Manifest(
                                manifest.schemaVersion(), pin, sources)));
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxFlopIntervalCollectionLoader.load(input, pin));
        assertTrue(failure.getMessage().contains("Physical later feedback replay differs"));
    }

    @Test
    void independentReceiptBindsCompleteSourceChecksAndCollectionBytes() throws Exception {
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(Files.readAllBytes(data("independent-check")));
        assertEquals(35, receipt.get("originalEndpointCertificatesChecked").asInt());
        assertEquals(6, receipt.get("sourceDecisionPolicyChecks").asInt());
        assertEquals(4, receipt.get("attemptsChecked").asInt());
        assertEquals(0, receipt.get("freshLpSolves").asInt());
        assertFalse(receipt.get("trainerAdmission").asBoolean());
        assertEquals(PIN, receipt.get("collectionHash").asText());
        assertEquals(sha(data("manifest")), receipt.get("manifestStoredBytesSha256").asText());
        assertEquals(sha(data("review")), receipt.get("reviewStoredBytesSha256").asText());
        assertEquals(
                sha(Path.of("../scripts/check-flop-interval-collection.py")),
                receipt.get("checkerSourceSha256").asText());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(path)));
    }
}
