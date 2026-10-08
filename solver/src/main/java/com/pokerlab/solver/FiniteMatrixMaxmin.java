package com.pokerlab.solver;

import java.util.*;

/** Owned bounded simplex for a row maximizer and column minimizer; no external LP dependency. */
public final class FiniteMatrixMaxmin {
    public static final int MAX_PLANS = 64;
    public static final int MAX_PIVOTS = 10_000;
    public static final double MAX_PAYOFF = 1_000_000;
    public static final double CERTIFICATE_TOLERANCE = 1e-9;
    private static final double EPS = 1e-12;

    public record Solution(
            List<Double> rowMixture,
            List<Double> columnMixture,
            double lowerValue,
            double upperValue,
            int pivots) {
        public Solution {
            rowMixture = List.copyOf(rowMixture);
            columnMixture = List.copyOf(columnMixture);
            validateMixture(rowMixture);
            validateMixture(columnMixture);
            if (!Double.isFinite(lowerValue)
                    || !Double.isFinite(upperValue)
                    || upperValue < lowerValue - CERTIFICATE_TOLERANCE
                    || upperValue - lowerValue > CERTIFICATE_TOLERANCE
                    || pivots < 0
                    || pivots > MAX_PIVOTS)
                throw new IllegalArgumentException("Invalid maxmin certificate");
        }
    }

    private FiniteMatrixMaxmin() {}

    public static Solution solve(double[][] values) {
        Objects.requireNonNull(values, "values");
        if (values.length < 1
                || values.length > MAX_PLANS
                || values[0] == null
                || values[0].length < 1
                || values[0].length > MAX_PLANS)
            throw new IllegalArgumentException("Matrix must have 1..64 rows and columns");
        int m = values.length, n = values[0].length;
        double minimum = Double.POSITIVE_INFINITY;
        for (var row : values) {
            if (row == null || row.length != n) throw new IllegalArgumentException("Ragged matrix");
            for (double value : row) {
                if (!Double.isFinite(value) || Math.abs(value) > MAX_PAYOFF)
                    throw new IllegalArgumentException("Finite bounded payoffs required");
                minimum = Math.min(minimum, value);
            }
        }
        // Positive A: max sum(y), A y <= 1, y >= 0. Slack basis makes the origin feasible.
        // Objective slack coefficients are the dual row weights; normalize both LP solutions.
        double shift = 1 - minimum;
        int rhs = n + m;
        double[][] table = new double[m + 1][rhs + 1];
        int[] basis = new int[m];
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) table[r][c] = values[r][c] + shift;
            table[r][n + r] = 1;
            table[r][rhs] = 1;
            basis[r] = n + r;
        }
        for (int c = 0; c < n; c++) table[m][c] = -1;
        int pivots = 0;
        while (true) {
            // Bland's variable order, including slack variables, bounds degenerate cycling.
            int enter = -1;
            for (int c = 0; c < rhs; c++)
                if (table[m][c] < -EPS) {
                    enter = c;
                    break;
                }
            if (enter < 0) break;
            int leave = -1;
            double ratio = Double.POSITIVE_INFINITY;
            for (int r = 0; r < m; r++)
                if (table[r][enter] > EPS) {
                    if (table[r][rhs] < -CERTIFICATE_TOLERANCE)
                        throw new IllegalStateException("Simplex feasibility lost");
                    double candidate = Math.max(0, table[r][rhs]) / table[r][enter];
                    if (candidate < ratio - EPS
                            || Math.abs(candidate - ratio) <= EPS
                                    && (leave < 0 || basis[r] < basis[leave])) {
                        ratio = candidate;
                        leave = r;
                    }
                }
            if (leave < 0) throw new IllegalStateException("Unbounded positive matrix LP");
            if (++pivots > MAX_PIVOTS)
                throw new IllegalStateException("Simplex pivot cap exceeded");
            double pivot = table[leave][enter];
            for (int c = 0; c <= rhs; c++) table[leave][c] /= pivot;
            for (int r = 0; r <= m; r++)
                if (r != leave) {
                    double factor = table[r][enter];
                    for (int c = 0; c <= rhs; c++) table[r][c] -= factor * table[leave][c];
                    table[r][enter] = 0;
                }
            table[leave][enter] = 1;
            basis[leave] = enter;
            for (var row : table)
                for (double value : row)
                    if (!Double.isFinite(value))
                        throw new IllegalStateException("Nonfinite simplex arithmetic");
        }
        double[] first = new double[m], second = new double[n];
        for (int r = 0; r < m; r++) {
            first[r] = table[m][n + r];
            if (basis[r] < n) second[basis[r]] = table[r][rhs];
        }
        var rowMixture = normalize(first);
        var columnMixture = normalize(second);
        // Recompute bounds on the ORIGINAL matrix, independently of tableau termination.
        double lower = Double.POSITIVE_INFINITY, upper = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < n; c++) {
            double value = 0;
            for (int r = 0; r < m; r++) value += rowMixture.get(r) * values[r][c];
            lower = Math.min(lower, value);
        }
        for (int r = 0; r < m; r++) {
            double value = 0;
            for (int c = 0; c < n; c++) value += columnMixture.get(c) * values[r][c];
            upper = Math.max(upper, value);
        }
        if (!Double.isFinite(lower)
                || !Double.isFinite(upper)
                || upper - lower > CERTIFICATE_TOLERANCE
                || upper < lower - CERTIFICATE_TOLERANCE)
            throw new IllegalStateException("Uncertified original matrix gap: " + (upper - lower));
        return new Solution(rowMixture, columnMixture, lower, upper, pivots);
    }

    private static List<Double> normalize(double[] weights) {
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            if (!Double.isFinite(weights[i]) || weights[i] < -CERTIFICATE_TOLERANCE)
                throw new IllegalStateException("Invalid simplex mixture");
            weights[i] = Math.max(0, weights[i]);
            sum += weights[i];
        }
        if (!Double.isFinite(sum) || sum <= 0)
            throw new IllegalStateException("Zero simplex mixture");
        var result = new ArrayList<Double>();
        for (double weight : weights) result.add(weight / sum);
        return List.copyOf(result);
    }

    private static void validateMixture(List<Double> mix) {
        if (mix.isEmpty()
                || mix.size() > MAX_PLANS
                || mix.stream().anyMatch(x -> x == null || !Double.isFinite(x) || x < 0 || x > 1)
                || Math.abs(mix.stream().mapToDouble(Double::doubleValue).sum() - 1) > 1e-12)
            throw new IllegalArgumentException("Invalid normalized mixture");
    }
}
