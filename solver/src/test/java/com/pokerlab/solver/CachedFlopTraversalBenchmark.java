package com.pokerlab.solver;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Explicit diagnostic main, outside Surefire. Run from the repository root with solver
 * dependencies.
 */
public final class CachedFlopTraversalBenchmark {
    private static final List<String> INPUTS =
            List.of(
                    "supplied-four-button-hand-btn-flop-feedback-request.json",
                    "supplied-paired-btn-facing-bet-feedback-request.json",
                    "supplied-weak-hand-qualified-flop-request.json");

    private record Sample(
            CfrSolution policy,
            MultiPlayerCfrSolver.Statistics statistics,
            long pruned,
            long nanos) {}

    private static Sample original(
            SixMaxOwnedFlopReferences game,
            MultiPlayerCfrSolver.InactivePruning mode,
            int iterations) {
        long start = System.nanoTime();
        var solver = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS, mode);
        var policy = solver.solve(iterations);
        return new Sample(
                policy,
                solver.statistics(),
                solver.inactiveUtilityPrunedNodes(),
                System.nanoTime() - start);
    }

    private static Sample cached(
            SixMaxOwnedFlopReferences owned,
            CachedTwoPlayerCfrGame game,
            MultiPlayerCfrSolver.InactivePruning mode,
            int iterations) {
        long start = System.nanoTime();
        if (mode == MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY) {
            // Exercise the actual production entry point; its public State API remains uncached.
            var run = owned.trainBounded(iterations);
            return new Sample(
                    run.policy(),
                    run.audit().statistics(),
                    run.audit().prunedSuffixRoots(),
                    System.nanoTime() - start);
        }
        var solver = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS, mode);
        var policy = solver.solve(iterations);
        return new Sample(
                policy,
                solver.statistics(),
                solver.inactiveUtilityPrunedNodes(),
                System.nanoTime() - start);
    }

    private static void compare(Sample a, Sample b) {
        if (!a.policy().equals(b.policy())
                || !a.statistics().equals(b.statistics())
                || a.pruned() != b.pruned())
            throw new IllegalStateException("Policy or traversal inventory differs; no retry");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException(
                    "Usage: CachedFlopTraversalBenchmark <new-result.json>");
        var output = Path.of(args[0]);
        var budget = Path.of(args[0] + ".budget.json");
        if (Files.exists(output) || Files.exists(budget))
            throw new IllegalArgumentException("New result and budget paths required");
        var hashes = new TreeMap<String, String>();
        for (String input : INPUTS)
            hashes.put(
                    input,
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    Files.readAllBytes(
                                                            Path.of("docs/data", input)))));
        var reservation = new LinkedHashMap<String, Object>();
        reservation.put("scope", "REFERENCE_TRAVERSAL_MICROBENCHMARK_ONLY_NO_TRAINER_ADMISSION");
        reservation.put("inputs", hashes);
        reservation.put("maximumCfrRuns", 18);
        reservation.put("totalDiagnosticIterations", 25200);
        reservation.put("warmupIterationsPerImplementationPerGame", 200);
        reservation.put("measuredIterationsPerRun", 2000);
        reservation.put("measuredPairsPerGame", 2);
        reservation.put("freshLpSolves", 0);
        reservation.put("fullQualificationRuns", 0);
        reservation.put("automaticRetries", 0);
        reservation.put("constructionIncludedInTiming", false);
        reservation.put("javaVersion", System.getProperty("java.version"));
        Files.writeString(
                budget, SixMaxTextureStudy.json(reservation), StandardOpenOption.CREATE_NEW);
        var results = new ArrayList<Object>();
        int runs = 0;
        for (int index = 0; index < INPUTS.size(); index++) {
            String input = INPUTS.get(index);
            var request = Path.of("docs/data", input);
            var connected =
                    index == 0
                            ? SixMaxBtnFlopFeedback.readRequest(request).connected()
                            : SixMaxRefinedFlopFeedback.readRequest(request)
                                    .conditional()
                                    .parent()
                                    .connected();
            var source =
                    new SixMaxSuppliedFlopBettingGame(
                            connected.input(), connected.flop(), connected.betPotFraction());
            var owned = new SixMaxOwnedFlopReferences(source);
            var cache = new CachedTwoPlayerCfrGame(source);
            var mode =
                    index == 0
                            ? MultiPlayerCfrSolver.InactivePruning.NONE
                            : MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY;
            compare(original(owned, mode, 200), cached(owned, cache, mode, 200));
            runs += 2;
            var pairs = new ArrayList<Object>();
            for (int repeat = 0; repeat < 2; repeat++) {
                Sample a, b;
                if (repeat == 0) {
                    a = original(owned, mode, 2000);
                    b = cached(owned, cache, mode, 2000);
                } else {
                    b = cached(owned, cache, mode, 2000);
                    a = original(owned, mode, 2000);
                }
                runs += 2;
                compare(a, b);
                pairs.add(
                        Map.of(
                                "repeat",
                                repeat,
                                "baselineNanos",
                                a.nanos(),
                                "cachedNanos",
                                b.nanos(),
                                "hotTraversalSpeedRatio",
                                (double) a.nanos() / b.nanos(),
                                "policyHash",
                                SixMaxHeadsUpPreflopGame.hash(a.policy()),
                                "statistics",
                                a.statistics(),
                                "prunedSuffixRoots",
                                a.pruned()));
            }
            results.add(
                    Map.of(
                            "input",
                            input,
                            "pruning",
                            mode,
                            "comparisons",
                            pairs,
                            "validatedTreeNodes",
                            cache.validatedTreeNodes()));
            System.out.println("Compared " + input + " mode=" + mode + "; policy/work identical");
        }
        if (runs != 18) throw new IllegalStateException("Run reservation differs");
        for (String input : INPUTS)
            if (!hashes.get(input)
                    .equals(
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(
                                                            Files.readAllBytes(
                                                                    Path.of("docs/data", input))))))
                throw new IllegalStateException("Input changed during benchmark");
        var result = new LinkedHashMap<String, Object>(reservation);
        result.put("actualCfrRuns", runs);
        result.put("allWholePoliciesBitwiseEqual", true);
        result.put("allTraversalStatisticsEqual", true);
        result.put("allInputsUnchanged", true);
        result.put("results", results);
        Files.writeString(output, SixMaxTextureStudy.json(result), StandardOpenOption.CREATE_NEW);
        System.out.println(SixMaxTextureStudy.json(result));
    }
}
