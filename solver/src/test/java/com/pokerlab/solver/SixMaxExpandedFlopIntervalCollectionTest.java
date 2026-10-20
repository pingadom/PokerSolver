package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxExpandedFlopIntervalCollectionTest {
    static final String PIN = "11db67eb188fe4fadf8b370f45ecfc0f29ed5412ae78d69eff514dcecc962122";
    static SixMaxExpandedFlopIntervalCollection collection;

    static Path data(String suffix) {
        return Path.of("../docs/data/supplied-ten-question-flop-collection-" + suffix + ".json");
    }

    @BeforeAll
    static void replayAllFourOwnedSources() throws Exception {
        collection = SixMaxExpandedFlopIntervalCollectionLoader.load(data("manifest"), PIN);
    }

    static SixMaxExpandedFlopIntervalCollection.Choice choice(int index, String action) {
        return new SixMaxExpandedFlopIntervalCollection.Choice(
                index, collection.question(index).decision().reportHash(), action);
    }

    static List<SixMaxExpandedFlopIntervalCollection.Choice> choices() {
        var values = new ArrayList<SixMaxExpandedFlopIntervalCollection.Choice>();
        for (int round = 0; round < 2; round++)
            for (int i = 0; i < 10; i++)
                values.add(
                        choice(
                                i,
                                (i == 1 || i >= 6)
                                        ? (round == 0 ? "fold" : "call")
                                        : (round == 0 ? "check" : "bet")));
        return values;
    }

    @Test
    void tenTypedQuestionsBindBothKindsOfIndicesAndEveryOriginalMenu() throws Exception {
        assertEquals(PIN, collection.collectionHash());
        assertEquals(10, collection.questions().size());
        var manifest = SixMaxExpandedFlopIntervalCollectionLoader.readManifest(data("manifest"));
        assertEquals(
                PIN,
                SixMaxExpandedFlopIntervalCollection.identity(
                        manifest.sources().get(0).reportHash(),
                        manifest.sources().get(1).reportHash(),
                        manifest.sources().get(2).reportHash(),
                        manifest.sources().get(3).reportHash()));
        for (int i = 0; i < 10; i++) {
            var q = collection.question(i);
            assertEquals(i, q.index());
            assertEquals(10, q.totalQuestions());
            assertEquals(i < 2 ? 0 : i < 6 ? i - 2 : i - 6, q.sourceQuestionIndex());
            var menu = (i == 1 || i >= 6) ? List.of("fold", "call") : List.of("check", "bet");
            assertEquals(menu, q.decision().legalActions());
            for (var action : menu) {
                var f = collection.grade(q, action);
                assertEquals(i, f.questionIndex());
                assertEquals(q.sourceQuestionIndex(), f.sourceQuestionIndex());
                assertEquals(q.decision().reportHash(), f.decision().reportHash());
                assertEquals(action, f.decision().selected().action());
                if (i >= 2 && i < 6) assertEquals(i - 2, f.decision().btn().questionIndex());
            }
            if (i >= 6) {
                assertEquals(i - 6, q.decision().paired().questionIndex());
                assertEquals(
                        SixMaxPairedFlopCurriculum.HANDS.get(i - 6),
                        q.decision().paired().heroCards());
                assertEquals(
                        SixMaxExpandedFlopIntervalCollection.PAIRED_BOARD,
                        q.decision().paired().context().board());
                assertNull(q.decision().btn());
                for (var action : menu)
                    assertEquals(
                            i - 6, collection.grade(q, action).decision().paired().questionIndex());
            }
            if (i >= 2 && i < 6) {
                assertEquals(
                        SixMaxBtnFlopCurriculum.HANDS.get(i - 2), q.decision().btn().heroCards());
                assertNull(q.decision().initial());
                assertNull(q.decision().later());
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> collection.questions().clear());
        assertEquals(
                List.of(
                        SixMaxSuppliedFlopFeedback.Result.class,
                        SixMaxLaterFlopFeedback.Result.class,
                        SixMaxBtnFlopCurriculum.Result.class,
                        SixMaxPairedFlopCurriculum.Result.class),
                Arrays.asList(
                        SixMaxExpandedFlopIntervalCollection.class.getConstructors()[0]
                                .getParameterTypes()));
        var json = SixMaxTextureStudy.json(collection.questions());
        for (var hidden :
                List.of("opponentCards", "worlds", "policy", "certificate", "payoffMatrix"))
            assertFalse(json.contains("\"" + hidden + "\""));
        assertNotEquals(
                SixMaxFlopIntervalCollection.SCHEMA, SixMaxExpandedFlopIntervalCollection.SCHEMA);
    }

    @Test
    void expandedReplayAndCanonicalBytesMatchAllTwentyOwnedMoves(@TempDir Path dir)
            throws Exception {
        var review = collection.review(choices());
        assertEquals(
                new SixMaxExpandedFlopIntervalCollection.Counts(10, 10, 10, 0), review.counts());
        assertEquals(
                review,
                collection.replay(SixMaxExpandedFlopIntervalCollection.read(data("review"))));
        assertEquals(Files.readString(data("review")), collection.reviewJson(choices()));
        for (var name : List.of("new.json", "new.json.gz")) {
            var path = dir.resolve(name);
            collection.write(path, review);
            assertEquals(
                    review, collection.replay(SixMaxExpandedFlopIntervalCollection.read(path)));
            assertThrows(FileAlreadyExistsException.class, () -> collection.write(path, review));
        }
        assertThrows(UnsupportedOperationException.class, () -> review.attempts().clear());
    }

    @Test
    void zeroAndHundredRetriesKeepCoverageAndRejectHundredAndOne() throws Exception {
        assertEquals(
                new SixMaxExpandedFlopIntervalCollection.Counts(0, 0, 0, 0),
                collection.review(List.of()).counts());
        var repeated = Collections.nCopies(100, choice(5, "bet"));
        assertEquals(
                new SixMaxExpandedFlopIntervalCollection.Counts(1, 100, 0, 0),
                collection.review(repeated).counts());
        assertTrue(
                collection
                                .reviewJson(repeated)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                .length
                        <= SixMaxExpandedFlopIntervalCollection.MAX_BYTES);
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Collections.nCopies(101, choice(5, "bet"))));
        assertThrows(IllegalArgumentException.class, () -> collection.review(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Arrays.asList(choice(0, "bet"), null)));
    }

    @Test
    void wrongSourcePinsMenusAndInventoryCannotGrantGrades() {
        assertThrows(IllegalArgumentException.class, () -> collection.question(-1));
        assertThrows(IllegalArgumentException.class, () -> collection.question(10));
        assertThrows(IllegalArgumentException.class, () -> collection.grade(null, "bet"));
        for (int i = 0; i < 10; i++) {
            var q = collection.question(i);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            collection.grade(
                                    q, (q.index() == 1 || q.index() >= 6) ? "check" : "call"));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            collection.review(
                                    List.of(
                                            new SixMaxExpandedFlopIntervalCollection.Choice(
                                                    q.index(), "0".repeat(64), "bet"))));
        }
        var a = collection.question(2);
        var b = collection.question(3);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxExpandedFlopIntervalCollection.Question(
                                PIN, 2, 10, a.selection(), 0, b.decision()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxExpandedFlopIntervalCollection.Decision(
                                a.decision().kind(),
                                collection.question(0).decision().initial(),
                                null,
                                a.decision().btn(),
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxExpandedFlopIntervalCollection.Grade(
                                SixMaxExpandedFlopIntervalCollection.Kind.BTN_AFTER_CHECK,
                                null,
                                null,
                                null,
                                null));
    }

    @Test
    void editedReviewsCannotMoveGradesBetweenBtnHands() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var original = collection.review(choices());
        for (var change :
                List.of(
                        "counts",
                        "pin",
                        "selected",
                        "inventory",
                        "local-index",
                        "grade-index",
                        "union",
                        "unknown",
                        "paired-board",
                        "paired-index",
                        "paired-grade",
                        "order",
                        "false-index",
                        "version")) {
            var tree = mapper.valueToTree(original);
            switch (change) {
                case "paired-board" ->
                        ((ObjectNode) tree.at("/questions/6/decision/paired/context"))
                                .set("board", mapper.valueToTree(List.of("2s", "8s", "Ts")));
                case "paired-index" ->
                        ((ObjectNode) tree.at("/questions/7/decision/paired"))
                                .put("questionIndex", 0);
                case "paired-grade" ->
                        ((ObjectNode) tree.at("/attempts/6/feedback/decision/paired/selected"))
                                .put("upperEvBb", 100);
                case "counts" ->
                        ((ObjectNode) tree.get("counts")).put("distinctQuestionsAttempted", 12);
                case "pin" ->
                        ((ObjectNode) tree.at("/attempts/2/feedback/decision/btn"))
                                .put("reportHash", "0".repeat(64));
                case "selected" ->
                        ((ObjectNode) tree.at("/attempts/2/feedback/decision/btn/selected"))
                                .put("upperEvBb", 100);
                case "inventory" ->
                        ((ObjectNode) tree.at("/questions/2/decision/btn"))
                                .put("heroCards", "Ah Kh");
                case "local-index" ->
                        ((ObjectNode) tree.at("/questions/3")).put("sourceQuestionIndex", 0);
                case "grade-index" ->
                        ((ObjectNode) tree.at("/attempts/3/feedback/decision/btn"))
                                .put("questionIndex", 0);
                case "union" ->
                        ((ObjectNode) tree.at("/questions/2/decision"))
                                .put("kind", "BB_FACING_BET");
                case "unknown" -> ((ObjectNode) tree.at("/attempts/0")).put("evLossBb", 0);
                case "order" -> ((ObjectNode) tree.at("/attempts/0")).put("index", 1);
                case "false-index" ->
                        ((ObjectNode) tree.at("/attempts/0")).put("questionIndex", false);
                case "version" ->
                        ((ObjectNode) tree)
                                .put("schemaVersion", SixMaxFlopIntervalCollection.SCHEMA);
            }
            assertThrows(
                    Exception.class,
                    () ->
                            collection.replay(
                                    mapper.treeToValue(
                                            tree,
                                            SixMaxExpandedFlopIntervalCollection.Review.class)),
                    change);
        }
    }

    @Test
    void badManifestsAndAliasesFailBeforePhysicalReplay(@TempDir Path dir) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = SixMaxExpandedFlopIntervalCollectionLoader.readManifest(data("manifest"));
        for (var change :
                List.of(
                        "order", "path", "pin", "alias", "version", "missing", "unknown",
                        "budget")) {
            var tree = mapper.valueToTree(manifest);
            switch (change) {
                case "order" -> ((ObjectNode) tree.at("/sources/0")).put("kind", "BTN_AFTER_CHECK");
                case "path" ->
                        ((ObjectNode) tree.at("/sources/2")).put("requestFile", "../other.json");
                case "pin" ->
                        ((ObjectNode) tree.at("/sources/2")).put("reportHash", "0".repeat(64));
                case "alias" ->
                        ((ObjectNode) tree.at("/sources/2"))
                                .put("reportFile", manifest.sources().get(2).requestFile());
                case "version" ->
                        ((ObjectNode) tree)
                                .put("schemaVersion", SixMaxFlopIntervalCollectionLoader.SCHEMA);
                case "missing" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode) tree.get("sources"))
                                .remove(2);
                case "unknown" -> ((ObjectNode) tree).put("qualified", true);
                case "budget" ->
                        ((ObjectNode) tree.get("startupBudget")).put("freshIterations", 491999);
            }
            var path = dir.resolve(change + ".json");
            Files.writeString(path, tree.toString());
            assertThrows(
                    Exception.class,
                    () -> SixMaxExpandedFlopIntervalCollectionLoader.load(path, PIN),
                    change);
        }
        for (var source : manifest.sources())
            for (var name : List.of(source.requestFile(), source.reportFile()))
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxExpandedFlopIntervalCollectionLoader.distinctOutput(
                                        data("manifest"),
                                        data("manifest").getParent().resolve(name)));
        var alias = dir.resolve("report-alias.json");
        Files.createLink(alias, data("manifest").toAbsolutePath());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxExpandedFlopIntervalCollectionLoader.distinctOutput(
                                data("manifest"), alias));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxExpandedFlopIntervalCollectionLoader.load(
                                data("manifest"), "0".repeat(64)));
    }

    @Test
    void rehashedInitialSourceStillRequiresItsOriginalPhysicalProof(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = SixMaxExpandedFlopIntervalCollectionLoader.readManifest(data("manifest"));
        for (var source : manifest.sources())
            for (var name : List.of(source.requestFile(), source.reportFile()))
                Files.copy(data("manifest").getParent().resolve(name), dir.resolve(name));
        var source = manifest.sources().getFirst();
        var report = SixMaxSuppliedFlopFeedback.readReport(dir.resolve(source.reportFile()));
        var tree = mapper.valueToTree(report);
        ((ObjectNode) tree.get("context")).put("potBb", 100);
        var changed = dir.resolve("changed-report.json");
        Files.writeString(changed, tree.toString());
        var changedPin =
                SixMaxHeadsUpPreflopGame.hash(SixMaxSuppliedFlopFeedback.readReport(changed));
        var pin =
                SixMaxExpandedFlopIntervalCollection.identity(
                        changedPin,
                        manifest.sources().get(1).reportHash(),
                        manifest.sources().get(2).reportHash(),
                        manifest.sources().get(3).reportHash());
        var sources = new ArrayList<>(manifest.sources());
        sources.set(
                0,
                new SixMaxExpandedFlopIntervalCollectionLoader.Source(
                        source.kind(),
                        source.requestFile(),
                        changed.getFileName().toString(),
                        changedPin));
        var input = dir.resolve("manifest.json");
        Files.writeString(
                input,
                SixMaxTextureStudy.json(
                        new SixMaxExpandedFlopIntervalCollectionLoader.Manifest(
                                manifest.schemaVersion(), pin, sources, manifest.startupBudget())));
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxExpandedFlopIntervalCollectionLoader.load(input, pin));
        assertTrue(
                failure.getMessage().contains("Physical flop feedback replay differs"),
                failure.getMessage());
    }

    @Test
    void cliRejectsInvalidMenusAndAllSourceOutputAliasesBeforeReplay() throws Exception {
        for (var args :
                List.of(
                        new String[] {
                            "create", data("manifest").toString(), PIN, data("manifest").toString()
                        },
                        new String[] {
                            "create", data("manifest").toString(), PIN, "new.json", "1:bet"
                        },
                        new String[] {
                            "create", data("manifest").toString(), PIN, "new.json", "10:check"
                        }))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxExpandedFlopIntervalCollectionMain.main(args));
    }

    @Test
    void receiptBindsAllFourIndependentSourceChecksAndReviewBytes() throws Exception {
        var r =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(Files.readAllBytes(data("independent-check")));
        assertEquals(99, r.get("originalEndpointCertificatesChecked").asInt());
        assertEquals(30, r.get("sourcePolicyContextChecks").asInt());
        assertEquals(20, r.get("attemptsChecked").asInt());
        assertFalse(r.get("trainerAdmission").asBoolean());
        assertEquals(4, r.get("sourceReceipts").size());
        assertEquals(sha(data("review")), r.get("reviewStoredBytesSha256").asText());
        assertEquals(sha(data("manifest")), r.get("manifestStoredBytesSha256").asText());
        assertEquals(
                sha(Path.of("../scripts/check-expanded-flop-interval-collection.py")),
                r.get("checkerSourceSha256").asText());
    }

    @Test
    void incompatibleConnectedModelsRejectBeforeOwnedReplay() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = SixMaxExpandedFlopIntervalCollectionLoader.readManifest(data("manifest"));
        var root = data("manifest").getParent();
        var a =
                SixMaxSuppliedFlopFeedback.readRequest(
                        root.resolve(manifest.sources().get(0).requestFile()));
        var b =
                SixMaxLaterFlopFeedback.readRequest(
                        root.resolve(manifest.sources().get(1).requestFile()));
        var c =
                SixMaxBtnFlopCurriculum.readRequest(
                        root.resolve(manifest.sources().get(2).requestFile()));
        var d =
                SixMaxPairedFlopCurriculum.readRequest(
                        root.resolve(manifest.sources().get(3).requestFile()));
        var changed = mapper.valueToTree(c);
        ((ObjectNode) changed.get("connected")).put("betPotFraction", .25);
        var other = mapper.treeToValue(changed, SixMaxBtnFlopCurriculum.Request.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxExpandedFlopIntervalCollection.compatible(a, b, other, d));
    }

    @Test
    void storedExpandedAndStrictJsonCapsApplyToTheNewProtocol(@TempDir Path dir) throws Exception {
        var path = dir.resolve("review.json");
        Files.writeString(path, " ".repeat(SixMaxExpandedFlopIntervalCollection.MAX_BYTES + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxExpandedFlopIntervalCollection.read(path));
        var zip = dir.resolve("review.json.gz");
        try (var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(zip))) {
            out.write(Files.readAllBytes(path));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxExpandedFlopIntervalCollection.read(zip));
        for (var raw : List.of("{} {}", "{\"index\":0,\"index\":1}", "null")) {
            Files.writeString(path, raw);
            assertThrows(
                    Exception.class,
                    () -> collection.replay(SixMaxExpandedFlopIntervalCollection.read(path)));
        }
        Files.writeString(
                path,
                " ".repeat(SixMaxExpandedFlopIntervalCollectionLoader.MAX_MANIFEST_BYTES + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxExpandedFlopIntervalCollectionLoader.load(path, PIN));
    }

    static String sha(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(path)));
    }
}
