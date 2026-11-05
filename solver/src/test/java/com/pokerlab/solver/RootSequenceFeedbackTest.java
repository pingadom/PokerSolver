package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RootSequenceFeedbackTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-root-sequence-feedback-request.json");
    static final Path ORIGINAL =
            Path.of("../docs/data/supplied-root-envelope-feedback-report.json.gz");
    static final Path LEGACY = Path.of("../docs/data/supplied-root-minimum-report.json.gz");
    static final Path MINIMUM =
            Path.of("../docs/data/supplied-root-sequence-minimum-report.json.gz");
    static final Path REPORT =
            Path.of("../docs/data/supplied-root-sequence-feedback-report.json.gz");
    static SixMaxRootSequenceFeedback.Result owned;

    @BeforeAll
    static void oneReservedCompletePhysicalReplay() throws Exception {
        owned = SixMaxRootSequenceFeedback.replay(REQUEST, ORIGINAL, LEGACY, MINIMUM, REPORT);
    }

    @Test
    void completeSavedReportAndAllPinnedSourcesReproduceExactly() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        assertEquals(
                owned.report(),
                m.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(REPORT, 8 * 1024 * 1024),
                        SixMaxRootSequenceFeedback.Report.class));
        assertEquals(
                SixMaxRootSequenceFeedback.readMinimum(MINIMUM), owned.report().sourceMinimum());
        assertEquals(
                SixMaxRootMinimumStudy.readSource(ORIGINAL), owned.report().originalEnvelope());
        assertEquals(
                owned.report().sourceMinimum().parent(),
                owned.report().originalEnvelope().envelope().parent());
    }

    @Test
    void raiseLowerBoundAndLossImproveWithEveryFullActionUpperRetained() {
        var r = owned.report();
        var moves = r.moves();
        var old = r.originalEnvelope().moves();
        assertEquals(
                List.of("fold", "call", "raise:9.0"), moves.stream().map(m -> m.action()).toList());
        assertEquals(old.subList(0, 2), moves.subList(0, 2));
        for (int i = 0; i < 3; i++) assertEquals(old.get(i).upperEvBb(), moves.get(i).upperEvBb());
        assertEquals(.211141818822754, moves.getLast().lowerEvBb(), 1e-12);
        assertEquals(.574637464148319, moves.getLast().upperLossBb(), 1e-12);
        assertTrue(old.getLast().upperLossBb() > 7.);
    }

    @Test
    void completeMenuLossBoundsAreDerivedFromEveryMove() {
        for (var move : owned.report().moves()) {
            double lo = 0, hi = 0;
            for (var other : owned.report().moves()) {
                lo = Math.max(lo, other.lowerEvBb() - move.upperEvBb());
                hi = Math.max(hi, other.upperEvBb() - move.lowerEvBb());
            }
            assertEquals(lo, move.lowerLossBb());
            assertEquals(hi, move.upperLossBb());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.classify(lo, hi, .01), move.classification());
        }
    }

    @Test
    void terminalProfitUnitsAndStrategyDependentRaiseRemainExplicit() {
        var r = owned.report();
        assertEquals(SixMaxRootSequenceFeedback.SCOPE, r.boundsScope());
        assertEquals(-1.00000001, r.moves().getFirst().lowerEvBb());
        assertEquals(
                List.of(
                        SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT),
                r.moves().stream().map(m -> m.classification()).toList());
        assertFalse(r.trainerAdmission());
    }

    @Test
    void primaryAndFreshReferencesPassNewMenuAndUnchangedQualityGates() {
        var r = owned.report();
        assertTrue(r.qualifiedForOfflineDrill());
        assertTrue(r.rejectionReasons().isEmpty());
        assertEquals(
                List.of(1, 80000, 160000), r.policies().stream().map(p -> p.iterations()).toList());
        for (int i = 0; i < 3; i++) {
            var p = r.policies().get(i);
            var source = r.sourceMinimum().policies().get(i);
            assertEquals(source.original().solutionHash(), p.solutionHash());
            assertTrue(p.boundsChecked());
            assertTrue(p.failures().isEmpty());
            assertTrue(p.maximumBoundViolationBb() <= 1e-8);
            assertTrue(source.minimumLowerSupported());
            assertTrue(source.original().fullParentQuality().nashConvBb() < .001);
            assertTrue(source.original().publicRootNashConvBb() < .001);
            assertTrue(source.original().decisionRegretBb() < .01);
            assertTrue(source.original().actualQuestionReach() >= .0001);
        }
    }

    @Test
    void minimumUpperBracketCannotBecomeAFullActionCeiling() {
        var r = owned.report();
        var primary = r.sourceMinimum().policies().getFirst();
        double minimumUpper = r.sourceMinimum().minimum().minimum().upperBound();
        assertTrue(primary.forcedActionUtilityBb() > minimumUpper + .5);
        assertTrue(r.moves().getLast().upperEvBb() > minimumUpper + .5);
        var bad = new ArrayList<>(r.moves());
        var raise = bad.getLast();
        bad.set(
                2,
                new SixMaxSuppliedIntervalFeedback.Move(
                        raise.action(),
                        raise.lowerEvBb(),
                        minimumUpper,
                        raise.lowerLossBb(),
                        raise.upperLossBb(),
                        raise.classification()));
        var rejected = SixMaxRootSequenceFeedback.assess(primary, bad);
        assertTrue(rejected.failures().contains("REFERENCE_OUTSIDE_MINIMUM_MENU_BOUNDS"));
        assertTrue(rejected.maximumBoundViolationBb() > .5);
    }

    @Test
    void outsideFaceAndPriorQualityFailuresStayUnqualifiedWithoutMoreSolves() throws Exception {
        var p = owned.report().sourceMinimum().policies().getFirst();
        var m = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) m.valueToTree(p.original());
        n.put("insideDeclaredFace", false);
        var outside = m.treeToValue(n, RootEnvelopePolicyChecks.Check.class);
        var control =
                new SixMaxRootSequenceMinimumStudy.PolicyAssessment(
                        outside,
                        p.forcedActionUtilityBb(),
                        null,
                        false,
                        List.of("FULL_PARENT_GAP", "REFERENCE_OUTSIDE_DECLARED_FACE"));
        var check = SixMaxRootSequenceFeedback.assess(control, owned.report().moves());
        assertFalse(check.boundsChecked());
        assertNull(check.maximumBoundViolationBb());
        assertTrue(
                check.failures()
                        .containsAll(
                                List.of(
                                        "FULL_PARENT_GAP",
                                        "REFERENCE_OUTSIDE_DECLARED_FACE",
                                        "MINIMUM_NOT_SUPPORTED",
                                        "MINIMUM_MENU_REFERENCE_OUTSIDE_FACE")));
    }

    @Test
    void qualificationObjectBindsWholeOwnedReportAndCannotBeParsedOrConstructed() throws Exception {
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(owned.report()),
                owned.qualifiedFeedback().orElseThrow().reportHash());
        for (var type :
                List.of(
                        SixMaxRootSequenceFeedback.Result.class,
                        SixMaxRootSequenceFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var parsed =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxTextureStudy.json(owned.report()),
                                SixMaxRootSequenceFeedback.Report.class);
        assertEquals(owned.report(), parsed);
        assertFalse(parsed.trainerAdmission());
    }

    @Test
    void forgedBoundsPoliciesScopeAndQualificationFlagsReject() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        for (String field : List.of("trainerAdmission", "qualifiedForOfflineDrill")) {
            var n = (ObjectNode) m.valueToTree(owned.report());
            n.put(field, !n.get(field).booleanValue());
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootSequenceFeedback.Report.class));
        }
        for (String field : List.of("schemaVersion", "publicationStatus", "boundsScope")) {
            var n = (ObjectNode) m.valueToTree(owned.report());
            n.put(field, "wrong");
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootSequenceFeedback.Report.class));
        }
        var n = (ObjectNode) m.valueToTree(owned.report());
        ((ObjectNode) n.withArray("moves").get(2)).put("upperLossBb", 0.);
        assertThrows(
                Exception.class, () -> m.treeToValue(n, SixMaxRootSequenceFeedback.Report.class));
        var p = (ObjectNode) m.valueToTree(owned.report());
        p.withArray("policies").remove(1);
        assertThrows(
                Exception.class, () -> m.treeToValue(p, SixMaxRootSequenceFeedback.Report.class));
    }

    @Test
    void alteredOrOversizedMinimumSourcesRejectBeforePhysicalWork(@TempDir Path temp)
            throws Exception {
        var path = temp.resolve("minimum.gz");
        byte[] bytes = Files.readAllBytes(MINIMUM);
        bytes[bytes.length - 1] ^= 1;
        Files.write(path, bytes);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootSequenceFeedback.readMinimum(path));
        Files.write(path, new byte[8 * 1024 * 1024 + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootSequenceFeedback.readMinimum(path));
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootSequenceFeedback.readMinimum(temp));
    }

    @Test
    void strictRequestJsonAndOriginalThresholdRejectChanges() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        String text = Files.readString(REQUEST);
        for (String bad :
                List.of(
                        text + " {}",
                        text.replaceFirst("\\{", "{\"extra\":0,"),
                        text.replaceFirst("\\{", "{\"schemaVersion\":\"duplicate\",")))
            assertThrows(
                    Exception.class,
                    () -> m.readValue(bad, SixMaxRootSequenceFeedback.Request.class));
        for (String key : List.of("schemaVersion", "minimumStoredSha256")) {
            var n = (ObjectNode) m.valueToTree(owned.report().request());
            n.put(key, "wrong");
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootSequenceFeedback.Request.class));
        }
        var n = (ObjectNode) m.valueToTree(owned.report().request());
        n.put("maximumLossBb", .02);
        assertThrows(
                Exception.class, () -> m.treeToValue(n, SixMaxRootSequenceFeedback.Request.class));
    }

    @Test
    void outputsCannotOverwriteAliasOrStartWithInvalidCommands(@TempDir Path temp)
            throws Exception {
        var keep = temp.resolve("keep.json");
        Files.writeString(keep, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceFeedbackMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    ORIGINAL.toString(),
                                    LEGACY.toString(),
                                    MINIMUM.toString(),
                                    keep.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceFeedbackMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    ORIGINAL.toString(),
                                    LEGACY.toString(),
                                    MINIMUM.toString(),
                                    MINIMUM.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceFeedbackMain.main(new String[] {"anything"}));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRootSequenceFeedback.write(keep, owned));
        assertEquals("keep", Files.readString(keep));
        var copy = temp.resolve("copy.gz");
        SixMaxRootSequenceFeedback.write(copy, owned);
        assertEquals(
                owned.report(),
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(copy, 8 * 1024 * 1024),
                                SixMaxRootSequenceFeedback.Report.class));
    }

    @Test
    void parsedReportsWrappedReportsAndForgedHashesCannotMintCapabilities() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        String text = SixMaxTextureStudy.json(owned.report());
        for (String input :
                new String[] {
                    text,
                    "{\"report\":" + text + "}",
                    "{\"reportHash\":\"" + SixMaxHeadsUpPreflopGame.hash(owned.report()) + "\"}"
                }) {
            assertThrows(
                    Exception.class,
                    () -> mapper.readValue(input, SixMaxRootSequenceFeedback.Result.class));
            assertThrows(
                    Exception.class,
                    () ->
                            mapper.readValue(
                                    input, SixMaxRootSequenceFeedback.QualifiedFeedback.class));
        }
    }

    @Test
    void failedSequenceMinimumCannotProduceFeedbackOrAnOfflineCapability() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) m.valueToTree(owned.report().sourceMinimum());
        for (String field :
                List.of(
                        "minimum",
                        "previousConservativeLowerUtilityBb",
                        "lowerBoundDifferenceBb",
                        "legacyMinimumWitnessDifferenceBb",
                        "legacyMinimumBracketsOverlap")) n.putNull(field);
        n.put("completeMinimum", false);
        n.put("referenceSupportedMinimum", false);
        n.set(
                "rejection",
                m.valueToTree(
                        new SixMaxRootSequenceMinimumStudy.Failure(
                                "LP", "WORK_LIMIT", "protocol control only")));
        n.putArray("rejectionReasons").add("MINIMUM:LP:WORK_LIMIT");
        for (var p : n.withArray("policies")) {
            ((ObjectNode) p).putNull("minimumLowerViolationBb");
            ((ObjectNode) p).put("minimumLowerSupported", false);
        }
        var failed = m.treeToValue(n, SixMaxRootSequenceMinimumStudy.Report.class);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceFeedback.moves(
                                failed, owned.report().originalEnvelope(), .01));
        var report = (ObjectNode) m.valueToTree(owned.report());
        report.set("sourceMinimum", m.valueToTree(failed));
        assertThrows(
                Exception.class,
                () -> m.treeToValue(report, SixMaxRootSequenceFeedback.Report.class));
    }

    @Test
    void allFeedbackCollectionsRemainImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().legacyMenuComparison().clear());
        assertThrows(UnsupportedOperationException.class, () -> owned.report().moves().clear());
        assertThrows(UnsupportedOperationException.class, () -> owned.report().policies().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().rejectionReasons().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().policies().getFirst().failures().clear());
    }

    @Test
    void legacyComparisonRetainsEverySmallDifferenceWithoutAnotherLegacySolve() {
        var r = owned.report();
        var old =
                SixMaxRootMinimumFeedback.moves(
                        r.sourceMinimum().legacyMinimum(), r.originalEnvelope(), .01);
        assertEquals(3, r.legacyMenuComparison().size());
        for (int i = 0; i < 3; i++) {
            var before = old.get(i);
            var after = r.moves().get(i);
            var comparison = r.legacyMenuComparison().get(i);
            assertEquals(before, comparison.legacyMove());
            assertEquals(after.action(), comparison.action());
            assertEquals(after.lowerEvBb() - before.lowerEvBb(), comparison.lowerEvDifferenceBb());
            assertEquals(after.upperEvBb() - before.upperEvBb(), comparison.upperEvDifferenceBb());
            assertEquals(
                    after.lowerLossBb() - before.lowerLossBb(), comparison.lowerLossDifferenceBb());
            assertEquals(
                    after.upperLossBb() - before.upperLossBb(), comparison.upperLossDifferenceBb());
            assertTrue(comparison.classificationUnchanged());
        }
        assertNotEquals(0., r.legacyMenuComparison().getLast().lowerEvDifferenceBb());
        assertTrue(Math.abs(r.legacyMenuComparison().getLast().lowerEvDifferenceBb()) < 1e-12);
        assertEquals(0, r.sourceMinimum().minimum().work().enumeratedContinuationPlans());
        assertEquals(1, r.sourceMinimum().minimum().work().endpointLpSolves());
    }

    @Test
    void erasedTinyComparisonChangesAndWrongOriginalMenuReject() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) m.valueToTree(owned.report());
        ((ObjectNode) n.withArray("legacyMenuComparison").get(2)).put("lowerEvDifferenceBb", 0.);
        assertThrows(
                Exception.class, () -> m.treeToValue(n, SixMaxRootSequenceFeedback.Report.class));
        var changed = (ObjectNode) m.valueToTree(owned.report());
        changed.withArray("legacyMenuComparison").remove(1);
        assertThrows(
                Exception.class,
                () -> m.treeToValue(changed, SixMaxRootSequenceFeedback.Report.class));
        var missing = (ObjectNode) m.valueToTree(owned.report());
        missing.withArray("moves").remove(1);
        assertThrows(
                Exception.class,
                () -> m.treeToValue(missing, SixMaxRootSequenceFeedback.Report.class));
    }

    @Test
    void genuineHardLinkAndWrongModeRejectBeforePhysicalWork(@TempDir Path temp) throws Exception {
        var source = temp.resolve("source.json");
        Files.writeString(source, "same local file");
        var alias = Files.createLink(temp.resolve("alias.json"), source);
        assertTrue(Files.isSameFile(source, alias));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxRootSequenceFeedbackMain.main(
                                        new String[] {
                                            "solve",
                                            source.toString(),
                                            alias.toString(),
                                            LEGACY.toString(),
                                            MINIMUM.toString(),
                                            temp.resolve("new.json").toString()
                                        }));
        assertTrue(error.getMessage().contains("distinct"));
        assertFalse(Files.exists(temp.resolve("new.json")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceFeedbackMain.main(
                                new String[] {
                                    "invalid",
                                    REQUEST.toString(),
                                    ORIGINAL.toString(),
                                    LEGACY.toString(),
                                    MINIMUM.toString(),
                                    temp.resolve("new.json").toString()
                                }));
    }

    @Test
    void originalAndLegacySourcesCannotBeSubstitutedBeforePhysicalWork(@TempDir Path temp)
            throws Exception {
        var request = owned.report().request();
        var invalid = temp.resolve("bad.gz");
        Files.writeString(invalid, "invalid pinned evidence");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceFeedback.solve(request, invalid, LEGACY, MINIMUM));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceFeedback.solve(request, ORIGINAL, invalid, MINIMUM));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceFeedback.solve(request, ORIGINAL, LEGACY, invalid));
    }
}
