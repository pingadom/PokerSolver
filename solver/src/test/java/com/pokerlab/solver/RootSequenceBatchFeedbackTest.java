package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RootSequenceBatchFeedbackTest {
    static final Path DATA = Path.of("../docs/data");
    static final Path REQUEST = DATA.resolve("supplied-root-sequence-batch-feedback-request.json");
    static final Path ENVELOPE = DATA.resolve("supplied-root-envelope-feedback-report.json.gz");
    static final Path MINIMUM = DATA.resolve("supplied-root-sequence-minimum-report.json.gz");
    static final Path BATCH = DATA.resolve("supplied-root-sequence-batch-report.json.gz");
    static final Path PREVIOUS = DATA.resolve("supplied-root-sequence-feedback-report.json.gz");
    static final Path REPORT = DATA.resolve("supplied-root-sequence-batch-feedback-report.json.gz");
    static SixMaxRootSequenceBatchFeedback.Result owned;

    @BeforeAll
    static void oneDeclaredPhysicalReplay() throws Exception {
        owned =
                SixMaxRootSequenceBatchFeedback.replay(
                        REQUEST, ENVELOPE, MINIMUM, BATCH, PREVIOUS, REPORT);
    }

    void reject(Consumer<ObjectNode> change) {
        var m = SixMaxTexturePayoffTable.mapper();
        ObjectNode n = m.valueToTree(owned.report());
        change.accept(n);
        assertThrows(
                Exception.class,
                () -> m.treeToValue(n, SixMaxRootSequenceBatchFeedback.Report.class));
    }

    @Test
    void completeOwnedReplayIssuesOnlySourceBoundOfflineCapability() throws Exception {
        var r = owned.report();
        assertEquals(SixMaxRootSequenceBatchFeedback.readReport(REPORT), r);
        assertTrue(r.qualifiedForOfflineDrill());
        assertTrue(r.rejectionReasons().isEmpty());
        assertFalse(r.trainerAdmission());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(r),
                owned.qualifiedFeedback().orElseThrow().reportHash());
        assertEquals(
                SixMaxRootSequenceBatchFeedback.BATCH_CANONICAL,
                owned.qualifiedFeedback().orElseThrow().sourceBatchHash());
    }

    @Test
    void everyLowerBoundRetainsBothInputsAndUsesOriginalConservativeFloor() {
        var r = owned.report();
        for (int i = 0; i < 3; i++) {
            var b = r.lowerBoundSources().get(i);
            var original = r.previousFeedback().originalEnvelope().moves().get(i);
            assertEquals(original.lowerEvBb(), b.originalConservativeLowerBb());
            assertEquals(
                    r.sourceBatch().batch().minima().get(i).minimum().lowerBound(),
                    b.sequenceMinimumLowerBb());
            assertEquals(
                    Math.max(b.originalConservativeLowerBb(), b.sequenceMinimumLowerBb()),
                    b.selectedLowerBb());
            assertEquals(b.selectedLowerBb(), r.moves().get(i).lowerEvBb());
        }
        var call = r.lowerBoundSources().get(1);
        assertTrue(call.sequenceMinimumLowerBb() < call.originalConservativeLowerBb());
        assertEquals(call.originalConservativeLowerBb(), call.selectedLowerBb());
        assertEquals(
                -1.1102230246251565e-16,
                call.sequenceMinimumLowerBb() - call.originalConservativeLowerBb());
    }

    @Test
    void allFullActionCeilingsRemainOriginalAndMinimumUpperIsNotSubstituted() {
        var r = owned.report();
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    r.previousFeedback().originalEnvelope().moves().get(i).upperEvBb(),
                    r.moves().get(i).upperEvBb());
            assertEquals(
                    r.moves().get(i).upperEvBb(),
                    r.lowerBoundSources().get(i).originalFullActionUpperBb());
        }
        var raise = r.moves().getLast();
        assertTrue(
                raise.upperEvBb()
                        > r.sourceBatch().batch().minima().getLast().minimum().upperBound() + .5);
        assertTrue(
                r.sourceBatch()
                                .policies()
                                .getFirst()
                                .original()
                                .actionUtilitiesBb()
                                .get("raise:9.0")
                        > r.sourceBatch().batch().minima().getLast().minimum().upperBound() + .5);
    }

    @Test
    void crossActionLossBoundsAndClassificationsReproducePreviousMenu() {
        var moves = owned.report().moves();
        assertEquals(owned.report().previousFeedback().moves(), moves);
        assertEquals(
                List.of(
                        SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT),
                moves.stream().map(m -> m.classification()).toList());
        for (var m : moves) {
            double lo = 0, hi = 0;
            for (var other : moves) {
                lo = Math.max(lo, other.lowerEvBb() - m.upperEvBb());
                hi = Math.max(hi, other.upperEvBb() - m.lowerEvBb());
            }
            assertEquals(lo, m.lowerLossBb());
            assertEquals(hi, m.upperLossBb());
        }
        assertEquals(-1.00000001, moves.getFirst().lowerEvBb());
    }

    @Test
    void primaryAndBothFreshPoliciesPassAllThirtySixUtilityLossInequalities() {
        var r = owned.report();
        assertEquals(
                List.of(1, 80000, 160000), r.policies().stream().map(p -> p.iterations()).toList());
        for (int i = 0; i < 3; i++) {
            var p = r.policies().get(i);
            assertEquals(
                    r.sourceBatch().policies().get(i).original().solutionHash(), p.solutionHash());
            assertTrue(p.sourceMinimaSupported());
            assertEquals(3, p.minimumLowerChecks());
            assertEquals(12, p.utilityLossInequalitiesChecked());
            assertTrue(p.boundsChecked());
            assertEquals(0., p.maximumBoundViolationBb());
            assertTrue(p.failures().isEmpty());
        }
    }

    @Test
    void everyPreviousMenuDifferenceIsRetainedWithoutImprovementClaim() {
        var r = owned.report();
        assertEquals(3, r.previousMenuComparison().size());
        for (int i = 0; i < 3; i++) {
            var c = r.previousMenuComparison().get(i);
            assertEquals(r.previousFeedback().moves().get(i), c.previousMove());
            assertEquals(r.moves().get(i).action(), c.action());
            assertEquals(0., c.lowerEvDifferenceBb());
            assertEquals(0., c.upperEvDifferenceBb());
            assertEquals(0., c.lowerLossDifferenceBb());
            assertEquals(0., c.upperLossDifferenceBb());
            assertTrue(c.classificationUnchanged());
        }
    }

    @Test
    void originalCompletePhysicalParentReferencesAndCumulativeWorkArePreserved() throws Exception {
        var source = owned.report().sourceBatch();
        assertEquals(SixMaxRootSequenceBatchStudy.readReport(BATCH), source);
        assertEquals(owned.report().previousFeedback().sourceMinimum(), source.previousMinimum());
        assertEquals(
                owned.report().previousFeedback().originalEnvelope().envelope().parent(),
                source.parent());
        var w = source.batch().work();
        assertEquals(1, w.parentSolveCalls());
        assertEquals(2, w.parentLpSolves());
        assertEquals(3, w.endpointLpSolves());
        assertEquals(8000000, w.compilerLimit());
        assertEquals(10000, w.endpointPivotLimit());
        assertEquals(4000000000L, w.endpointArithmeticLimit());
        assertEquals(6126441, w.compilerUnits());
        assertEquals(1171142949L, w.endpointArithmeticWork());
        assertEquals(0, w.enumeratedContinuationPlans());
    }

    @Test
    void neitherParsedNorWrappedReportCanMintResultOrQualification() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        String report = SixMaxTextureStudy.json(owned.report());
        for (var type :
                List.of(
                        SixMaxRootSequenceBatchFeedback.Result.class,
                        SixMaxRootSequenceBatchFeedback.QualifiedFeedback.class)) {
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
            for (String json : List.of(report, "{\"report\":" + report + "}"))
                assertThrows(Exception.class, () -> mapper.readValue(json, type));
        }
    }

    @Test
    void publishedCollectionsAndQualificationInputsAreImmutable() {
        var r = owned.report();
        assertThrows(UnsupportedOperationException.class, () -> r.moves().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.policies().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.lowerBoundSources().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.previousMenuComparison().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.rejectionReasons().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.policies().getFirst().failures().clear());
    }

    @Test
    void strictRequestJsonPinsAndLossThresholdRejectBeforePhysicalWork() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        String text = Files.readString(REQUEST);
        for (String bad :
                List.of(
                        text + " {}",
                        text.replaceFirst("\\{", "{\"extra\":0,"),
                        text.replaceFirst("\\{", "{\"schemaVersion\":\"duplicate\",")))
            assertThrows(
                    Exception.class,
                    () -> mapper.readValue(bad, SixMaxRootSequenceBatchFeedback.Request.class));
        for (String field :
                List.of("schemaVersion", "batchStoredSha256", "previousFeedbackStoredSha256")) {
            ObjectNode n = mapper.valueToTree(owned.report().request());
            n.put(field, "wrong");
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(n, SixMaxRootSequenceBatchFeedback.Request.class));
        }
        ObjectNode n = mapper.valueToTree(owned.report().request());
        n.put("maximumLossBb", .0101);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(n, SixMaxRootSequenceBatchFeedback.Request.class));
    }

    @Test
    void changedBatchPreviousEnvelopeAndMinimumRejectBeforePhysicalReplay(@TempDir Path temp)
            throws Exception {
        Path bad = temp.resolve("bad.gz");
        var q = owned.report().request();
        for (Path path : List.of(BATCH, PREVIOUS, ENVELOPE, MINIMUM)) {
            byte[] bytes = Files.readAllBytes(path);
            bytes[bytes.length - 1] ^= 1;
            Files.write(bad, bytes);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxRootSequenceBatchFeedback.solve(
                                    q,
                                    path.equals(ENVELOPE) ? bad : ENVELOPE,
                                    path.equals(MINIMUM) ? bad : MINIMUM,
                                    path.equals(BATCH) ? bad : BATCH,
                                    path.equals(PREVIOUS) ? bad : PREVIOUS));
        }
    }

    @Test
    void alteredOversizedAndNonRegularPublishedEvidenceRejectBeforeReplay(@TempDir Path temp)
            throws Exception {
        Path bad = temp.resolve("bad.gz");
        byte[] bytes = Files.readAllBytes(REPORT);
        bytes[bytes.length - 1] ^= 1;
        Files.write(bad, bytes);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceBatchFeedback.replay(
                                REQUEST, ENVELOPE, MINIMUM, BATCH, PREVIOUS, bad));
        Files.write(bad, new byte[SixMaxRootSequenceBatchFeedback.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchFeedback.readReport(bad));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceBatchFeedback.solve(
                                owned.report().request(), ENVELOPE, MINIMUM, temp, PREVIOUS));
    }

    @Test
    void commandsCannotOverwriteAliasOrStartForInvalidArguments(@TempDir Path temp)
            throws Exception {
        Path keep = temp.resolve("keep.gz");
        Files.writeString(keep, "keep");
        String[] args = {
            "solve",
            REQUEST.toString(),
            ENVELOPE.toString(),
            MINIMUM.toString(),
            BATCH.toString(),
            PREVIOUS.toString(),
            keep.toString()
        };
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRootSequenceBatchFeedbackMain.main(args));
        args[6] = REQUEST.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchFeedbackMain.main(args));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchFeedbackMain.main(new String[] {"anything"}));
        Path copy = Files.copy(PREVIOUS, temp.resolve("previous.gz"));
        Path alias = temp.resolve("alias.gz");
        Files.createLink(alias, copy);
        assertTrue(Files.isSameFile(copy, alias));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceBatchFeedback.solve(
                                owned.report().request(), ENVELOPE, MINIMUM, copy, alias));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRootSequenceBatchFeedback.write(keep, owned));
        assertEquals("keep", Files.readString(keep));
        Path output = temp.resolve("copy.gz");
        SixMaxRootSequenceBatchFeedback.write(output, owned);
        assertEquals(owned.report(), SixMaxRootSequenceBatchFeedback.readReport(output));
    }

    @Test
    void forgedReportScopeSourcesAndQualificationClaimsReject() {
        for (String key :
                List.of("schemaVersion", "publicationStatus", "boundsScope", "qualificationScope"))
            reject(n -> n.put(key, "wrong"));
        reject(n -> n.put("trainerAdmission", true));
        reject(n -> n.put("qualifiedForOfflineDrill", false));
        reject(n -> ((ObjectNode) n.get("sourceBatch")).put("referenceSupportedMinima", false));
        reject(
                n ->
                        ((ObjectNode) n.get("previousFeedback"))
                                .put("qualifiedForOfflineDrill", false));
    }

    @Test
    void forgedBoundInputsMovesCountersAndPreviousComparisonsReject() {
        reject(
                n ->
                        ((ObjectNode) n.withArray("lowerBoundSources").get(1))
                                .put("sequenceMinimumLowerBb", 42.));
        reject(
                n ->
                        ((ObjectNode) n.withArray("lowerBoundSources").get(1))
                                .put("selectedLowerBb", 42.));
        reject(n -> ((ObjectNode) n.withArray("moves").get(2)).put("upperEvBb", .211141838822754));
        reject(
                n ->
                        ((ObjectNode) n.withArray("policies").get(0))
                                .put("utilityLossInequalitiesChecked", 0));
        reject(
                n ->
                        ((ObjectNode) n.withArray("previousMenuComparison").get(0))
                                .put("lowerEvDifferenceBb", 42.));
        reject(n -> n.withArray("policies").remove(1));
    }

    @Test
    void outsideFaceOrUnsupportedMinimaRetainsReasonsAndCannotQualify() throws Exception {
        var r = owned.report();
        var p = r.sourceBatch().policies().getFirst();
        var m = SixMaxTexturePayoffTable.mapper();
        ObjectNode node = m.valueToTree(p.original());
        node.put("insideDeclaredFace", false);
        var original = m.treeToValue(node, RootEnvelopePolicyChecks.Check.class);
        var unsupported =
                new SixMaxRootSequenceBatchStudy.PolicyAssessment(
                        original,
                        p.minimumLowerViolationsBb(),
                        false,
                        List.of("ORIGINAL_QUALITY_FAILURE"));
        var check = SixMaxRootSequenceBatchFeedback.assess(unsupported, r.moves());
        assertFalse(check.boundsChecked());
        assertEquals(0, check.utilityLossInequalitiesChecked());
        assertNull(check.maximumBoundViolationBb());
        assertTrue(
                check.failures()
                        .containsAll(
                                List.of(
                                        "BATCH_MINIMA_NOT_SUPPORTED",
                                        "BATCH_MENU_REFERENCE_OUTSIDE_FACE",
                                        "ORIGINAL_QUALITY_FAILURE")));
        assertFalse(
                SixMaxRootSequenceBatchFeedback.reasons(r.sourceBatch(), r.moves(), List.of(check))
                        .isEmpty());
    }

    @Test
    void eachUtilityAndLossInequalityCanIndependentlyRejectAPolicy() {
        var r = owned.report();
        var p = r.sourceBatch().policies().getFirst();
        var base = r.moves().getFirst();
        double value = p.original().actionUtilitiesBb().get(base.action()),
                loss = p.original().actionLossBb().get(base.action());
        for (int i = 0; i < 4; i++) {
            var changed =
                    new SixMaxSuppliedIntervalFeedback.Move(
                            base.action(),
                            i == 0 ? value + 1 : i == 1 ? value - 2 : base.lowerEvBb(),
                            i == 0 ? value + 2 : i == 1 ? value - 1 : base.upperEvBb(),
                            i == 2 ? loss + 1 : i == 3 ? 0 : base.lowerLossBb(),
                            i == 2 ? loss + 2 : i == 3 ? loss - 1 : base.upperLossBb(),
                            base.classification());
            var moves = new ArrayList<>(r.moves());
            moves.set(0, changed);
            var check = SixMaxRootSequenceBatchFeedback.assess(p, moves);
            assertTrue(check.maximumBoundViolationBb() > .9);
            assertTrue(check.failures().contains("REFERENCE_OUTSIDE_BATCH_MENU_BOUNDS"));
        }
    }

    @Test
    void incompleteReorderedAndNonfinitePureAssessmentInputsReject() throws Exception {
        var r = owned.report();
        var shortMenu = r.moves().subList(0, 2);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceBatchFeedback.assess(
                                r.sourceBatch().policies().getFirst(), shortMenu));
        var reversed = new ArrayList<>(r.lowerBoundSources());
        Collections.reverse(reversed);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchFeedback.moves(reversed, .01));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchFeedback.moves(r.lowerBoundSources(), .02));
        var p = r.sourceBatch().policies().getFirst();
        var m = SixMaxTexturePayoffTable.mapper();
        ObjectNode node = m.valueToTree(p.original());
        ((ObjectNode) node.get("actionUtilitiesBb")).put("fold", Double.NaN);
        var nonfinite = m.treeToValue(node, RootEnvelopePolicyChecks.Check.class);
        var assessment =
                new SixMaxRootSequenceBatchStudy.PolicyAssessment(
                        nonfinite, p.minimumLowerViolationsBb(), true, List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceBatchFeedback.assess(assessment, r.moves()));
    }
}
