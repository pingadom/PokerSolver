package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerAffineSequenceForm.*;

import java.util.*;

/**
 * Exact integer elimination of dependent own-action masses. A projection is not a solved strategy
 * or trainer admission.
 */
final class SequenceFormAffineProjection {
    static final long MAX_REDUCTION_WORK = FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK;

    static final class Budget {
        private final long limit;
        private long used;

        Budget() {
            this(MAX_REDUCTION_WORK);
        }

        Budget(long limit) {
            if (limit < 0 || limit > MAX_REDUCTION_WORK)
                throw reject(Failure.INVALID_INPUT, "Invalid reduced work limit");
            this.limit = limit;
        }

        void charge(long count) {
            if (count < 0 || count > limit - used)
                throw reject(Failure.WORK_LIMIT, "Affine reduction work exhausted");
            used += count;
        }

        ReductionWork audit() {
            return new ReductionWork(used, limit);
        }
    }

    static final class Projection {
        private final int[] offset, independent, rhs;
        private final int[][] transform, matrix;
        private final ProjectionAudit audit;

        private Projection(
                int[] offset,
                int[] independent,
                int[][] transform,
                int[][] matrix,
                int[] rhs,
                ProjectionAudit audit) {
            this.offset = offset;
            this.independent = independent;
            this.transform = transform;
            this.matrix = matrix;
            this.rhs = rhs;
            this.audit = audit;
        }

        ProjectionAudit audit() {
            return audit;
        }

        int variables() {
            return independent.length;
        }

        double[] reconstruct(double[] variables) {
            if (variables.length != independent.length)
                throw reject(Failure.INVALID_INPUT, "Projection cardinality");
            double[] result = new double[offset.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = offset[i];
                for (int j = 0; j < variables.length; j++) {
                    if (!Double.isFinite(variables[j]))
                        throw reject(Failure.INVALID_INPUT, "Nonfinite projected variable");
                    result[i] += transform[i][j] * variables[j];
                }
                finite(result[i]);
            }
            return result;
        }
    }

    private static List<Integer> integers(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    private static List<List<Integer>> integers(int[][] values) {
        return Arrays.stream(values).map(SequenceFormAffineProjection::integers).toList();
    }

    private static List<Double> doubles(double[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    private static List<List<Double>> doubles(double[][] values) {
        return Arrays.stream(values).map(SequenceFormAffineProjection::doubles).toList();
    }

    private static double finite(double value) {
        if (!Double.isFinite(value))
            throw reject(Failure.NUMERICAL_FAILURE, "Nonfinite projected payoff");
        return value;
    }

    static Projection build(
            List<FiniteTwoPlayerSequenceForm.Sequence> sequences,
            List<FiniteTwoPlayerSequenceForm.Conservation> conservation,
            Budget budget)
            throws Exception {
        Objects.requireNonNull(budget);
        Objects.requireNonNull(sequences);
        Objects.requireNonNull(conservation);
        int n = sequences.size(), h = conservation.size();
        if (n < 2
                || n > FiniteTwoPlayerSequenceForm.MAX_SEQUENCES_PER_ACTOR
                || h < 1
                || h > FiniteTwoPlayerSequenceForm.MAX_INFORMATION_SETS_PER_ACTOR)
            throw reject(Failure.INVALID_INPUT, "Bounded original flow dimensions");
        sequences = List.copyOf(sequences);
        conservation = List.copyOf(conservation);
        budget.charge((long) n + h);
        if (!sequences.getFirst().equals(new FiniteTwoPlayerSequenceForm.Sequence(0, "", "")))
            throw reject(Failure.INVALID_INPUT, "Original empty sequence");
        for (int i = 0; i < n; i++)
            if (sequences.get(i).index() != i)
                throw reject(Failure.INVALID_INPUT, "Original sequence index");
        boolean[] childSeen = new boolean[n];
        childSeen[0] = true;
        Set<String> keys = new HashSet<>();
        int variables = 0;
        for (var row : conservation) {
            if (row.key() == null
                    || !keys.add(row.key())
                    || row.parentSequence() < 0
                    || row.parentSequence() >= n
                    || row.childSequences().isEmpty()
                    || row.childSequences().size() > FiniteTwoPlayerSequenceForm.MAX_ACTIONS)
                throw reject(Failure.INVALID_INPUT, "Original conservation metadata");
            variables += row.childSequences().size() - 1;
            for (int child : row.childSequences()) {
                if (child <= 0
                        || child >= n
                        || childSeen[child]
                        || !sequences.get(child).key().equals(row.key()))
                    throw reject(Failure.INVALID_INPUT, "Original child ownership");
                childSeen[child] = true;
            }
        }
        for (boolean seen : childSeen)
            if (!seen) throw reject(Failure.INVALID_INPUT, "Missing original sequence");
        budget.charge((long) n * (variables + 1) + (long) h * (variables + 1) + n + h);
        int[] offset = new int[n],
                independent = new int[variables],
                rhs = new int[h],
                firstVariable = new int[h];
        int[][] transform = new int[n][variables], matrix = new int[h][variables];
        offset[0] = 1;
        int next = 0;
        for (int row = 0; row < h; row++) {
            firstVariable[row] = next;
            var children = conservation.get(row).childSequences();
            for (int a = 0; a < children.size() - 1; a++) independent[next++] = children.get(a);
        }
        // Dependencies, not lexical key order, determine which original parent is available.
        boolean[] available = new boolean[n], done = new boolean[h];
        available[0] = true;
        int remaining = h;
        while (remaining > 0) {
            boolean progressed = false;
            for (int row = 0; row < h; row++) {
                budget.charge(1);
                var c = conservation.get(row);
                int parent = c.parentSequence();
                if (done[row] || !available[parent]) continue;
                budget.charge(2L * variables + c.childSequences().size());
                int last = c.childSequences().getLast();
                offset[last] = offset[parent];
                System.arraycopy(transform[parent], 0, transform[last], 0, variables);
                for (int j = 0; j < variables; j++) matrix[row][j] = -transform[parent][j];
                rhs[row] = offset[parent];
                for (int a = 0; a < c.childSequences().size() - 1; a++) {
                    int variable = firstVariable[row] + a, child = c.childSequences().get(a);
                    transform[child][variable] = 1;
                    transform[last][variable] -= 1;
                    matrix[row][variable] += 1;
                }
                for (int child : c.childSequences()) available[child] = true;
                done[row] = true;
                remaining--;
                progressed = true;
            }
            if (!progressed)
                throw reject(Failure.INVALID_INPUT, "Cyclic or disconnected original flow");
        }
        // These are exact integer identities. No numerical tolerance can conceal a wrong topology.
        budget.charge((long) n * (variables + 1));
        for (int i = 0; i < n; i++) {
            if (offset[i] < 0 || offset[i] > 1)
                throw reject(Failure.INVALID_INPUT, "Original flow offset");
            for (int coefficient : transform[i])
                if (Math.abs(coefficient) > 1)
                    throw reject(Failure.INVALID_INPUT, "Original flow coefficient");
        }
        for (var c : conservation) {
            budget.charge((long) (c.childSequences().size() + 1) * (variables + 1));
            int constant = -offset[c.parentSequence()];
            for (int child : c.childSequences()) constant += offset[child];
            if (constant != 0)
                throw reject(Failure.INVALID_INPUT, "Symbolic original constant flow");
            for (int j = 0; j < variables; j++) {
                int coefficient = -transform[c.parentSequence()][j];
                for (int child : c.childSequences()) coefficient += transform[child][j];
                if (coefficient != 0)
                    throw reject(Failure.INVALID_INPUT, "Symbolic original variable flow");
            }
        }
        var values =
                Map.of(
                        "independentSequences",
                        integers(independent),
                        "offset",
                        integers(offset),
                        "transform",
                        integers(transform),
                        "inequalities",
                        integers(matrix),
                        "rhs",
                        integers(rhs));
        var audit =
                new ProjectionAudit(
                        SixMaxHistoryPhysicalConditionalRefinement.hash(
                                Map.of("sequences", sequences, "conservation", conservation)),
                        SixMaxHistoryPhysicalConditionalRefinement.hash(values),
                        integers(independent),
                        integers(offset),
                        integers(transform),
                        integers(matrix),
                        integers(rhs));
        return new Projection(offset, independent, transform, matrix, rhs, audit);
    }

    static ProjectedPayoffAudit reduce(
            double[][] original, Projection first, Projection second, Budget budget)
            throws Exception {
        int n = first.offset.length,
                m = second.offset.length,
                z = first.variables(),
                w = second.variables();
        if (original == null || original.length != n)
            throw reject(Failure.INVALID_INPUT, "Original payoff dimensions");
        for (var row : original) {
            if (row == null || row.length != m)
                throw reject(Failure.INVALID_INPUT, "Original payoff dimensions");
            for (double value : row) finite(value);
        }
        budget.charge((long) n * m * (w + 1) + (long) n * (1L + z * (w + 1L) + w));
        double[] ay0 = new double[n], a = new double[z], b = new double[w];
        double[][] ay = new double[n][w], d = new double[z][w];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                ay0[i] += original[i][j] * second.offset[j];
                for (int k = 0; k < w; k++) ay[i][k] += original[i][j] * second.transform[j][k];
            }
        double constant = 0;
        for (int i = 0; i < n; i++) {
            constant += first.offset[i] * ay0[i];
            for (int j = 0; j < z; j++) {
                a[j] += first.transform[i][j] * ay0[i];
                for (int k = 0; k < w; k++) d[j][k] += first.transform[i][j] * ay[i][k];
            }
            for (int k = 0; k < w; k++) b[k] += first.offset[i] * ay[i][k];
        }
        finite(constant);
        for (double value : a) finite(value);
        for (double value : b) finite(value);
        for (var row : d) for (double value : row) finite(value);
        var values =
                Map.of(
                        "constant",
                        constant,
                        "firstCost",
                        doubles(a),
                        "secondCost",
                        doubles(b),
                        "matrix",
                        doubles(d));
        return new ProjectedPayoffAudit(
                SixMaxHistoryPhysicalConditionalRefinement.hash(original),
                SixMaxHistoryPhysicalConditionalRefinement.hash(values),
                constant,
                doubles(a),
                doubles(b),
                doubles(d));
    }

    static BoundedLinearProgram.Result optimize(
            ProjectedPayoffAudit payoff,
            Projection own,
            Projection other,
            boolean row,
            Budget budget)
            throws Exception {
        // x=x0+Tz, y=y0+Uw; payoff=c0+a.z+b.w+z'Dw, Pz<=p, Qw<=q.
        // Row max: a.z-q.lambda, -D'z-Q'lambda<=b. Add c0 to its optimum.
        // Column max: -b.w-p.mu, Dw-P'mu<=-a. Subtract its optimum from c0.
        // All variables are nonnegative, including the inequality duals; no free-variable split.
        int primal = own.variables(),
                dual = other.matrix.length,
                inequalities = other.variables(),
                rows = inequalities + own.matrix.length,
                columns = primal + dual;
        budget.charge((long) rows * columns + rows + columns);
        double[][] matrix = new double[rows][columns];
        double[] rhs = new double[rows], cost = new double[columns];
        for (int j = 0; j < primal; j++)
            cost[j] = row ? payoff.firstCost().get(j) : -payoff.secondCost().get(j);
        for (int j = 0; j < dual; j++) cost[primal + j] = -other.rhs[j];
        for (int i = 0; i < inequalities; i++) {
            rhs[i] = row ? payoff.secondCost().get(i) : -payoff.firstCost().get(i);
            for (int j = 0; j < primal; j++)
                matrix[i][j] = row ? -payoff.matrix().get(j).get(i) : payoff.matrix().get(i).get(j);
            for (int j = 0; j < dual; j++) matrix[i][primal + j] = -other.matrix[j][i];
        }
        for (int i = 0; i < own.matrix.length; i++) {
            for (int j = 0; j < primal; j++) matrix[inequalities + i][j] = own.matrix[i][j];
            rhs[inequalities + i] = own.rhs[i];
        }
        return BoundedLinearProgram.solve(matrix, rhs, cost);
    }

    static List<Double> realization(
            Projection projection, BoundedLinearProgram.Result solved, Budget budget) {
        budget.charge((long) projection.offset.length * (projection.variables() + 1));
        double[] variables =
                solved.point().subList(0, projection.variables()).stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray();
        double[] mass = projection.reconstruct(variables);
        var result = new ArrayList<Double>();
        for (double value : mass) {
            if (value < -BoundedLinearProgram.CERTIFICATE_TOLERANCE)
                throw reject(Failure.NUMERICAL_FAILURE, "Invalid original affine realization");
            result.add(Math.max(0, value));
        }
        return List.copyOf(result);
    }
}
