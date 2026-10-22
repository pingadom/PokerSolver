package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** v2 rejected-reach evidence only. Never used to construct positive-reach posteriors. */
final class NegativeReachBehavior {
    private static final double TOLERANCE = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    private NegativeReachBehavior() {}

    static FlowAudit reconstruct(
            Flow flow,
            List<Double> original,
            Map<String, Map<String, Double>> strategy,
            SequenceFormAffineProjection.Budget budget) {
        if (original.size() != flow.sequences().size())
            throw new IllegalStateException("Rejected flow dimensions differ");
        long work = original.size();
        for (var info : flow.infos())
            work += (long) info.actions().size() * (info.ownHistory().size() + 4) + 1;
        budget.charge(work);
        var point = new ArrayList<Double>();
        for (double value : original) {
            if (!Double.isFinite(value) || value < -TOLERANCE || value > 1 + TOLERANCE)
                throw new IllegalStateException("Invalid rejected realization mass");
            point.add(Math.max(0, value));
        }
        double residual = Math.abs(original.getFirst() - 1);
        var rows = new LinkedHashMap<String, Map<String, Double>>();
        for (var conservation : flow.conservation()) {
            double total = 0, signedTotal = 0;
            for (int child : conservation.childSequences()) {
                total += point.get(child);
                signedTotal += original.get(child);
            }
            residual =
                    Math.max(
                            residual,
                            Math.abs(signedTotal - original.get(conservation.parentSequence())));
            // Check both the original affine vector and the nonnegative conversion.
            residual =
                    Math.max(residual, Math.abs(total - point.get(conservation.parentSequence())));
            var row = new LinkedHashMap<String, Double>();
            for (int child : conservation.childSequences()) {
                double probability =
                        total > 0
                                ? point.get(child) / total
                                : 1.0 / conservation.childSequences().size();
                if (!Double.isFinite(probability) || probability < 0 || probability > 1)
                    throw new IllegalStateException("Invalid rejected child normalization");
                row.put(flow.sequences().get(child).action(), probability);
            }
            rows.put(conservation.key(), Map.copyOf(row));
        }
        if (residual > TOLERANCE)
            throw new IllegalStateException("Original rejected flow conservation failed");
        // Recompute every original coordinate from the complete perfect-recall history. A local
        // conservation check alone cannot bound the accumulated error through chained decisions.
        double maximumDifference = Math.abs(original.getFirst() - 1);
        for (var info : flow.infos()) {
            var row = rows.get(info.key());
            if (row == null || !row.keySet().equals(new HashSet<>(info.actions())))
                throw new IllegalStateException("Rejected policy action coverage differs");
            double parent = 1;
            for (var past : info.ownHistory()) {
                var pastRow = rows.get(past.key());
                if (pastRow == null || !pastRow.containsKey(past.action()))
                    throw new IllegalStateException("Rejected perfect-recall history differs");
                parent *= pastRow.get(past.action());
            }
            double sum = 0;
            for (var action : info.actions()) {
                sum += row.get(action);
                int index = flow.indices().get(new OwnAction(info.key(), action));
                maximumDifference =
                        Math.max(
                                maximumDifference,
                                Math.abs(parent * row.get(action) - original.get(index)));
            }
            if (Math.abs(sum - 1) > TOLERANCE)
                throw new IllegalStateException("Rejected policy normalization failed");
        }
        if (maximumDifference > TOLERANCE)
            throw new IllegalStateException("Rejected policy changes original realization");
        // Do not leave a partially converted policy when a gate rejects.
        strategy.putAll(rows);
        return new FlowAudit(flow.actor(), flow.sequences(), flow.conservation(), point, residual);
    }
}
