package com.pokerlab.benchmark;

import com.pokerlab.core.batch.*;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.PlayerHand;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Reproducible engine-only scaling benchmark; excludes SQS, HTTP and database latency. */
public final class BenchmarkMain {
    private BenchmarkMain() {}

    private record Measurement(int workers, int repeat, double seconds, long trials) {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "docs/data/benchmark.csv");
        long trials = args.length > 1 ? Long.parseLong(args[1]) : 10_000_000;
        int repeats = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        if (repeats < 1 || repeats > 20) throw new IllegalArgumentException("repeats must be 1–20");
        var players =
                List.of(
                        new PlayerHand("AA", Card.parse("AS"), Card.parse("AH")),
                        new PlayerHand("KK", Card.parse("KS"), Card.parse("KH")),
                        new PlayerHand("QQ", Card.parse("QS"), Card.parse("QH")));
        var config = new SimulationConfiguration(players, List.of(), trials, 100_000, 123456789);
        var jobs =
                BatchPlanner.plan(UUID.fromString("00000000-0000-0000-0000-000000000001"), config);
        for (int warmup = 0; warmup < 20; warmup++) new BatchExecutor().execute(jobs.getFirst());
        List<Measurement> measurements = new ArrayList<>();
        List<PlayerResult> reference = null;
        for (int workers : new int[] {1, 2, 4, 8}) {
            for (int repeat = 1; repeat <= repeats; repeat++) {
                long start = System.nanoTime();
                List<BatchResult> results = new ArrayList<>();
                try (var pool = Executors.newFixedThreadPool(workers)) {
                    var futures =
                            jobs.stream()
                                    .map(job -> pool.submit(() -> new BatchExecutor().execute(job)))
                                    .toList();
                    for (var future : futures) results.add(future.get());
                }
                double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
                if (results.stream().mapToLong(BatchResult::trials).sum() != trials)
                    throw new IllegalStateException("Trial conservation failed");
                // Compare each batch, not only aggregate equity, across all concurrency levels.
                var flattened =
                        results.stream().flatMap(result -> result.players().stream()).toList();
                if (reference == null) reference = flattened;
                else if (!reference.equals(flattened))
                    throw new IllegalStateException("Worker count changed seeded results");
                measurements.add(new Measurement(workers, repeat, seconds, trials));
                System.out.printf(
                        Locale.ROOT,
                        "%d workers, repeat %d: %.4fs, %.0f trials/s%n",
                        workers,
                        repeat,
                        seconds,
                        trials / seconds);
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        double baseline = median(measurements, 1);
        var csv =
                new StringBuilder(
                        "trials,workers,repeat,wall_seconds,trials_per_second,speedup,efficiency\n");
        for (var value : measurements)
            csv.append(
                    String.format(
                            Locale.ROOT,
                            "%d,%d,%d,%.6f,%.2f,%.6f,%.6f%n",
                            value.trials(),
                            value.workers(),
                            value.repeat(),
                            value.seconds(),
                            value.trials() / value.seconds(),
                            baseline / value.seconds(),
                            baseline / value.seconds() / value.workers()));
        Files.writeString(output, csv);
        var summary =
                new StringBuilder("workers,median_seconds,trials_per_second,speedup,efficiency\n");
        for (int workers : new int[] {1, 2, 4, 8}) {
            double seconds = median(measurements, workers);
            summary.append(
                    String.format(
                            Locale.ROOT,
                            "%d,%.6f,%.2f,%.6f,%.6f%n",
                            workers,
                            seconds,
                            trials / seconds,
                            baseline / seconds,
                            baseline / seconds / workers));
        }
        Files.writeString(output.resolveSibling("benchmark-summary.csv"), summary);
        Files.writeString(
                output.resolveSibling("benchmark-environment.txt"),
                "Measured at: "
                        + Instant.now()
                        + "\nJava: "
                        + System.getProperty("java.version")
                        + "\nVM: "
                        + System.getProperty("java.vm.name")
                        + "\nOS: "
                        + System.getProperty("os.name")
                        + " "
                        + System.getProperty("os.arch")
                        + "\nAvailable processors: "
                        + Runtime.getRuntime().availableProcessors()
                        + "\nWarmup: 20 batches of up to 100000 trials\nScenario: AS AH vs KS KH vs QS QH; empty board; seed 123456789; batch size 100000\nBoundary: engine worker threads, pool startup included; no queue, HTTP or database\n");
    }

    private static double median(List<Measurement> measurements, int workers) {
        var sorted =
                measurements.stream()
                        .filter(m -> m.workers() == workers)
                        .mapToDouble(Measurement::seconds)
                        .sorted()
                        .toArray();
        return sorted.length % 2 == 1
                ? sorted[sorted.length / 2]
                : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2;
    }
}
