package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SizeMenuFeedbackTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-size-menu-99-feedback-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-size-menu-99-feedback-report.json.gz");
    static SixMaxSizeMenuFeedback.Request request;
    static SixMaxSuppliedFlopSizeMenuGame game;
    static SixMaxSizeMenuConditionalStudy.Result conditional;
    static SixMaxSizeMenuFeedback.Result result;

    @BeforeAll
    static void replayCompleteOwnedSourceAndQualify() throws Exception {
        request = SixMaxSizeMenuFeedback.readRequest(REQUEST);
        var p = request.conditional().parent();
        game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        conditional = SixMaxSizeMenuConditionalStudy.solve(request.conditional(), game);
        result = SixMaxSizeMenuFeedback.qualify(request, game, conditional);
    }

    static Map<String, Map<String, Double>> strategy() {
        var copy = new TreeMap<String, Map<String, Double>>();
        conditional
                .report()
                .parent()
                .candidate()
                .strategy()
                .forEach((k, v) -> copy.put(k, new TreeMap<>(v)));
        return copy;
    }

    static void pure(Map<String, Double> row, String action) {
        assertTrue(row.containsKey(action));
        row.replaceAll((a, p) -> a.equals(action) ? 1.0 : 0.0);
    }

    static SixMaxSuppliedFlopFeedback.Check assess(Map<String, Map<String, Double>> policy) {
        return SixMaxSizeMenuFeedback.assess(
                game, conditional.report(), new CfrSolution(1, policy));
    }

    @Test
    void fullReportAndEveryOriginalConditionalFieldReplayExactly() throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        REPORT, SixMaxSizeMenuFeedback.MAX_BYTES),
                                SixMaxSizeMenuFeedback.Report.class);
        assertEquals(saved, result.report());
        var original =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-size-menu-99-conditional-report.json.gz"),
                                        SixMaxSizeMenuFeedback.MAX_BYTES),
                                SixMaxSizeMenuConditionalStudy.Report.class);
        assertEquals(original, result.report().conditional());
    }

    @Test
    void passingAllThreePoliciesDoesNotBypassMissingRobustOutsideMove() {
        assertEquals(List.of("NO_ROBUST_MOVE_OUTSIDE_LIMIT"), result.report().rejectionReasons());
        assertFalse(result.report().qualifiedForOfflineDrill());
        assertFalse(result.report().trainerAdmission());
        assertTrue(result.qualifiedFeedback().isEmpty());
        assertEquals(
                List.of(
                        SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                        SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT),
                result.report().moves().stream().map(m -> m.classification()).toList());
    }

    @Test
    void referencesAreTheTwoFreshOwnedSourceRunsAndNeverRetrained() {
        var source = result.report().conditional().parent();
        assertEquals(
                List.of(80_000, 160_000),
                result.report().references().stream().map(c -> c.iterations()).toList());
        assertEquals(
                source.references().stream().map(r -> r.policyHash()).toList(),
                result.report().references().stream().map(c -> c.solutionHash()).toList());
        assertEquals(source.solutionHash(), result.report().primary().solutionHash());
        assertEquals(
                240_000, source.references().stream().mapToInt(r -> r.policy().iterations()).sum());
    }

    @Test
    void eachPolicyPassesFaceQualityReachAllNineActionValuesAndRegret() {
        var checks = new ArrayList<>(result.report().references());
        checks.add(result.report().primary());
        for (var check : checks) {
            assertEquals(List.of(), check.failures());
            assertTrue(check.insideDeclaredFace());
            assertTrue(check.boundsChecked());
            assertTrue(check.fullParentQuality().nashConvBb() <= .001);
            assertTrue(check.reachedFlopQuality().conditionalBestResponse().nashConvBb() <= .001);
            assertTrue(check.actualQuestionReachGivenPhysicalFlop() >= .0001);
            assertEquals(1.0 / 3, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
            assertEquals(
                    check.actualQuestionRootReach() / game.handoffs().getFirst().physicalFlopMass(),
                    check.actualQuestionReachGivenPhysicalFlop());
            assertTrue(check.maximumBoundViolationBb() <= 1e-8);
            assertTrue(check.decisionRegretBb() <= .01);
            assertEquals(
                    Set.of("check", "bet:0.25", "bet:0.5"), check.actionUtilitiesBb().keySet());
            double best = Collections.max(check.actionUtilitiesBb().values());
            for (var a : conditional.report().actions()) {
                double value = check.actionUtilitiesBb().get(a.action());
                assertTrue(
                        value >= a.lowerUtilityBb() - 1e-8 && value <= a.upperUtilityBb() + 1e-8);
                assertEquals(Math.max(0, best - value), check.actionLossBb().get(a.action()));
            }
        }
    }

    @Test
    void zeroHeroCallFailsActualReachEvenWhenCounterfactualReachAndFacePass() {
        var policy = strategy();
        pure(
                policy.get("5:supplied-size-menu-preflop:supplied-conditional-preflop:9d 9h:"),
                "fold");
        var check = assess(policy);
        assertEquals(0, check.actualQuestionRootReach());
        assertEquals(1.0 / 3, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
        assertTrue(check.insideDeclaredFace());
        assertTrue(check.failures().contains("INSUFFICIENT_ACTUAL_QUESTION_REACH"));
        assertFalse(check.failures().contains("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH"));
    }

    @Test
    void globallySmallGapCannotHideLocalGapOrBadSelectedMove() {
        var policy = strategy();
        pure(policy.get(conditional.report().informationSet()), "bet:0.5");
        for (var entry : policy.entrySet()) {
            if (!entry.getKey().startsWith("3:supplied-size-menu-flop:")
                    || !entry.getKey().contains(":|BB:call:")) continue;
            if (entry.getKey().endsWith(":bet:0.25")) pure(entry.getValue(), "fold");
            if (entry.getKey().endsWith(":bet:0.5")) pure(entry.getValue(), "call");
        }
        var check = assess(policy);
        assertTrue(check.fullParentQuality().nashConvBb() < .001);
        assertTrue(check.failures().contains("REACHED_FLOP_GAP"));
        assertTrue(check.failures().contains("SELECTED_DECISION_REGRET"));
        assertTrue(check.decisionRegretBb() > .01);
    }

    @Test
    void completelyUnreachedBoundaryCannotInventNormalizedLocalQuality() {
        var policy = strategy();
        for (var entry : policy.entrySet())
            if (entry.getKey().startsWith("5:supplied-size-menu-preflop:")
                    && !entry.getKey().contains("|")) pure(entry.getValue(), "fold");
        var check = assess(policy);
        assertEquals(0, check.reachedFlopQuality().candidateRootFlopReach());
        assertEquals(List.of(), check.reachedFlopQuality().rawWorldMasses());
        assertEquals(List.of(), check.reachedFlopQuality().reachedWorlds());
        assertNull(check.reachedFlopQuality().conditionalBestResponse());
        assertTrue(check.failures().contains("REACHED_FLOP_GAP"));
        assertTrue(check.failures().contains("INSUFFICIENT_ACTUAL_QUESTION_REACH"));
        assertEquals(1.0 / 3, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
    }

    @Test
    void opponentOutsideFullSecurityFaceCannotClaimVerifiedBounds() {
        var policy = strategy();
        int changed = 0;
        for (var entry : policy.entrySet())
            if (entry.getKey().startsWith("3:supplied-size-menu-flop:")
                    && entry.getKey().endsWith(":bet:0.25")) {
                pure(entry.getValue(), "fold");
                changed++;
            }
        assertTrue(changed > 0);
        var check = assess(policy);
        assertFalse(check.insideDeclaredFace());
        assertFalse(check.boundsChecked());
        assertNull(check.maximumBoundViolationBb());
        assertTrue(check.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        assertEquals(3, check.actionUtilitiesBb().size());
    }

    @Test
    void incompleteNonfiniteWrongActionAndUnnormalizedPoliciesReject() {
        var missing = strategy();
        missing.remove(missing.keySet().iterator().next());
        assertThrows(IllegalArgumentException.class, () -> assess(missing));
        for (double value : List.of(Double.NaN, Double.POSITIVE_INFINITY, -.1, 2.0, .2)) {
            var invalid = strategy();
            var row = invalid.get(conditional.report().informationSet());
            row.replaceAll((a, p) -> value);
            assertThrows(IllegalArgumentException.class, () -> assess(invalid));
        }
        var wrong = strategy();
        var row = wrong.get(conditional.report().informationSet());
        row.put("bet", row.remove("bet:0.25"));
        assertThrows(IllegalArgumentException.class, () -> assess(wrong));
    }

    @Test
    void assessmentDoesNotMutateOwnedPoliciesOrQualification() throws Exception {
        var before = result.report();
        var altered = strategy();
        pure(altered.get(conditional.report().informationSet()), "check");
        assess(altered);
        assertEquals(before, SixMaxSizeMenuFeedback.qualify(request, game, conditional).report());
        assertThrows(UnsupportedOperationException.class, () -> before.references().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> before.primary().actionUtilitiesBb().clear());
    }

    @Test
    void foreignOwnedQuestionCannotQualifyUnderAnotherRequest() {
        var q = request.conditional();
        var other =
                new SixMaxSizeMenuConditionalStudy.Request(
                        q.schemaVersion(),
                        q.parent(),
                        q.hero(),
                        "7d 7h",
                        q.publicPreflopHistory(),
                        q.flopActions());
        var foreign = new SixMaxSizeMenuFeedback.Request(request.schemaVersion(), other, .01);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSizeMenuFeedback.qualify(foreign, game, conditional));
        assertThrows(
                NullPointerException.class,
                () -> SixMaxSizeMenuFeedback.qualify(request, game, null));
    }

    @Test
    void strictReadsAndExclusiveOutputsProtectSourceBeforeSolving(@TempDir Path temp)
            throws Exception {
        var json = Files.readString(REQUEST);
        for (String bad :
                List.of(
                        json + " {}",
                        json.replace(
                                "\"maximumLossBb\": 0.01",
                                "\"maximumLossBb\":0.01,\"maximumLossBb\":0.01"),
                        json.replace("\"maximumLossBb\"", "\"unknown\""),
                        json.replace("0.01", "1e999"))) {
            var path = temp.resolve("bad.json");
            Files.writeString(path, bad);
            assertThrows(Exception.class, () -> SixMaxSizeMenuFeedback.readRequest(path));
        }
        var output = temp.resolve("feedback.json.gz");
        SixMaxSizeMenuFeedback.write(output, result);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSizeMenuFeedback.write(output, result));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuFeedbackMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuFeedbackMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
    }

    @Test
    void serializedClaimsCannotIssueCapabilityOrBypassRejection() throws Exception {
        for (var type :
                List.of(
                        SixMaxSizeMenuFeedback.Result.class,
                        SixMaxSizeMenuFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var json = SixMaxTextureStudy.json(result.report());
        for (String bad :
                List.of(
                        json.replace("\"trainerAdmission\" : false", "\"trainerAdmission\" : true"),
                        json.replace(
                                "\"qualifiedForOfflineDrill\" : false",
                                "\"qualifiedForOfflineDrill\" : true"),
                        json.replace("NO_ROBUST_MOVE_OUTSIDE_LIMIT", "NO_OTHER_REASON"),
                        json.replace("STRATEGY_DEPENDENT", "OUTSIDE_LIMIT")))
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxTexturePayoffTable.mapper()
                                    .readValue(bad, SixMaxSizeMenuFeedback.Report.class));
    }
}
