package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RootSequenceBatchStudyTest {
    static final Path DATA = Path.of("../docs/data");
    static final Path REQUEST = DATA.resolve("supplied-root-sequence-batch-request.json");
    static final Path SOURCE = DATA.resolve("supplied-root-envelope-feedback-report.json.gz");
    static final Path PREVIOUS = DATA.resolve("supplied-root-sequence-minimum-report.json.gz");
    static final Path REPORT = DATA.resolve("supplied-root-sequence-batch-report.json.gz");
    static SixMaxRootSequenceBatchStudy.Result owned;
    static SixMaxRootEnvelopeFeedback.Report source;

    @BeforeAll
    static void oneDeclaredPhysicalReplay() throws Exception {
        source = SixMaxRootMinimumStudy.readSource(SOURCE);
        owned = SixMaxRootSequenceBatchStudy.replay(REQUEST, SOURCE, PREVIOUS, REPORT);
    }

    void reject(Consumer<ObjectNode> mutate) {
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode json = mapper.valueToTree(owned.report());
        mutate.accept(json);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(json, SixMaxRootSequenceBatchStudy.Report.class));
    }

    @Test
    void completePhysicalReportReproducesSavedBatchAndOriginalParent() throws Exception {
        var r = owned.report();
        assertEquals(SixMaxRootSequenceBatchStudy.readReport(REPORT), r);
        assertEquals(source.envelope().parent(), r.parent());
        assertEquals(source.envelope().envelope(), r.originalEnvelope());
        assertEquals(source.envelope().context(), r.context());
        assertTrue(r.completeMinima());
        assertTrue(r.referenceSupportedMinima());
        assertNull(r.rejection());
        assertTrue(r.rejectionReasons().isEmpty());
    }

    @Test
    void originalCompleteActionMenuSharesOneOwnedParentAndCumulativeBudget() {
        var r = owned.report();
        var b = r.batch();
        var w = b.work();
        assertEquals(
                SixMaxRootSequenceBatchStudy.ACTIONS,
                b.minima().stream().map(a -> a.question().action()).toList());
        assertEquals(1, w.parentSolveCalls());
        assertEquals(2, w.parentLpSolves());
        assertEquals(3, w.endpointLpSolves());
        assertEquals(0, w.enumeratedContinuationPlans());
        assertEquals(8000000, w.compilerLimit());
        assertEquals(10000, w.endpointPivotLimit());
        assertEquals(4000000000L, w.endpointArithmeticLimit());
        long compiler = 3L * r.parent().solve().treeNodes(), arithmetic = 0;
        int pivots = 0;
        assertEquals(compiler, w.preflightCompilerUnits());
        for (var a : b.minima()) {
            assertEquals(r.parent().solve(), a.baseline());
            assertEquals(r.parent().firstLpWitness(), a.firstParentWitness());
            assertEquals(r.parent().secondLpWitness(), a.secondParentWitness());
            assertEquals(8000000 - compiler, a.work().compilerLimit());
            assertEquals(.0001, a.requiredMinimumReach());
            assertEquals(1e-8, a.securitySlack());
            assertEquals(8, a.support().size());
            assertEquals(1. / 3, a.originalQuestionReach(), 1e-15);
            compiler += a.work().compilerUnits();
            arithmetic += a.work().endpointArithmeticWork();
            pivots += a.work().endpointPivots();
        }
        assertEquals(compiler, w.compilerUnits());
        assertEquals(arithmetic, w.endpointArithmeticWork());
        assertEquals(pivots, w.endpointPivots());
        assertTrue(compiler <= 8000000 && arithmetic <= 4000000000L && pivots <= 10000);
    }

    @Test
    void priorRaiseAuditIsIdenticalExceptAdvertisedRemainingCompilerLimit() {
        var r = owned.report();
        var raise = r.batch().minima().getLast();
        assertTrue(SixMaxRootSequenceBatchStudy.sameEndpoint(raise, r.previousMinimum().minimum()));
        assertTrue(r.comparisons().getLast().previousRaiseBracketsOverlap());
        assertTrue(r.comparisons().getLast().exactPreviousRaiseExceptRemainingCompilerLimit());
        assertEquals(.211141818822754, raise.minimum().lowerBound(), 1e-12);
    }

    @Test
    void allThreeActionsRetainOriginalConservativeComparisons() {
        var r = owned.report();
        for (int i = 0; i < 3; i++) {
            var a = r.batch().minima().get(i);
            var c = r.comparisons().get(i);
            var old =
                    r.originalEnvelope().actions().stream()
                            .filter(x -> x.action().equals(c.action()))
                            .findFirst()
                            .orElseThrow();
            assertEquals(old.lowerUtility(), c.originalLowerUtilityBb());
            assertEquals(old.upperUtility(), c.fullActionUpperUtilityBb());
            assertEquals(a.minimum().lowerBound() - old.lowerUtility(), c.lowerBoundDifferenceBb());
            assertTrue(a.minimum().lowerBound() <= a.minimum().upperBound());
            if (i < 2) {
                assertNull(c.previousRaiseBracketsOverlap());
                assertNull(c.exactPreviousRaiseExceptRemainingCompilerLimit());
            }
        }
        assertEquals(-1., r.batch().minima().getFirst().minimum().witnessUtility(), 1e-8);
    }

    @Test
    void primaryAndBothFreshReferencesPassAllNineLowerBoundChecks() {
        var expected = new ArrayList<>(List.of(source.primary()));
        expected.addAll(source.references());
        assertEquals(
                List.of(1, 80000, 160000),
                owned.report().policies().stream().map(p -> p.original().iterations()).toList());
        for (int i = 0; i < 3; i++) {
            var p = owned.report().policies().get(i);
            assertEquals(expected.get(i), p.original());
            assertTrue(p.allMinimumLowerBoundsSupported());
            assertTrue(p.failures().isEmpty());
            assertEquals(
                    Set.copyOf(SixMaxRootSequenceBatchStudy.ACTIONS),
                    p.minimumLowerViolationsBb().keySet());
            assertTrue(p.minimumLowerViolationsBb().values().stream().allMatch(v -> v == 0.));
            assertTrue(p.original().insideDeclaredFace());
            assertTrue(p.original().decisionRegretBb() < .01);
        }
    }

    @Test
    void minimumUpperBracketDoesNotBecomeFullActionCeilingOrTrainerAdmission() {
        var r = owned.report();
        var p = r.policies().getFirst();
        assertTrue(
                p.original().actionUtilitiesBb().get("raise:9.0")
                        > r.batch().minima().getLast().minimum().upperBound() + .5);
        assertTrue(p.allMinimumLowerBoundsSupported());
        assertFalse(r.trainerAdmission());
        assertFalse(r.batch().trainerAdmission());
        assertEquals(FiniteTwoPlayerRootSequenceMinimax.SCOPE, r.minimumScope());
        for (var a : r.batch().minima()) assertFalse(a.trainerAdmission());
    }

    @Test
    void reportCannotCreateOwnedHandleAndPublishedCollectionsAreImmutable() throws Exception {
        assertTrue(
                Arrays.stream(SixMaxRootSequenceBatchStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var m = SixMaxTexturePayoffTable.mapper();
        assertThrows(
                Exception.class,
                () ->
                        m.treeToValue(
                                m.valueToTree(owned.report()),
                                SixMaxRootSequenceBatchStudy.Result.class));
        assertThrows(
                UnsupportedOperationException.class, () -> owned.report().comparisons().clear());
        assertThrows(UnsupportedOperationException.class, () -> owned.report().policies().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().policies().getFirst().minimumLowerViolationsBb().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().request().actions().clear());
    }

    @Test
    void requestJsonPinsAndOriginalActionOrderRejectBeforePhysicalWork() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        String text = Files.readString(REQUEST);
        for (String bad :
                List.of(
                        text + " {}",
                        text.replaceFirst("\\{", "{\"extra\":0,"),
                        text.replaceFirst("\\{", "{\"schemaVersion\":\"duplicate\",")))
            assertThrows(
                    Exception.class,
                    () -> m.readValue(bad, SixMaxRootSequenceBatchStudy.Request.class));
        for (String field :
                List.of("schemaVersion", "sourceReportHash", "previousMinimumStoredSha256")) {
            ObjectNode q = m.valueToTree(owned.report().request());
            q.put(field, "wrong");
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(q, SixMaxRootSequenceBatchStudy.Request.class));
        }
        ObjectNode q = m.valueToTree(owned.report().request());
        q.putArray("actions").add("raise:9.0").add("fold").add("call");
        assertThrows(
                Exception.class,
                () -> m.treeToValue(q, SixMaxRootSequenceBatchStudy.Request.class));
    }

    @Test
    void changedSourcePriorAndPublishedBytesRejectBeforePhysicalWork(@TempDir Path temp)
            throws Exception {
        Path bad = temp.resolve("bad.gz");
        byte[] bytes = Files.readAllBytes(SOURCE);
        bytes[bytes.length - 1] ^= 1;
        Files.write(bad, bytes);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchStudy.solve(owned.report().request(), bad, PREVIOUS));
        bytes = Files.readAllBytes(PREVIOUS);
        bytes[bytes.length - 1] ^= 1;
        Files.write(bad, bytes);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchStudy.solve(owned.report().request(), SOURCE, bad));
        bytes = Files.readAllBytes(REPORT);
        bytes[bytes.length - 1] ^= 1;
        Files.write(bad, bytes);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchStudy.replay(REQUEST, SOURCE, PREVIOUS, bad));
        Files.write(bad, new byte[SixMaxRootSequenceBatchStudy.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootSequenceBatchStudy.readReport(bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchStudy.solve(owned.report().request(), temp, PREVIOUS));
    }

    @Test
    void commandsRejectOverwriteAliasesAndInvalidArgumentsBeforeReplay(@TempDir Path temp)
            throws Exception {
        Path keep = temp.resolve("keep.gz");
        Files.writeString(keep, "keep");
        String[] args = {
            "solve", REQUEST.toString(), SOURCE.toString(), PREVIOUS.toString(), keep.toString()
        };
        assertThrows(
                FileAlreadyExistsException.class, () -> SixMaxRootSequenceBatchMain.main(args));
        args[4] = REQUEST.toString();
        assertThrows(IllegalArgumentException.class, () -> SixMaxRootSequenceBatchMain.main(args));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchMain.main(new String[] {"anything"}));
        Path priorCopy = Files.copy(PREVIOUS, temp.resolve("prior.gz"));
        Path alias = temp.resolve("alias.gz");
        Files.createLink(alias, priorCopy);
        assertTrue(Files.isSameFile(alias, priorCopy));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceBatchStudy.solve(
                                owned.report().request(), priorCopy, alias));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRootSequenceBatchStudy.write(keep, owned));
        assertEquals("keep", Files.readString(keep));
        Path copy = temp.resolve("copy.gz");
        SixMaxRootSequenceBatchStudy.write(copy, owned);
        assertEquals(owned.report(), SixMaxRootSequenceBatchStudy.readReport(copy));
    }

    @Test
    void forgedScopeAdmissionAndSourceLineageCannotPassReportValidation() {
        for (String key : List.of("schemaVersion", "publicationStatus", "minimumScope"))
            reject(n -> n.put(key, "wrong"));
        for (String key : List.of("trainerAdmission", "completeMinima", "referenceSupportedMinima"))
            reject(n -> n.put(key, !n.get(key).booleanValue()));
        reject(n -> n.withArray("policies").remove(1));
        reject(n -> ((ObjectNode) n.get("previousMinimum")).put("completeMinimum", false));
    }

    @Test
    void forgedWorkMenuComparisonsAndReferenceSupportAreRejected() {
        reject(n -> ((ObjectNode) n.get("batch").get("work")).put("parentLpSolves", 6));
        reject(n -> ((ObjectNode) n.get("batch").get("work")).put("preflightCompilerUnits", 0));
        reject(n -> ((ObjectNode) n.get("batch").get("work")).put("compilerLimit", 8000001));
        reject(
                n ->
                        ((ObjectNode) n.get("batch").get("minima").get(1).get("work"))
                                .put("compilerLimit", 8000000));
        reject(
                n ->
                        ((ObjectNode) n.withArray("comparisons").get(0))
                                .put("lowerBoundDifferenceBb", 42.));
        reject(
                n ->
                        ((ObjectNode) n.withArray("comparisons").get(2))
                                .put("exactPreviousRaiseExceptRemainingCompilerLimit", false));
        reject(
                n ->
                        ((ObjectNode)
                                        n.withArray("policies")
                                                .get(0)
                                                .get("minimumLowerViolationsBb"))
                                .put("call", 1.));
    }

    @Test
    void typedFailureRetainsOriginalEvidenceWithoutPartialMinimaOrSupport() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        ObjectNode n = m.valueToTree(owned.report());
        n.putNull("batch");
        n.putArray("comparisons");
        n.put("completeMinima", false);
        n.put("referenceSupportedMinima", false);
        var failure = n.putObject("rejection");
        failure.put("kind", "LP");
        failure.put("reason", "WORK_LIMIT");
        failure.put("detail", "protocol control only");
        n.putArray("rejectionReasons").add("BATCH:LP:WORK_LIMIT");
        for (var policy : n.withArray("policies")) {
            var p = (ObjectNode) policy;
            p.putObject("minimumLowerViolationsBb");
            p.put("allMinimumLowerBoundsSupported", false);
        }
        var failed = m.treeToValue(n, SixMaxRootSequenceBatchStudy.Report.class);
        assertFalse(
                failed.completeMinima()
                        || failed.referenceSupportedMinima()
                        || failed.trainerAdmission());
        assertNull(failed.batch());
        assertEquals(3, failed.policies().size());
        n.put("completeMinima", true);
        assertThrows(
                Exception.class, () -> m.treeToValue(n, SixMaxRootSequenceBatchStudy.Report.class));
    }
}
