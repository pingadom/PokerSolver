package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedFlopActionBoundsTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-four-button-hand-connected-flop-request.json");
    static final Path SAVED =
            Path.of("../docs/data/supplied-four-button-hand-flop-action-bounds.json.gz");
    static SixMaxSuppliedFlopBettingStudy.Request request;
    static SixMaxSuppliedFlopBettingGame game;
    static SixMaxSuppliedFlopActionBounds.Result result;

    @BeforeAll
    static void build() throws Exception {
        request = SixMaxSuppliedFlopBettingStudy.readRequest(REQUEST);
        game =
                new SixMaxSuppliedFlopBettingGame(
                        request.input(), request.flop(), request.betPotFraction());
        result = SixMaxSuppliedFlopActionBounds.solve(request, game);
    }

    @Test
    void physicalChanceAndOriginalEquilibriumReportRemainUnchanged() throws Exception {
        var parent =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-connected-flop-report.json.gz"),
                                        SixMaxSuppliedFlopBettingStudy.MAX_BYTES),
                                SixMaxSuppliedFlopBettingStudy.Report.class);
        assertEquals(parent, result.report().parent());
        assertEquals(24, result.report().physicalIncidence().size());
        assertEquals(1.0 / 9880, result.report().physicalFlopMass());
        assertEquals(.0001 / 9880, result.report().rawRequiredMinimumReach(), 1e-20);
        assertEquals(24, game.chanceOutcomes(game.initialState()).size());
        assertEquals(SixMaxSuppliedFlopActionBounds.REACH_UNITS, result.report().reachUnits());
        assertFalse(result.report().trainerAdmission());
    }

    @Test
    void allSixOriginalDualBoundsAndCounterfactualReachUnitsAreExplicit() {
        assertEquals(3, result.report().decisions().size());
        for (var decision : result.report().decisions()) {
            assertEquals(5, decision.hero());
            assertEquals(
                    List.of("check", "bet"),
                    decision.actions().stream()
                            .map(SixMaxSuppliedFlopActionBounds.Action::action)
                            .toList());
            for (var action : decision.actions()) {
                var a = action.diagnostic();
                assertEquals(
                        FiniteTwoPlayerConditionalActionIntervals.PHYSICAL_FLOP_ALGORITHM,
                        a.algorithm());
                assertEquals(1.0 / 3, a.minimumReachWitness().questionReach(), 1e-12);
                assertEquals(1.0 / 3, a.maximumReachWitness().questionReach(), 1e-12);
                assertTrue(action.rawCertifiedReachLowerBound() < .0001);
                assertEquals(
                        result.report().physicalFlopMass() * a.certifiedReachLowerBound(),
                        action.rawCertifiedReachLowerBound());
                assertTrue(a.certifiedReachLowerBound() > .0001);
                assertEquals(8, a.minimumReachWitness().posterior().size());
                for (var w : a.minimumReachWitness().posterior()) {
                    assertEquals(0, w.opponentSequence());
                    assertEquals(1.0 / 24, w.chanceWeight(), 1e-12);
                    assertEquals(1.0 / 8, w.probability(), 1e-12);
                }
                var witnesses = new ArrayList<>(a.upperWitnesses());
                witnesses.add(a.lowerWitness());
                witnesses.add(a.minimumReachWitness());
                witnesses.add(a.maximumReachWitness());
                for (var w : witnesses) {
                    assertEquals(
                            BoundedLinearProgram.RESOLVABLE_COST_ALGORITHM,
                            w.certificate().algorithm());
                    assertTrue(
                            w.globalHeroBestResponse()
                                    <= a.globalHeroUpperValue() + a.securitySlack() + 1e-8);
                    assertTrue(w.certificate().absoluteDualityGap() <= 1e-8);
                }
                assertTrue(action.lowerUtilityBb() <= a.lowerUtility());
                assertTrue(action.upperUtilityBb() >= a.upperUtility() - 1e-9);
                assertTrue(action.lowerLossBb() <= action.upperLossBb());
            }
        }
        var sevens = result.report().decisions().getFirst();
        assertTrue(sevens.informationSet().contains("7d 7h"));
        assertTrue(sevens.actions().get(1).lowerLossBb() > .09);
        assertEquals(0, sevens.actions().getFirst().lowerLossBb());
    }

    @Test
    void freshReferencesAreActualDistinctRunsWithGlobalAndConditionalQuality() {
        var refs = result.report().freshReferences();
        assertEquals(List.of(2000, 4000), refs.stream().map(r -> r.policy().iterations()).toList());
        assertNotEquals(refs.getFirst().solutionHash(), refs.getLast().solutionHash());
        for (var reference : refs) {
            assertEquals(
                    SixMaxConnectedPostflopAudit.solutionHash(reference.policy()),
                    reference.solutionHash());
            assertTrue(reference.fullParentQuality().nashConvBb() < 1e-4);
            assertTrue(
                    reference.reachedFlopQuality().conditionalBestResponse().nashConvBb() < 1e-4);
        }
        assertTrue(
                refs.getLast().fullParentQuality().nashConvBb()
                        < refs.getFirst().fullParentQuality().nashConvBb());
    }

    @Test
    void unknownPreflopAndExcessiveReachFloorQuestionsFailClosed() {
        var q = result.report().decisions().getFirst();
        var rejected =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlop(
                                        game,
                                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                                5, q.informationSet(), "check"),
                                        1e-8,
                                        .5));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                rejected.reason());
        assertTrue(rejected.reachFailure().isPresent());
        assertFalse(rejected.reachFailure().orElseThrow().trainerAdmission());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlop(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        5, "5:preflop", "call"),
                                1e-8,
                                .0001));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlop(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        5, q.informationSet(), "raise"),
                                1e-8,
                                .0001));
    }

    @Test
    void explicitExactRowsBoundLaterBigBlindFoldAndCallWithoutChangingInitialReport()
            throws Exception {
        var handoff = game.handoffs().getFirst();
        var keys = new TreeSet<String>();
        for (var world : handoff.worlds()) {
            if (!world.sixDealtCombos().get(5).equals("7d 7h")) continue;
            var state =
                    new SixMaxSuppliedFlopBettingGame.State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            assertEquals(List.of("check", "bet"), game.legalActions(state));
            state = game.afterAction(state, "check");
            assertEquals(3, game.currentPlayer(state));
            state = game.afterAction(state, "bet");
            assertEquals(5, game.currentPlayer(state));
            assertEquals(List.of("fold", "call"), game.legalActions(state));
            keys.add(game.currentPlayer(state) + ":" + game.informationSet(state));
        }
        assertEquals(1, keys.size());
        String key = keys.first();
        var fold =
                FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlopExactPowerOfTwo(
                        game,
                        new FiniteTwoPlayerConditionalActionIntervals.Question(5, key, "fold"),
                        1e-8,
                        .0001);
        var call =
                FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlopExactPowerOfTwo(
                        game,
                        new FiniteTwoPlayerConditionalActionIntervals.Question(5, key, "call"),
                        1e-8,
                        .0001);
        for (var result : List.of(fold, call)) {
            var audit = result.audit();
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals
                            .PHYSICAL_FLOP_EXACT_POWER_TWO_ALGORITHM,
                    audit.algorithm());
            assertFalse(audit.trainerAdmission());
            assertTrue(audit.fixedHeroPast().stream().anyMatch(a -> a.action().equals("check")));
            assertTrue(audit.certifiedReachLowerBound() > .0001);
            var witnesses = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Witness>();
            witnesses.add(audit.minimumReachWitness());
            witnesses.add(audit.maximumReachWitness());
            witnesses.add(audit.lowerWitness());
            witnesses.addAll(audit.upperWitnesses());
            for (var witness : witnesses)
                assertEquals(
                        BoundedLinearProgram.EXACT_POWER_TWO_ALGORITHM,
                        witness.certificate().algorithm());
            assertTrue(audit.lowerUtility() <= audit.upperUtility());
            assertTrue(
                    audit.work().intervalLpArithmeticWork()
                            <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        }
        double bestLower = Math.max(fold.audit().lowerUtility(), call.audit().lowerUtility());
        double bestUpper = Math.max(fold.audit().upperUtility(), call.audit().upperUtility());
        assertTrue(bestUpper - fold.audit().lowerUtility() < .01);
        assertTrue(bestLower - call.audit().upperUtility() > .4);
        assertEquals(SixMaxSuppliedFlopActionBounds.SCHEMA, result.report().schemaVersion());
    }

    @Test
    void exactRowsCannotAdmitAQuestionWithGenuineInsufficientFullFaceReach() throws Exception {
        var handoff = game.handoffs().getFirst();
        var keys = new TreeSet<String>();
        for (var world : handoff.worlds()) {
            if (!world.sixDealtCombos().get(3).equals("9s Js")) continue;
            var state =
                    new SixMaxSuppliedFlopBettingGame.State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            state = game.afterAction(state, "bet");
            assertEquals(3, game.currentPlayer(state));
            assertEquals(List.of("fold", "call"), game.legalActions(state));
            keys.add(game.currentPlayer(state) + ":" + game.informationSet(state));
        }
        assertEquals(1, keys.size());
        for (String action : List.of("fold", "call")) {
            var rejected =
                    assertThrows(
                            FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                            () ->
                                    FiniteTwoPlayerConditionalActionIntervals
                                            .solvePhysicalFlopExactPowerOfTwo(
                                                    game,
                                                    new FiniteTwoPlayerConditionalActionIntervals
                                                            .Question(3, keys.first(), action),
                                                    1e-8,
                                                    .0001));
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                    rejected.reason());
            assertTrue(rejected.reachFailure().isPresent());
            assertFalse(rejected.reachFailure().orElseThrow().trainerAdmission());
            assertEquals(
                    BoundedLinearProgram.EXACT_POWER_TWO_ALGORITHM,
                    rejected.reachFailure()
                            .orElseThrow()
                            .minimumReachWitness()
                            .certificate()
                            .algorithm());
        }
    }

    @Test
    void savedPhysicalReplayMatchesAndAlteredBoundsAreRejected(@TempDir Path folder)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        SAVED, SixMaxSuppliedFlopBettingStudy.MAX_BYTES),
                                SixMaxSuppliedFlopActionBounds.Report.class);
        assertEquals(result.report(), saved);
        var tree = SixMaxTexturePayoffTable.mapper().valueToTree(saved);
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                        tree.get("decisions").get(0).get("actions").get(1))
                .put("lowerLossBb", 0);
        var altered = folder.resolve("altered.json");
        Files.writeString(altered, tree.toString());
        var rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxSuppliedFlopActionBounds.replay(REQUEST, altered));
        assertTrue(rejected.getMessage().contains("Physical flop action bounds replay differs"));
    }

    @Test
    void ownedResultsCollectionsAndExclusivePlainGzipWritesAreProtected(@TempDir Path folder)
            throws Exception {
        assertTrue(
                Arrays.stream(SixMaxSuppliedFlopActionBounds.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        assertThrows(
                UnsupportedOperationException.class, () -> result.report().decisions().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.report().decisions().getFirst().actions().clear());
        for (String name : List.of("report.json", "report.json.gz")) {
            var path = folder.resolve(name);
            SixMaxSuppliedFlopActionBounds.write(path, result);
            byte[] original = Files.readAllBytes(path);
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxSuppliedFlopActionBounds.write(path, result));
            assertArrayEquals(original, Files.readAllBytes(path));
            assertEquals(
                    result.report(),
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(
                                    SixMaxRankTexturePayoffTable.readBytes(
                                            path, SixMaxSuppliedFlopBettingStudy.MAX_BYTES),
                                    SixMaxSuppliedFlopActionBounds.Report.class));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSuppliedFlopActionBoundsMain.main(
                                    new String[] {"solve", REQUEST.toString(), path.toString()}));
        }
    }

    @Test
    void independentReceiptBindsCheckerHelperRequestAndStoredBytes() throws Exception {
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-flop-action-bounds-independent-check.json")));
        var bindings =
                Map.of(
                        "checkerSourceSha256",
                        Path.of("../scripts/check-supplied-flop-action-bounds.py"),
                        "physicalHelperSourceSha256",
                        Path.of("../scripts/check-supplied-connected-flop.py"),
                        "requestStoredBytesSha256",
                        REQUEST,
                        "reportStoredBytesSha256",
                        SAVED);
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (var b : bindings.entrySet())
            assertEquals(
                    receipt.get(b.getKey()).asText(),
                    HexFormat.of().formatHex(digest.digest(Files.readAllBytes(b.getValue()))));
        assertEquals(27, receipt.get("originalEndpointCertificatesChecked").asInt());
        assertFalse(receipt.get("trainerAdmission").asBoolean());
    }
}
