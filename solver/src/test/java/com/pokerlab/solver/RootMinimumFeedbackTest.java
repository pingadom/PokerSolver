package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RootMinimumFeedbackTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-root-minimum-feedback-request.json");
    static final Path ORIGINAL =
            Path.of("../docs/data/supplied-root-envelope-feedback-report.json.gz");
    static final Path MINIMUM = Path.of("../docs/data/supplied-root-minimum-report.json.gz");
    static final Path REPORT =
            Path.of("../docs/data/supplied-root-minimum-feedback-report.json.gz");
    static SixMaxRootMinimumFeedback.Result owned;

    @BeforeAll
    static void oneReservedCompletePhysicalReplay() throws Exception {
        owned = SixMaxRootMinimumFeedback.replay(REQUEST, ORIGINAL, MINIMUM, REPORT);
    }

    @Test
    void completeSavedReportAndBothPinnedSourcesReproduceExactly() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        assertEquals(
                owned.report(),
                m.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(REPORT, 8 * 1024 * 1024),
                        SixMaxRootMinimumFeedback.Report.class));
        assertEquals(
                SixMaxRootMinimumFeedback.readMinimum(MINIMUM), owned.report().sourceMinimum());
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
        assertEquals(.21114181882276112, moves.getLast().lowerEvBb(), 1e-12);
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
        assertEquals(SixMaxRootMinimumFeedback.SCOPE, r.boundsScope());
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
        var rejected = SixMaxRootMinimumFeedback.assess(primary, bad);
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
                new SixMaxRootMinimumStudy.PolicyAssessment(
                        outside,
                        p.forcedActionUtilityBb(),
                        null,
                        false,
                        List.of("FULL_PARENT_GAP", "REFERENCE_OUTSIDE_DECLARED_FACE"));
        var check = SixMaxRootMinimumFeedback.assess(control, owned.report().moves());
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
                        SixMaxRootMinimumFeedback.Result.class,
                        SixMaxRootMinimumFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var parsed =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxTextureStudy.json(owned.report()),
                                SixMaxRootMinimumFeedback.Report.class);
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
                    () -> m.treeToValue(n, SixMaxRootMinimumFeedback.Report.class));
        }
        for (String field : List.of("schemaVersion", "publicationStatus", "boundsScope")) {
            var n = (ObjectNode) m.valueToTree(owned.report());
            n.put(field, "wrong");
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootMinimumFeedback.Report.class));
        }
        var n = (ObjectNode) m.valueToTree(owned.report());
        ((ObjectNode) n.withArray("moves").get(2)).put("upperLossBb", 0.);
        assertThrows(
                Exception.class, () -> m.treeToValue(n, SixMaxRootMinimumFeedback.Report.class));
        var p = (ObjectNode) m.valueToTree(owned.report());
        p.withArray("policies").remove(1);
        assertThrows(
                Exception.class, () -> m.treeToValue(p, SixMaxRootMinimumFeedback.Report.class));
    }

    @Test
    void alteredOrOversizedMinimumSourcesRejectBeforePhysicalWork(@TempDir Path temp)
            throws Exception {
        var path = temp.resolve("minimum.gz");
        byte[] bytes = Files.readAllBytes(MINIMUM);
        bytes[bytes.length - 1] ^= 1;
        Files.write(path, bytes);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootMinimumFeedback.readMinimum(path));
        Files.write(path, new byte[8 * 1024 * 1024 + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootMinimumFeedback.readMinimum(path));
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootMinimumFeedback.readMinimum(temp));
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
                    () -> m.readValue(bad, SixMaxRootMinimumFeedback.Request.class));
        for (String key : List.of("schemaVersion", "minimumStoredSha256")) {
            var n = (ObjectNode) m.valueToTree(owned.report().request());
            n.put(key, "wrong");
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootMinimumFeedback.Request.class));
        }
        var n = (ObjectNode) m.valueToTree(owned.report().request());
        n.put("maximumLossBb", .02);
        assertThrows(
                Exception.class, () -> m.treeToValue(n, SixMaxRootMinimumFeedback.Request.class));
    }

    @Test
    void outputsCannotOverwriteAliasOrStartWithInvalidCommands(@TempDir Path temp)
            throws Exception {
        var keep = temp.resolve("keep.json");
        Files.writeString(keep, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootMinimumFeedbackMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    ORIGINAL.toString(),
                                    MINIMUM.toString(),
                                    keep.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootMinimumFeedbackMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    ORIGINAL.toString(),
                                    MINIMUM.toString(),
                                    MINIMUM.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootMinimumFeedbackMain.main(new String[] {"anything"}));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRootMinimumFeedback.write(keep, owned));
        assertEquals("keep", Files.readString(keep));
        var copy = temp.resolve("copy.gz");
        SixMaxRootMinimumFeedback.write(copy, owned);
        assertEquals(
                owned.report(),
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(copy, 8 * 1024 * 1024),
                                SixMaxRootMinimumFeedback.Report.class));
    }

    @Test
    void allFeedbackCollectionsRemainImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> owned.report().moves().clear());
        assertThrows(UnsupportedOperationException.class, () -> owned.report().policies().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().rejectionReasons().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().policies().getFirst().failures().clear());
    }
}
