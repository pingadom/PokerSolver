package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Independently constructed own-action products and original bilinear payoffs. */
final class SequenceFormAffineProjectionTest {
    record Flow(
            List<FiniteTwoPlayerSequenceForm.Sequence> sequences,
            List<FiniteTwoPlayerSequenceForm.Conservation> conservation) {}

    static Flow randomFlow(SplittableRandom random, int target) {
        var sequences = new ArrayList<FiniteTwoPlayerSequenceForm.Sequence>();
        var rows = new ArrayList<FiniteTwoPlayerSequenceForm.Conservation>();
        sequences.add(new FiniteTwoPlayerSequenceForm.Sequence(0, "", ""));
        while (sequences.size() < target && rows.size() < 64) {
            int parent = random.nextInt(sequences.size()),
                    actions = Math.min(target - sequences.size(), random.nextInt(1, 5));
            String key = "info:" + random.nextLong();
            var children = new ArrayList<Integer>();
            for (int a = 0; a < actions; a++) {
                int child = sequences.size();
                children.add(child);
                sequences.add(new FiniteTwoPlayerSequenceForm.Sequence(child, key, "a" + a));
            }
            rows.add(new FiniteTwoPlayerSequenceForm.Conservation(key, parent, children));
        }
        // Shuffle sequence indices independently of topology and sort keys against dependencies.
        var permutation = new ArrayList<Integer>();
        for (int i = 1; i < sequences.size(); i++) permutation.add(i);
        Collections.shuffle(permutation, new Random(random.nextLong()));
        int[] remap = new int[sequences.size()];
        for (int i = 1; i < sequences.size(); i++) remap[i] = permutation.get(i - 1);
        var reordered = new FiniteTwoPlayerSequenceForm.Sequence[sequences.size()];
        reordered[0] = sequences.getFirst();
        for (int i = 1; i < sequences.size(); i++) {
            var s = sequences.get(i);
            reordered[remap[i]] =
                    new FiniteTwoPlayerSequenceForm.Sequence(remap[i], s.key(), s.action());
        }
        var result = new ArrayList<FiniteTwoPlayerSequenceForm.Conservation>();
        for (var row : rows)
            result.add(
                    new FiniteTwoPlayerSequenceForm.Conservation(
                            row.key(),
                            remap[row.parentSequence()],
                            row.childSequences().stream().map(c -> remap[c]).toList()));
        result.sort(Comparator.comparing(FiniteTwoPlayerSequenceForm.Conservation::key));
        return new Flow(List.of(reordered), List.copyOf(result));
    }

    static double[] literal(Flow flow, SplittableRandom random, boolean pure) {
        double[] mass = new double[flow.sequences().size()];
        mass[0] = 1;
        boolean[] ready = new boolean[mass.length], done = new boolean[flow.conservation().size()];
        ready[0] = true;
        int remaining = done.length;
        while (remaining > 0) {
            boolean progress = false;
            for (int i = 0; i < done.length; i++) {
                var row = flow.conservation().get(i);
                if (done[i] || !ready[row.parentSequence()]) continue;
                double[] weights = new double[row.childSequences().size()];
                double sum = 0;
                int selected = random.nextInt(weights.length);
                for (int j = 0; j < weights.length; j++) {
                    weights[j] = pure ? (j == selected ? 1 : 0) : random.nextDouble();
                    sum += weights[j];
                }
                for (int j = 0; j < weights.length; j++) {
                    int child = row.childSequences().get(j);
                    mass[child] = mass[row.parentSequence()] * weights[j] / sum;
                    ready[child] = true;
                }
                done[i] = true;
                remaining--;
                progress = true;
            }
            if (!progress) throw new AssertionError("Independent literal topology");
        }
        return mass;
    }

    static double[] variables(SequenceFormAffineProjection.Projection projection, double[] mass) {
        return projection.audit().independentSequences().stream()
                .mapToDouble(i -> mass[i])
                .toArray();
    }

    static void checkProjection(SequenceFormAffineProjection.Projection p, double[] expected) {
        double[] variables = variables(p, expected), actual = p.reconstruct(variables);
        for (int i = 0; i < actual.length; i++)
            if (Math.abs(expected[i] - actual[i]) > 1e-12)
                throw new AssertionError("Literal own product differs");
        for (int i = 0; i < p.audit().inequalities().size(); i++) {
            double value = 0;
            for (int j = 0; j < variables.length; j++)
                value += p.audit().inequalities().get(i).get(j) * variables[j];
            if (value > p.audit().rhs().get(i) + 1e-12)
                throw new AssertionError("Residual nonnegative branch differs");
        }
    }

    static void rejected(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected rejection");
    }

    @Test
    void randomOriginalFlowsAndPayoffsAgreeForTwentyThousandPureAndMixedProfiles()
            throws Exception {
        var random = new SplittableRandom(261);
        long profiles = 0, maximumWork = 0;
        double maximumError = 0;
        for (int trial = 0; trial < 200; trial++) {
            var f = randomFlow(random, random.nextInt(2, 130));
            var s = randomFlow(random, random.nextInt(2, 130));
            var budget = new SequenceFormAffineProjection.Budget();
            var fp = SequenceFormAffineProjection.build(f.sequences(), f.conservation(), budget);
            var sp = SequenceFormAffineProjection.build(s.sequences(), s.conservation(), budget);
            double[][] payoff = new double[f.sequences().size()][s.sequences().size()];
            for (var row : payoff)
                for (int j = 0; j < row.length; j++) row[j] = random.nextDouble(-16, 16);
            var reduced = SequenceFormAffineProjection.reduce(payoff, fp, sp, budget);
            maximumWork = Math.max(maximumWork, budget.audit().chargedUnits());
            for (int sample = 0; sample < 100; sample++) {
                var x = literal(f, random, sample % 4 == 0);
                var y = literal(s, random, sample % 4 == 0);
                checkProjection(fp, x);
                checkProjection(sp, y);
                double[] z = variables(fp, x), w = variables(sp, y);
                double original = 0, affine = reduced.constant();
                for (int i = 0; i < x.length; i++)
                    for (int j = 0; j < y.length; j++) original += x[i] * payoff[i][j] * y[j];
                for (int i = 0; i < z.length; i++) {
                    affine += z[i] * reduced.firstCost().get(i);
                    for (int j = 0; j < w.length; j++)
                        affine += z[i] * reduced.matrix().get(i).get(j) * w[j];
                }
                for (int j = 0; j < w.length; j++) affine += w[j] * reduced.secondCost().get(j);
                double error = Math.abs(original - affine);
                maximumError = Math.max(maximumError, error);
                if (error > 1e-10) throw new AssertionError("Independent bilinear payoff");
                profiles++;
            }
            // Recompile unchanged input for deterministic identity; never return mutable
            // coefficient arrays.
            var repeat =
                    SequenceFormAffineProjection.build(
                            f.sequences(),
                            f.conservation(),
                            new SequenceFormAffineProjection.Budget());
            if (!fp.audit().equals(repeat.audit()))
                throw new AssertionError("Projection identity drift");
            try {
                fp.audit().transform().getFirst().add(7);
                throw new AssertionError("Mutable certificate");
            } catch (UnsupportedOperationException expected) {
            }
        }
        var f = randomFlow(random, 129);
        try {
            SequenceFormAffineProjection.build(
                    f.sequences(), f.conservation(), new SequenceFormAffineProjection.Budget(0));
            throw new AssertionError("Missing work gate");
        } catch (IllegalArgumentException expected) {
        }
        rejected(() -> new SequenceFormAffineProjection.Budget(-1));
        rejected(() -> new SequenceFormAffineProjection.Budget(8_000_001));
        var budget = new SequenceFormAffineProjection.Budget(3);
        budget.charge(3);
        rejected(() -> budget.charge(1));
        rejected(() -> budget.charge(-1));
        rejected(() -> budget.charge(Long.MAX_VALUE));
        // A disconnected cycle cannot be hidden by lexical ordering or zero coefficients.
        var cycle =
                List.of(
                        new FiniteTwoPlayerSequenceForm.Sequence(0, "", ""),
                        new FiniteTwoPlayerSequenceForm.Sequence(1, "x", "a"),
                        new FiniteTwoPlayerSequenceForm.Sequence(2, "y", "a"));
        try {
            SequenceFormAffineProjection.build(
                    cycle,
                    List.of(
                            new FiniteTwoPlayerSequenceForm.Conservation("x", 2, List.of(1)),
                            new FiniteTwoPlayerSequenceForm.Conservation("y", 1, List.of(2))),
                    new SequenceFormAffineProjection.Budget());
            throw new AssertionError("Missing cycle rejection");
        } catch (IllegalArgumentException expected) {
        }
        assertEquals(20000, profiles);
        assertTrue(maximumError < 1e-10);
        assertTrue(maximumWork <= FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK);
    }
}
