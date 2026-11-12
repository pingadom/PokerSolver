package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BankRaiseSequenceStudyTest {
    static final Path DATA = Path.of("../docs/data");
    static final String REPORT = "supplied-bank-raise-sequence-report.json.gz";
    static SixMaxRootHandBankRaiseStudy.Report saved, owned;
    @TempDir Path temp;

    @BeforeAll
    static void replayOneCompleteOwnedPhysicalBatch() throws Exception {
        byte[] bytes = Files.readAllBytes(DATA.resolve(REPORT));
        assertEquals(
                "e01215a5da6ee3ffe3ea4e427c6777ad211881bdbd7ef970c3abb1a9d74df034",
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        saved = SixMaxRootHandBankRaiseStudy.readReport(DATA.resolve(REPORT));
        owned =
                SixMaxRootHandBankRaiseStudy.replay(
                                DATA.resolve("supplied-bank-raise-sequence-request.json"),
                                DATA.resolve("supplied-root-hand-bank-report.json.gz"),
                                DATA.resolve(REPORT))
                        .report();
        assertEquals(saved, owned);
    }

    @Test
    void completeSharedPhysicalBatchReproducesBothHandMinima() {
        assertTrue(owned.completeMinima());
        assertTrue(owned.referenceSupportedMinima());
        assertNull(owned.rejection());
        assertTrue(owned.rejectionReasons().isEmpty());
        assertEquals(
                List.of(0, 2),
                owned.comparisons().stream()
                        .map(SixMaxRootHandBankRaiseStudy.Comparison::sourceQuestionIndex)
                        .toList());
        assertEquals(-.4222958, owned.batch().minima().get(0).minimum().lowerBound(), 1e-6);
        assertEquals(-3.4421594, owned.batch().minima().get(1).minimum().lowerBound(), 1e-6);
    }

    @Test
    void neverClaimsFullActionIntervalsOrTrainerAuthority() {
        assertFalse(owned.trainerAdmission());
        assertFalse(owned.batch().trainerAdmission());
        assertEquals(FiniteTwoPlayerRootSequenceMinimax.SCOPE, owned.minimumScope());
        assertTrue(
                owned.batch().minima().stream()
                        .noneMatch(FiniteTwoPlayerRootSequenceMinimax.Audit::trainerAdmission));
        assertEquals(
                1,
                Arrays.stream(SixMaxRootHandBankRaiseStudy.Result.class.getDeclaredConstructors())
                        .count());
        assertTrue(
                java.lang.reflect.Modifier.isPrivate(
                        SixMaxRootHandBankRaiseStudy.Result.class.getDeclaredConstructors()[0]
                                .getModifiers()));
    }

    @Test
    void retainsAllOriginalSourceHandsAndFreshReferenceIdentities() {
        assertEquals(List.of(0, 1, 2), owned.sourceBank().supportedQuestionIndices());
        assertEquals(owned.sourceBank().parent(), owned.parent());
        assertEquals(6, owned.policySupports().size());
        assertEquals(
                List.of(1, 80000, 160000, 1, 80000, 160000),
                owned.policySupports().stream()
                        .map(SixMaxRootHandBankRaiseStudy.PolicySupport::iterations)
                        .toList());
        assertTrue(
                owned.policySupports().stream()
                        .allMatch(SixMaxRootHandBankRaiseStudy.PolicySupport::supported));
    }

    @Test
    void treatsMinimumBracketAsMinimumRatherThanUpperBoundForEveryPolicy() {
        boolean above = false;
        for (int i = 0; i < 2; i++)
            for (var p : owned.sourceBank().assessments().get(List.of(0, 2).get(i)).policies())
                above |=
                        p.actionUtilitiesBb().get("raise:9.0")
                                > owned.batch().minima().get(i).minimum().upperBound() + .1;
        assertTrue(above);
        assertTrue(owned.referenceSupportedMinima());
    }

    @Test
    void preservesHandReachAndAllOriginalRootStateIdentities() {
        for (var a : owned.batch().minima()) {
            assertEquals(1. / 3, a.originalQuestionReach(), 1e-8);
            assertEquals(8, a.support().size());
            assertEquals(
                    1.,
                    a.support().stream()
                            .mapToDouble(FiniteTwoPlayerRootMinimax.Support::posteriorProbability)
                            .sum(),
                    1e-8);
        }
        assertNotEquals(
                owned.batch().minima().get(0).support(), owned.batch().minima().get(1).support());
    }

    @Test
    void usesOneParentAndOriginalCumulativeBudgetsWithoutPlanEnumeration() {
        var w = owned.batch().work();
        assertEquals(1, w.parentSolveCalls());
        assertEquals(2, w.parentLpSolves());
        assertEquals(2, w.endpointLpSolves());
        assertEquals(0, w.enumeratedContinuationPlans());
        assertEquals(8000000, w.compilerLimit());
        assertEquals(10000, w.endpointPivotLimit());
        assertEquals(4000000000L, w.endpointArithmeticLimit());
        long used = w.preflightCompilerUnits();
        for (var a : owned.batch().minima()) {
            assertEquals(8000000 - used, a.work().compilerLimit());
            used += a.work().compilerUnits();
        }
        assertEquals(w.compilerUnits(), used);
    }

    private void corrupt(java.util.function.Consumer<ObjectNode> change) {
        ObjectNode node = SixMaxTexturePayoffTable.mapper().valueToTree(saved);
        change.accept(node);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(node, SixMaxRootHandBankRaiseStudy.Report.class));
    }

    @Test
    void rejectsMissingOrReorderedHandEndpoints() {
        corrupt(n -> n.withObject("/batch").withArray("minima").remove(0));
        corrupt(
                n -> {
                    var a = n.withObject("/batch").withArray("minima");
                    var x = a.get(0);
                    a.set(0, a.get(1));
                    a.set(1, x);
                });
    }

    @Test
    void rejectsChangedSourceParentAndSourceBank() {
        corrupt(n -> n.withObject("/parent/solve").put("snapshotHash", "wrong"));
        corrupt(n -> n.withObject("/sourceBank").put("trainerAdmission", true));
    }

    @Test
    void rejectsChangedHandSupportAndQuestion() {
        corrupt(n -> ((ObjectNode) n.at("/batch/minima/0/support/0")).put("stateHash", "wrong"));
        corrupt(n -> ((ObjectNode) n.at("/batch/minima/0/question")).put("action", "call"));
    }

    @Test
    void rejectsResetOrIncreasedWorkCounters() {
        for (String field :
                List.of(
                        "compilerLimit",
                        "endpointPivotLimit",
                        "endpointArithmeticLimit",
                        "parentSolveCalls",
                        "endpointLpSolves"))
            corrupt(n -> n.withObject("/batch/work").put(field, 0));
        corrupt(n -> ((ObjectNode) n.at("/batch/minima/1/work")).put("compilerLimit", 8000000));
    }

    @Test
    void rejectsFabricatedComparisonsAndPolicySupport() {
        corrupt(n -> ((ObjectNode) n.at("/comparisons/0")).put("lowerBoundImprovementBb", 42));
        corrupt(n -> ((ObjectNode) n.at("/policySupports/0")).put("minimumLowerViolationBb", 1));
        corrupt(n -> n.put("referenceSupportedMinima", false));
    }

    @Test
    void rejectsIncompleteSuccessAndAdmission() {
        corrupt(n -> n.putNull("batch"));
        corrupt(n -> n.put("completeMinima", false));
        corrupt(n -> n.put("trainerAdmission", true));
    }

    @Test
    void preservesTypedNumericalRejectionWithoutPartialSuccess() throws Exception {
        ObjectNode n = SixMaxTexturePayoffTable.mapper().valueToTree(saved);
        n.putNull("batch");
        n.put("completeMinima", false);
        n.put("referenceSupportedMinima", false);
        n.set("comparisons", n.arrayNode());
        n.set("policySupports", n.arrayNode());
        n.set(
                "rejection",
                n.objectNode()
                        .put("kind", "LP")
                        .put("reason", "PIVOT_LIMIT")
                        .put("detail", "declared limit"));
        n.set("rejectionReasons", n.arrayNode().add("BATCH:LP:PIVOT_LIMIT"));
        var rejected =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(n, SixMaxRootHandBankRaiseStudy.Report.class);
        assertFalse(rejected.completeMinima());
        assertFalse(rejected.referenceSupportedMinima());
        assertEquals(0, rejected.comparisons().size());
    }

    @Test
    void requestRejectsChangedPinsHandsActionsAndCoercions() throws Exception {
        var request = SixMaxTexturePayoffTable.mapper().valueToTree(saved.request());
        for (String field :
                List.of("schemaVersion", "bankStoredSha256", "bankStudyHash", "action")) {
            var bad = ((ObjectNode) request).deepCopy();
            bad.put(field, "wrong");
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxTexturePayoffTable.mapper()
                                    .treeToValue(bad, SixMaxRootHandBankRaiseStudy.Request.class));
        }
        var bad = ((ObjectNode) request).deepCopy();
        bad.withArray("heroCards").remove(0);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(bad, SixMaxRootHandBankRaiseStudy.Request.class));
        Path file = temp.resolve("duplicate.json");
        Files.writeString(file, "{\"schemaVersion\":\"a\",\"schemaVersion\":\"b\"}");
        assertThrows(Exception.class, () -> SixMaxRootHandBankRaiseStudy.readRequest(file));
    }

    @Test
    void rejectsChangedSourceBytesBeforeNumericalWork() throws Exception {
        Path bad = temp.resolve("bank.gz");
        Files.write(bad, new byte[] {1, 2, 3});
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseStudy.solve(saved.request(), bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseStudy.solve(saved.request(), temp));
    }

    @Test
    void cliRefusesExistingOutputBeforeAnySolve() throws Exception {
        Path existing = temp.resolve("existing.gz");
        byte[] sentinel = {1, 2, 3};
        Files.write(existing, sentinel);
        assertThrows(
                FileAlreadyExistsException.class,
                () ->
                        SixMaxRootHandBankRaiseMain.main(
                                new String[] {
                                    "solve",
                                    DATA.resolve("supplied-bank-raise-sequence-request.json")
                                            .toString(),
                                    DATA.resolve("supplied-root-hand-bank-report.json.gz")
                                            .toString(),
                                    existing.toString()
                                }));
        assertArrayEquals(sentinel, Files.readAllBytes(existing));
    }

    @Test
    void reportAndReplayRejectMalformedOrMismatchedRequests() throws Exception {
        Path bad = temp.resolve("bad.gz");
        Files.write(bad, new byte[] {1, 2, 3});
        assertThrows(Exception.class, () -> SixMaxRootHandBankRaiseStudy.readReport(bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankRaiseMain.main(new String[] {"no"}));
    }
}
