package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxBtnFlopCurriculumTest {
    static final Path INPUT =
            Path.of("../docs/data/supplied-four-button-hand-btn-curriculum-request.json");
    static final Path SAVED =
            Path.of("../docs/data/supplied-four-button-hand-btn-curriculum-report.json.gz");
    static SixMaxBtnFlopCurriculum.Request request;
    static SixMaxBtnFlopCurriculum.Result owned;

    @BeforeAll
    static void physicalReplay() throws Exception {
        request = SixMaxBtnFlopCurriculum.readRequest(INPUT);
        // Rebuild all eight endpoints and both complete fresh runs. No imported policy grants
        // freshness.
        owned = SixMaxBtnFlopCurriculum.replay(INPUT, SAVED);
    }

    @Test
    void allFourOwnedContextsQualifyWithTheUnchangedPhysicalParent() throws Exception {
        var report = owned.report();
        assertTrue(report.qualifiedForOfflineDrill(), report.rejectionReasons().toString());
        assertFalse(report.trainerAdmission());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(report),
                owned.qualifiedFeedback().orElseThrow().reportHash());
        var parent =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-connected-flop-report.json.gz"),
                                        SixMaxBtnFlopCurriculum.MAX_BYTES),
                                SixMaxSuppliedFlopBettingStudy.Report.class);
        assertEquals(parent, report.parent());
        assertEquals(
                SixMaxBtnFlopCurriculum.HANDS,
                report.decisions().stream().map(d -> d.heroCards()).toList());
        for (var d : report.decisions()) {
            assertTrue(d.qualified());
            assertTrue(d.rejectionReasons().isEmpty());
            assertEquals(3, d.context().actingSeat().ordinal());
            assertEquals(6.5, d.context().potBb());
            assertEquals(0, d.context().toCallBb());
            assertEquals(List.of("check", "bet"), d.context().legalActions());
            assertEquals(
                    "3:supplied-flop-one-bet:" + d.heroCards() + ":|BB:call:2s 8s Ts:check",
                    d.informationSet());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                    d.moves().getFirst().classification());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                    d.moves().getLast().classification());
            for (var a : d.actions()) {
                assertEquals(request.intervalAlgorithm(), a.diagnostic().algorithm());
                assertEquals(
                        BoundedLinearProgram.REFINED_DIRECTION_ALGORITHM,
                        a.diagnostic().lowerWitness().certificate().algorithm());
                assertEquals(List.of(), a.diagnostic().fixedHeroPast());
                assertTrue(a.diagnostic().certifiedReachLowerBound() > .249);
            }
        }
    }

    @Test
    void sharedFreshBudgetMeansThreePoliciesAndTwelveContextChecks() throws Exception {
        var report = owned.report();
        assertEquals(
                List.of(80_000, 160_000),
                report.references().stream().map(CfrSolution::iterations).toList());
        assertEquals(240_000, request.referenceBudget().totalIterations());
        assertEquals(3, report.uniquePoliciesChecked());
        assertEquals(12, report.policyContextChecks());
        var unique = new HashSet<String>();
        unique.add(report.parent().solutionHash());
        for (var policy : report.references())
            unique.add(SixMaxConnectedPostflopAudit.solutionHash(policy));
        assertEquals(3, unique.size());
        for (var d : report.decisions()) {
            var checks = new ArrayList<SixMaxSuppliedFlopFeedback.Check>();
            checks.add(d.primary());
            checks.addAll(d.references());
            for (var c : checks) {
                assertTrue(c.failures().isEmpty());
                assertTrue(c.insideDeclaredFace());
                assertTrue(c.boundsChecked());
                assertEquals(0, c.maximumBoundViolationBb());
                assertEquals(
                        c.actualQuestionReachGivenPhysicalFlop(),
                        c.counterfactualQuestionReachGivenPhysicalFlop(),
                        1e-12);
                assertEquals(
                        c.actualQuestionReachGivenPhysicalFlop() / 9880,
                        c.actualQuestionRootReach(),
                        1e-20);
                assertTrue(c.actualQuestionReachGivenPhysicalFlop() > .249);
            }
            for (int i = 0; i < 2; i++)
                assertEquals(
                        SixMaxConnectedPostflopAudit.solutionHash(report.references().get(i)),
                        d.references().get(i).solutionHash());
        }
    }

    @Test
    void lowIterationReferencesStillFailTheOriginalFaceForEveryNewContext() throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        var weak = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(2000);
        for (var d : owned.report().decisions()) {
            var check =
                    SixMaxBtnFlopFeedback.assess(
                            game, d.heroCards(), request.publicPreflopHistory(), d.actions(), weak);
            assertTrue(check.fullParentQuality().nashConvBb() < .001);
            assertFalse(check.insideDeclaredFace());
            assertFalse(check.boundsChecked());
            assertNull(check.maximumBoundViolationBb());
            assertTrue(check.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        }
    }

    @Test
    void unsupportedSelectionOrderAlgorithmsAndBudgetsFailBeforeSolving() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind : List.of("algorithm", "hands", "order", "selection", "budget", "path")) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(request);
            switch (kind) {
                case "algorithm" ->
                        node.put(
                                "intervalAlgorithm",
                                FiniteTwoPlayerConditionalActionIntervals
                                        .PHYSICAL_FLOP_EXACT_POWER_TWO_ALGORITHM);
                case "hands" -> node.set("heroHands", mapper.valueToTree(List.of("9s Js")));
                case "order" ->
                        node.set(
                                "heroHands",
                                mapper.valueToTree(List.of("Ah Kh", "9s Js", "As Qs", "Qh Th")));
                case "selection" -> node.put("selection", "automatic");
                case "budget" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.get("referenceBudget"))
                                .put("totalIterations", 960_000);
                case "path" -> node.set("flopActions", mapper.valueToTree(List.of("bet")));
            }
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(node, SixMaxBtnFlopCurriculum.Request.class));
        }
        assertThrows(
                Exception.class,
                () ->
                        SixMaxBtnFlopCurriculum.readRequest(
                                Path.of(
                                        "../docs/data/supplied-four-button-hand-btn-flop-feedback-request.json")));
        assertThrows(Exception.class, () -> SixMaxBtnFlopFeedback.readRequest(INPUT));
    }

    @Test
    void exactReplayGateRejectsEditedValuesPoliciesReachContextAndCounters() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind : List.of("value", "policy", "reach", "context", "counter", "order")) {
            var node = mapper.valueToTree(owned.report());
            switch (kind) {
                case "value" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/decisions/1/moves/0"))
                                .put("lowerEvBb", 0);
                case "policy" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/references/0/strategy").elements().next())
                                .put("call", .1);
                case "reach" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/decisions/2/references/0"))
                                .put("counterfactualQuestionReachGivenPhysicalFlop", .1);
                case "context" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/decisions/3/context"))
                                .put("potBb", 7);
                case "counter" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                                .put("uniquePoliciesChecked", 12);
                case "order" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode) node.get("decisions"))
                                .remove(0);
            }
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxBtnFlopCurriculum.verifyReport(
                                    mapper.treeToValue(node, SixMaxBtnFlopCurriculum.Report.class),
                                    owned));
        }
        SixMaxBtnFlopCurriculum.verifyReport(owned.report(), owned);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopCurriculum.verifyReport(owned.report(), null));
        for (var type :
                List.of(
                        SixMaxBtnFlopCurriculum.Result.class,
                        SixMaxBtnFlopCurriculum.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void everyContextMustHaveBothRobustClassesAndAllPolicyGates() {
        var d = owned.report().decisions().getFirst();
        assertEquals(
                List.of(),
                SixMaxBtnFlopCurriculum.decisionFailures(d.moves(), d.primary(), d.references()));
        var tight =
                d.moves().stream()
                        .map(
                                m ->
                                        new SixMaxSuppliedIntervalFeedback.Move(
                                                m.action(),
                                                m.lowerEvBb(),
                                                m.upperEvBb(),
                                                m.lowerLossBb(),
                                                m.upperLossBb(),
                                                SixMaxSuppliedIntervalFeedback.classify(
                                                        m.lowerLossBb(), m.upperLossBb(), 1e-9)))
                        .toList();
        assertTrue(
                SixMaxBtnFlopCurriculum.decisionFailures(tight, d.primary(), d.references())
                        .contains("NO_ROBUST_MOVE_WITHIN_LIMIT"));
        assertTrue(
                SixMaxBtnFlopCurriculum.decisionFailures(
                                List.of(d.moves().getLast()), d.primary(), d.references())
                        .contains("NO_ROBUST_MOVE_OUTSIDE_LIMIT"));
    }

    @Test
    void filesRemainBoundedExclusiveAndRequestBound(@TempDir Path dir) throws Exception {
        var output = dir.resolve("report.json.gz");
        SixMaxBtnFlopCurriculum.write(output, owned);
        byte[] bytes = Files.readAllBytes(output);
        assertEquals(owned.report(), SixMaxBtnFlopCurriculum.readReport(output));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxBtnFlopCurriculum.write(output, owned));
        assertArrayEquals(bytes, Files.readAllBytes(output));
        var altered =
                new SixMaxBtnFlopCurriculum.Request(
                        request.schemaVersion(),
                        request.selection(),
                        request.connected(),
                        request.intervalAlgorithm(),
                        request.referenceBudget(),
                        request.heroHands(),
                        request.publicPreflopHistory(),
                        request.flopActions(),
                        .005);
        var input = dir.resolve("input.json");
        Files.writeString(input, SixMaxTextureStudy.json(altered));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopCurriculum.replay(input, output));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopCurriculum.replay(output, output));
        Files.write(input, new byte[SixMaxBtnFlopCurriculum.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxBtnFlopCurriculum.readRequest(input));
    }

    @Test
    void receiptBindsSourceBytesAndTwelveChecksDoNotClaimTwelveFreshPolicies() throws Exception {
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-btn-curriculum-independent-check.json")));
        assertEquals(32, receipt.get("originalEndpointCertificatesChecked").asInt());
        assertEquals(3, receipt.get("uniquePoliciesChecked").asInt());
        assertEquals(12, receipt.get("policyContextChecks").asInt());
        assertEquals(240000, receipt.get("declaredSharedFreshReferenceIterations").asInt());
        assertEquals(0, receipt.get("freshCfrRuns").asInt());
        assertEquals(0, receipt.get("freshLpSolves").asInt());
        assertFalse(receipt.get("trainerAdmission").asBoolean());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(owned.report()), receipt.get("reportHash").asText());
        assertEquals(sha(INPUT), receipt.get("requestStoredBytesSha256").asText());
        assertEquals(sha(SAVED), receipt.get("reportStoredBytesSha256").asText());
        for (var entry :
                Map.of(
                                "checkerSourceSha256",
                                "check-btn-flop-curriculum.py",
                                "boundsHelperSourceSha256",
                                "check-supplied-flop-action-bounds.py",
                                "physicalHelperSourceSha256",
                                "check-supplied-connected-flop.py")
                        .entrySet())
            assertEquals(
                    sha(Path.of("../scripts/" + entry.getValue())),
                    receipt.get(entry.getKey()).asText());
        assertThrows(UnsupportedOperationException.class, () -> request.heroHands().clear());
        assertThrows(UnsupportedOperationException.class, () -> owned.report().decisions().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().references().getFirst().strategy().clear());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(path)));
    }
}
