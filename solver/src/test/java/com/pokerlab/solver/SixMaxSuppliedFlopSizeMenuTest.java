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

class SixMaxSuppliedFlopSizeMenuTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-flop-size-menu-request.json");
    static final Path REPORT = Path.of("../docs/data/supplied-flop-size-menu-report.json.gz");
    static SixMaxSuppliedFlopSizeMenuStudy.Request request;
    static SixMaxSuppliedFlopBettingGame carrier;
    static SixMaxSuppliedFlopSizeMenuGame game;
    static SixMaxSuppliedFlopSizeMenuStudy.Result result;

    @BeforeAll
    static void physicalReplay() throws Exception {
        request = SixMaxSuppliedFlopSizeMenuStudy.readRequest(REQUEST);
        carrier = new SixMaxSuppliedFlopBettingGame(request.input(), request.flop(), .5);
        game = new SixMaxSuppliedFlopSizeMenuGame(carrier, request.menu());
        result = SixMaxSuppliedFlopSizeMenuStudy.solve(request, game);
    }

    static State root(int world, String history) {
        return new State(world, history, Phase.FLOP, "");
    }

    static State start() {
        return root(0, "|BB:call");
    }

    static double[] play(State state, String... actions) {
        for (String action : actions) state = game.afterAction(state, action);
        return game.terminalUtilities(state);
    }

    @Test
    void fullSavedReportReplaysExactly() throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        REPORT, SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES),
                                SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        assertEquals(saved, result.report());
        assertFalse(saved.trainerAdmission());
        assertFalse(saved.binding().trainerAdmission());
        assertEquals(carrier.handoffs(), saved.handoffs());
        assertEquals(carrier.residuals(), saved.residuals());
        assertEquals(carrier.source().payoffs(), saved.sourcePayoffs());
    }

    @Test
    void unchangedCapsAdmitAsymmetricMenuAndRejectBothPlayersHavingTwoSizes() {
        var b = game.binding().budget();
        assertEquals(1609, b.completeTreeStates());
        assertEquals(Map.of(Seat.BTN, 56, Seat.BB, 33), b.informationSets());
        assertEquals(Map.of(Seat.BTN, 121, Seat.BB, 85), b.sequences());
        assertEquals(63936, b.additionalEnumeratedRunouts());
        assertEquals(24, b.compatibleDeals());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopSizeMenuGame.preflight(
                                request.input(),
                                request.flop(),
                                new SixMaxSuppliedFlopSizeMenuGame.Menu(
                                        List.of(.25, .5), List.of(.25, .5))));
    }

    @Test
    void sizeAliasesAreRejectedBeforePhysicalEvaluation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopSizeMenuGame.preflight(
                                request.input(),
                                request.flop(),
                                new SixMaxSuppliedFlopSizeMenuGame.Menu(
                                        List.of(.5, 1.0), List.of(1.0))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopSizeMenuGame.preflight(
                                request.input(),
                                request.flop(),
                                new SixMaxSuppliedFlopSizeMenuGame.Menu(
                                        List.of(.25, .250000001), List.of(1.0))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopSizeMenuGame.preflight(
                                request.input(),
                                request.flop(),
                                new SixMaxSuppliedFlopSizeMenuGame.Menu(
                                        List.of(1e-12), List.of(1.0))));
    }

    @Test
    void menusAreImmutableOrderedFiniteAndBounded() {
        for (var sizes :
                List.of(
                        List.of(0.0),
                        List.of(-.5),
                        List.of(Double.NaN),
                        List.of(Double.POSITIVE_INFINITY),
                        List.of(1.1),
                        List.of(.5, .25),
                        List.of(.5, .5),
                        List.<Double>of(),
                        List.of(.1, .2, .3, .4, .5)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxSuppliedFlopSizeMenuGame.Menu(sizes, List.of(.5)));
        var mutable = new ArrayList<>(List.of(.25, .5));
        var menu = new SixMaxSuppliedFlopSizeMenuGame.Menu(mutable, List.of(1.0));
        mutable.clear();
        assertEquals(List.of(.25, .5), menu.firstToAct());
        assertThrows(UnsupportedOperationException.class, () -> menu.firstToAct().clear());
    }

    static void equivalent(SixMaxSuppliedFlopSizeMenuGame menu, State old, State replacement) {
        assertEquals(carrier.currentPlayer(old), menu.currentPlayer(replacement));
        if (carrier.isTerminal(old)) {
            assertArrayEquals(carrier.terminalUtilities(old), menu.terminalUtilities(replacement));
        } else if (carrier.currentPlayer(old) == -1) {
            var left = carrier.chanceOutcomes(old);
            var right = menu.chanceOutcomes(replacement);
            assertEquals(left.size(), right.size());
            for (int i = 0; i < left.size(); i++) {
                assertEquals(left.get(i).probability(), right.get(i).probability());
                equivalent(menu, left.get(i).state(), right.get(i).state());
            }
        } else {
            assertNotEquals(carrier.informationSet(old), menu.informationSet(replacement));
            for (String action : carrier.legalActions(old))
                equivalent(
                        menu,
                        carrier.afterAction(old, action),
                        menu.afterAction(replacement, action.equals("bet") ? "bet:0.5" : action));
        }
    }

    @Test
    void singletonMenuIsBitExactToOriginalPhysicalTreeWithoutPolicyTransfer() {
        var singleton =
                new SixMaxSuppliedFlopSizeMenuGame(
                        carrier, new SixMaxSuppliedFlopSizeMenuGame.Menu(List.of(.5), List.of(.5)));
        assertEquals(1321, singleton.binding().budget().completeTreeStates());
        equivalent(singleton, carrier.initialState(), singleton.initialState());
    }

    @Test
    void sizeTaggedHistoryPreservesPerfectRecallWithoutLeakingWorldOrOpponentCards() {
        var seen = new HashMap<String, String>();
        for (var handoff : game.handoffs())
            for (var world : handoff.worlds()) {
                var state = root(world.originalDealIndex(), handoff.publicHistory());
                for (String size : List.of("bet:0.25", "bet:0.5")) {
                    var facing = game.afterAction(state, size);
                    String key = game.informationSet(facing);
                    assertTrue(key.endsWith(":" + size));
                    String identity =
                            world.sixDealtCombos().get(3) + handoff.publicHistory() + size;
                    var previous = seen.putIfAbsent(identity, key);
                    if (previous != null) assertEquals(previous, key);
                }
                assertNotEquals(
                        game.informationSet(game.afterAction(state, "bet:0.25")),
                        game.informationSet(game.afterAction(state, "bet:0.5")));
            }
        assertEquals(32, seen.size());
        assertEquals(89, result.report().candidate().strategy().size());
        assertEquals(1609, FiniteTwoPlayerSequenceForm.checked(game).treeNodes());
    }

    @Test
    void actualAmountsAreVisibleWithoutPrivateCardsAndUseBoundaryPotNotGrowingPot() {
        var view = game.publicFlopState(start());
        assertEquals(Seat.BB, view.actingSeat());
        assertEquals(6.5, view.potBb());
        assertEquals(
                List.of(
                        new SixMaxSuppliedFlopSizeMenuGame.PublicAction("check", 0),
                        new SixMaxSuppliedFlopSizeMenuGame.PublicAction("bet:0.25", 1.625),
                        new SixMaxSuppliedFlopSizeMenuGame.PublicAction("bet:0.5", 3.25)),
                view.actions());
        var facing = game.publicFlopState(game.afterAction(start(), "bet:0.25"));
        assertEquals(Seat.BTN, facing.actingSeat());
        assertEquals(8.125, facing.potBb());
        assertEquals(1.625, facing.toCallBb());
        assertEquals(1.625, facing.actions().getLast().amountBb());
        var afterCheck = game.publicFlopState(game.afterAction(start(), "check"));
        assertEquals(6.5, afterCheck.actions().getLast().amountBb());
        for (var handoff : game.handoffs())
            for (var world : handoff.worlds()) {
                var state = root(world.originalDealIndex(), handoff.publicHistory());
                assertEquals(
                        game.publicFlopState(root(0, handoff.publicHistory())),
                        game.publicFlopState(state));
                for (String size : List.of("bet:0.25", "bet:0.5")) {
                    var publicView = game.publicFlopState(game.afterAction(state, size));
                    for (var player : publicView.players()) {
                        assertEquals(100, player.committedBb() + player.remainingStackBb());
                        assertTrue(player.remainingStackBb() >= 0);
                    }
                }
            }
    }

    @Test
    void foldRefundsUncalledBetsWhileCalledSizesChangePayoffsAndConserveChips() {
        for (var handoff : game.handoffs())
            for (var world : handoff.worlds()) {
                var state = root(world.originalDealIndex(), handoff.publicHistory());
                var quarterFold = play(state, "bet:0.25", "fold");
                assertArrayEquals(quarterFold, play(state, "bet:0.5", "fold"));
                for (String size : List.of("bet:0.25", "bet:0.5")) {
                    var call = play(state, size, "call");
                    assertEquals(0, Arrays.stream(call).sum(), 1e-12);
                    double amount =
                            game.publicFlopState(state).actions().stream()
                                    .filter(a -> a.action().equals(size))
                                    .findFirst()
                                    .orElseThrow()
                                    .amountBb();
                    double share = (world.counts().firstWins() + .5 * world.counts().ties()) / 666;
                    assertEquals(
                            world.checkdownUtilitiesBb().get(3) + amount * (2 * share - 1),
                            call[3],
                            1e-12);
                    assertEquals(0, Arrays.stream(quarterFold).sum(), 1e-12);
                }
            }
    }

    @Test
    void foreignActionsStatesAndMutableViewsCannotEnterTheGame() {
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(start(), "bet"));
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(start(), "bet:1.0"));
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(start(), "bet:0.250"));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.currentPlayer(new State(999, "", Phase.FLOP, "")));
        assertThrows(
                IllegalArgumentException.class, () -> game.publicFlopState(game.initialState()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.publicFlopState(
                                game.afterAction(game.afterAction(start(), "bet:0.25"), "fold")));
        var view = game.publicFlopState(start());
        assertThrows(UnsupportedOperationException.class, () -> view.actions().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.players().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.flopHistory().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> game.binding().budget().sequences().clear());
        var terminal = game.afterAction(game.afterAction(start(), "check"), "check");
        var utilities = game.terminalUtilities(terminal);
        utilities[0] = 99;
        assertEquals(0, game.terminalUtilities(terminal)[0]);
    }

    @Test
    void ownedSolveHasBothCertificatesAndTwoFreshBoundedReferencesWithIndependentQuality() {
        var report = result.report();
        assertTrue(report.solve().behavioralQuality().nashConvBb() < 1e-8);
        assertEquals(report.solve().firstLp(), report.firstLpWitness().certificate());
        assertEquals(report.solve().secondLp(), report.secondLpWitness().certificate());
        for (var reference : report.references()) {
            assertEquals(1609, reference.validatedTreeNodes());
            assertEquals(Map.of(0, 0.0, 1, 0.0, 2, 0.0, 4, -.5), reference.inactiveUtilities());
            assertEquals(
                    reference.quality(),
                    MultiPlayerInformationSetBestResponse.assess(game, reference.policy()));
            assertTrue(reference.quality().nashConvBb() < .001);
            assertEquals(4L * reference.policy().iterations(), reference.prunedSuffixRoots());
            assertEquals(0, reference.statistics().sampledChanceNodes());
            assertEquals(0, reference.statistics().baselineCorrections());
        }
        assertNotEquals(
                report.references().getFirst().policyHash(),
                report.references().getLast().policyHash());
        assertTrue(
                Arrays.stream(
                                SixMaxSuppliedFlopSizeMenuStudy.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void strictRequestParsingRejectsDuplicateUnknownNonfiniteAndTrailingData(@TempDir Path temp)
            throws Exception {
        var json = Files.readString(REQUEST);
        var invalid =
                List.of(
                        json.replace("\"menu\":", "\"menu\":{},\"menu\":"),
                        json.replace("\"afterCheck\"", "\"unknown\""),
                        json.replace("0.25", "1e999"),
                        json + " {}",
                        json.replace(
                                "pokerlab-supplied-flop-size-menu-request/v1",
                                "pokerlab-supplied-connected-flop-request/v1"));
        for (int i = 0; i < invalid.size(); i++) {
            var path = temp.resolve(i + ".json");
            Files.writeString(path, invalid.get(i));
            assertThrows(Exception.class, () -> SixMaxSuppliedFlopSizeMenuStudy.readRequest(path));
        }
    }

    @Test
    void cliRejectsExistingOutputAndAliasesBeforeExpensiveWork(@TempDir Path temp)
            throws Exception {
        var output = temp.resolve("already.json");
        Files.writeString(output, "protected");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopSizeMenuMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertEquals("protected", Files.readString(output));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedFlopSizeMenuMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        var preflight = temp.resolve("budget.json");
        SixMaxSuppliedFlopSizeMenuMain.main(
                new String[] {"preflight", REQUEST.toString(), preflight.toString()});
        assertTrue(Files.readString(preflight).contains("1609"));
    }

    @Test
    void reportWritesExclusivelyAndRejectsCorruptIdentityWithoutOwningResult(@TempDir Path temp)
            throws Exception {
        var target = temp.resolve("report.json.gz");
        SixMaxSuppliedFlopSizeMenuStudy.write(target, result);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSuppliedFlopSizeMenuStudy.write(target, result));
        var json = SixMaxTextureStudy.json(result.report());
        for (String poisoned :
                List.of(
                        json.replace("\"trainerAdmission\" : false", "\"trainerAdmission\" : true"),
                        json.replace(result.report().solutionHash(), "0".repeat(64))))
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxTexturePayoffTable.mapper()
                                    .readValue(
                                            poisoned,
                                            SixMaxSuppliedFlopSizeMenuStudy.Report.class));
    }
}
