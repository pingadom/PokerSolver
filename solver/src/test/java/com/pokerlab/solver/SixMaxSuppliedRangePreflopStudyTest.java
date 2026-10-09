package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedRangePreflopStudyTest {
    static SixMaxSuppliedRangePreflopStudy.Result study;
    @TempDir Path temporary;

    @BeforeAll
    static void solve() throws Exception {
        study = SixMaxSuppliedRangePreflopStudy.solve(SixMaxSuppliedRangePreflopGameTest.input());
    }

    @Test
    void savedEighteenWorldStudyReplaysAllPhysicalCountsAndBothReferences() throws Exception {
        var replay =
                SixMaxSuppliedRangePreflopStudy.replay(
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-button-defense-input.json"),
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-button-defense-policy.json"),
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-button-defense-report.json"));
        assertEquals(study.report(), replay.report());
        assertEquals(study.artifact(), replay.artifact());
        assertTrue(study.report().qualifiedForOfflinePractice());
        assertFalse(study.report().trainerAdmission());
        assertEquals(5, study.report().materialDecisions());
        assertEquals(5, study.report().stableDecisions());
        assertEquals(
                List.of(500, 1000),
                study.report().references().stream()
                        .map(SixMaxHeadsUpPreflopStudy.Reference::iterations)
                        .toList());
        assertTrue(
                study.report().references().stream()
                        .allMatch(r -> r.quality().nashConvBb() < .001));
        assertTrue(
                study.report().decisions().stream()
                        .flatMap(d -> d.comparisons().stream())
                        .allMatch(
                                c ->
                                        c.failures().isEmpty()
                                                && c.maximumActionEvDriftBb() < .000016));
        assertEquals(18, study.report().payoffs().size());
        assertEquals(
                11844144,
                study.report().payoffs().stream().mapToLong(p -> p.counts().boards()).sum());
    }

    @Test
    void everyLiteralTerminalConservesDeadMoneyAndUncalledRaises() {
        var game = study.core();
        for (var root : game.chanceOutcomes(game.initialState())) {
            var s = root.state();
            double[] shares = game.payoffs().get(s.dealIndex()).counts().estimate().shares();
            var fold = game.afterAction(s, "fold");
            assertArrayEquals(
                    new double[] {0, 0, 0, 1.5, -.5, -1}, game.terminalUtilities(fold), 1e-12);
            var call = game.afterAction(s, "call");
            assertArrayEquals(
                    new double[] {0, 0, 0, 6.5 * shares[3] - 3, -.5, 6.5 * shares[5] - 3},
                    game.terminalUtilities(call),
                    1e-12);
            var raise = game.afterAction(s, "raise:9.0");
            assertArrayEquals(
                    new double[] {0, 0, 0, -3, -.5, 3.5},
                    game.terminalUtilities(game.afterAction(raise, "fold")),
                    1e-12);
            assertArrayEquals(
                    new double[] {0, 0, 0, 18.5 * shares[3] - 9, -.5, 18.5 * shares[5] - 9},
                    game.terminalUtilities(game.afterAction(raise, "call")),
                    1e-12);
            assertEquals(OptionalDouble.of(-.5), game.inactivePlayerUtility(s, 4));
        }
        assertThrows(IllegalArgumentException.class, () -> game.dealtHands(new State(18, "")));
        assertThrows(IllegalArgumentException.class, () -> game.legalActions(game.initialState()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.afterAction(
                                game.chanceOutcomes(game.initialState()).getFirst().state(),
                                "raise:100.0"));
    }

    @Test
    void everyCandidateAndReferenceActionEvMatchesLiteralBayesAndLeafPayouts() {
        var game = study.core();
        var candidate = study.artifact().orElseThrow().solution();
        var policies = new ArrayList<CfrSolution>();
        policies.add(candidate);
        for (int budget : List.of(500, 1000))
            policies.add(
                    new MultiPlayerCfrSolver<>(
                                    game,
                                    CfrSolver.Variant.CFR_PLUS,
                                    MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY)
                            .solve(budget));
        for (var d : study.report().decisions()) {
            var row = d.primary();
            var posterior = new TreeMap<Integer, Double>();
            double mass = 0;
            for (var world : game.prior())
                if (world.dealtCombos().get(row.actor().ordinal()).equals(row.ownHand())) {
                    double p = world.conditionalProbability();
                    if (row.actor() == Seat.BTN)
                        p *=
                                candidate
                                        .strategy()
                                        .get(
                                                "5:"
                                                        + game.informationSet(
                                                                new State(world.dealIndex(), "")))
                                        .get("raise:9.0");
                    if (p > 0) {
                        posterior.put(world.dealIndex(), p);
                        mass += p;
                    }
                }
            assertEquals(row.decisionProbability(), mass, 1e-12);
            for (int index = 0; index < policies.size(); index++) {
                var policy = policies.get(index);
                var actual =
                        index == 0
                                ? row.values()
                                : d.comparisons().get(index - 1).fixedQuestionReferenceValues();
                for (String action : actual.actionEvBb().keySet()) {
                    double expected = 0;
                    for (var w : posterior.entrySet()) {
                        double[] shares =
                                game.payoffs().get(w.getKey()).counts().estimate().shares();
                        double ev;
                        if (row.actor() == Seat.BTN)
                            ev = action.equals("fold") ? 0 : 18.5 * shares[3] - 6;
                        else if (action.equals("fold")) ev = 0;
                        else if (action.equals("call")) ev = 6.5 * shares[5] - 2;
                        else {
                            var btn = new State(w.getKey(), "|BB:raise:9.0");
                            var response = policy.strategy().get("3:" + game.informationSet(btn));
                            ev =
                                    response.get("fold") * 4.5
                                            + response.get("call") * (18.5 * shares[5] - 8);
                        }
                        expected += w.getValue() / mass * ev;
                    }
                    assertEquals(
                            expected,
                            actual.actionEvBb().get(action),
                            1e-11,
                            row.informationSet() + ":" + action + ":" + index);
                }
            }
        }
    }

    @Test
    void gzipRoundTripAndExclusiveOutputsPreserveEvidence() throws Exception {
        var policy = temporary.resolve("policy.json.gz");
        var report = temporary.resolve("report.json.gz");
        SixMaxSuppliedRangePreflopStudy.write(policy, report, study);
        assertEquals(
                study.report(),
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        report, SixMaxSuppliedRangePreflopStudy.MAX_BYTES),
                                SixMaxSuppliedRangePreflopStudy.Report.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopStudy.write(policy, report, study));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedRangePreflopStudy.write(
                                temporary.resolve("same"), temporary.resolve("same"), study));
        assertEquals(
                study.artifact().orElseThrow(),
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        policy, SixMaxSuppliedRangePreflopStudy.MAX_BYTES),
                                SixMaxSuppliedRangePreflopStudy.Artifact.class));
    }

    @Test
    void tamperedPhysicalCountsCannotBeMadeValidByRehashingArtifacts() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var report = (ObjectNode) mapper.valueToTree(study.report());
        var counts = (ObjectNode) report.path("payoffs").get(0).path("counts");
        counts.put("firstWins", counts.path("firstWins").asLong() + 1);
        counts.put("secondWins", counts.path("secondWins").asLong() - 1);
        var changed = mapper.treeToValue(report, SixMaxSuppliedRangePreflopStudy.Report.class);
        var binding = (ObjectNode) report.path("binding");
        binding.put("payoffHash", SixMaxHeadsUpPreflopGame.hash(changed.payoffs()));
        changed = mapper.treeToValue(report, SixMaxSuppliedRangePreflopStudy.Report.class);
        var artifact = (ObjectNode) mapper.valueToTree(study.artifact().orElseThrow());
        artifact.set("binding", binding);
        artifact.put("reportHash", SixMaxHeadsUpPreflopGame.hash(changed));
        var p = temporary.resolve("policy.json");
        var r = temporary.resolve("report.json");
        Files.writeString(p, mapper.writeValueAsString(artifact));
        Files.writeString(r, mapper.writeValueAsString(report));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedRangePreflopStudy.replay(
                                SixMaxSuppliedRangePreflopGameTest.data(
                                        "supplied-range-button-defense-input.json"),
                                p,
                                r));
    }

    @Test
    void changedInputAndForgedAdmissionFailBeforeExpensiveReplay() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var input = (ObjectNode) mapper.valueToTree(study.report().input());
        ((ObjectNode) input.path("worlds").get(0)).put("weight", 2);
        var path = temporary.resolve("input.json");
        Files.writeString(path, mapper.writeValueAsString(input));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedRangePreflopStudy.replay(
                                path,
                                SixMaxSuppliedRangePreflopGameTest.data(
                                        "supplied-range-button-defense-policy.json"),
                                SixMaxSuppliedRangePreflopGameTest.data(
                                        "supplied-range-button-defense-report.json")));
        var report = (ObjectNode) mapper.valueToTree(study.report());
        report.put("trainerAdmission", true);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(report, SixMaxSuppliedRangePreflopStudy.Report.class));
        var unknown = (ObjectNode) mapper.valueToTree(study.report());
        unknown.put("unexpected", 1);
        String json = mapper.writeValueAsString(unknown);
        assertThrows(
                Exception.class,
                () -> mapper.readValue(json, SixMaxSuppliedRangePreflopStudy.Report.class));
        assertEquals(0, SixMaxSuppliedRangePreflopStudy.Result.class.getConstructors().length);
    }

    @Test
    void strongerHandMenuRemainsRejectedDespiteExcellentWholeGameGap() throws Exception {
        var result =
                SixMaxSuppliedRangePreflopStudy.replay(
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-strong-button-rejected-input.json"),
                        temporary.resolve("absent-policy.json"),
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-strong-button-rejected-report.json"));
        assertFalse(result.report().qualifiedForOfflinePractice());
        assertTrue(result.artifact().isEmpty());
        assertTrue(
                result.report().references().stream()
                        .allMatch(r -> r.quality().nashConvBb() < .000006));
        assertTrue(
                result.report().decisions().stream()
                        .flatMap(d -> d.comparisons().stream())
                        .anyMatch(c -> c.failures().contains("ACTION_EV_DRIFT")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSuppliedRangePreflopTrainer(result));
        var p = temporary.resolve("no-export.json");
        var r = temporary.resolve("diagnostic.json");
        SixMaxSuppliedRangePreflopStudy.write(p, r, result);
        assertFalse(Files.exists(p));
        assertTrue(Files.exists(r));
    }

    @Test
    void seededTrainerShowsBothSeatsAllFiveHandsAndBindsGrading() throws Exception {
        var trainer = new SixMaxSuppliedRangePreflopTrainer(study);
        var seen = new HashSet<String>();
        for (int seed = 0; seed < 100; seed++) {
            var q = trainer.question(seed);
            assertEquals(q, trainer.question(seed));
            seen.add(q.hero() + ":" + q.heroCards());
            assertEquals(SixMaxSuppliedRangePreflopGame.MODEL, q.model());
            assertEquals(SixMaxSuppliedRangePreflopGame.BELIEFS, q.beliefs());
            assertEquals(
                    SixMaxSuppliedRangePreflopGame.HISTORY_REACH, q.sourceHistoryReachStatus());
            assertFalse(q.trainerAdmission());
            assertEquals(q.hero(), q.table().actingSeat());
            assertEquals(
                    4,
                    q.table().players().stream()
                            .filter(p -> p.status() == SixMaxPreflopPublicTable.PlayerStatus.FOLDED)
                            .count());
            for (String action : q.legalActions()) {
                var feedback = trainer.grade(q, action);
                assertTrue(feedback.evLossBb() >= 0);
                assertEquals(
                        feedback.values().actionEvBb().get(action), feedback.selectedActionEvBb());
            }
            assertThrows(IllegalArgumentException.class, () -> trainer.grade(q, "raise:100.0"));
        }
        assertEquals(5, seen.size());
        var actions = new ArrayList<String>();
        for (int i = 0; i < 10; i++)
            actions.add(trainer.sessionQuestion(711, i).legalActions().getFirst());
        var review = trainer.review(711, trainer.studyHash(), actions);
        assertEquals(10, review.attempts().size());
        assertEquals(review.totalEvLossBb() / 10, review.averageEvLossBb());
        assertEquals(review, trainer.review(711, trainer.studyHash(), actions));
        assertThrows(IllegalArgumentException.class, () -> trainer.review(711, "wrong", actions));
        assertThrows(IllegalArgumentException.class, () -> trainer.sessionQuestion(711, 10));
    }

    @Test
    void cliPreflightsAndRejectsAliasesBeforeReadingInputs() throws Exception {
        assertDoesNotThrow(
                () ->
                        SixMaxSuppliedRangePreflopStudyMain.main(
                                new String[] {
                                    "preflight",
                                    SixMaxSuppliedRangePreflopGameTest.data(
                                                    "supplied-range-button-defense-input.json")
                                            .toString()
                                }));
        var missing = temporary.resolve("missing.json").toString();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedRangePreflopStudyMain.main(
                                new String[] {
                                    "solve",
                                    missing,
                                    missing,
                                    temporary.resolve("out.json").toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopStudyMain.main(new String[] {"solve"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopTrainerMain.main(new String[] {"question"}));
    }
}
