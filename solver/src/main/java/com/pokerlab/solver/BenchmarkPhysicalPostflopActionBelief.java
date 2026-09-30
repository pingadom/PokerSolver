package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible checkdown-reach audit for flop/turn action-conditioned river observations. */
public final class BenchmarkPhysicalPostflopActionBelief {
    private BenchmarkPhysicalPostflopActionBelief() {}

    public static void main(String[] args) {
        if (args.length > 8)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalPostflopActionBelief [boards] [seed] [minimum-discovery-boards] [3x3|5x5] [correct|uninformative|reversed] [true-response] [assumed-response] [connected-iterations]; responses: call, pair, strategic, strategic-reversed, connected, connected-alt");
        int boards = args.length >= 1 ? Integer.parseInt(args[0]) : 20_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int minimum = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
        var profile =
                args.length >= 4
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[3])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var assumption =
                args.length >= 5
                        ? ButtonBigBlindRangeValidationFixture.PostflopBeliefAssumption.parse(
                                args[4])
                        : ButtonBigBlindRangeValidationFixture.PostflopBeliefAssumption.CORRECT;
        var preflop = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var bigBlindRange =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(deal -> deal.state().bigBlind())
                        .distinct()
                        .toList();
        String trueLabel = args.length >= 6 ? args[5] : "call";
        String assumedLabel = args.length >= 7 ? args[6] : trueLabel;
        int connectedIterations = args.length >= 8 ? Integer.parseInt(args[7]) : 3_000;
        if (connectedIterations < 1 || connectedIterations > 100_000)
            throw new IllegalArgumentException("Connected iterations must be in 1-100000");
        CfrSolution connectedSolution =
                trueLabel.equals("connected") || assumedLabel.equals("connected")
                        ? new CfrSolver<>(
                                        game,
                                        CfrSolver.Variant.VANILLA,
                                        CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                        seed + 100_003)
                                .solve(connectedIterations)
                        : null;
        CfrSolution alternateSolution =
                trueLabel.equals("connected-alt") || assumedLabel.equals("connected-alt")
                        ? new CfrSolver<>(
                                        game,
                                        CfrSolver.Variant.VANILLA,
                                        CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                        seed + 200_003)
                                .solve(connectedIterations)
                        : null;
        var response =
                response(
                        trueLabel,
                        game,
                        connectedSolution,
                        alternateSolution,
                        bigBlindRange,
                        preflop,
                        ButtonBigBlindRangeValidationFixture.postflopBelief(),
                        game.potBb() / 2);
        var assumedResponse =
                response(
                        assumedLabel,
                        game,
                        connectedSolution,
                        alternateSolution,
                        bigBlindRange,
                        preflop,
                        ButtonBigBlindRangeValidationFixture.assumedPostflopBelief(assumption),
                        game.potBb() / 2);
        if (connectedSolution != null || alternateSolution != null)
            System.out.printf(
                    Locale.ROOT,
                    "Connected response source: %s, %d sampled-chance vanilla CFR iterations; primary solve seed %d, alternate solve seed %d; missing BTN keys fall back to pair-call%n",
                    game.contentHash(),
                    connectedIterations,
                    seed + 100_003,
                    seed + 200_003);
        var report =
                PhysicalPostflopActionBeliefAudit.assess(
                        boards,
                        minimum,
                        seed,
                        profile,
                        ButtonBigBlindRangeValidationFixture.postflopBelief(),
                        ButtonBigBlindRangeValidationFixture.assumedPostflopBelief(assumption),
                        response,
                        assumedResponse);
        System.out.printf(
                Locale.ROOT,
                "Action-conditioned checkdown %s: %d reached rivers from %d attempted deals (%.1f%%), seed %d, minimum %d discovery boards, action belief %s, true BTN response %s, assumed BTN response %s%n",
                profile,
                report.sampledBoards(),
                report.attemptedDeals(),
                100 * report.checkdownReachRate(),
                seed,
                minimum,
                assumption,
                trueLabel,
                assumedLabel);
        print("Static", report.staticRange());
        print("Preflop", report.preflopOnly());
        print("Postflop", report.postflopConditioned());
        print("Response-value", report.responseAware());
        print("Response-value with backoff", report.responseWithBackoff());
        var paired = report.postflopMinusPreflop();
        System.out.printf(
                Locale.ROOT,
                "Postflop minus preflop selected gain: %.4fbb, paired SE %.4fbb, approximate 95%% interval [%.4f, %.4f]bb%n",
                paired.postflopMinusPreflopBb(),
                paired.standardErrorBb(),
                paired.approximateLower95Bb(),
                paired.approximateUpper95Bb());
        var responsePaired = report.responseMinusPostflop();
        System.out.printf(
                Locale.ROOT,
                "Response-value minus postflop selected gain: %.4fbb, paired SE %.4fbb, approximate 95%% interval [%.4f, %.4f]bb%n",
                responsePaired.responseMinusPostflopBb(),
                responsePaired.standardErrorBb(),
                responsePaired.approximateLower95Bb(),
                responsePaired.approximateUpper95Bb());
        var backoffPaired = report.backoffMinusPostflop();
        System.out.printf(
                Locale.ROOT,
                "Response-backoff minus postflop selected gain: %.4fbb, paired SE %.4fbb, approximate 95%% interval [%.4f, %.4f]bb%n",
                backoffPaired.responseMinusPostflopBb(),
                backoffPaired.standardErrorBb(),
                backoffPaired.approximateLower95Bb(),
                backoffPaired.approximateUpper95Bb());
        System.out.println(
                "The response-value bucket encodes the sign of an assumed bet value, so in-model separation is partly by construction. The declared one-step strategic response is optimal only against its own BB betting likelihood. Connected responses are CFR averages from a sparse abstract game. Intervals condition on synthetic generating actions and one discovery split; this is not equilibrium or trainer-policy validation.");
        printCoverage("True", response);
        printCoverage("Assumed", assumedResponse);
    }

    private static void print(String label, PhysicalRiverHeldOutDecisionAudit.ModeResult mode) {
        System.out.printf(
                Locale.ROOT,
                "%s: %d buckets, %.1f%% held-out support, gain %.4fbb, regret %.4fbb%n",
                label,
                mode.discoveredBuckets(),
                100 * mode.supportRate(),
                mode.selectedGainBb(),
                mode.regretBb());
    }

    private static RiverCallPolicy response(
            String label,
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution connectedSolution,
            CfrSolution alternateSolution,
            java.util.List<WeightedCombo> bigBlindRange,
            PreflopActionBelief preflop,
            PostflopActionBelief postflop,
            double halfPotBb) {
        return switch (label) {
            case "call" ->
                    RiverCallPolicy.fixed(PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
            case "pair" ->
                    RiverCallPolicy.fixed(
                            PhysicalActionBeliefAudit.ResponseModel.PAIR_OR_BETTER_CALL);
            case "strategic" ->
                    new StrategicRiverCallPolicy(
                            bigBlindRange, preflop, postflop, 0.25, 0.75, halfPotBb, 8);
            case "strategic-reversed" ->
                    new StrategicRiverCallPolicy(
                            bigBlindRange, preflop, postflop, 0.75, 0.25, halfPotBb, 8);
            case "connected" ->
                    new ConnectedRiverCallPolicy(
                            game,
                            connectedSolution,
                            RiverCallPolicy.fixed(
                                    PhysicalActionBeliefAudit.ResponseModel.PAIR_OR_BETTER_CALL));
            case "connected-alt" ->
                    new ConnectedRiverCallPolicy(
                            game,
                            alternateSolution,
                            RiverCallPolicy.fixed(
                                    PhysicalActionBeliefAudit.ResponseModel.PAIR_OR_BETTER_CALL));
            default -> throw new IllegalArgumentException("Unknown river response: " + label);
        };
    }

    private static void printCoverage(String label, RiverCallPolicy policy) {
        if (policy instanceof ConnectedRiverCallPolicy connected) {
            var coverage = connected.coverage();
            System.out.printf(
                    Locale.ROOT,
                    "%s connected BTN coverage: %d/%d queries (%.1f%%), %d/%d distinct information sets (%.1f%%); missing lookups use the declared pair-call fallback%n",
                    label,
                    coverage.learnedQueries(),
                    coverage.queries(),
                    100 * coverage.querySupportRate(),
                    coverage.learnedInformationSets(),
                    coverage.queriedInformationSets(),
                    100 * coverage.informationSetSupportRate());
            System.out.println(
                    "Connected response is queried off-policy on synthetic checkdown reach; these gains are not connected-game evaluation or a full-game best-response bound.");
        }
    }
}
