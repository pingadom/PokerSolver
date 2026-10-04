package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.core.card.Card;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Paired cache-on/cache-off timing with identical exact-game strategies and best responses. */
public final class SixMaxPostflopTraversalBenchmarkMain {
    public record Measurement(
            String solutionHash,
            int informationSets,
            double solveSeconds,
            double auditSeconds,
            HeadsUpBestResponse.Report quality) {}

    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String publicationStatus,
            String interpretation,
            long flopSeed,
            int warmupIterations,
            int measuredIterations,
            List<String> flop,
            List<SixMaxPreflopResearchTrainer.PublicAction> publicHistory,
            Measurement repeatedActionValidation,
            Measurement oncePerInformationSetValidation,
            Measurement experimentalGameCache,
            double actionValidationSolveSpeedup,
            double gameCacheSolveSpeedup,
            double gameCacheAuditSpeedup) {}

    private SixMaxPostflopTraversalBenchmarkMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPostflopTraversalBenchmarkMain <pack.json> <output.json> <flop-seed> <iterations>");
        var input = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Benchmark must not overwrite its source");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        long seed = Long.parseLong(args[2]);
        int iterations = Integer.parseInt(args[3]);
        if (iterations < 1 || iterations > 100)
            throw new IllegalArgumentException("Benchmark requires 1–100 measured iterations");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var base = pack.rebuildGame();
        var example =
                SixMaxPreflopContinuationAudit.assess(base, pack.solution(), seed, 1)
                        .examples()
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Source has no reached heads-up history"));
        var transition = new SixMaxPolicyFlopTransition(base, pack.solution(), example.history());
        var flop =
                transition.conditionOnFlop(
                        example.sampledFlop().stream().map(Card::parse).toList());
        if (flop.deals().size() > 4)
            throw new IllegalArgumentException("Benchmark supports at most four reached deals");
        double f = Math.min(transition.potBb() * .5, transition.remainingStackBb());
        double t = (transition.potBb() + 2 * f) * .5;
        double r =
                (transition.potBb() + 2 * f + 2 * Math.min(t, transition.remainingStackBb() - f))
                        * .5;
        var uncached = new SixMaxHeadsUpPostflopGame(flop, f, t, r, List.of(), false);
        var cached = new SixMaxHeadsUpPostflopGame(flop, f, t, r, List.of(), true);
        int warmup = 2;
        new CfrSolver<>(
                        uncached,
                        CfrSolver.Variant.CFR_PLUS,
                        CfrSolver.ChanceMode.EXHAUSTIVE,
                        0,
                        true)
                .solve(warmup);
        new CfrSolver<>(uncached, CfrSolver.Variant.CFR_PLUS).solve(warmup);
        new CfrSolver<>(cached, CfrSolver.Variant.CFR_PLUS).solve(warmup);
        var before = measure(uncached, iterations, true);
        var after = measure(uncached, iterations, false);
        var cachedResult = measure(cached, iterations, false);
        if (!before.solutionHash().equals(after.solutionHash())
                || !before.quality().equals(after.quality())
                || !before.solutionHash().equals(cachedResult.solutionHash())
                || !before.quality().equals(cachedResult.quality()))
            throw new IllegalStateException(
                    "Cache changed the policy or exact best-response report");
        var artifact =
                new Artifact(
                        "six-max-postflop-traversal-benchmark/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        "VALIDATION_ONLY",
                        "Same physical posterior, observations, actions and chip settlement. Compare the original repeated action-list validation with validation on information-set creation plus consistency checks on every visit, then add experimental game-state caching. Two warmup iterations populate each game's existing chance/information caches; experimental node facts and action children are also populated. Measured solves restart CFR+ from zero regrets. All strategy hashes and exact best-response reports must match. Wall-clock timings depend on machine, JVM, warmup and load; they are not a universal speedup or a strategy quality improvement. Game-state caching remains disabled by default.",
                        seed,
                        warmup,
                        iterations,
                        example.sampledFlop(),
                        example.history(),
                        before,
                        after,
                        cachedResult,
                        before.solveSeconds() / after.solveSeconds(),
                        after.solveSeconds() / cachedResult.solveSeconds(),
                        after.auditSeconds() / cachedResult.auditSeconds());
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), artifact);
        System.out.printf(
                Locale.ROOT,
                "infosets=%d action_validation_speedup=%.2fx experimental_game_cache_solve_speedup=%.2fx audit_speedup=%.2fx identical_policy=%s%n",
                after.informationSets(),
                artifact.actionValidationSolveSpeedup(),
                artifact.gameCacheSolveSpeedup(),
                artifact.gameCacheAuditSpeedup(),
                after.solutionHash());
    }

    private static Measurement measure(
            SixMaxHeadsUpPostflopGame game, int iterations, boolean repeatActionValidation) {
        long started = System.nanoTime();
        var policy =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.CFR_PLUS,
                                CfrSolver.ChanceMode.EXHAUSTIVE,
                                0,
                                repeatActionValidation)
                        .solve(iterations);
        double solveSeconds = (System.nanoTime() - started) / 1e9;
        started = System.nanoTime();
        var quality = HeadsUpBestResponse.assess(game, policy);
        return new Measurement(
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                policy.strategy().size(),
                solveSeconds,
                (System.nanoTime() - started) / 1e9,
                quality);
    }
}
