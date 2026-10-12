package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.Phase;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedFlopBettingGameTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-four-button-hand-connected-flop-request.json");
    static final Path SAVED =
            Path.of("../docs/data/supplied-four-button-hand-connected-flop-report.json.gz");
    static SixMaxSuppliedFlopBettingStudy.Request request;
    static SixMaxSuppliedRangePreflopGame source;
    static SixMaxSuppliedFlopBettingGame game;
    static SixMaxSuppliedFlopBettingStudy.Result result;

    @BeforeAll
    static void solvePhysicalGame() throws Exception {
        request = SixMaxSuppliedFlopBettingStudy.readRequest(REQUEST);
        source = new SixMaxSuppliedRangePreflopGame(request.input());
        game = new SixMaxSuppliedFlopBettingGame(source, request.flop(), request.betPotFraction());
        result = SixMaxSuppliedFlopBettingStudy.solve(request, game);
    }

    static State boundary(String... actions) {
        var state = game.chanceOutcomes(game.initialState()).getFirst().state();
        for (String action : actions) state = game.afterAction(state, action);
        return state;
    }

    static State flop(State boundary) {
        return game.chanceOutcomes(boundary).getFirst().state();
    }

    static double[] play(State state, String... actions) {
        for (String action : actions) state = game.afterAction(state, action);
        return game.terminalUtilities(state);
    }

    @Test
    void originalPhysicalSourceAndPriorAreUnchangedAndOnlyTwoPlayersAct() throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-reach-bounds-report.json.gz"),
                                        8 * 1024 * 1024),
                                SixMaxSuppliedReachBounds.Report.class);
        assertEquals(saved.binding(), source.binding());
        assertEquals(saved.payoffs(), source.payoffs());
        assertEquals(saved.prior(), source.prior());
        var roots = game.chanceOutcomes(game.initialState());
        assertEquals(24, roots.size());
        for (int i = 0; i < roots.size(); i++) {
            assertEquals(i, roots.get(i).state().originalDealIndex());
            assertEquals(
                    source.prior().get(i).conditionalProbability(), roots.get(i).probability());
        }
        var audit = result.report().solve();
        assertEquals(3, audit.firstActor());
        assertEquals(5, audit.secondActor());
        assertEquals(.5, audit.constantSum(), 1e-12);
        assertFalse(result.report().trainerAdmission());
    }

    @Test
    void completePerfectRecallTreeMatchesThePreflightAndFitsUnchangedCaps() {
        var budget = game.binding().budget();
        var audit = result.report().solve();
        assertEquals(1321, budget.completeTreeStates());
        assertEquals(1321, audit.treeNodes());
        assertEquals(40, budget.informationSets().get(Seat.BTN));
        assertEquals(33, budget.informationSets().get(Seat.BB));
        assertEquals(89, audit.firstFlow().sequences().size());
        assertEquals(73, audit.secondFlow().sequences().size());
        assertEquals(4, game.handoffs().size());
        assertEquals(96, game.residuals().size());
        assertEquals(4 * 24 * 666, budget.additionalEnumeratedRunouts());
        assertEquals(2 * budget.additionalEnumeratedRunouts(), budget.additionalHandEvaluations());
        for (var info : audit.informationSets()) {
            assertFalse(
                    info.key().contains("4c 4d")); // Opponent/folded cards do not leak into keys.
            if (info.key().contains("supplied-flop-one-bet:") && info.actor() == 5)
                assertFalse(info.ownHistory().isEmpty());
        }
    }

    @Test
    void explicitlyOrderedOriginalCertificatesAndFullGameBestResponsesAgree() {
        var audit = result.report().solve();
        assertEquals(FiniteTwoPlayerAffineSequenceForm.ASCENDING_ROWS_ALGORITHM, audit.algorithm());
        for (var certificate : List.of(audit.firstLp(), audit.secondLp())) {
            assertEquals(BoundedLinearProgram.ASCENDING_L1_ALGORITHM, certificate.algorithm());
            assertTrue(certificate.absoluteDualityGap() <= 1e-8);
            assertTrue(certificate.maximumPrimalViolation() <= 1e-8);
            assertTrue(certificate.maximumDualViolation() <= 1e-8);
            assertTrue(
                    certificate.work().arithmeticWork()
                            <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        }
        assertEquals(.51929118674596, audit.lowerValue(), 1e-10);
        assertEquals(audit.lowerValue(), audit.upperValue(), 1e-8);
        assertTrue(audit.behavioralQuality().nashConvBb() <= 1e-8);
        assertTrue(audit.firstFlow().maximumResidual() <= 1e-8);
        assertTrue(audit.secondFlow().maximumResidual() <= 1e-8);
    }

    @Test
    void selectedChanceKeepsTheParentWorldAndAllOtherFlopsKeepTheirMass() {
        for (var root : game.chanceOutcomes(game.initialState())) {
            var boundary = game.afterAction(root.state(), "call");
            var outcomes = game.chanceOutcomes(boundary);
            assertEquals(2, outcomes.size());
            assertEquals(1.0 / 9880, outcomes.getFirst().probability());
            assertEquals(1 - 1.0 / 9880, outcomes.getLast().probability());
            for (var o : outcomes)
                assertEquals(root.state().originalDealIndex(), o.state().originalDealIndex());
            assertEquals(5, game.currentPlayer(outcomes.getFirst().state()));
            assertEquals(List.of("check", "bet"), game.legalActions(outcomes.getFirst().state()));
            assertTrue(game.isTerminal(outcomes.getLast().state()));
        }
    }

    @Test
    void exactResidualUsesTenFlopPartitionsAndRecoversEveryOriginalCheckdownUtility() {
        double p = 1.0 / 9880;
        for (var handoff : game.handoffs())
            for (var world : handoff.worlds()) {
                var residual =
                        game.residuals().stream()
                                .filter(
                                        r ->
                                                r.publicHistory().equals(handoff.publicHistory())
                                                        && r.originalDealIndex()
                                                                == world.originalDealIndex())
                                .findFirst()
                                .orElseThrow();
                assertEquals(6_579_414, residual.counts().boardFlopPartitions());
                var original =
                        source.terminalUtilities(
                                new SixMaxHeadsUpPreflopGame.State(
                                        world.originalDealIndex(), handoff.publicHistory()));
                for (int seat = 0; seat < 6; seat++)
                    assertEquals(
                            original[seat],
                            p * world.checkdownUtilitiesBb().get(seat)
                                    + (1 - p) * residual.utilitiesBb().get(seat),
                            1e-12);
            }
    }

    @Test
    void literalPotSettlementReturnsUncalledBetsAndConservesEverySeat() {
        var flop = flop(boundary("call"));
        var bbWins = play(flop, "bet", "fold");
        assertEquals(3.5, bbWins[5], 1e-12);
        assertEquals(-3, bbWins[3], 1e-12);
        assertEquals(-.5, bbWins[4], 1e-12);
        var btnWins = play(flop, "check", "bet", "fold");
        assertEquals(3.5, btnWins[3], 1e-12);
        assertEquals(-3, btnWins[5], 1e-12);
        var world = game.handoffs().getFirst().worlds().getFirst();
        double firstShare = world.counts().estimate().shares()[3];
        var called = play(flop, "bet", "call");
        assertEquals(13 * firstShare - 6.25, called[3], 1e-12);
        var checked = play(flop, "check", "check");
        assertArrayEquals(
                world.checkdownUtilitiesBb().stream().mapToDouble(Double::doubleValue).toArray(),
                checked,
                1e-12);
        for (var u : List.of(bbWins, btnWins, called, checked))
            assertEquals(0, Arrays.stream(u).sum(), 1e-12);
    }

    @Test
    void foldedCardsChangeRunoutCountsEvenWithIdenticalActiveHands() {
        var handoff = game.handoffs().getFirst();
        var variants =
                handoff.worlds().stream()
                        .filter(
                                w ->
                                        w.sixDealtCombos().get(3).equals("9s Js")
                                                && w.sixDealtCombos().get(5).equals("7d 7h"))
                        .toList();
        assertEquals(2, variants.size());
        assertEquals(
                Set.of(653L, 654L),
                variants.stream()
                        .map(w -> w.counts().firstWins())
                        .collect(java.util.stream.Collectors.toSet()));
        for (var w : variants) {
            assertEquals(666, w.counts().runouts());
            assertEquals(1332, w.counts().handEvaluations());
            assertEquals(1.0 / 24, w.originalJointProbability());
            assertEquals(1.0 / 24, w.privateGivenPhysicalFlopWeight());
        }
    }

    @Test
    void betsCapAtRemainingStacksAndSmallBlindActsBeforeButton() throws Exception {
        var capped = new SixMaxSuppliedFlopBettingGame(source, request.flop(), 1);
        var state = capped.chanceOutcomes(capped.initialState()).getFirst().state();
        for (String action : List.of("raise:9.0", "raise:22.0", "raise:50.0", "call"))
            state = capped.afterAction(state, action);
        state = capped.chanceOutcomes(state).getFirst().state();
        state = capped.afterAction(capped.afterAction(state, "bet"), "call");
        var cappedUtility = capped.terminalUtilities(state);
        double share =
                capped.handoffs().getLast().worlds().getFirst().counts().estimate().shares()[3];
        assertEquals(200.5 * share - 100, cappedUtility[3], 1e-12);
        assertEquals(0, Arrays.stream(cappedUtility).sum(), 1e-12);

        var history = new ArrayList<>(request.input().specification().history().subList(0, 4));
        history.add(new SixMaxPreflopResearchTrainer.PublicAction(Seat.SB, "raise:9.0"));
        history.add(new SixMaxPreflopResearchTrainer.PublicAction(Seat.BB, "fold"));
        var input =
                new SixMaxSuppliedRangePreflopGame.Input(
                        SixMaxSuppliedRangePreflopGame.INPUT_SCHEMA,
                        new SixMaxHeadsUpPreflopGame.Specification(
                                history, request.input().specification().rules()),
                        List.of(request.input().worlds().getFirst()));
        var blind = new SixMaxSuppliedFlopBettingGame(input, request.flop(), .5);
        var root = blind.chanceOutcomes(blind.initialState()).getFirst().state();
        var selected = blind.chanceOutcomes(blind.afterAction(root, "call")).getFirst().state();
        assertEquals(4, blind.currentPlayer(selected));
        assertEquals(3, blind.currentPlayer(blind.afterAction(selected, "check")));
        assertEquals(19, blind.handoffs().getFirst().potBb());
        var terminal = blind.afterAction(blind.afterAction(selected, "bet"), "fold");
        assertEquals(-1, blind.terminalUtilities(terminal)[5]);
        assertEquals(0, Arrays.stream(blind.terminalUtilities(terminal)).sum(), 1e-12);
    }

    @Test
    void blockedWorldsAreFilteredOnlyAtTheFlopAndNeverFromOriginalChance() throws Exception {
        var partial = new SixMaxSuppliedFlopBettingGame(source, List.of("2d", "8s", "Ts"), .5);
        assertEquals(24, partial.chanceOutcomes(partial.initialState()).size());
        assertEquals(841, partial.binding().budget().completeTreeStates());
        double selectedMass = 0;
        for (var root : partial.chanceOutcomes(partial.initialState())) {
            var b = partial.afterAction(root.state(), "call");
            if (partial.isTerminal(b)) continue;
            selectedMass += root.probability() * partial.chanceOutcomes(b).getFirst().probability();
        }
        assertEquals(.5 / 9880, selectedMass, 1e-15);
        for (var h : partial.handoffs()) {
            assertEquals(12, h.worlds().size());
            assertEquals(.5, h.compatibleOriginalPriorMass(), 1e-12);
            for (var w : h.worlds()) {
                assertEquals(1.0 / 24, w.originalJointProbability());
                assertEquals(1.0 / 12, w.privateGivenPhysicalFlopWeight());
            }
        }
    }

    @Test
    void invalidFlopsFractionsBoundariesAndStatesReturnNoOwnedHandle() {
        for (var bad :
                List.of(
                        List.of("2s", "2s", "Ts"),
                        List.of("4c", "8s", "Ts"),
                        List.of("AS", "8s", "Ts")))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedFlopBettingGame.preflight(request.input(), bad, .5));
        for (double bad : List.of(0.0, -1.0, 1.01, Double.NaN, Double.POSITIVE_INFINITY))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSuppliedFlopBettingGame.preflight(
                                    request.input(), request.flop(), bad));
        for (var path :
                List.of(
                        List.<String>of(),
                        List.of("fold"),
                        List.of("raise:9.0", "raise:22.0", "raise:50.0", "raise:100.0", "call")))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedFlopHandoff.capture(source, path, request.flop()));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.currentPlayer(new State(0, "", Phase.FLOP, "")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.afterAction(flop(boundary("call")), "raise"));
        assertThrows(
                IllegalArgumentException.class, () -> game.chanceOutcomes(flop(boundary("call"))));
    }

    @Test
    void reachedFlopQualityNeverManufacturesAnUnreachedPosterior() {
        double sum = 0;
        for (var local : result.report().reachedFlopQuality()) {
            assertEquals(SixMaxSuppliedFlopBettingStudy.LOCAL_SCOPE, local.scope());
            sum += local.candidateRootFlopReach();
            if (local.conditioning()
                    == SixMaxSuppliedFlopBettingStudy.Conditioning.BELOW_CANDIDATE_REACH_FLOOR) {
                assertNull(local.conditionalBestResponse());
                assertTrue(local.reachedWorlds().isEmpty());
                assertTrue(local.candidateBoundaryReachGivenPhysicalFlop() < .0001);
            } else {
                assertEquals(
                        1,
                        local.reachedWorlds().stream()
                                .mapToDouble(
                                        SixMaxSuppliedFlopBettingStudy.ReachedWorld
                                                ::conditionalWeight)
                                .sum(),
                        1e-12);
                assertEquals(
                        local.candidateRootFlopReach(),
                        local.reachedWorlds().stream()
                                .mapToDouble(
                                        SixMaxSuppliedFlopBettingStudy.ReachedWorld
                                                ::jointRootFlopReach)
                                .sum(),
                        1e-15);
                assertTrue(local.conditionalBestResponse().nashConvBb() <= 1e-8);
            }
            assertEquals(
                    local.candidateRootFlopReach(),
                    local.rawWorldMasses().stream()
                            .mapToDouble(
                                    SixMaxSuppliedFlopBettingStudy.WorldMass::jointRootFlopReach)
                            .sum(),
                    1e-15);
        }
        assertTrue(sum <= 1.0 / 9880 + 1e-15);
        assertTrue(sum > 0);
    }

    @Test
    void immutableOwnedReportsCannotBeManufacturedByParsingAuditData(@TempDir Path folder)
            throws Exception {
        assertTrue(
                Arrays.stream(SixMaxSuppliedFlopHandoff.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertTrue(
                Arrays.stream(SixMaxSuppliedFlopBettingStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> game.handoffs().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> game.handoffs().getFirst().worlds().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> game.residuals().getFirst().utilitiesBb().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.report().candidate().strategy().clear());
        for (String name : List.of("report.json", "report.json.gz")) {
            var output = folder.resolve(name);
            SixMaxSuppliedFlopBettingStudy.write(output, result);
            var original = Files.readAllBytes(output);
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxSuppliedFlopBettingStudy.write(output, result));
            assertArrayEquals(original, Files.readAllBytes(output));
            assertEquals(
                    result.report(),
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(
                                    SixMaxRankTexturePayoffTable.readBytes(output, 8 * 1024 * 1024),
                                    SixMaxSuppliedFlopBettingStudy.Report.class));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopBettingStudyMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    folder.resolve("report.json").toString()
                                }));
    }

    @Test
    void savedReportPhysicallyReplaysAndMutationsFail(@TempDir Path folder) throws Exception {
        assertEquals(
                result.report(), SixMaxSuppliedFlopBettingStudy.replay(REQUEST, SAVED).report());
        var tree = SixMaxTexturePayoffTable.mapper().valueToTree(result.report());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("trainerAdmission", true);
        var changed = folder.resolve("admitted.json");
        Files.writeString(changed, tree.toString());
        assertThrows(
                Exception.class, () -> SixMaxSuppliedFlopBettingStudy.replay(REQUEST, changed));
        tree = SixMaxTexturePayoffTable.mapper().valueToTree(result.report());
        var handoffs = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("handoffs");
        var counts =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        handoffs.get(0).get("worlds").get(0).get("counts");
        counts.put("firstWins", counts.get("firstWins").asLong() + 1);
        counts.put("secondWins", counts.get("secondWins").asLong() - 1);
        var altered =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(handoffs, SixMaxSuppliedFlopHandoff.Audit[].class);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("binding"))
                .put("handoffsHash", SixMaxHeadsUpPreflopGame.hash(altered));
        var wrong = folder.resolve("wrong-counts.json");
        Files.writeString(wrong, tree.toString());
        var rejected =
                assertThrows(
                        Exception.class,
                        () -> SixMaxSuppliedFlopBettingStudy.replay(REQUEST, wrong));
        assertTrue(rejected.getMessage().contains("Physical connected flop replay differs"));
        assertThrows(
                Exception.class, () -> SixMaxSuppliedFlopBettingStudy.replay(REQUEST, REQUEST));
    }

    @Test
    void recordedIndependentReceiptBindsExactSourceRequestAndStoredReportBytes() throws Exception {
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-connected-flop-independent-check.json")));
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (var binding :
                Map.of(
                                "checkerSourceSha256",
                                Path.of("../scripts/check-supplied-connected-flop.py"),
                                "requestStoredBytesSha256",
                                REQUEST,
                                "reportStoredBytesSha256",
                                SAVED)
                        .entrySet()) {
            var hash =
                    HexFormat.of().formatHex(digest.digest(Files.readAllBytes(binding.getValue())));
            assertEquals(hash, receipt.get(binding.getKey()).asText());
        }
        assertEquals(0, receipt.get("freshLpSolves").asInt());
        assertFalse(receipt.get("originalLpCertificatesIndependentlyChecked").asBoolean());
        assertEquals(1, receipt.get("reachedFlopQualityChecks").asInt());
        assertEquals(3, receipt.get("suppressedTinyReachPosteriors").asInt());
    }
}
