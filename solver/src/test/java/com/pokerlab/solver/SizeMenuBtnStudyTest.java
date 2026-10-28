package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SizeMenuBtnStudyTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-size-menu-btn-quarter-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-size-menu-btn-quarter-report.json.gz");
    static SixMaxSizeMenuBtnStudy.Request request;
    static SixMaxSuppliedFlopSizeMenuGame game;
    static SixMaxSizeMenuBtnStudy.Result owned;
    static SixMaxSizeMenuBtnDrill.Result drill;

    @BeforeAll
    static void replayOnePhysicalGameAllFourHandsAndFreshReferences() throws Exception {
        request = SixMaxSizeMenuBtnStudy.readRequest(REQUEST);
        var p = request.parent();
        game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        owned = SixMaxSizeMenuBtnStudy.solve(request, game);
        drill = SixMaxSizeMenuBtnDrill.create(owned);
    }

    static Map<String, Map<String, Double>> policy() {
        var rows = new TreeMap<String, Map<String, Double>>();
        owned.report()
                .parent()
                .candidate()
                .strategy()
                .forEach((k, v) -> rows.put(k, new TreeMap<>(v)));
        return rows;
    }

    static void pure(Map<String, Double> row, String action) {
        assertNotNull(row);
        assertTrue(row.containsKey(action));
        row.replaceAll((a, p) -> a.equals(action) ? 1.0 : 0.0);
    }

    static com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check assess(
            Map<String, Map<String, Double>> rows) {
        var c = owned.report().cases().getFirst();
        return SizeMenuPolicyChecks.assess(
                game,
                owned.report().parent(),
                new SizeMenuPolicyChecks.Selection(
                        3,
                        c.heroCards(),
                        SixMaxSizeMenuBtnStudy.HISTORY,
                        List.of(SixMaxSizeMenuBtnStudy.BET),
                        c.informationSet()),
                SixMaxSizeMenuBtnStudy.actions(c.endpoints()),
                new CfrSolution(1, rows));
    }

    @Test
    void fullReportReplaysExactlyAndSharedParentMatchesPublishedSizingGame() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        assertEquals(
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                REPORT, SixMaxSizeMenuBtnStudy.MAX_BYTES),
                        SixMaxSizeMenuBtnStudy.Report.class),
                owned.report());
        assertEquals(
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of("../docs/data/supplied-flop-size-menu-report.json.gz"),
                                SixMaxSizeMenuBtnStudy.MAX_BYTES),
                        SixMaxSuppliedFlopSizeMenuStudy.Report.class),
                owned.report().parent());
        assertFalse(owned.report().trainerAdmission());
    }

    @Test
    void allFourHandsBothActionsAndThirtyTwoCertificatesAreRetained() {
        assertEquals(
                List.of("2c 3c", "Ah Kh", "As Qs", "Qh Th"),
                owned.report().cases().stream().map(c -> c.heroCards()).toList());
        int solves = 0;
        for (var c : owned.report().cases()) {
            assertEquals(
                    List.of("fold", "call"), c.endpoints().stream().map(e -> e.action()).toList());
            assertTrue(c.qualifiedForOfflineDrill());
            assertEquals(List.of(), c.rejectionReasons());
            for (var e : c.endpoints()) {
                assertNull(e.rejection());
                assertNotNull(e.utility());
                var a = e.utility().diagnostic();
                solves += a.work().intervalLpSolves();
                assertEquals(owned.report().parent().solve(), a.baseline());
                assertEquals(List.of(), a.fixedHeroPast());
                assertEquals(1, a.numeratorPlans().size());
                assertEquals(4, a.work().intervalLpSolves());
                assertEquals(.0001, a.requiredMinimumReach());
                assertEquals(1e-8, a.securitySlack());
                assertTrue(a.certifiedReachLowerBound() >= .0001);
                assertTrue(
                        a.work().intervalLpArithmeticWork()
                                <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
                for (var witness :
                        List.of(
                                a.minimumReachWitness(),
                                a.maximumReachWitness(),
                                a.lowerWitness(),
                                a.upperWitnesses().getFirst())) {
                    assertEquals(6, witness.posterior().size());
                    assertEquals(
                            1,
                            witness.posterior().stream().mapToDouble(p -> p.probability()).sum(),
                            1e-12);
                    assertTrue(witness.globalHeroBestResponse() <= a.globalHeroUpperValue() + 2e-8);
                }
            }
        }
        assertEquals(32, solves);
    }

    @Test
    void twelvePolicyAssessmentsPassAndReuseExactlyTwoFreshReferenceRuns() {
        assertEquals(
                240_000,
                owned.report().parent().references().stream()
                        .mapToInt(r -> r.policy().iterations())
                        .sum());
        for (var c : owned.report().cases()) {
            assertEquals(
                    List.of(80_000, 160_000),
                    c.references().stream().map(r -> r.iterations()).toList());
            assertEquals(
                    owned.report().parent().references().stream().map(r -> r.policyHash()).toList(),
                    c.references().stream().map(r -> r.solutionHash()).toList());
            var checks = new ArrayList<>(c.references());
            checks.add(c.primary());
            for (var check : checks) {
                assertEquals(List.of(), check.failures());
                assertTrue(check.insideDeclaredFace());
                assertTrue(check.boundsChecked());
                assertTrue(check.fullParentQuality().nashConvBb() <= .001);
                assertTrue(
                        check.reachedFlopQuality().conditionalBestResponse().nashConvBb() <= .001);
                assertTrue(check.actualQuestionReachGivenPhysicalFlop() >= .0001);
                assertEquals(
                        check.actualQuestionReachGivenPhysicalFlop(),
                        check.counterfactualQuestionReachGivenPhysicalFlop(),
                        1e-12);
                assertTrue(check.decisionRegretBb() <= .01);
                assertTrue(check.maximumBoundViolationBb() <= 1e-8);
            }
        }
    }

    @Test
    void sixPublicPlayersPotAndPriceFollowTheOwnedQuarterPotEdge() {
        for (var c : owned.report().cases()) {
            var context = c.context();
            assertEquals(3, context.actingSeat().ordinal());
            assertEquals(8.125, context.potBb());
            assertEquals(1.625, context.toCallBb());
            assertEquals(
                    List.of(0.0, 1.625),
                    context.actions().stream().map(a -> a.amountBb()).toList());
            assertEquals(4.625, context.players().get(5).committedBb());
            assertEquals(3, context.players().get(3).committedBb());
            assertEquals(context, SixMaxSizeMenuBtnStudy.question(game, c.heroCards()).context());
            assertEquals(6, context.players().size());
            for (var p : context.players())
                assertEquals(100, p.committedBb() + p.remainingStackBb());
        }
    }

    @Test
    void opponentQuarterPotChoiceStaysInActualAndCounterfactualPosterior() {
        var rows = policy();
        pure(rows.get("5:supplied-size-menu-flop:7d 7h:|BB:call:6d Qc Td:"), "check");
        var check = assess(rows);
        assertEquals(1.0 / 6, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
        assertEquals(
                check.actualQuestionReachGivenPhysicalFlop(),
                check.counterfactualQuestionReachGivenPhysicalFlop(),
                1e-12);
        assertEquals(
                check.actualQuestionRootReach() / game.handoffs().getFirst().physicalFlopMass(),
                check.actualQuestionReachGivenPhysicalFlop());
    }

    @Test
    void opponentPreflopCallStaysInPosteriorAndCurrentHeroChoiceDoesNotAffectReach() {
        var rows = policy();
        pure(rows.get("5:supplied-size-menu-preflop:supplied-conditional-preflop:7d 7h:"), "fold");
        var check = assess(rows);
        assertEquals(1.0 / 6, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
        var a = policy();
        var b = policy();
        String key = owned.report().cases().getFirst().informationSet();
        pure(a.get(key), "fold");
        pure(b.get(key), "call");
        assertEquals(assess(a).actualQuestionRootReach(), assess(b).actualQuestionRootReach());
        assertEquals(assess(a).actionUtilitiesBb(), assess(b).actionUtilitiesBb());
    }

    @Test
    void unobservedQuarterPotBetCannotInventValuesOrPassReach() {
        var rows = policy();
        for (var e : rows.entrySet())
            if (e.getKey().startsWith("5:supplied-size-menu-flop:")
                    && e.getKey().endsWith(":|BB:call:6d Qc Td:")) pure(e.getValue(), "check");
        var check = assess(rows);
        assertEquals(0, check.actualQuestionRootReach());
        assertEquals(0, check.counterfactualQuestionReachGivenPhysicalFlop());
        assertTrue(check.actionUtilitiesBb().isEmpty());
        assertNull(check.decisionRegretBb());
        assertFalse(check.boundsChecked());
        assertTrue(check.failures().contains("INSUFFICIENT_ACTUAL_QUESTION_REACH"));
        assertTrue(check.failures().contains("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH"));
    }

    @Test
    void incompleteEndpointRetainsItsUtilityButHasNoLossGradesOrCapability() {
        var c = owned.report().cases().getFirst();
        var partial =
                List.of(
                        c.endpoints().getFirst(),
                        new SixMaxSizeMenuBtnStudy.Endpoint(
                                "call",
                                null,
                                new SixMaxSizeMenuBtnStudy.Rejection(
                                        "LP", "NUMERICAL_FAILURE", "controlled", null)));
        var rejected =
                new SixMaxSizeMenuBtnStudy.Case(
                        c.index(),
                        c.heroCards(),
                        c.informationSet(),
                        c.context(),
                        partial,
                        List.of(),
                        null,
                        List.of(),
                        false,
                        List.of("ENDPOINT:call:LP:NUMERICAL_FAILURE"));
        assertEquals(c.endpoints().getFirst().utility(), rejected.endpoints().getFirst().utility());
        assertTrue(rejected.moves().isEmpty());
        assertNull(rejected.primary());
        assertFalse(rejected.qualifiedForOfflineDrill());
        assertThrows(IllegalArgumentException.class, () -> SixMaxSizeMenuBtnStudy.actions(partial));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSizeMenuBtnStudy.Case(
                                c.index(),
                                c.heroCards(),
                                c.informationSet(),
                                c.context(),
                                partial,
                                c.moves(),
                                c.primary(),
                                c.references(),
                                true,
                                List.of()));
    }

    @Test
    void malformedOrOldPoliciesRejectRatherThanDefaultingMissingInformationSets() {
        var rows = policy();
        rows.remove(rows.keySet().iterator().next());
        assertThrows(IllegalArgumentException.class, () -> assess(rows));
        var wrong = policy();
        var row = wrong.get(owned.report().cases().getFirst().informationSet());
        row.put("check", row.remove("fold"));
        assertThrows(IllegalArgumentException.class, () -> assess(wrong));
        var invalid = policy();
        invalid.get(owned.report().cases().getFirst().informationSet())
                .replaceAll((a, p) -> Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> assess(invalid));
    }

    @Test
    void allFourPrivateCapabilitiesBindTheCompleteStudyAndCannotComeFromParsedReports()
            throws Exception {
        assertEquals(Set.of(0, 1, 2, 3), owned.qualifiedCases().keySet());
        String hash = SixMaxHeadsUpPreflopGame.hash(owned.report());
        for (var e : owned.qualifiedCases().entrySet()) {
            assertEquals(e.getKey(), e.getValue().index());
            assertEquals(hash, e.getValue().studyHash());
        }
        for (var type :
                List.of(
                        SixMaxSizeMenuBtnStudy.Result.class,
                        SixMaxSizeMenuBtnStudy.QualifiedCase.class,
                        SixMaxSizeMenuBtnDrill.Result.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> owned.qualifiedCases().clear());
    }

    @Test
    void offlineCacheContainsAllFourQuestionsAndEightPinnedCertifiedGrades() {
        String hash = drill.snapshot().studyHash();
        assertFalse(drill.snapshot().trainerAdmission());
        assertEquals(4, drill.snapshot().questions().size());
        for (var q : drill.snapshot().questions())
            for (var move : q.moves()) {
                var grade = drill.grade(hash, q.sourceCaseIndex(), move.action());
                assertEquals(move, grade.move());
                assertSame(grade, drill.grade(hash, q.sourceCaseIndex(), move.action()));
            }
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                drill.grade(hash, 0, "fold").move().classification());
        for (int i : List.of(1, 2, 3))
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                    drill.grade(hash, i, "call").move().classification());
        assertThrows(IllegalArgumentException.class, () -> drill.grade("0".repeat(64), 0, "fold"));
        assertThrows(IllegalArgumentException.class, () -> drill.grade(hash, 4, "call"));
        assertThrows(IllegalArgumentException.class, () -> drill.grade(hash, 0, "bet:0.25"));
        assertThrows(
                UnsupportedOperationException.class, () -> drill.snapshot().questions().clear());
    }

    @Test
    void offlineReviewsKeepOrderRepeatsCountsAndExactCachedGrades() throws Exception {
        String hash = drill.snapshot().studyHash();
        var attempts = new ArrayList<SixMaxSizeMenuBtnDrill.Attempt>();
        for (int i = 0; i < 4; i++)
            for (String action : List.of("fold", "call"))
                attempts.add(new SixMaxSizeMenuBtnDrill.Attempt(i, action));
        var review = drill.review(hash, attempts);
        assertEquals(new SixMaxSizeMenuBtnDrill.Counts(4, 4, 4, 0), review.counts());
        for (int i = 0; i < 8; i++) {
            var row = review.attempts().get(i);
            assertEquals(i, row.ordinal());
            assertEquals(attempts.get(i), row.attempt());
            assertSame(
                    drill.grade(hash, row.attempt().sourceCaseIndex(), row.attempt().action()),
                    row.grade());
        }
        assertEquals(
                SixMaxTextureStudy.json(review),
                SixMaxTextureStudy.json(drill.review(hash, attempts)));
        assertEquals(
                2,
                drill.review(hash, List.of(attempts.getFirst(), attempts.getFirst()))
                        .counts()
                        .withinLimit());
        assertEquals(
                new SixMaxSizeMenuBtnDrill.Counts(0, 0, 0, 0),
                drill.review(hash, List.of()).counts());
        assertEquals(
                128,
                drill.review(hash, Collections.nCopies(128, attempts.getFirst()))
                        .attempts()
                        .size());
        assertThrows(
                IllegalArgumentException.class,
                () -> drill.review(hash, Collections.nCopies(129, attempts.getFirst())));
        assertThrows(IllegalArgumentException.class, () -> drill.review("0".repeat(64), attempts));
    }

    @Test
    void strictStudyReaderAndExclusiveOutputRejectBeforeExpensiveWork(@TempDir Path temp)
            throws Exception {
        var json = Files.readString(REQUEST);
        for (String bad :
                List.of(
                        json + " {}",
                        json.replace(
                                "\"maximumLossBb\": 0.01",
                                "\"maximumLossBb\":0.01,\"maximumLossBb\":0.01"),
                        json.replace("\"maximumLossBb\"", "\"heroHands\""),
                        json.replace("0.01", "1e999"),
                        json.replace("0.01", "0.1"))) {
            var path = temp.resolve("bad.json");
            Files.writeString(path, bad);
            assertThrows(Exception.class, () -> SixMaxSizeMenuBtnStudy.readRequest(path));
        }
        var output = temp.resolve("report.json.gz");
        SixMaxSizeMenuBtnStudy.write(output, owned);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSizeMenuBtnStudy.write(output, owned));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuBtnMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuBtnMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
    }

    @Test
    void reportIdentityAndEndpointLineageCannotBeForged() throws Exception {
        var json = SixMaxTextureStudy.json(owned.report());
        for (String bad :
                List.of(
                        json.replace("\"trainerAdmission\" : false", "\"trainerAdmission\" : true"),
                        json.replace(
                                "SIZE_MENU_BTN_QUARTER_RESEARCH_ONLY",
                                "SIZE_MENU_CONDITIONAL_INTERVAL_RESEARCH_ONLY"),
                        json.replace("\"securitySlack\" : 1.0E-8", "\"securitySlack\" : 1.0E-4")))
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxTexturePayoffTable.mapper()
                                    .readValue(bad, SixMaxSizeMenuBtnStudy.Report.class));
    }
}
