package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RootEnvelopeFeedbackTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-root-envelope-feedback-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-root-envelope-feedback-report.json.gz");
    static SixMaxRootEnvelopeFeedback.Request request;
    static SixMaxSuppliedFlopSizeMenuGame game;
    static SixMaxRootEnvelopeFeedback.Result owned;
    static FiniteTwoPlayerSequenceForm.Checked checked;

    @BeforeAll
    static void oneDeclaredPhysicalReplay() throws Exception {
        request = SixMaxRootEnvelopeFeedback.readRequest(REQUEST);
        var p = request.envelope().parent();
        game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        owned = SixMaxRootEnvelopeFeedback.solve(request, game);
        checked = FiniteTwoPlayerSequenceForm.checked(game);
    }

    @Test
    void fullQualificationAndOriginalEnvelopeReproduceExactly() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(REPORT, 8 * 1024 * 1024),
                        SixMaxRootEnvelopeFeedback.Report.class);
        assertEquals(saved, owned.report());
        var old =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of("../docs/data/supplied-root-envelope-report.json.gz"),
                                8 * 1024 * 1024),
                        SixMaxRootEnvelopeStudy.Report.class);
        assertEquals(old, owned.report().envelope());
        assertEquals(5, old.envelope().work().lpSolves());
        assertEquals(471304900, old.envelope().work().lpArithmeticWork());
    }

    @Test
    void ownedCapabilityBindsCompleteReportWhilePublicAdmissionRemainsFalse() throws Exception {
        assertTrue(owned.report().qualifiedForOfflineDrill());
        assertTrue(owned.report().rejectionReasons().isEmpty());
        assertFalse(owned.report().trainerAdmission());
        assertFalse(owned.report().envelope().trainerAdmission());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(owned.report()),
                owned.qualifiedFeedback().orElseThrow().reportHash());
        for (var type :
                List.of(
                        SixMaxRootEnvelopeFeedback.Result.class,
                        SixMaxRootEnvelopeFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void allThreeMovesRetainAmbiguityInsteadOfInventingExactRaiseValue() {
        var moves = owned.report().moves();
        assertEquals(
                List.of("fold", "call", "raise:9.0"), moves.stream().map(m -> m.action()).toList());
        assertEquals(
                List.of(
                        SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT),
                moves.stream().map(m -> m.classification()).toList());
        assertTrue(moves.getFirst().lowerLossBb() > 1.78);
        assertTrue(moves.get(1).upperLossBb() < 1e-6);
        assertEquals(0, moves.getLast().lowerLossBb());
        assertTrue(moves.getLast().upperLossBb() > 7);
    }

    @Test
    void primaryAndBothFreshPoliciesPassWholeMenuAndOriginalPublicRootGates() {
        var r = owned.report();
        var all = new ArrayList<>(List.of(r.primary()));
        all.addAll(r.references());
        assertEquals(List.of(1, 80000, 160000), all.stream().map(c -> c.iterations()).toList());
        for (var c : all) {
            assertEquals(RootEnvelopePolicyChecks.PUBLIC_SCOPE, c.publicRootQualityScope());
            assertEquals(1, c.publicRootReach());
            assertEquals(c.fullParentQuality().nashConvBb(), c.publicRootNashConvBb());
            assertEquals(1. / 3, c.actualQuestionReach(), 1e-15);
            assertEquals(c.actualQuestionReach(), c.counterfactualQuestionReach());
            assertTrue(c.insideDeclaredFace());
            assertTrue(c.boundsChecked());
            assertTrue(c.failures().isEmpty());
            assertEquals(3, c.actionUtilitiesBb().size());
            assertEquals(3, c.actionLossBb().size());
            assertTrue(c.decisionRegretBb() < .01);
            assertTrue(c.maximumBoundViolationBb() <= 1e-8);
        }
        assertEquals("BB", r.envelope().context().actingSeat().toString());
        assertEquals(4.5, r.envelope().context().potBb());
        assertEquals(2, r.envelope().context().toCallBb());
        assertEquals("", r.envelope().context().publicHistory());
    }

    @Test
    void predeterminedFoldAndUniformOpponentControlsFailWithoutMoreSolves() throws Exception {
        var source = owned.report().envelope();
        var original = source.parent().candidate();
        var rows = new LinkedHashMap<>(original.strategy());
        var key = source.envelope().question().informationSet();
        var bad = new LinkedHashMap<String, Double>();
        rows.get(key).keySet().forEach(a -> bad.put(a, a.equals("fold") ? 1. : 0.));
        rows.put(key, bad);
        var folded =
                RootEnvelopePolicyChecks.assess(
                        checked, source.envelope(), new CfrSolution(1, rows), .01);
        assertTrue(folded.failures().contains("SELECTED_DECISION_REGRET"));
        assertTrue(folded.decisionRegretBb() > 1.78);
        var uniform = new LinkedHashMap<>(original.strategy());
        original.strategy()
                .forEach(
                        (k, row) -> {
                            if (k.startsWith("3:")) {
                                var replacement = new LinkedHashMap<String, Double>();
                                row.keySet().forEach(a -> replacement.put(a, 1. / row.size()));
                                uniform.put(k, replacement);
                            }
                        });
        var outside =
                RootEnvelopePolicyChecks.assess(
                        checked, source.envelope(), new CfrSolution(1, uniform), .01);
        assertTrue(outside.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        assertFalse(outside.boundsChecked());
        assertNull(outside.maximumBoundViolationBb());
    }

    @Test
    void parsedReportCannotBorrowQualificationIdentityOrAlterWholeMenu() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field : List.of("trainerAdmission", "qualifiedForOfflineDrill")) {
            var n = (ObjectNode) mapper.valueToTree(owned.report());
            n.put(field, !n.get(field).booleanValue());
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(n, SixMaxRootEnvelopeFeedback.Report.class));
        }
        var n = (ObjectNode) mapper.valueToTree(owned.report());
        ((ObjectNode) n.withArray("moves").get(2)).put("classification", "WITHIN_LIMIT");
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(n, SixMaxRootEnvelopeFeedback.Report.class));
        var dropped = (ObjectNode) mapper.valueToTree(owned.report());
        dropped.withArray("references").remove(0);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(dropped, SixMaxRootEnvelopeFeedback.Report.class));
        String text = Files.readString(REQUEST);
        for (String invalid : List.of(text + " {}", text.replaceFirst("\\{", "{\"extra\":0,")))
            assertThrows(
                    Exception.class,
                    () -> mapper.readValue(invalid, SixMaxRootEnvelopeFeedback.Request.class));
    }

    @Test
    void incompleteSourceProtocolRetainsFailureAndCannotClaimQualification() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) mapper.valueToTree(owned.report().envelope());
        n.put("completeOuterBounds", false);
        n.putNull("envelope");
        var failure = n.putObject("rejection");
        failure.put("kind", "LP");
        failure.put("reason", "WORK_LIMIT");
        failure.put("detail", "protocol control only");
        var incomplete = mapper.treeToValue(n, SixMaxRootEnvelopeStudy.Report.class);
        var report =
                new SixMaxRootEnvelopeFeedback.Report(
                        SixMaxRootEnvelopeFeedback.REPORT_SCHEMA,
                        SixMaxRootEnvelopeFeedback.STATUS,
                        false,
                        request,
                        incomplete,
                        List.of(),
                        null,
                        List.of(),
                        false,
                        List.of("INCOMPLETE_ROOT_ENVELOPE"));
        assertFalse(report.qualifiedForOfflineDrill());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRootEnvelopeFeedback.Report(
                                SixMaxRootEnvelopeFeedback.REPORT_SCHEMA,
                                SixMaxRootEnvelopeFeedback.STATUS,
                                false,
                                request,
                                incomplete,
                                List.of(),
                                null,
                                List.of(),
                                true,
                                List.of()));
    }

    @Test
    void qualificationEvidenceAndActionMapsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> owned.report().moves().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> owned.report().references().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().primary().actionLossBb().clear());
    }

    @Test
    void commandOutputsCannotOverwriteOrAliasInputs(@TempDir Path temp) throws Exception {
        Path keep = temp.resolve("keep.json");
        Files.writeString(keep, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootEnvelopeFeedbackMain.main(
                                new String[] {"solve", REQUEST.toString(), keep.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootEnvelopeFeedbackMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertEquals("keep", Files.readString(keep));
    }
}
