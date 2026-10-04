package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Measures connected postflop convergence separately from restricted-turn checkdown bias. */
public final class SixMaxConnectedPostflopAudit {
    public record TreeSize(long chanceNodes, long decisionNodes, long terminalNodes) {}

    public record Solve(
            String publicChanceModel,
            List<Double> turnQuantiles,
            int iterations,
            int informationSets,
            TreeSize treeSize,
            String solutionHash,
            HeadsUpBestResponse.Report bestResponse,
            Map<Seat, Double> solvedUtilitiesBb,
            Map<Seat, Double> modelCheckdownUtilitiesBb,
            double signedCheckdownBiasBb,
            double firstPlayerChangeFromFlopOnlyBb,
            List<SixMaxPostflopDecisionEvaluator.Decision> firstPlayerDecisions) {
        public Solve {
            turnQuantiles = List.copyOf(turnQuantiles);
            solvedUtilitiesBb = Map.copyOf(solvedUtilitiesBb);
            modelCheckdownUtilitiesBb = Map.copyOf(modelCheckdownUtilitiesBb);
            firstPlayerDecisions = List.copyOf(firstPlayerDecisions);
        }
    }

    public record Example(
            List<PublicAction> preflopHistory,
            double preflopReachProbability,
            List<String> flop,
            double flopProbability,
            Seat firstToAct,
            Seat secondToAct,
            double potBb,
            double remainingStackBb,
            double flopBetBb,
            double requestedTurnBetBb,
            double requestedRiverBetBb,
            int posteriorJointDeals,
            double flopOnlyGapBb,
            Map<Seat, Double> flopOnlyUtilitiesBb,
            Map<Seat, Double> physicalCheckdownUtilitiesBb,
            Solve exact,
            Solve restrictedTurn) {
        public Example {
            preflopHistory = List.copyOf(preflopHistory);
            flop = List.copyOf(flop);
            flopOnlyUtilitiesBb = Map.copyOf(flopOnlyUtilitiesBb);
            physicalCheckdownUtilitiesBb = Map.copyOf(physicalCheckdownUtilitiesBb);
        }
    }

    public record Report(
            String privateChanceModel,
            String continuationModel,
            long seed,
            int flopOnlyIterations,
            int exactIterations,
            int restrictedIterations,
            double betPotFraction,
            double maximumExactGapBb,
            double maximumRestrictedGapBb,
            double maximumAbsoluteRestrictedCheckdownBiasBb,
            List<Example> examples) {
        public Report {
            examples = List.copyOf(examples);
        }
    }

    private SixMaxConnectedPostflopAudit() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame sourceGame,
            CfrSolution source,
            long seed,
            int exampleLimit,
            int exactIterations,
            int restrictedIterations,
            double fraction,
            List<Double> quantiles) {
        if (exampleLimit < 1 || exampleLimit > 3)
            throw new IllegalArgumentException("Connected audit requires 1–3 examples");
        if (exactIterations < 1
                || exactIterations > 1000
                || restrictedIterations < 1
                || restrictedIterations > 10000)
            throw new IllegalArgumentException(
                    "Budgets must be 1–1000 exact and 1–10000 restricted iterations");
        quantiles = List.copyOf(quantiles);
        if (quantiles.isEmpty() || quantiles.size() > 8)
            throw new IllegalArgumentException("Restricted audit requires 1–8 turn quantiles");
        for (double q : quantiles)
            if (!Double.isFinite(q) || q < 0 || q >= 1)
                throw new IllegalArgumentException("Invalid turn quantile");
        var previous =
                SixMaxFlopContinuationAudit.assess(
                        sourceGame, source, seed, exampleLimit, 5000, fraction);
        var examples = new ArrayList<Example>();
        double maxExact = 0, maxRestricted = 0, maxBias = 0;
        for (var selected : previous.examples()) {
            if (selected.posteriorJointDeals() > 4)
                throw new IllegalArgumentException(
                        "Exact connected audit supports at most four posterior deals per flop");
            var handoff =
                    new SixMaxPolicyFlopTransition(sourceGame, source, selected.preflopHistory());
            var flop = handoff.conditionOnFlop(selected.board().stream().map(Card::parse).toList());
            double turnBet = (handoff.potBb() + 2 * selected.betBb()) * fraction;
            double riverBet =
                    (handoff.potBb()
                                    + 2 * selected.betBb()
                                    + 2
                                            * Math.min(
                                                    turnBet,
                                                    handoff.remainingStackBb() - selected.betBb()))
                            * fraction;
            var exactGame =
                    new SixMaxHeadsUpPostflopGame(flop, selected.betBb(), turnBet, riverBet);
            var restrictedGame =
                    new SixMaxHeadsUpPostflopGame(
                            flop, selected.betBb(), turnBet, riverBet, quantiles);
            var physicalBaseline = selected.checkdownUtilitiesBb();
            var exact =
                    solve(
                            exactGame,
                            exactIterations,
                            physicalBaseline,
                            selected.solvedUtilitiesBb());
            var restricted =
                    solve(
                            restrictedGame,
                            restrictedIterations,
                            physicalBaseline,
                            selected.solvedUtilitiesBb());
            maxExact = Math.max(maxExact, exact.bestResponse().gap());
            maxRestricted = Math.max(maxRestricted, restricted.bestResponse().gap());
            maxBias = Math.max(maxBias, Math.abs(restricted.signedCheckdownBiasBb()));
            examples.add(
                    new Example(
                            selected.preflopHistory(),
                            selected.preflopReachProbability(),
                            selected.board(),
                            selected.flopProbability(),
                            handoff.firstToAct(),
                            handoff.secondToAct(),
                            handoff.potBb(),
                            handoff.remainingStackBb(),
                            selected.betBb(),
                            turnBet,
                            riverBet,
                            flop.deals().size(),
                            selected.bestResponse().gap(),
                            selected.solvedUtilitiesBb(),
                            physicalBaseline,
                            exact,
                            restricted));
        }
        return new Report(
                previous.chanceModel(),
                "ONE_BET_PER_STREET_CONNECTED_FLOP_TURN_RIVER",
                seed,
                5000,
                exactIterations,
                restrictedIterations,
                fraction,
                maxExact,
                maxRestricted,
                maxBias,
                examples);
    }

    private static Solve solve(
            SixMaxHeadsUpPostflopGame game,
            int iterations,
            Map<Seat, Double> physicalBaseline,
            Map<Seat, Double> flopOnlyValues) {
        var tree = treeSize(game);
        var solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        var quality = HeadsUpBestResponse.assess(game, solution);
        var values = game.profileUtilitiesBb(solution);
        var checkdown = game.checkdownUtilitiesBb();
        var evaluator = new SixMaxPostflopDecisionEvaluator(game, solution);
        var decisions =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(o -> game.ownHand(o.state()).key())
                        .distinct()
                        .sorted()
                        .map(
                                combo ->
                                        evaluator.evaluate(
                                                SixMaxPostflopDecisionEvaluator.History.flop(
                                                        List.of()),
                                                combo))
                        .toList();
        return new Solve(
                game.chanceModel().name(),
                game.turnQuantiles(),
                iterations,
                solution.strategy().size(),
                tree,
                solutionHash(solution),
                quality,
                values,
                checkdown,
                checkdown.get(game.seat(0)) - physicalBaseline.get(game.seat(0)),
                values.get(game.seat(0)) - flopOnlyValues.get(game.seat(0)),
                decisions);
    }

    /**
     * Hash the complete sorted solution without storing tens of thousands of policy rows in the
     * audit.
     */
    public static String solutionHash(CfrSolution solution) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                new ObjectMapper()
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .writeValue(stream, solution);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash connected solution", exception);
        }
    }

    public static TreeSize treeSize(SixMaxHeadsUpPostflopGame game) {
        long[] counts = new long[3];
        count(game, game.initialState(), counts);
        return new TreeSize(counts[0], counts[1], counts[2]);
    }

    private static void count(
            SixMaxHeadsUpPostflopGame game, SixMaxHeadsUpPostflopGame.State state, long[] counts) {
        if (game.isTerminal(state)) {
            counts[2]++;
            return;
        }
        if (game.currentPlayer(state) == -1) {
            counts[0]++;
            for (var outcome : game.chanceOutcomes(state)) count(game, outcome.state(), counts);
        } else {
            counts[1]++;
            for (String action : game.legalActions(state))
                count(game, game.afterAction(state, action), counts);
        }
    }
}
