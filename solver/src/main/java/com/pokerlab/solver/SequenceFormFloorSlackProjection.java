package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerBehaviorFloor.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Epsilon-dependent affine projection with nonnegative excess mass for each free child. */
final class SequenceFormFloorSlackProjection {
    record Projection(
            Flow flow,
            double epsilon,
            double[] offset,
            double[][] transform,
            double[][] matrix,
            double[] rhs,
            Map<Integer, Integer> free,
            List<Integer> order,
            FiniteTwoPlayerSlackFloor.ProjectionAudit audit) {
        int variables() {
            return transform[0].length;
        }
    }

    private SequenceFormFloorSlackProjection() {}

    static List<Double> list(double[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    static List<List<Double>> list(double[][] values) {
        return Arrays.stream(values).map(SequenceFormFloorSlackProjection::list).toList();
    }

    static Projection build(Flow flow, double epsilon, SequenceFormAffineProjection.Budget budget)
            throws Exception {
        var free = new TreeMap<Integer, Integer>();
        for (var c : flow.conservation())
            for (int child : c.childSequences().subList(0, c.childSequences().size() - 1))
                free.put(child, free.size());
        int n = free.size(), rows = flow.conservation().size(), sequences = flow.sequences().size();
        budget.charge((long) (sequences + rows) * (n + 1));
        var offset = new double[sequences];
        var transform = new double[sequences][n];
        offset[0] = 1;
        var matrix = new double[rows][n];
        var rhs = new double[rows];
        var order = new ArrayList<Integer>();
        for (int i = 0; i < rows; i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> flow.infos().get(i).ownHistory().size()));
        var written = new boolean[sequences];
        written[0] = true;
        for (int index : order) {
            var c = flow.conservation().get(index);
            int parent = c.parentSequence(),
                    k = c.childSequences().size(),
                    last = c.childSequences().getLast();
            if (!written[parent])
                throw reject(Failure.INVALID_INPUT, "Unordered own-action floor graph");
            double alpha = 1 - k * epsilon, remaining = 1 - (k - 1) * epsilon;
            for (int child : c.childSequences()) {
                if (written[child])
                    throw reject(Failure.INVALID_INPUT, "Duplicate floor child sequence");
                written[child] = true;
            }
            for (int child : c.childSequences().subList(0, k - 1)) {
                offset[child] = epsilon * offset[parent];
                for (int j = 0; j < n; j++) transform[child][j] = epsilon * transform[parent][j];
                transform[child][free.get(child)] += 1;
            }
            offset[last] = remaining * offset[parent];
            for (int j = 0; j < n; j++) {
                transform[last][j] = remaining * transform[parent][j];
                matrix[index][j] = -alpha * transform[parent][j];
            }
            for (int child : c.childSequences().subList(0, k - 1)) {
                transform[last][free.get(child)] -= 1;
                matrix[index][free.get(child)] += 1;
            }
            rhs[index] = alpha * offset[parent];
        }
        for (boolean complete : written)
            if (!complete)
                throw reject(Failure.INVALID_INPUT, "Incomplete floor sequence projection");
        // Independently check E*q0=e and E*T=0 against EVERY original conservation row.
        double residual = 0;
        for (var c : flow.conservation()) {
            budget.charge((long) (c.childSequences().size() + 1) * (n + 1));
            for (int j = -1; j < n; j++) {
                double value =
                        j < 0 ? -offset[c.parentSequence()] : -transform[c.parentSequence()][j];
                for (int child : c.childSequences())
                    value += j < 0 ? offset[child] : transform[child][j];
                residual = Math.max(residual, Math.abs(value));
            }
        }
        if (!Double.isFinite(residual) || residual > 1e-12)
            throw reject(
                    Failure.NUMERICAL_FAILURE, "Original floor projection conservation failed");
        var values =
                Map.of(
                        "epsilon",
                        epsilon,
                        "free",
                        free,
                        "order",
                        order,
                        "offset",
                        list(offset),
                        "transform",
                        list(transform),
                        "inequalities",
                        list(matrix),
                        "rhs",
                        list(rhs));
        var audit =
                new FiniteTwoPlayerSlackFloor.ProjectionAudit(
                        SixMaxHeadsUpPreflopGame.hash(
                                Map.of(
                                        "sequences",
                                        flow.sequences(),
                                        "conservation",
                                        flow.conservation())),
                        SixMaxHeadsUpPreflopGame.hash(values),
                        new ArrayList<>(free.keySet()),
                        order,
                        list(offset),
                        list(transform),
                        list(matrix),
                        list(rhs),
                        residual);
        return new Projection(
                flow,
                epsilon,
                offset,
                transform,
                matrix,
                rhs,
                Map.copyOf(free),
                List.copyOf(order),
                audit);
    }

    static FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit reduce(
            double[][] original,
            Projection first,
            Projection second,
            SequenceFormAffineProjection.Budget budget)
            throws Exception {
        int n = first.offset().length,
                m = second.offset().length,
                z = first.variables(),
                w = second.variables();
        budget.charge((long) n * m * (w + 1) + (long) n * (1L + z * (w + 1L) + w));
        var ay0 = new double[n];
        var ay = new double[n][w];
        var a = new double[z];
        var b = new double[w];
        var d = new double[z][w];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                ay0[i] += original[i][j] * second.offset()[j];
                for (int k = 0; k < w; k++) ay[i][k] += original[i][j] * second.transform()[j][k];
            }
        double constant = 0;
        for (int i = 0; i < n; i++) {
            constant += first.offset()[i] * ay0[i];
            for (int j = 0; j < z; j++) {
                a[j] += first.transform()[i][j] * ay0[i];
                for (int k = 0; k < w; k++) d[j][k] += first.transform()[i][j] * ay[i][k];
            }
            for (int k = 0; k < w; k++) b[k] += first.offset()[i] * ay[i][k];
        }
        var values =
                Map.of(
                        "constant",
                        constant,
                        "firstCost",
                        list(a),
                        "secondCost",
                        list(b),
                        "matrix",
                        list(d));
        return new FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit(
                SixMaxHeadsUpPreflopGame.hash(original),
                SixMaxHeadsUpPreflopGame.hash(values),
                constant,
                list(a),
                list(b),
                list(d));
    }

    static BoundedLinearProgram.Result optimize(
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit payoff,
            Projection own,
            Projection other,
            boolean row,
            SequenceFormAffineProjection.Budget budget)
            throws Exception {
        int primal = own.variables(),
                dual = other.rhs().length,
                inequalities = other.variables(),
                variables = primal + dual,
                rows = inequalities + own.rhs().length;
        if (variables > BoundedLinearProgram.MAX_VARIABLES
                || rows > BoundedLinearProgram.MAX_CONSTRAINTS)
            throw reject(Failure.INVALID_INPUT, "Slack floor form exceeds unchanged LP dimensions");
        budget.charge((long) variables * rows + variables + rows);
        var matrix = new double[rows][variables];
        var rhs = new double[rows];
        var cost = new double[variables];
        for (int j = 0; j < primal; j++)
            cost[j] = row ? payoff.firstCost().get(j) : -payoff.secondCost().get(j);
        for (int j = 0; j < dual; j++) cost[primal + j] = -other.rhs()[j];
        for (int i = 0; i < inequalities; i++) {
            rhs[i] = row ? payoff.secondCost().get(i) : -payoff.firstCost().get(i);
            for (int j = 0; j < primal; j++)
                matrix[i][j] = row ? -payoff.matrix().get(j).get(i) : payoff.matrix().get(i).get(j);
            for (int j = 0; j < dual; j++) matrix[i][primal + j] = -other.matrix()[j][i];
        }
        for (int i = 0; i < own.rhs().length; i++) {
            System.arraycopy(own.matrix()[i], 0, matrix[inequalities + i], 0, primal);
            rhs[inequalities + i] = own.rhs()[i];
        }
        return BoundedLinearProgram.solve(matrix, rhs, cost);
    }

    static FiniteTwoPlayerSlackFloor.BehaviorAudit behavior(
            Projection p,
            List<Double> point,
            Map<String, Map<String, Double>> policy,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge((long) p.offset().length * (p.variables() + 1));
        var z = point.subList(0, p.variables()).stream().mapToDouble(Double::doubleValue).toArray();
        for (double value : z)
            if (!Double.isFinite(value) || value < 0)
                throw reject(Failure.NUMERICAL_FAILURE, "Invalid nonnegative excess mass");
        var q = new double[p.offset().length];
        q[0] = 1;
        double minimum = 1, relativeFloor = 0, relativeFlow = 0, affineError = 0, residual = 0;
        for (int i : p.order()) {
            var c = p.flow().conservation().get(i);
            var info = p.flow().infos().get(i);
            double parent = q[c.parentSequence()],
                    remaining = 1 - (c.childSequences().size() - 1) * p.epsilon(),
                    total = 0;
            if (parent < Double.MIN_NORMAL)
                throw reject(
                        Failure.NUMERICAL_FAILURE,
                        "Slack floor cannot use zero-own-reach fallback");
            var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < c.childSequences().size(); j++) {
                int child = c.childSequences().get(j);
                double probability;
                if (j == c.childSequences().size() - 1) probability = remaining;
                else {
                    double excess = z[p.free().get(child)] / parent;
                    probability = p.epsilon() + excess;
                    remaining -= excess;
                }
                if (!Double.isFinite(probability) || probability < 0 || probability > 1 + 1e-8)
                    throw reject(Failure.NUMERICAL_FAILURE, "Invalid slack floor behavior");
                q[child] = parent * probability;
                total += probability;
                row.put(info.actions().get(j), probability);
            }
            relativeFlow = Math.max(relativeFlow, Math.abs(total - 1));
            if (Math.abs(total - 1) > 1e-8)
                throw reject(Failure.NUMERICAL_FAILURE, "Relative slack floor conservation failed");
            for (var entry : row.entrySet()) {
                double probability = entry.getValue() / total;
                entry.setValue(probability);
                minimum = Math.min(minimum, probability);
                relativeFloor =
                        Math.max(
                                relativeFloor,
                                Math.max(0, (p.epsilon() - probability) / p.epsilon()));
            }
            policy.put(info.key(), Map.copyOf(row));
        }
        if (relativeFloor > 1e-6)
            throw reject(Failure.NUMERICAL_FAILURE, "Relative slack floor bound failed");
        for (int i = 0; i < q.length; i++) {
            double reconstructed = p.offset()[i];
            for (int j = 0; j < z.length; j++) reconstructed += p.transform()[i][j] * z[j];
            affineError = Math.max(affineError, Math.abs(reconstructed - q[i]));
        }
        for (var c : p.flow().conservation()) {
            double sum = -q[c.parentSequence()];
            for (int child : c.childSequences()) sum += q[child];
            residual = Math.max(residual, Math.abs(sum));
        }
        if (!Double.isFinite(affineError) || affineError > 1e-8 || residual > 1e-8)
            throw reject(Failure.NUMERICAL_FAILURE, "Original slack floor realization failed");
        return new FiniteTwoPlayerSlackFloor.BehaviorAudit(
                new FlowAudit(
                        p.flow().actor(),
                        p.flow().sequences(),
                        p.flow().conservation(),
                        list(q),
                        residual),
                minimum,
                relativeFloor,
                relativeFlow,
                affineError);
    }
}
