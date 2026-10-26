package com.pokerlab.solver;

import java.util.*;

/** Marginal EV envelopes with an exact zero diagonal; pure arithmetic, never solver authority. */
public final class ConditionalPairwiseRegret {
    public static final String ALGORITHM =
            "OTHER_ACTION_INTERVAL_DIFFERENCES_ZERO_DIAGONAL_OUTWARD_ROUNDED/v1";

    public record Interval(String action, double lower, double upper) {
        public Interval {
            if (action == null
                    || action.isBlank()
                    || action.length() > 128
                    || !Double.isFinite(lower)
                    || !Double.isFinite(upper)
                    || lower > upper)
                throw new IllegalArgumentException("Finite ordered action EV interval required");
        }
    }

    public record Bound(String action, double lower, double upper) {}

    private ConditionalPairwiseRegret() {}

    public static List<Bound> bounds(List<Interval> intervals) {
        intervals = List.copyOf(intervals);
        if (intervals.size() < 2
                || intervals.size() > 16
                || intervals.stream().map(Interval::action).distinct().count() != intervals.size())
            throw new IllegalArgumentException("Distinct bounded action menu required");
        var result = new ArrayList<Bound>();
        for (var selected : intervals) {
            double lower = 0, upper = 0;
            for (var alternative : intervals) {
                if (selected.action().equals(alternative.action())) continue;
                double lo = alternative.lower() - selected.upper();
                double hi = alternative.upper() - selected.lower();
                if (!Double.isFinite(lo) || !Double.isFinite(hi))
                    throw new IllegalArgumentException("Pairwise difference exceeds finite range");
                double paddedLower = Math.max(0, Math.nextDown(lo));
                double paddedUpper = Math.max(0, Math.nextUp(hi));
                if (!Double.isFinite(paddedLower) || !Double.isFinite(paddedUpper))
                    throw new IllegalArgumentException(
                            "Padded pairwise bound exceeds finite range");
                lower = Math.max(lower, paddedLower);
                upper = Math.max(upper, paddedUpper);
            }
            result.add(new Bound(selected.action(), lower, upper));
        }
        return List.copyOf(result);
    }

    static List<SixMaxSuppliedIntervalFeedback.Move> moves(
            SixMaxRefinedConditionalStudy.Report source, double limit) {
        var intervals =
                source.actions().stream()
                        .map(a -> new Interval(a.action(), a.lowerUtilityBb(), a.upperUtilityBb()))
                        .toList();
        var bounds = bounds(intervals);
        var moves = new ArrayList<SixMaxSuppliedIntervalFeedback.Move>();
        for (int i = 0; i < intervals.size(); i++) {
            var ev = intervals.get(i);
            var loss = bounds.get(i);
            moves.add(
                    new SixMaxSuppliedIntervalFeedback.Move(
                            ev.action(),
                            ev.lower(),
                            ev.upper(),
                            loss.lower(),
                            loss.upper(),
                            SixMaxSuppliedIntervalFeedback.classify(
                                    loss.lower(), loss.upper(), limit)));
        }
        return List.copyOf(moves);
    }
}
