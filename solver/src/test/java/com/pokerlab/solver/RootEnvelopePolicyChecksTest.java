package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.*;

class RootEnvelopePolicyChecksTest {
    static FiniteTwoPlayerSequenceForm.Snapshot game;
    static FiniteTwoPlayerSequenceForm.Checked checked;
    static FiniteTwoPlayerRootSecurityEnvelope.Audit audit;
    static CfrSolution policy;

    @BeforeAll
    static void analyticFiniteModelOnly() throws Exception {
        game = RootSecurityEnvelopeTest.model(0, 0, .5);
        checked = FiniteTwoPlayerSequenceForm.checked(game);
        audit =
                FiniteTwoPlayerRootSecurityEnvelope.solve(
                                game,
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0"))
                        .audit();
        policy =
                new CfrSolution(
                        1,
                        Map.of(
                                "0:type0",
                                Map.of("safe", 1., "risk", 0.),
                                "0:type1",
                                Map.of("safe", 1., "risk", 0.),
                                "1:opponent",
                                Map.of("x", 0., "y", 1.)));
    }

    static CfrSolution change(String key, Map<String, Double> row) {
        var rows = new LinkedHashMap<>(policy.strategy());
        rows.put(key, row);
        return new CfrSolution(1, rows);
    }

    static RootEnvelopePolicyChecks.Check assess(CfrSolution value) throws Exception {
        return RootEnvelopePolicyChecks.assess(checked, audit, value, .01);
    }

    static FiniteTwoPlayerRootSecurityEnvelope.Audit mutate(
            java.util.function.Consumer<ObjectNode> change) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) mapper.valueToTree(audit);
        change.accept(n);
        return mapper.treeToValue(n, FiniteTwoPlayerRootSecurityEnvelope.Audit.class);
    }

    @Test
    void feasiblePolicyChecksWholeGameAndAllForcedContinuations() throws Exception {
        var c = assess(policy);
        assertTrue(c.failures().isEmpty());
        assertTrue(c.insideDeclaredFace());
        assertTrue(c.boundsChecked());
        assertEquals(1, c.actionUtilitiesBb().get("safe"), 1e-12);
        assertEquals(0, c.actionUtilitiesBb().get("risk"), 1e-12);
        assertEquals(0, c.decisionRegretBb());
        assertEquals(1, c.actionLossBb().get("risk"), 1e-12);
        assertEquals(0, c.maximumBoundViolationBb(), 1e-8);
    }

    @Test
    void publicQualityRetainsHiddenTypesAndQuestionUsesOriginalChance() throws Exception {
        var c = assess(policy);
        assertEquals(RootEnvelopePolicyChecks.PUBLIC_SCOPE, c.publicRootQualityScope());
        assertEquals(1, c.publicRootReach());
        assertEquals(c.fullParentQuality().nashConvBb(), c.publicRootNashConvBb());
        assertEquals(.5, c.actualQuestionReach());
        assertEquals(.5, c.counterfactualQuestionReach());
        var actual = assess(change("0:type0", Map.of("safe", 0., "risk", 1.)));
        assertTrue(
                actual.failures()
                        .containsAll(
                                List.of(
                                        "FULL_PARENT_GAP",
                                        "PUBLIC_ROOT_GAP",
                                        "SELECTED_DECISION_REGRET")));
        assertEquals(.5, actual.fullParentQuality().nashConvBb(), 1e-12);
        assertEquals(1, actual.decisionRegretBb(), 1e-12);
    }

    @Test
    void outsideFaceCannotClaimCheckedBoundsEvenWhenActionsAreEvaluated() throws Exception {
        var c = assess(change("1:opponent", Map.of("x", 1., "y", 0.)));
        assertFalse(c.insideDeclaredFace());
        assertFalse(c.boundsChecked());
        assertNull(c.maximumBoundViolationBb());
        assertTrue(c.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        assertEquals(10, c.actionUtilitiesBb().get("risk"), 1e-12);
    }

    @Test
    void malformedMissingForeignAndNonfinitePolicyRowsReject() {
        for (var row :
                List.of(
                        Map.of("safe", .5, "risk", .4),
                        Map.of("safe", Double.NaN, "risk", 0.),
                        Map.of("safe", 1., "illegal", 0.)))
            assertThrows(IllegalArgumentException.class, () -> assess(change("0:type0", row)));
        var missing = new LinkedHashMap<>(policy.strategy());
        missing.remove("0:type1");
        assertThrows(IllegalArgumentException.class, () -> assess(new CfrSolution(1, missing)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assess(change("invented", Map.of("fold", 1.))));
    }

    @Test
    void differentGameUnsupportedQuestionAndMissingOriginalSupportReject() throws Exception {
        var different =
                FiniteTwoPlayerSequenceForm.checked(RootSecurityEnvelopeTest.model(0, 0, .25));
        assertThrows(
                IllegalArgumentException.class,
                () -> RootEnvelopePolicyChecks.assess(different, audit, policy, .01));
        var missing =
                mutate(n -> ((ObjectNode) n.get("question")).put("informationSet", "0:absent"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RootEnvelopePolicyChecks.assess(checked, missing, policy, .01));
        var zero = mutate(n -> n.put("questionChanceMass", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> RootEnvelopePolicyChecks.assess(checked, zero, policy, .01));
        var dropped = mutate(n -> n.withArray("support").remove(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> RootEnvelopePolicyChecks.assess(checked, dropped, policy, .01));
    }

    @Test
    void forgedBoundsAreComparedWithActualContinuationAndLoss() throws Exception {
        var forged =
                mutate(
                        n -> {
                            var action = (ObjectNode) n.withArray("actions").get(0);
                            action.put("lowerUtility", 2.);
                            action.put("upperUtility", 2.);
                        });
        var c = RootEnvelopePolicyChecks.assess(checked, forged, policy, .01);
        assertTrue(c.failures().contains("REFERENCE_OUTSIDE_DECISION_BOUNDS"));
        assertTrue(c.maximumBoundViolationBb() > .9);
        var missing = mutate(n -> n.withArray("actions").remove(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> RootEnvelopePolicyChecks.assess(checked, missing, policy, .01));
    }

    @Test
    void robustContrastAndEveryPolicyFailureAreRequired() throws Exception {
        var c = assess(policy);
        var moves = RootEnvelopePolicyChecks.moves(audit, .01);
        assertTrue(
                RootEnvelopePolicyChecks.reasons(moves, c, List.of(c, c))
                        .contains("NO_ROBUST_MOVE_OUTSIDE_LIMIT"));
        var good =
                new SixMaxSuppliedIntervalFeedback.Move(
                        "safe",
                        1,
                        1,
                        0,
                        0,
                        SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT);
        var bad =
                new SixMaxSuppliedIntervalFeedback.Move(
                        "risk",
                        0,
                        0,
                        1,
                        1,
                        SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT);
        assertTrue(
                RootEnvelopePolicyChecks.reasons(List.of(good, bad), c, List.of(c, c)).isEmpty());
        var failed = assess(change("0:type0", Map.of("safe", 0., "risk", 1.)));
        assertTrue(
                RootEnvelopePolicyChecks.reasons(List.of(good, bad), c, List.of(failed))
                        .contains("1:SELECTED_DECISION_REGRET"));
        assertThrows(
                IllegalArgumentException.class, () -> RootEnvelopePolicyChecks.moves(audit, .02));
    }

    @Test
    void reportsAndAssessmentCannotConstructOwnedCapability() throws Exception {
        for (var type :
                List.of(
                        SixMaxRootEnvelopeFeedback.Result.class,
                        SixMaxRootEnvelopeFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var qualify =
                Arrays.stream(SixMaxRootEnvelopeFeedback.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("qualify"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                Arrays.asList(qualify.getParameterTypes())
                        .contains(SixMaxRootEnvelopeStudy.Result.class));
        assertFalse(
                Arrays.asList(qualify.getParameterTypes())
                        .contains(SixMaxRootEnvelopeStudy.Report.class));
        var c = assess(policy);
        assertThrows(UnsupportedOperationException.class, () -> c.actionUtilitiesBb().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.failures().clear());
    }
}
