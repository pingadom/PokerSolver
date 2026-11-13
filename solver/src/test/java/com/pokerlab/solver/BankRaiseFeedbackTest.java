package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class BankRaiseFeedbackTest {
    static final Path DATA = Path.of("../docs/data");
    static SixMaxRootHandBankRaiseFeedback.Report saved;
    @TempDir Path temp;

    @BeforeAll
    static void readFrozenReportWithoutSolving() throws Exception {
        Path report = DATA.resolve("supplied-bank-raise-feedback-report.json.gz");
        assertEquals(
                "7077bd260e422690e0060a31d36dea53e545c8ee88641ef8e5e811633d9723e2",
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(report))));
        saved = SixMaxRootHandBankRaiseFeedback.readReport(report);
    }

    void reject(java.util.function.Consumer<ObjectNode> change) {
        ObjectNode n = SixMaxTexturePayoffTable.mapper().valueToTree(saved);
        change.accept(n);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(n, SixMaxRootHandBankRaiseFeedback.Report.class));
    }

    @Test
    void onlyRaisesGainALowerBoundAndAllOriginalFullActionCeilingsStay() {
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 3; j++) {
                var a = saved.assessments().get(i);
                var b = a.lowerBoundSources().get(j);
                var old =
                        saved.sourceMinimum()
                                .sourceBank()
                                .assessments()
                                .get(a.sourceQuestionIndex())
                                .moves()
                                .get(j);
                assertEquals(old.lowerEvBb(), b.originalLowerBb());
                assertEquals(old.upperEvBb(), b.originalFullActionUpperBb());
                assertEquals(old.upperEvBb(), a.moves().get(j).upperEvBb());
                if (j == 2)
                    assertEquals(
                            saved.sourceMinimum().batch().minima().get(i).minimum().lowerBound(),
                            b.selectedLowerBb());
                else {
                    assertNull(b.sequenceMinimumLowerBb());
                    assertEquals(old.lowerEvBb(), b.selectedLowerBb());
                }
            }
    }

    @Test
    void tighterRaiseLossBoundsKeepAllThreeClassificationsUnchanged() {
        assertEquals(
                1.1078040536882738, saved.assessments().get(0).moves().get(2).upperLossBb(), 1e-12);
        assertEquals(
                3.595333327470523, saved.assessments().get(1).moves().get(2).upperLossBb(), 1e-12);
        for (var a : saved.assessments())
            assertEquals(
                    List.of(
                            SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                            SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                            SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT),
                    a.moves().stream()
                            .map(SixMaxSuppliedIntervalFeedback.Move::classification)
                            .toList());
    }

    @Test
    void allSixOriginalPoliciesRetainWholeFaceAndSeventyTwoUtilityLossChecks() {
        assertEquals(
                72,
                saved.assessments().stream()
                        .flatMap(a -> a.policies().stream())
                        .mapToInt(
                                SixMaxRootHandBankRaiseFeedback.PolicyCheck
                                        ::utilityLossInequalitiesChecked)
                        .sum());
        for (var a : saved.assessments())
            for (var p : a.policies()) {
                assertTrue(p.sourceMinimumSupported());
                assertTrue(p.boundsChecked());
                assertEquals(1, p.minimumLowerChecks());
                assertTrue(p.maximumBoundViolationBb() <= 1e-8);
                assertTrue(p.failures().isEmpty());
            }
    }

    @Test
    void minimumUpperBracketsAreNeverUsedAsFullActionCeilings() {
        for (int i = 0; i < 2; i++)
            assertTrue(
                    saved.assessments().get(i).moves().get(2).upperEvBb()
                            > saved.sourceMinimum().batch().minima().get(i).minimum().upperBound()
                                    + 1);
        reject(
                n ->
                        ((ObjectNode) n.at("/assessments/0/moves/2"))
                                .put("upperEvBb", -.4222957865556651));
    }

    @Test
    void completePreviousMenuComparisonRetainsEveryChange() {
        for (var a : saved.assessments())
            for (int j = 0; j < 3; j++) {
                var c = a.previousMenuComparison().get(j);
                var m = a.moves().get(j);
                assertTrue(c.classificationUnchanged());
                assertEquals(0., c.upperEvDifferenceBb());
                assertEquals(
                        m.upperLossBb() - c.previousMove().upperLossBb(),
                        c.upperLossDifferenceBb());
                if (j == 2)
                    assertTrue(c.lowerEvDifferenceBb() > 0 && c.upperLossDifferenceBb() < 0);
            }
    }

    @Test
    void requestRejectsChangedPinsThresholdCoercionsDuplicateAndUnknownFields() throws Exception {
        ObjectNode q = SixMaxTexturePayoffTable.mapper().valueToTree(saved.request());
        for (String k : List.of("schemaVersion", "minimumStoredSha256")) {
            var bad = q.deepCopy();
            bad.put(k, "wrong");
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxTexturePayoffTable.mapper()
                                    .treeToValue(
                                            bad, SixMaxRootHandBankRaiseFeedback.Request.class));
        }
        for (Object value : List.of(.1, "0.01", true)) {
            var bad = q.deepCopy();
            bad.set("maximumLossBb", SixMaxTexturePayoffTable.mapper().valueToTree(value));
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxTexturePayoffTable.mapper()
                                    .treeToValue(
                                            bad, SixMaxRootHandBankRaiseFeedback.Request.class));
        }
        Path file = temp.resolve("bad.json");
        Files.writeString(file, "{\"schemaVersion\":\"a\",\"schemaVersion\":\"b\"}");
        assertThrows(Exception.class, () -> SixMaxRootHandBankRaiseFeedback.readRequest(file));
        q.put("unknown", true);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(q, SixMaxRootHandBankRaiseFeedback.Request.class));
    }

    @Test
    void rejectsMissingReorderedAndBorrowedHandMenus() {
        reject(n -> n.withArray("assessments").remove(0));
        reject(
                n -> {
                    var a = n.withArray("assessments");
                    var x = a.get(0);
                    a.set(0, a.get(1));
                    a.set(1, x);
                });
        reject(n -> ((ObjectNode) n.at("/assessments/0")).put("sourceQuestionIndex", 2));
    }

    @Test
    void rejectsChangedSourceScopeAndModelIdentity() {
        reject(n -> n.withObject("/sourceMinimum").put("referenceSupportedMinima", false));
        reject(n -> n.put("boundsScope", "CURRENT_DECISION_EV"));
        reject(n -> ((ObjectNode) n.at("/assessments/0/context")).put("potBb", 99));
    }

    @Test
    void rejectsForgedUtilityLossAndClassifications() {
        reject(n -> ((ObjectNode) n.at("/assessments/0/moves/2")).put("upperLossBb", 0));
        reject(
                n ->
                        ((ObjectNode) n.at("/assessments/0/moves/2"))
                                .put("classification", "WITHIN_LIMIT"));
        reject(
                n ->
                        ((ObjectNode) n.at("/assessments/1/lowerBoundSources/0"))
                                .put("sequenceMinimumLowerBb", 0));
    }

    @Test
    void rejectsMissingPolicyAndFabricatedSupportCounters() {
        reject(n -> ((ObjectNode) n.at("/assessments/0")).withArray("policies").remove(0));
        reject(
                n ->
                        ((ObjectNode) n.at("/assessments/0/policies/0"))
                                .put("utilityLossInequalitiesChecked", 0));
        reject(
                n ->
                        ((ObjectNode) n.at("/assessments/0/policies/0"))
                                .put("maximumBoundViolationBb", 1));
    }

    @Test
    void reportCannotInventAdmissionQualificationOrMintACapability() {
        reject(n -> n.put("trainerAdmission", true));
        reject(n -> n.put("qualifiedForOfflineDrill", false));
        reject(n -> ((ObjectNode) n.at("/assessments/0")).put("qualifiedForOfflineDrill", false));
        for (var type :
                List.of(
                        SixMaxRootHandBankRaiseFeedback.Result.class,
                        SixMaxRootHandBankRaiseFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void outsideFaceControlsKeepZeroInequalityChecksAndExplicitFailure() throws Exception {
        var original = saved.sourceMinimum().sourceBank().assessments().get(0).policies().get(0);
        ObjectNode n = SixMaxTexturePayoffTable.mapper().valueToTree(original);
        n.put("insideDeclaredFace", false);
        var bad =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(n, RootEnvelopePolicyChecks.Check.class);
        var assessed =
                SixMaxRootHandBankRaiseFeedback.assess(
                        bad,
                        saved.sourceMinimum().policySupports().get(0),
                        saved.assessments().get(0).moves());
        assertFalse(assessed.boundsChecked());
        assertNull(assessed.maximumBoundViolationBb());
        assertEquals(0, assessed.utilityLossInequalitiesChecked());
        assertTrue(assessed.failures().contains("BANK_MENU_REFERENCE_OUTSIDE_FACE"));
    }

    @Test
    void unsupportedMinimumControlsRemainExplicitFailures() {
        var p = saved.sourceMinimum().policySupports().get(0);
        var bad =
                new SixMaxRootHandBankRaiseStudy.PolicySupport(
                        p.sourceQuestionIndex(),
                        p.iterations(),
                        p.solutionHash(),
                        1,
                        p.insideDeclaredFace(),
                        false);
        var checked =
                SixMaxRootHandBankRaiseFeedback.assess(
                        saved.sourceMinimum().sourceBank().assessments().get(0).policies().get(0),
                        bad,
                        saved.assessments().get(0).moves());
        assertFalse(checked.sourceMinimumSupported());
        assertTrue(checked.failures().contains("RAISE_MINIMUM_NOT_SUPPORTED"));
    }

    @Test
    void forgedPolicyUtilityViolationsCannotPassNewBounds() throws Exception {
        ObjectNode n =
                SixMaxTexturePayoffTable.mapper()
                        .valueToTree(
                                saved.sourceMinimum()
                                        .sourceBank()
                                        .assessments()
                                        .get(0)
                                        .policies()
                                        .get(0));
        n.withObject("/actionUtilitiesBb").put("raise:9.0", -10);
        var bad =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(n, RootEnvelopePolicyChecks.Check.class);
        var p =
                SixMaxRootHandBankRaiseFeedback.assess(
                        bad,
                        saved.sourceMinimum().policySupports().get(0),
                        saved.assessments().get(0).moves());
        assertTrue(p.maximumBoundViolationBb() > 1);
        assertTrue(p.failures().contains("REFERENCE_OUTSIDE_BANK_MENU_BOUNDS"));
    }

    @Test
    void invalidMinimumBytesRejectBeforeNumericalWork() throws Exception {
        Path bad = temp.resolve("bad.gz");
        Files.write(bad, new byte[] {1, 2, 3});
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootHandBankRaiseFeedback.solve(
                                saved.request(),
                                DATA.resolve("supplied-root-hand-bank-report.json.gz"),
                                bad));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootHandBankRaiseFeedback.solve(
                                saved.request(),
                                DATA.resolve("supplied-root-hand-bank-report.json.gz"),
                                temp));
    }

    @Test
    void cliRefusesExistingOutputsAndInvalidArguments() throws Exception {
        Path output = temp.resolve("existing.gz");
        byte[] sentinel = {1, 2, 3};
        Files.write(output, sentinel);
        assertThrows(
                FileAlreadyExistsException.class,
                () ->
                        SixMaxRootHandBankRaiseFeedbackMain.main(
                                new String[] {
                                    "solve",
                                    DATA.resolve("supplied-bank-raise-feedback-request.json")
                                            .toString(),
                                    DATA.resolve("supplied-root-hand-bank-report.json.gz")
                                            .toString(),
                                    DATA.resolve("supplied-bank-raise-sequence-report.json.gz")
                                            .toString(),
                                    output.toString()
                                }));
        assertArrayEquals(sentinel, Files.readAllBytes(output));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseFeedbackMain.main(new String[] {"invalid"}));
    }

    @Test
    void malformedAndOversizedReportsRejectWithoutSolving() throws Exception {
        Path bad = temp.resolve("bad.gz");
        Files.write(bad, new byte[] {1, 2, 3});
        assertThrows(Exception.class, () -> SixMaxRootHandBankRaiseFeedback.readReport(bad));
        try (var channel = Files.newByteChannel(bad, StandardOpenOption.WRITE)) {
            channel.position(SixMaxRootHandBankRaiseFeedback.MAX_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseFeedback.readReport(bad));
    }
}
