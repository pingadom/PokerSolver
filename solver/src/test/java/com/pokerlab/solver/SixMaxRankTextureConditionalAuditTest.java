package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxRankTextureConditionalAuditTest {
    private record Fixture(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            SixMaxRankTextureFlopGame game,
            Map<String, Map<String, Double>> forcedPreflop) {}

    private static Fixture fixture() {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxRankTextureFlopGameTest.table(source);
        var game =
                new SixMaxRankTextureFlopGame(source, table, SixMaxRankTextureFlopGameTest.menu());
        var rows = new LinkedHashMap<>(source.solution().strategy());
        for (var root : game.sourceGame().chanceOutcomes(game.sourceGame().initialState())) {
            var state = root.state();
            for (var action : SixMaxConnectedPreflopGameTest.HISTORY) {
                rows.put(
                        game.sourceGame().currentPlayer(state)
                                + ":"
                                + game.sourceGame().informationSet(state),
                        pure(game.sourceGame().legalActions(state), action.action()));
                state = game.sourceGame().afterAction(state, action.action());
            }
        }
        return new Fixture(source, table, game, rows);
    }

    private static Map<String, Double> pure(List<String> actions, String chosen) {
        var row = new LinkedHashMap<String, Double>();
        actions.forEach(a -> row.put(a, a.equals(chosen) ? 1.0 : 0.0));
        return row;
    }

    private static SixMaxRankTextureStudy.Checkpoint checkpoint(Fixture f, CfrSolution policy)
            throws Exception {
        return SixMaxRankTextureStudy.checkpoint(
                f.source(), f.table(), f.game(), policy, MultiPlayerCfrSolver.InactivePruning.NONE);
    }

    @Test
    void conditionalCountsRetainFoldedBlockersAndPureResponsesEmbedIntoParent() throws Exception {
        var f = fixture();
        var baseline = f.game().checkdownBaseline(new CfrSolution(1, f.forcedPreflop()));
        var mistakes = new LinkedHashMap<>(baseline.strategy());
        mistakes.replaceAll(
                (key, row) ->
                        key.contains(":postflop:rank-texture:") && row.containsKey("f")
                                ? pure(List.copyOf(row.keySet()), "f")
                                : row);
        var policy = new CfrSolution(1, mistakes);
        var cp = checkpoint(f, policy);
        String originalHash = cp.solutionHash();
        var report = SixMaxRankTextureConditionalAudit.assess(f.source(), f.table(), cp);
        var history = report.histories().getFirst();
        assertEquals("AUDITED", history.status());
        assertEquals(1, history.historyProbability(), 1e-15);
        assertEquals(1, history.signalProbabilitiesSum(), 1e-12);
        assertEquals(f.table().signals().size(), history.signals().size());
        for (int signal = 0; signal < history.signals().size(); signal++) {
            var row = history.signals().get(signal);
            long first = f.table().deals().getFirst().flopCounts().get(signal);
            long second = f.table().deals().getLast().flopCounts().get(signal);
            assertEquals(
                    (.75 * first + .25 * second) / 9880,
                    row.signalProbabilityGivenHistory(),
                    1e-15);
            assertEquals((first > 0 ? 1 : 0) + (second > 0 ? 1 : 0), row.posteriorPrivateDeals());
            // One private hand per active player; folded-seat uncertainty must not create new
            // decisions.
            assertEquals(1, row.firstCombosAtFivePercent());
            assertEquals(1, row.secondCombosAtFivePercent());
            assertEquals(6.5, row.quality().nashConvBb(), 1e-12);
        }
        assertEquals(6.5, report.parentWitness().reachWeightedLocalNashConvBb(), 1e-12);
        assertEquals(3.25, report.parentWitness().reachWeightedLocalGainsBb().get(3), 1e-12);
        assertEquals(3.25, report.parentWitness().reachWeightedLocalGainsBb().get(5), 1e-12);
        assertTrue(
                report.parentWitness().embeddingErrorsBb().stream()
                        .allMatch(e -> Math.abs(e) < 1e-12));
        assertEquals(0, report.summary().bothPlayersTwoCombosAtFivePercent());
        assertEquals(originalHash, SixMaxConnectedPostflopAudit.solutionHash(policy));
        assertEquals(2, f.game().chanceOutcomes(f.game().initialState()).size());
        bruteCheck(f.game(), policy, history, history.signals().get(200));
    }

    @Test
    void zeroReachAndImpossibleSignalsStayDistinctAndTinyWorldsFailClosed() throws Exception {
        var f = fixture();
        var rows = new LinkedHashMap<>(f.forcedPreflop());
        var root =
                f.game()
                        .sourceGame()
                        .chanceOutcomes(f.game().sourceGame().initialState())
                        .getFirst()
                        .state();
        rows.put(
                "0:" + f.game().sourceGame().informationSet(root),
                pure(f.game().sourceGame().legalActions(root), "call"));
        var filtered =
                SixMaxRankTextureConditionalAudit.assess(
                        f.source(),
                        f.table(),
                        checkpoint(f, f.game().checkdownBaseline(new CfrSolution(1, rows))));
        var history = filtered.histories().getFirst();
        assertEquals(.25, history.historyProbability(), 1e-15);
        int impossible = 0;
        for (int signal = 0; signal < history.signals().size(); signal++) {
            var row = history.signals().get(signal);
            long count = f.table().deals().getLast().flopCounts().get(signal);
            assertEquals(count / 9880.0, row.signalProbabilityGivenHistory(), 1e-15);
            if (count == 0) {
                impossible++;
                assertEquals("NO_REACHED_PRIVATE_SUPPORT", row.status());
                assertNull(row.quality());
                assertTrue(row.firstMarginal().isEmpty());
                assertEquals(0, row.posteriorPrivateDeals());
            } else assertEquals(1, row.posteriorPrivateDeals());
        }
        assertEquals(impossible, filtered.summary().signalsWithoutReachedPrivateSupport());
        assertTrue(impossible > 0, "Fixture must exercise a physically unsupported signal");
        var last =
                f.game()
                        .sourceGame()
                        .chanceOutcomes(f.game().sourceGame().initialState())
                        .getLast()
                        .state();
        rows.put(
                "0:" + f.game().sourceGame().informationSet(last),
                pure(f.game().sourceGame().legalActions(last), "call"));
        var zero =
                SixMaxRankTextureConditionalAudit.assess(
                        f.source(),
                        f.table(),
                        checkpoint(f, f.game().checkdownBaseline(new CfrSolution(1, rows))));
        assertEquals("ZERO_POLICY_REACH", zero.histories().getFirst().status());
        assertTrue(zero.histories().getFirst().signals().isEmpty());
        assertEquals(1, zero.summary().zeroReachHistories());
        assertEquals(0, zero.parentWitness().reachWeightedLocalNashConvBb());
        assertEquals(2, f.game().chanceOutcomes(f.game().initialState()).size());
        var tiny = new LinkedHashMap<>(f.forcedPreflop());
        var tinyRow = new LinkedHashMap<>(pure(f.game().sourceGame().legalActions(last), "call"));
        tinyRow.put("fold", Double.MIN_VALUE);
        tiny.put("0:" + f.game().sourceGame().informationSet(last), tinyRow);
        var tinyCp = checkpoint(f, f.game().checkdownBaseline(new CfrSolution(1, tiny)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureConditionalAudit.assess(f.source(), f.table(), tinyCp));
    }

    @Test
    void strictReplayIsDeterministicAndInvalidInputsPreserveOutput(@TempDir Path temp)
            throws Exception {
        var f = fixture();
        var cp = checkpoint(f, f.game().checkdownBaseline(new CfrSolution(1, f.forcedPreflop())));
        var source = temp.resolve("source.json");
        var table = temp.resolve("table.json.gz");
        var checkpoint = temp.resolve("checkpoint.json.gz");
        var report = temp.resolve("report.json.gz");
        Files.writeString(source, MultiwayPackJson.writeFullRound(f.source()));
        SixMaxRankTexturePayoffTable.writeBytes(
                table,
                SixMaxRankTexturePayoffTable.json(f.table())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                4 * 1024 * 1024);
        SixMaxRankTextureStudy.write(checkpoint, cp, f.source(), f.table());
        String[] args = {
            "audit", source.toString(), table.toString(), checkpoint.toString(), report.toString()
        };
        SixMaxRankTextureConditionalAuditMain.main(args);
        byte[] bytes = Files.readAllBytes(report);
        SixMaxRankTextureConditionalAuditMain.main(args);
        assertArrayEquals(bytes, Files.readAllBytes(report));
        args[0] = "replay";
        SixMaxRankTextureConditionalAuditMain.main(args);
        assertArrayEquals(bytes, Files.readAllBytes(report));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var changed =
                mapper.readTree(
                        SixMaxRankTexturePayoffTable.readBytes(
                                report, SixMaxRankTextureConditionalAudit.MAX_REPORT_BYTES));
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get("summary"))
                .put("auditedSignals", 0);
        var corrupt = temp.resolve("corrupt.json");
        Files.writeString(corrupt, mapper.writeValueAsString(changed));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureConditionalAudit.replay(corrupt, f.source(), f.table(), cp));
        var partial = checkpoint(f, f.source().solution());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureConditionalAudit.assess(f.source(), f.table(), partial));
        var foreign = new LinkedHashMap<>(cp.solution().strategy());
        foreign.put("0:foreign", Map.of("fold", 1.0));
        var foreignCp = checkpoint(f, new CfrSolution(1, foreign));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureConditionalAudit.assess(f.source(), f.table(), foreignCp));
        args[0] = "audit";
        Files.writeString(checkpoint, "{}");
        assertThrows(Exception.class, () -> SixMaxRankTextureConditionalAuditMain.main(args));
        assertArrayEquals(bytes, Files.readAllBytes(report));
        SixMaxRankTextureStudy.write(checkpoint, cp, f.source(), f.table());
        var alias = temp.resolve("hardlink.json");
        Files.createLink(alias, source);
        args[4] = alias.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureConditionalAuditMain.main(args));
        assertEquals(MultiwayPackJson.writeFullRound(f.source()), Files.readString(source));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureConditionalAuditMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRankTextureConditionalAudit.write(
                                report,
                                new SixMaxRankTextureConditionalAudit.Report(
                                        "wrong", "wrong", "wrong", "wrong", "", "", "", "", "", "",
                                        "", 1, 1, 0, 0, List.of(), null, null)));
    }

    /** Enumerates pure own-information-set plans, independently of the best-response algorithm. */
    static void bruteCheck(
            SixMaxRankTextureFlopGame game,
            CfrSolution policy,
            SixMaxRankTextureConditionalAudit.HistoryAudit history,
            SixMaxRankTextureConditionalAudit.SignalAudit signal) {
        bruteCheck(
                game,
                game.sourceGame(),
                SixMaxFlopPayoffView.rank(game.payoffTable()),
                policy,
                history.history(),
                game.payoffTable().signals().indexOf(signal.signal()),
                signal.signalProbabilityGivenHistory(),
                signal.quality());
    }

    static void bruteCheck(
            MultiPlayerCfrGame<SixMaxRankTextureFlopGame.State> game,
            SixMaxPreflopCheckdownGame source,
            SixMaxFlopPayoffView view,
            CfrSolution policy,
            List<SixMaxPreflopResearchTrainer.PublicAction> history,
            int index,
            double expectedProbability,
            SixMaxConnectedPreflopAudit.Quality quality) {
        var transition =
                new SixMaxPolicyFlopTransition(
                        source, SixMaxPreflopContinuationFeedback.preflopPolicy(policy), history);
        var roots = new ArrayList<ChanceOutcome<SixMaxRankTextureFlopGame.State>>();
        double sum = 0;
        for (var deal : transition.deals()) {
            var keys = deal.hands().stream().map(WeightedCombo::key).toList();
            int world = -1;
            for (int i = 0; i < view.dealCount(); i++) if (view.hands(i).equals(keys)) world = i;
            double mass = deal.probability() * view.counts(world).get(index) / 9880;
            if (mass == 0) continue;
            var state = game.chanceOutcomes(game.initialState()).get(world).state();
            for (var action : history) state = game.afterAction(state, action.action());
            roots.add(
                    new ChanceOutcome<>(
                            new SixMaxRankTextureFlopGame.State(state.preflop(), index, ""), mass));
            sum += mass;
        }
        assertEquals(expectedProbability, sum, 1e-15);
        double normalizer = sum;
        var local =
                new SixMaxRankTextureConditionalAudit.ConditionalGame(
                        game,
                        roots.stream()
                                .map(
                                        r ->
                                                new ChanceOutcome<>(
                                                        r.state(), r.probability() / normalizer))
                                .toList());
        var decisions = new LinkedHashMap<String, List<String>>();
        collect(local, local.initialState(), decisions);
        var base = new LinkedHashMap<String, Map<String, Double>>();
        decisions.forEach((key, actions) -> base.put(key, policy.strategy().get(key)));
        var profile = MultiPlayerStrategyEvaluator.utilities(local, new CfrSolution(1, base));
        for (int player = 0; player < 6; player++) {
            int target = player;
            var own =
                    decisions.entrySet().stream()
                            .filter(e -> e.getKey().startsWith(target + ":"))
                            .toList();
            int plans = 1;
            for (var decision : own) plans = Math.multiplyExact(plans, decision.getValue().size());
            assertTrue(plans <= 256, "Independent enumeration budget exceeded");
            double best = Double.NEGATIVE_INFINITY;
            for (int plan = 0; plan < plans; plan++) {
                var rows = new LinkedHashMap<>(base);
                int remaining = plan;
                for (var decision : own) {
                    var actions = decision.getValue();
                    rows.put(
                            decision.getKey(),
                            pure(actions, actions.get(remaining % actions.size())));
                    remaining /= actions.size();
                }
                best =
                        Math.max(
                                best,
                                MultiPlayerStrategyEvaluator.utilities(
                                        local, new CfrSolution(1, rows))[player]);
            }
            assertEquals(quality.profileUtilitiesBb().get(player), profile[player], 1e-10);
            assertEquals(quality.bestResponseUtilitiesBb().get(player), best, 1e-10);
        }
    }

    private static void collect(
            MultiPlayerCfrGame<SixMaxRankTextureFlopGame.State> game,
            SixMaxRankTextureFlopGame.State state,
            Map<String, List<String>> decisions) {
        if (game.isTerminal(state)) return;
        if (game.currentPlayer(state) < 0) {
            for (var root : game.chanceOutcomes(state)) collect(game, root.state(), decisions);
            return;
        }
        decisions.putIfAbsent(
                game.currentPlayer(state) + ":" + game.informationSet(state),
                game.legalActions(state));
        for (var action : game.legalActions(state))
            collect(game, game.afterAction(state, action), decisions);
    }
}
