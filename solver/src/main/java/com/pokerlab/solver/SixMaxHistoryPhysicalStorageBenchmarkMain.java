package com.pokerlab.solver;

import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.nio.file.*;
import java.util.*;

/** Isolated-process observational benchmark. Never writes solver/admission evidence. */
public final class SixMaxHistoryPhysicalStorageBenchmarkMain {
    private record Retained(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalStudy.Checkpoint checkpoint,
            Object table,
            SixMaxOneBetFlopGame game) {}

    private SixMaxHistoryPhysicalStorageBenchmarkMain() {}

    private static Retained load(String mode, List<Path> paths) throws Exception {
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var original = SixMaxHistoryPhysicalPayoffTable.replay(paths.get(2), source, parent);
        var cp = SixMaxHistoryPhysicalStudy.read(paths.get(3), source, parent, original);
        if (mode.equals("baseline")) return new Retained(source, parent, cp, null, null);
        if (mode.equals("original"))
            return new Retained(
                    source,
                    parent,
                    cp,
                    original,
                    new SixMaxHistoryPhysicalFlopGame(source, parent, original).core());
        var compact = SixMaxHistoryPhysicalCompactStorage.replay(paths.get(4), original);
        return new Retained(source, parent, cp, compact, compact.core(source, parent));
    }

    private static long heap() throws Exception {
        // Advisory GC: measurements are observations, never assertions about a JVM guarantee.
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(100);
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 7 || !Set.of("baseline", "original", "compact").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalStorageBenchmarkMain <baseline|original|compact> <source> <rank-table> <original-table> <checkpoint> <compact-table> <measurement-output>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        if (Files.exists(paths.get(5)))
            throw new IllegalArgumentException("Measurement output must be new");
        long started = System.nanoTime();
        var retained = load(args[0], paths);
        long loadNanos = System.nanoTime() - started;
        long liveHeap = heap();
        var times = new ArrayList<Long>();
        List<Double> utilities = List.of();
        if (retained.game() != null) {
            for (int i = 0; i < 2; i++)
                MultiPlayerStrategyEvaluator.utilities(
                        retained.game(), retained.checkpoint().solution());
            double[] first = null;
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                var values =
                        MultiPlayerStrategyEvaluator.utilities(
                                retained.game(), retained.checkpoint().solution());
                times.add(System.nanoTime() - start);
                if (first != null && !Arrays.equals(first, values))
                    throw new IllegalStateException("Repeated evaluator values differ");
                first = values;
            }
            utilities = Arrays.stream(first).boxed().toList();
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "OBSERVATIONAL_RESOURCE_MEASUREMENT_NOT_ADMISSION");
        result.put("mode", args[0]);
        result.put("javaRuntime", System.getProperty("java.runtime.version"));
        result.put("os", System.getProperty("os.name"));
        result.put("maximumHeapBytes", Runtime.getRuntime().maxMemory());
        result.put("advisoryGcRetainedHeapBytes", liveHeap);
        result.put("loadAndExactPayoffReplayNanos", loadNanos);
        result.put("payoffTableHash", retained.checkpoint().binding().payoffTableHash());
        result.put("solutionHash", retained.checkpoint().solutionHash());
        result.put("completeStates", retained.checkpoint().binding().completeTreeStates());
        result.put("twoWarmupsFiveEvaluatorNanos", times);
        result.put("utilitiesBb", utilities);
        Files.writeString(
                paths.get(5), SixMaxTextureStudy.json(result), StandardOpenOption.CREATE_NEW);
        Reference.reachabilityFence(retained);
        System.out.println(SixMaxTextureStudy.json(result));
    }
}
