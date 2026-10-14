package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxBtnFlopFeedbackTest {
    static final Path INPUT =
            Path.of("../docs/data/supplied-four-button-hand-btn-flop-feedback-request.json");
    static final Path SAVED =
            Path.of("../docs/data/supplied-four-button-hand-btn-flop-feedback-report.json.gz");
    static SixMaxBtnFlopFeedback.Request request;
    static SixMaxBtnFlopFeedback.Result owned;

    @BeforeAll
    static void physicalReplay() throws Exception {
        request = SixMaxBtnFlopFeedback.readRequest(INPUT);
        // Once per class: full physical parent, both original endpoints and both fresh CFR+ runs.
        // No parsed-policy shortcut or shared checkpoint replaces this replay.
        owned = SixMaxBtnFlopFeedback.replay(INPUT, SAVED);
    }

    @Test
    void completeOwnedBtnDecisionQualifiesAndPreservesOriginalParent() throws Exception {
        var report = owned.report();
        assertTrue(report.qualifiedForOfflineDrill(), report.rejectionReasons().toString());
        assertFalse(report.trainerAdmission());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(report),
                owned.qualifiedFeedback().orElseThrow().reportHash());
        var original =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-connected-flop-report.json.gz"),
                                        SixMaxBtnFlopFeedback.MAX_BYTES),
                                SixMaxSuppliedFlopBettingStudy.Report.class);
        assertEquals(original, report.parent());
        assertEquals(3, report.context().actingSeat().ordinal());
        assertEquals(6.5, report.context().potBb());
        assertEquals(0, report.context().toCallBb());
        assertEquals(
                List.of("check"),
                report.context().flopHistory().stream().map(m -> m.action()).toList());
        assertEquals(List.of("check", "bet"), report.context().legalActions());
        assertEquals(
                "3:supplied-flop-one-bet:9s Js:|BB:call:2s 8s Ts:check", report.informationSet());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                report.moves().getFirst().classification());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                report.moves().getLast().classification());
        assertTrue(report.moves().getFirst().lowerLossBb() > .08);
        assertTrue(report.moves().getLast().upperLossBb() < .001);
        for (var action : report.actions()) {
            assertEquals(request.intervalAlgorithm(), action.diagnostic().algorithm());
            assertEquals(List.of(), action.diagnostic().fixedHeroPast());
            assertEquals(
                    BoundedLinearProgram.EXACT_POWER_TWO_ALGORITHM,
                    action.diagnostic().lowerWitness().certificate().algorithm());
            assertTrue(action.diagnostic().certifiedReachLowerBound() > .249);
        }
    }

    @Test
    void explicitlyBudgetedReferencesHaveFullReachQualityAndBoundsChecks() {
        var report = owned.report();
        assertEquals(List.of(80_000, 160_000), request.referenceBudget().iterations());
        assertEquals(240_000, request.referenceBudget().totalIterations());
        assertEquals(
                List.of(80_000, 160_000),
                report.references().stream().map(r -> r.policy().iterations()).toList());
        assertNotEquals(
                report.references().getFirst().check().solutionHash(),
                report.references().getLast().check().solutionHash());
        var checks = new ArrayList<SixMaxSuppliedFlopFeedback.Check>();
        checks.add(report.primary());
        report.references().forEach(r -> checks.add(r.check()));
        for (var check : checks) {
            assertTrue(check.insideDeclaredFace());
            assertTrue(check.boundsChecked());
            assertEquals(0, check.maximumBoundViolationBb());
            assertTrue(check.failures().isEmpty());
            assertTrue(
                    check.fullParentQuality().nashConvBb()
                            <= SixMaxSuitDecisionStability.LOCAL_GAP_BB);
            assertTrue(
                    check.reachedFlopQuality().conditionalBestResponse().nashConvBb()
                            <= SixMaxSuitDecisionStability.LOCAL_GAP_BB);
            assertTrue(
                    check.decisionRegretBb() <= SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB);
            assertEquals(
                    check.actualQuestionReachGivenPhysicalFlop() / 9880,
                    check.actualQuestionRootReach(),
                    1e-20);
            // All modeled past actions belong to BB. Neither call nor check can cancel for BTN.
            assertEquals(
                    check.actualQuestionReachGivenPhysicalFlop(),
                    check.counterfactualQuestionReachGivenPhysicalFlop(),
                    1e-12);
            assertTrue(check.counterfactualQuestionReachGivenPhysicalFlop() > .249);
        }
    }

    @Test
    void olderReferenceTierCannotPassByRelabellingLocalQuality() throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        for (int iterations : List.of(2000, 4000)) {
            var policy =
                    new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
            var check =
                    SixMaxBtnFlopFeedback.assess(game, request, owned.report().actions(), policy);
            assertTrue(
                    check.fullParentQuality().nashConvBb()
                            < SixMaxSuitDecisionStability.LOCAL_GAP_BB);
            assertFalse(check.insideDeclaredFace());
            assertFalse(check.boundsChecked());
            assertNull(check.maximumBoundViolationBb());
            assertTrue(check.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        }
    }

    @Test
    void weakPolicyRetainsOpponentPastAndRejectsRegret() throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        var weak = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1);
        var check = SixMaxBtnFlopFeedback.assess(game, request, owned.report().actions(), weak);
        assertFalse(check.insideDeclaredFace());
        assertFalse(check.boundsChecked());
        assertNull(check.maximumBoundViolationBb());
        assertTrue(check.failures().contains("SELECTED_DECISION_REGRET"));
        assertEquals(1.0 / 24, check.actualQuestionReachGivenPhysicalFlop(), 1e-12);
        assertEquals(1.0 / 24, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
    }

    @Test
    void budgetsAndUnsupportedContextsFailBeforeSpendingIterations() throws Exception {
        for (var counts :
                List.of(
                        List.of(2000, 4000),
                        List.of(160_000, 80_000),
                        List.of(80_000),
                        List.of(80_000, 160_000, 1)))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxBtnFlopFeedback.ReferenceBudget(
                                    SixMaxBtnFlopFeedback.REFERENCE_ALGORITHM, counts, 240_000));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxBtnFlopFeedback.ReferenceBudget(
                                "resume", List.of(80_000, 160_000), 240_000));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxBtnFlopFeedback.ReferenceBudget(
                                SixMaxBtnFlopFeedback.REFERENCE_ALGORITHM,
                                List.of(80_000, 160_000),
                                240_001));
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field : List.of("hero", "path", "algorithm", "budget", "cards")) {
            var tree = mapper.valueToTree(request);
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) tree;
            switch (field) {
                case "hero" -> object.put("hero", 5);
                case "path" ->
                        object.set("flopActions", mapper.valueToTree(List.of("check", "bet")));
                case "algorithm" -> object.put("intervalAlgorithm", "automatic");
                case "budget" -> object.putNull("referenceBudget");
                case "cards" -> object.put("heroCards", "2c 2d");
            }
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(tree, SixMaxBtnFlopFeedback.Request.class));
        }
        var unknown =
                new SixMaxBtnFlopFeedback.Request(
                        request.schemaVersion(),
                        request.connected(),
                        request.intervalAlgorithm(),
                        request.referenceBudget(),
                        3,
                        request.heroCards(),
                        "unknown",
                        request.flopActions(),
                        .01);
        assertThrows(IllegalArgumentException.class, () -> SixMaxBtnFlopFeedback.solve(unknown));
    }

    @Test
    void finalPhysicalReplayGateRejectsEditedEvidenceWithoutGrantingAuthority() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field : List.of("context", "value", "reach", "policy", "face")) {
            var tree = mapper.valueToTree(owned.report());
            switch (field) {
                case "context" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("context"))
                                .put("potBb", 7);
                case "value" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/moves/0"))
                                .put("lowerEvBb", 0);
                case "reach" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        tree.at("/references/0/check"))
                                .put("actualQuestionRootReach", .1);
                case "policy" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        tree.at("/references/0/policy/strategy").elements().next())
                                .put("call", .1);
                case "face" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        tree.at("/references/0/check"))
                                .put("inclusionMarginBb", .5);
            }
            var changed = mapper.treeToValue(tree, SixMaxBtnFlopFeedback.Report.class);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxBtnFlopFeedback.verifyReport(changed, owned));
        }
        SixMaxBtnFlopFeedback.verifyReport(owned.report(), owned);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopFeedback.verifyReport(owned.report(), null));
        for (var type :
                List.of(
                        SixMaxBtnFlopFeedback.Result.class,
                        SixMaxBtnFlopFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void tighterEducationalLimitDoesNotRelabelTheOriginalEnvelope() {
        var bet = owned.report().moves().getLast();
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                SixMaxSuppliedIntervalFeedback.classify(
                        bet.lowerLossBb(), bet.upperLossBb(), 1e-9));
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.referenceBudget().iterations().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.flopActions().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> owned.report().references().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().primary().actionLossBb().clear());
    }

    @Test
    void boundedExclusiveFilesAndRequestBindingRejectBadInputs(@TempDir Path folder)
            throws Exception {
        var output = folder.resolve("report.json.gz");
        SixMaxBtnFlopFeedback.write(output, owned);
        byte[] original = Files.readAllBytes(output);
        assertThrows(
                FileAlreadyExistsException.class, () -> SixMaxBtnFlopFeedback.write(output, owned));
        assertArrayEquals(original, Files.readAllBytes(output));
        assertEquals(owned.report(), SixMaxBtnFlopFeedback.readReport(output));
        var changed =
                new SixMaxBtnFlopFeedback.Request(
                        request.schemaVersion(),
                        request.connected(),
                        request.intervalAlgorithm(),
                        request.referenceBudget(),
                        3,
                        request.heroCards(),
                        request.publicPreflopHistory(),
                        request.flopActions(),
                        .005);
        var input = folder.resolve("request.json");
        Files.writeString(input, SixMaxTextureStudy.json(changed));
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxBtnFlopFeedback.replay(input, output));
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxBtnFlopFeedback.replay(output, output));
        var oversized = folder.resolve("large.json");
        Files.write(oversized, new byte[SixMaxBtnFlopFeedback.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxBtnFlopFeedback.readReport(oversized));
        Files.writeString(input, "{\"schemaVersion\":\"a\",\"schemaVersion\":\"b\"}");
        assertThrows(Exception.class, () -> SixMaxBtnFlopFeedback.readRequest(input));
    }

    @Test
    void independentReceiptBindsActualFilesAndAllCheckerSources() throws Exception {
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-btn-flop-feedback-independent-check.json")));
        assertFalse(receipt.get("trainerAdmission").asBoolean());
        assertEquals(8, receipt.get("originalEndpointCertificatesChecked").asInt());
        assertEquals(3, receipt.get("policiesChecked").asInt());
        assertEquals(240_000, receipt.get("declaredFreshReferenceIterations").asInt());
        assertEquals(0, receipt.get("freshCfrRuns").asInt());
        assertEquals(0, receipt.get("freshLpSolves").asInt());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(owned.report()), receipt.get("reportHash").asText());
        assertEquals(sha(INPUT), receipt.get("requestStoredBytesSha256").asText());
        assertEquals(sha(SAVED), receipt.get("reportStoredBytesSha256").asText());
        for (var item :
                Map.of(
                                "checkerSourceSha256",
                                "check-btn-flop-feedback.py",
                                "boundsHelperSourceSha256",
                                "check-supplied-flop-action-bounds.py",
                                "physicalHelperSourceSha256",
                                "check-supplied-connected-flop.py")
                        .entrySet())
            assertEquals(
                    sha(Path.of("../scripts/" + item.getValue())),
                    receipt.get(item.getKey()).asText());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(path)));
    }
}
