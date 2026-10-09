package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxHeadsUpPreflopStudyTest {
    static SixMaxPreflopSolutionPack source;
    static List<SixMaxHeadsUpPreflopStudy.Result> studies;
    static final List<String> LABELS = List.of("three-nine", "three-nine-twentytwo", "five-target");
    @TempDir Path temporary;

    @BeforeAll
    static void solve() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        var results = new ArrayList<SixMaxHeadsUpPreflopStudy.Result>();
        for (String label : LABELS)
            results.add(SixMaxHeadsUpPreflopStudy.solve(source, specification(label)));
        studies = List.copyOf(results);
    }

    @AfterAll
    static void release() {
        source = null;
        studies = null;
    }

    static Path data(String label, String kind) {
        return Path.of("../docs/data/heads-up-preflop-" + label + "-" + kind + ".json");
    }

    static SixMaxHeadsUpPreflopGame.Specification specification(String label) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        Files.readAllBytes(data(label, "specification")),
                        SixMaxHeadsUpPreflopGame.Specification.class);
    }

    @Test
    void savedEvidenceExactlyReproducesBothAcceptedMenusAndRejectedLargerMenu() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (int i = 0; i < 3; i++) {
            var study = studies.get(i);
            String label = LABELS.get(i);
            assertEquals(
                    study.report(),
                    mapper.readValue(
                            Files.readAllBytes(data(label, "report")),
                            SixMaxHeadsUpPreflopStudy.Report.class));
            assertEquals(i < 2, study.report().accepted());
            assertFalse(study.report().trainerAdmission());
            assertEquals(i == 0 ? 4 : 5, study.report().materialDecisions());
            assertEquals(i == 0 ? 4 : i == 1 ? 5 : 0, study.report().stableDecisions());
            assertEquals(
                    List.of(500, 1000),
                    study.report().references().stream()
                            .map(SixMaxHeadsUpPreflopStudy.Reference::iterations)
                            .toList());
            assertEquals(
                    FiniteTwoPlayerAffineSequenceForm.ALGORITHM,
                    study.report().solve().algorithm());
            assertTrue(study.report().solve().behavioralQuality().nashConvBb() < 1e-8);
            if (i < 2)
                assertEquals(
                        study.artifact().orElseThrow(),
                        mapper.readValue(
                                Files.readAllBytes(data(label, "policy")),
                                SixMaxHeadsUpPreflopStudy.Artifact.class));
            else {
                assertFalse(Files.exists(data(label, "policy")));
                assertTrue(
                        study.report().rejectionReasons().contains("UNSTABLE_MATERIAL_DECISIONS"));
            }
        }
        assertTrue(
                studies.get(2).report().decisions().stream()
                        .flatMap(d -> d.comparisons().stream())
                        .anyMatch(c -> c.failures().contains("ACTION_EV_DRIFT")));
    }

    @Test
    void completeSourcePolicyConditionsJointWorldsIncludingConcealedFoldedCards() {
        var game = studies.getFirst().core();
        var original = source.rebuildGame();
        double reach = 0;
        var masses = new TreeMap<Integer, Double>();
        for (var root : original.chanceOutcomes(original.initialState())) {
            var state = root.state();
            double likelihood = 1;
            for (var action : game.binding().specification().history()) {
                likelihood *=
                        source.solution()
                                .at(original.currentPlayer(state), original.informationSet(state))
                                .get(action.action());
                state = original.afterAction(state, action.action());
            }
            double mass = root.probability() * likelihood;
            reach += mass;
            masses.put(state.dealIndex(), mass);
        }
        assertEquals(.049379766642009104, reach, 1e-15);
        assertEquals(reach, game.binding().historyReach(), 1e-15);
        assertEquals(12, game.posterior().size());
        double total = 0;
        for (var posterior : game.posterior()) {
            assertEquals(6, posterior.dealtCombos().size());
            assertEquals(
                    masses.get(posterior.dealIndex()) / reach,
                    posterior.conditionalProbability(),
                    1e-15);
            total += posterior.conditionalProbability();
            assertTrue(posterior.historyLikelihood() > 0);
        }
        assertEquals(1, total, 1e-15);
        assertEquals(List.of(Seat.CO, Seat.BB), game.activeSeats());
        for (int folded : List.of(Seat.HJ.ordinal(), Seat.BTN.ordinal()))
            assertEquals(
                    2,
                    game.posterior().stream()
                            .map(p -> p.dealtCombos().get(folded))
                            .distinct()
                            .count());
        assertTrue(
                game.posterior().stream()
                        .anyMatch(p -> p.dealtCombos().get(Seat.HJ.ordinal()).contains("Ah")));
        assertTrue(
                game.posterior().stream()
                        .anyMatch(p -> p.dealtCombos().get(Seat.CO.ordinal()).contains("Ah")));
        assertFalse(
                game.posterior().stream()
                        .anyMatch(
                                p ->
                                        p.dealtCombos().get(Seat.HJ.ordinal()).contains("Ah")
                                                && p.dealtCombos()
                                                        .get(Seat.CO.ordinal())
                                                        .contains("Ah")));
        assertNotEquals(
                game.posterior().getFirst().sourceProbability(),
                game.posterior().getFirst().conditionalProbability());
        assertEquals(
                2,
                game.chanceOutcomes(game.initialState()).stream()
                        .map(r -> game.informationSet(r.state()))
                        .distinct()
                        .count());
        assertThrows(UnsupportedOperationException.class, () -> game.posterior().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> game.posterior().getFirst().dealtCombos().clear());
    }

    @Test
    void everyOriginalMenuTerminalMatchesIndependentFullSixSeatSourceSettlement() {
        var game = studies.getFirst().core();
        var original = source.rebuildGame();
        int terminals = 0;
        for (var root : game.chanceOutcomes(game.initialState())) {
            var state = new SixMaxPreflopCheckdownGame.State(root.state().dealIndex(), "");
            for (var action : game.binding().specification().history())
                state = original.afterAction(state, action.action());
            terminals += compare(game, root.state(), original, state);
        }
        assertEquals(48, terminals);
    }

    static int compare(
            SixMaxHeadsUpPreflopGame game,
            State state,
            SixMaxPreflopCheckdownGame original,
            SixMaxPreflopCheckdownGame.State other) {
        assertEquals(original.isTerminal(other), game.isTerminal(state));
        if (game.isTerminal(state)) {
            double[] value = game.terminalUtilities(state);
            assertArrayEquals(original.terminalUtilities(other), value, 1e-12);
            assertEquals(-.5, value[Seat.SB.ordinal()]);
            assertEquals(0, Arrays.stream(value).sum(), 1e-12);
            value[0] = 123;
            assertNotEquals(123, game.terminalUtilities(state)[0]);
            return 1;
        }
        assertEquals(original.currentPlayer(other), game.currentPlayer(state));
        assertEquals(original.legalActions(other), game.legalActions(state));
        int count = 0;
        for (String action : game.legalActions(state))
            count +=
                    compare(
                            game,
                            game.afterAction(state, action),
                            original,
                            original.afterAction(other, action));
        return count;
    }

    @Test
    void allReportedActionEvsMatchIndependentLiteralPureHeroPlansIncludingOffPolicyActions()
            throws Exception {
        int controls = 0;
        for (var study : studies) {
            var game = study.core();
            var policy =
                    new CfrSolution(1, FiniteTwoPlayerAffineSequenceForm.solve(game).strategy());
            var policies = new ArrayList<CfrSolution>();
            policies.add(policy);
            for (int budget : SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS)
                policies.add(
                        new MultiPlayerCfrSolver<>(
                                        game,
                                        CfrSolver.Variant.CFR_PLUS,
                                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY)
                                .solve(budget));
            var primary = SixMaxHeadsUpPreflopDecisionValues.assess(game, policy);
            for (var decision : primary)
                if (!decision.roots().isEmpty())
                    for (var fixed : policies) {
                        var values =
                                SixMaxHeadsUpPreflopDecisionValues.values(
                                        game, decision.roots(), fixed);
                        var expected = pureBest(game, decision.roots(), fixed);
                        for (String action : expected.keySet()) {
                            assertEquals(
                                    expected.get(action), values.actionEvBb().get(action), 1e-10);
                            controls++;
                        }
                        if (expected.containsKey("fold"))
                            assertEquals(0, values.actionEvBb().get("fold"), 1e-12);
                        assertEquals(
                                values.decisionRegretBb(),
                                values.rootMixtureRegretBb() + values.continuationRegretBb(),
                                1e-10);
                    }
        }
        assertTrue(controls >= 100);
    }

    static void ownRows(
            SixMaxHeadsUpPreflopGame game,
            State state,
            int actor,
            Map<String, List<String>> infos) {
        if (game.isTerminal(state)) return;
        if (game.currentPlayer(state) == actor)
            infos.put(actor + ":" + game.informationSet(state), game.legalActions(state));
        for (String action : game.legalActions(state))
            ownRows(game, game.afterAction(state, action), actor, infos);
    }

    static Map<String, Double> pureBest(
            SixMaxHeadsUpPreflopGame game, List<ChanceOutcome<State>> roots, CfrSolution policy) {
        int actor = game.currentPlayer(roots.getFirst().state());
        var infos = new TreeMap<String, List<String>>();
        for (var root : roots) ownRows(game, root.state(), actor, infos);
        long plans = 1;
        for (var row : infos.values()) plans *= row.size();
        assertTrue(plans <= 256, "Independent pure-plan control cap");
        String key = actor + ":" + game.informationSet(roots.getFirst().state());
        var best = new TreeMap<String, Double>();
        for (String action : game.legalActions(roots.getFirst().state()))
            best.put(action, Double.NEGATIVE_INFINITY);
        enumerate(
                game,
                roots,
                actor,
                new ArrayList<>(infos.entrySet()),
                0,
                new TreeMap<>(policy.strategy()),
                key,
                best);
        return best;
    }

    static void enumerate(
            SixMaxHeadsUpPreflopGame game,
            List<ChanceOutcome<State>> roots,
            int actor,
            List<Map.Entry<String, List<String>>> infos,
            int index,
            Map<String, Map<String, Double>> rows,
            String key,
            Map<String, Double> best) {
        if (index == infos.size()) {
            var policy = new CfrSolution(1, rows);
            double value =
                    game.publicBettingState(roots.getFirst().state())
                            .committedBb(Seat.values()[actor]);
            for (var root : roots)
                value += root.probability() * literal(game, root.state(), actor, policy);
            String action =
                    rows.get(key).entrySet().stream()
                            .filter(e -> e.getValue() == 1)
                            .findFirst()
                            .orElseThrow()
                            .getKey();
            best.merge(action, value, Math::max);
            return;
        }
        var row = infos.get(index);
        var old = rows.get(row.getKey());
        for (String chosen : row.getValue()) {
            var pure = new TreeMap<String, Double>();
            for (String action : row.getValue()) pure.put(action, chosen.equals(action) ? 1. : 0.);
            rows.put(row.getKey(), pure);
            enumerate(game, roots, actor, infos, index + 1, rows, key, best);
        }
        rows.put(row.getKey(), old);
    }

    static double literal(
            SixMaxHeadsUpPreflopGame game, State state, int actor, CfrSolution policy) {
        if (game.isTerminal(state)) return game.terminalUtilities(state)[actor];
        double value = 0;
        for (String action : game.legalActions(state))
            value +=
                    policy.at(game.currentPlayer(state), game.informationSet(state)).get(action)
                            * literal(game, game.afterAction(state, action), actor, policy);
        return value;
    }

    @Test
    void changedMenuHasDistinctSnapshotAndBindingWithoutRelabellingSource() {
        var a = studies.get(0).report();
        var b = studies.get(1).report();
        assertEquals(a.binding().sourcePackHash(), b.binding().sourcePackHash());
        assertEquals(a.binding().posteriorHash(), b.binding().posteriorHash());
        assertEquals(a.binding().sourcePolicyHash(), b.binding().sourcePolicyHash());
        assertNotEquals(a.binding(), b.binding());
        assertNotEquals(a.solve().snapshotHash(), b.solve().snapshotHash());
        assertNotEquals(a.candidateSolutionHash(), b.candidateSolutionHash());
        assertEquals(1, studies.get(0).artifact().orElseThrow().solution().iterations());
        assertEquals(500, source.solution().iterations());
    }

    @Test
    void rejectUnsupportedRulesHistoriesAndForeignStates() throws Exception {
        var spec = specification("three-nine");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHeadsUpPreflopGame.Specification(List.of(), spec.rules()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpPreflopGame.Specification(
                                spec.history(),
                                new SixMaxPreflopBetting.Rules(100, .5, List.of(3., 9.))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpPreflopGame.Specification(
                                spec.history(),
                                new SixMaxPreflopBetting.Rules(
                                        100,
                                        .5,
                                        List.of(3., 9., 22., 50., 80., 100.),
                                        SixMaxPreflopBetting.RaiseSchedule.NEXT_TARGET)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpPreflopGame(
                                source,
                                new SixMaxHeadsUpPreflopGame.Specification(
                                        spec.history(),
                                        new SixMaxPreflopBetting.Rules(
                                                99,
                                                .5,
                                                List.of(3., 9.),
                                                SixMaxPreflopBetting.RaiseSchedule.NEXT_TARGET))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpPreflopGame(
                                source,
                                new SixMaxHeadsUpPreflopGame.Specification(
                                        List.of(new PublicAction(Seat.HJ, "fold")), spec.rules())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpPreflopGame(
                                source,
                                new SixMaxHeadsUpPreflopGame.Specification(
                                        List.of(new PublicAction(Seat.UTG, "fold")),
                                        spec.rules())));
        var altered = new ArrayList<>(spec.history());
        altered.set(2, new PublicAction(Seat.CO, "raise:9.0"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpPreflopGame(
                                source,
                                new SixMaxHeadsUpPreflopGame.Specification(altered, spec.rules())));
        var game = studies.getFirst().core();
        for (var state :
                List.of(new State(-1, "foreign"), new State(999, ""), new State(0, "foreign")))
            assertThrows(IllegalArgumentException.class, () -> game.isTerminal(state));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.afterAction(
                                game.chanceOutcomes(game.initialState()).getFirst().state(),
                                "raise:22.0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.inactivePlayerUtility(game.initialState(), 6));
    }

    @Test
    void incompleteOrNonNormalizedPoliciesAndMixedQuestionPosteriorsReject() {
        var study = studies.getFirst();
        var game = study.core();
        var policy = study.artifact().orElseThrow().solution();
        var rows = new TreeMap<>(policy.strategy());
        rows.remove(rows.firstKey());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHeadsUpPreflopDecisionValues.assess(game, new CfrSolution(1, rows)));
        rows.clear();
        rows.putAll(policy.strategy());
        rows.put("foreign", Map.of("fold", 1.));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHeadsUpPreflopDecisionValues.assess(game, new CfrSolution(1, rows)));
        var root = game.chanceOutcomes(game.initialState()).getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHeadsUpPreflopDecisionValues.values(game, List.of(root), policy));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopDecisionValues.values(
                                game,
                                List.of(
                                        new ChanceOutcome<>(root.state(), .5),
                                        new ChanceOutcome<>(root.state(), .5)),
                                policy));
    }

    @Test
    void trainerQuestionsBindPublicTableCardsAndStudyAndNeverExposeHiddenAnswers()
            throws Exception {
        var trainer = new SixMaxHeadsUpPreflopTrainer(studies.get(1));
        var actors = new HashSet<Seat>();
        for (long seed = 0; seed < 40; seed++) {
            var question = trainer.question(seed);
            assertEquals(question, trainer.question(seed));
            assertFalse(question.trainerAdmission());
            assertEquals(6, question.table().players().size());
            assertEquals(question.hero(), question.table().actingSeat());
            actors.add(question.hero());
            assertEquals(
                    4,
                    question.table().players().stream()
                            .filter(p -> p.status() == SixMaxPreflopPublicTable.PlayerStatus.FOLDED)
                            .count());
            assertEquals(
                    question.table(),
                    SixMaxPreflopPublicTable.replay(
                            studies.get(1).core().binding().specification().rules(),
                            question.history()));
            var json = SixMaxTextureStudy.json(question);
            assertFalse(json.contains("dealtCombos"));
            assertFalse(json.contains("frequencies"));
            assertFalse(json.contains("actionEvBb"));
            for (String action : question.legalActions()) {
                var feedback = trainer.grade(question, action);
                assertFalse(feedback.trainerAdmission());
                assertEquals(
                        Collections.max(feedback.values().actionEvBb().values())
                                - feedback.selectedActionEvBb(),
                        feedback.evLossBb(),
                        1e-12);
                if (action.equals("fold")) assertEquals(0, feedback.selectedActionEvBb(), 1e-12);
            }
        }
        assertEquals(Set.of(Seat.CO, Seat.BB), actors);
        var question = trainer.question(711);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = mapper.valueToTree(question);
        for (String field : List.of("heroCards", "studyHash", "model", "publicationStatus")) {
            var changed = tree.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put(field, "tampered");
            var forged = mapper.treeToValue(changed, SixMaxHeadsUpPreflopTrainer.Question.class);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> trainer.grade(forged, question.legalActions().getFirst()));
        }
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(question, "raise:100.0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHeadsUpPreflopTrainer(studies.get(2)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpPreflopTrainer(studies.get(0))
                                .grade(question, question.legalActions().getFirst()));
    }

    @Test
    void deterministicTenQuestionReviewRecomputesEveryEvAndRejectsWrongLengthOrStudy()
            throws Exception {
        var trainer = new SixMaxHeadsUpPreflopTrainer(studies.get(1));
        var actions = new ArrayList<String>();
        double total = 0;
        for (int i = 0; i < 10; i++) {
            var q = trainer.sessionQuestion(711, i);
            String action = q.legalActions().getFirst();
            actions.add(action);
            total += trainer.grade(q, action).evLossBb();
        }
        var review = trainer.review(711, trainer.studyHash(), actions);
        assertEquals(10, review.attempts().size());
        assertEquals(total, review.totalEvLossBb());
        assertEquals(total / 10, review.averageEvLossBb());
        assertEquals(review, trainer.review(711, trainer.studyHash(), actions));
        assertThrows(UnsupportedOperationException.class, () -> review.attempts().clear());
        assertThrows(IllegalArgumentException.class, () -> trainer.sessionQuestion(711, 10));
        assertThrows(IllegalArgumentException.class, () -> trainer.review(711, "wrong", actions));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(711, trainer.studyHash(), actions.subList(0, 9)));
    }

    @Test
    void acceptedAndRejectedEvidenceRoundTripsGzipAndReplaysEveryCertificate() throws Exception {
        for (int i = 0; i < 3; i++) {
            var policy = temporary.resolve("policy" + i + ".json.gz");
            var report = temporary.resolve("report" + i + ".json.gz");
            SixMaxHeadsUpPreflopStudy.write(policy, report, studies.get(i));
            assertEquals(i < 2, Files.exists(policy));
            assertEquals(
                    studies.get(i).report(),
                    SixMaxHeadsUpPreflopStudy.replay(policy, report, source).report());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxHeadsUpPreflopStudy.write(policy, report, studies.getFirst()));
        }
    }
}
