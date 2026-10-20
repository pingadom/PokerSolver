package com.pokerlab.solver;

import java.util.*;

/**
 * Owned two-phase revised simplex: maximize c.x subject to A.x <= b and x >= 0. Every certificate
 * is recomputed on the copied original problem, not an accumulated tableau. Numerical or work-limit
 * failures return no solved handle.
 */
public final class BoundedLinearProgram {
    public static final String ALGORITHM = "OWNED_TWO_PHASE_ORIGINAL_BASIS_LU_LARGEST_BLAND/v1";
    public static final String ASCENDING_L1_ALGORITHM =
            "OWNED_STABLE_ASCENDING_L1_ROWS_ORIGINAL_CERTIFICATE/v2";
    public static final String RESOLVABLE_COST_ALGORITHM =
            "OWNED_ASCENDING_L1_RESOLVABLE_COST_EXACT_RATIO_ORIGINAL_CERTIFICATE/v3";
    public static final String EXACT_POWER_TWO_ALGORITHM =
            "OWNED_EXACT_POWER_TWO_L1_RHS_RESOLVABLE_COST_ORIGINAL_CERTIFICATE/v1";
    public static final String REFINED_DIRECTION_ALGORITHM =
            "OWNED_EXACT_POWER_TWO_RESOLVABLE_COST_COMPENSATED_DIRECTION_ORIGINAL_CERTIFICATE/v2";
    public static final String ASCENDING_REFINED_DIRECTION_ALGORITHM =
            "OWNED_ASCENDING_L1_COMPENSATED_PHASE_TWO_DIRECTION_ORIGINAL_CERTIFICATE/v4";
    public static final String ASCENDING_REFINED_BASIS_ALGORITHM =
            "OWNED_ASCENDING_L1_COMPENSATED_BASIS_DUAL_DIRECTION_STRICT_RATIOS_ORIGINAL_CERTIFICATE/v5";
    public static final String RESOLVABLE_REFINED_BASIS_ALGORITHM =
            "OWNED_ASCENDING_L1_RESOLVABLE_COST_COMPENSATED_BASIS_DUAL_DIRECTION_ORIGINAL_CERTIFICATE/v6";
    public static final int DIRECTION_REFINEMENT_PASSES = 2;
    public static final int MAX_VARIABLES = 512;
    public static final int MAX_CONSTRAINTS = 512;
    public static final int MAX_PIVOTS = 10_000;
    public static final long MAX_ARITHMETIC_WORK = 4_000_000_000L;
    public static final double MAX_COEFFICIENT = 1_000_000;
    public static final double CERTIFICATE_TOLERANCE = 1e-8;
    private static final double EPS = 1e-10;
    private static final double PIVOT_EPS = 1e-12;

    public enum Failure {
        INVALID_INPUT,
        WORK_LIMIT,
        NUMERICAL_FAILURE,
        NO_CERTIFIED_OPTIMUM
    }

    /** A failure is deliberately not a certificate of infeasibility or unboundedness. */
    public static final class Rejected extends IllegalArgumentException {
        private final Failure reason;

        private Rejected(Failure reason, String detail) {
            super(detail);
            this.reason = reason;
        }

        public Failure reason() {
            return reason;
        }
    }

    public record Work(
            int phaseOnePivots,
            int cleanupPivots,
            int phaseTwoPivots,
            int basisFactorizations,
            long arithmeticWork,
            boolean blandFallback) {
        public int pivots() {
            return phaseOnePivots + cleanupPivots + phaseTwoPivots;
        }
    }

    public record Certificate(
            String algorithm,
            String problemHash,
            int variables,
            int constraints,
            double primalValue,
            double dualValue,
            double maximumPrimalViolation,
            double maximumDualViolation,
            double absoluteDualityGap,
            Work work) {}

    /** Only a successful solve can construct this deeply immutable result. */
    public static final class Result {
        private final List<Double> point, dual;
        private final Certificate certificate;

        private Result(double[] point, double[] dual, Certificate certificate) {
            this.point = Arrays.stream(point).boxed().toList();
            this.dual = Arrays.stream(dual).boxed().toList();
            this.certificate = certificate;
        }

        public List<Double> point() {
            return point;
        }

        public List<Double> dual() {
            return dual;
        }

        public Certificate certificate() {
            return certificate;
        }
    }

    private record Problem(double[][] matrix, double[] rhs, double[] cost) {}

    private BoundedLinearProgram() {}

    public static Result solve(double[][] matrix, double[] rhs, double[] cost) throws Exception {
        return solve(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    // Test controls can only make work limits smaller; there is no public cap override.
    static Result solve(double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        if (pivots < 0 || pivots > MAX_PIVOTS || work < 0 || work > MAX_ARITHMETIC_WORK)
            throw reject(Failure.INVALID_INPUT, "Invalid bounded LP work limits");
        var problem = copy(matrix, rhs, cost);
        String hash = SixMaxHistoryPhysicalConditionalRefinement.hash(problem);
        return new Engine(problem, pivots, work).solve(hash);
    }

    /** Explicit representation, never an automatic retry of a rejected original-order solve. */
    public static Result solveAscendingL1Rows(double[][] matrix, double[] rhs, double[] cost)
            throws Exception {
        return solveAscendingL1Rows(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    static Result solveAscendingL1Rows(
            double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        return solveRows(matrix, rhs, cost, pivots, work, false);
    }

    /**
     * Fixed direction refinement with the original ascending-row pivot rules; no automatic retry.
     */
    public static Result solveAscendingRefinedDirections(
            double[][] matrix, double[] rhs, double[] cost) throws Exception {
        return solveAscendingRefinedDirections(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    static Result solveAscendingRefinedDirections(
            double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        return solveRows(matrix, rhs, cost, pivots, work, false, true);
    }

    /** Explicit basis/dual refinement and strict computed phase-II ratios; no fallback. */
    public static Result solveAscendingRefinedBasis(double[][] matrix, double[] rhs, double[] cost)
            throws Exception {
        return solveAscendingRefinedBasis(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    static Result solveAscendingRefinedBasis(
            double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        return solveRows(matrix, rhs, cost, pivots, work, false, true, true);
    }

    /** Explicit resolvable pricing with fixed basis/dual/direction refinement; no retry. */
    public static Result solveResolvableRefinedBasis(double[][] matrix, double[] rhs, double[] cost)
            throws Exception {
        return solveResolvableRefinedBasis(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    static Result solveResolvableRefinedBasis(
            double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        return solveRows(matrix, rhs, cost, pivots, work, true, true, true);
    }

    /** Explicit phase-II rule; never retries or changes the default solver's certificates. */
    public static Result solveResolvableCosts(double[][] matrix, double[] rhs, double[] cost)
            throws Exception {
        return solveResolvableCosts(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    static Result solveResolvableCosts(
            double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        return solveRows(matrix, rhs, cost, pivots, work, true);
    }

    /**
     * Explicit row representation for problems whose ordinary resolvable-cost representation cannot
     * certify an optimum. This never retries a failed solve or changes its work limits. Each
     * positive power-of-two row factor must preserve every original coefficient exactly; the
     * returned dual and certificate are then checked against the original problem.
     */
    public static Result solveExactPowerOfTwoRows(double[][] matrix, double[] rhs, double[] cost)
            throws Exception {
        return solveExactPowerOfTwoRows(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    static Result solveExactPowerOfTwoRows(
            double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        return solveExactPowerOfTwoRows(matrix, rhs, cost, pivots, work, false);
    }

    /** Fixed compensated phase-II direction evaluation, selected before any optimization. */
    public static Result solveRefinedDirections(double[][] matrix, double[] rhs, double[] cost)
            throws Exception {
        return solveRefinedDirections(matrix, rhs, cost, MAX_PIVOTS, MAX_ARITHMETIC_WORK);
    }

    static Result solveRefinedDirections(
            double[][] matrix, double[] rhs, double[] cost, int pivots, long work)
            throws Exception {
        return solveExactPowerOfTwoRows(matrix, rhs, cost, pivots, work, true);
    }

    private static Result solveExactPowerOfTwoRows(
            double[][] matrix,
            double[] rhs,
            double[] cost,
            int pivots,
            long work,
            boolean refinedDirections)
            throws Exception {
        if (pivots < 0 || pivots > MAX_PIVOTS || work < 0 || work > MAX_ARITHMETIC_WORK)
            throw reject(Failure.INVALID_INPUT, "Invalid bounded LP work limits");
        dimensions(matrix, rhs, cost);
        int m = rhs.length, n = cost.length;
        var budget = new RowBudget(work);
        // Include original copying/hash/certificate, scaling and inverse mapping. The nested
        // ordered solve charges its own work to the same remaining budget.
        budget.charge(64L * m * n + 64L * m + 16L * n);
        var original = copy(matrix, rhs, cost);
        var scaled = new double[m][n];
        var bounds = new double[m];
        var factors = new double[m];
        for (int i = 0; i < m; i++) {
            double metric = Math.abs(original.rhs()[i]);
            for (double coefficient : original.matrix()[i])
                metric = finite(metric + Math.abs(coefficient));
            factors[i] = metric == 0 ? 1 : Math.scalb(1.0, -Math.getExponent(metric));
            for (int j = 0; j < n; j++)
                scaled[i][j] = exactPowerOfTwoScale(original.matrix()[i][j], factors[i]);
            bounds[i] = exactPowerOfTwoScale(original.rhs()[i], factors[i]);
        }
        var solved =
                solveRows(
                        scaled,
                        bounds,
                        original.cost(),
                        pivots,
                        work - budget.used,
                        true,
                        refinedDirections);
        budget.charge(solved.certificate().work().arithmeticWork());
        var point = solved.point().stream().mapToDouble(Double::doubleValue).toArray();
        var dual = new double[m];
        double primalViolation = 0, dualViolation = 0;
        for (int i = 0; i < m; i++) {
            dual[i] = finite(solved.dual().get(i) * factors[i]);
            primalViolation =
                    Math.max(primalViolation, dot(original.matrix()[i], point) - original.rhs()[i]);
            dualViolation = Math.max(dualViolation, -dual[i]);
        }
        for (int j = 0; j < n; j++) {
            primalViolation = Math.max(primalViolation, -point[j]);
            double column = 0;
            for (int i = 0; i < m; i++) column += original.matrix()[i][j] * dual[i];
            dualViolation = Math.max(dualViolation, original.cost()[j] - finite(column));
        }
        double value = dot(original.cost(), point), upper = dot(original.rhs(), dual);
        double gap = Math.abs(finite(upper - value));
        if (finite(primalViolation) > CERTIFICATE_TOLERANCE
                || finite(dualViolation) > CERTIFICATE_TOLERANCE
                || gap > CERTIFICATE_TOLERANCE)
            throw reject(Failure.NUMERICAL_FAILURE, "Original scaled LP certificate failed");
        var previous = solved.certificate().work();
        return new Result(
                point,
                dual,
                new Certificate(
                        refinedDirections ? REFINED_DIRECTION_ALGORITHM : EXACT_POWER_TWO_ALGORITHM,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(original),
                        n,
                        m,
                        value,
                        upper,
                        primalViolation,
                        dualViolation,
                        gap,
                        new Work(
                                previous.phaseOnePivots(),
                                previous.cleanupPivots(),
                                previous.phaseTwoPivots(),
                                previous.basisFactorizations(),
                                budget.used,
                                previous.blandFallback())));
    }

    static double exactPowerOfTwoScale(double value, double factor) {
        if (!(factor > 0)
                || !Double.isFinite(factor)
                || factor != Math.scalb(1.0, Math.getExponent(factor)))
            throw reject(Failure.NUMERICAL_FAILURE, "Invalid exact LP row scale");
        double scaled = finite(value * factor);
        double restored = finite(scaled / factor);
        if (Double.doubleToRawLongBits(restored) != Double.doubleToRawLongBits(value))
            throw reject(Failure.NUMERICAL_FAILURE, "LP row scaling lost an original coefficient");
        return scaled;
    }

    private static Result solveRows(
            double[][] matrix,
            double[] rhs,
            double[] cost,
            int pivots,
            long work,
            boolean resolvableCosts)
            throws Exception {
        return solveRows(matrix, rhs, cost, pivots, work, resolvableCosts, false);
    }

    private static Result solveRows(
            double[][] matrix,
            double[] rhs,
            double[] cost,
            int pivots,
            long work,
            boolean resolvableCosts,
            boolean refinedDirections)
            throws Exception {
        return solveRows(
                matrix, rhs, cost, pivots, work, resolvableCosts, refinedDirections, false);
    }

    private static Result solveRows(
            double[][] matrix,
            double[] rhs,
            double[] cost,
            int pivots,
            long work,
            boolean resolvableCosts,
            boolean refinedDirections,
            boolean refinedBasis)
            throws Exception {
        if (pivots < 0 || pivots > MAX_PIVOTS || work < 0 || work > MAX_ARITHMETIC_WORK)
            throw reject(Failure.INVALID_INPUT, "Invalid bounded LP work limits");
        dimensions(matrix, rhs, cost);
        int m = rhs.length, n = cost.length;
        var budget = new RowBudget(work);
        // Reserve copying, coefficient/metric validation, reindexing, hashing and the ORIGINAL
        // certificate's arithmetic before doing that work. Comparisons are charged separately.
        budget.charge(32L * m * n + 48L * m + 16L * n);
        var original = copy(matrix, rhs, cost);
        var metric = new double[m];
        var order = new ArrayList<Integer>(m);
        for (int i = 0; i < m; i++) {
            order.add(i);
            for (double value : original.matrix()[i]) metric[i] += Math.abs(value);
            finite(metric[i]);
        }
        order.sort(
                (a, b) -> {
                    budget.charge(8);
                    int compared = Double.compare(metric[a], metric[b]);
                    return compared == 0 ? Integer.compare(a, b) : compared;
                });
        var sorted = new double[m][];
        var bounds = new double[m];
        for (int i = 0; i < m; i++) {
            sorted[i] = original.matrix()[order.get(i)].clone();
            bounds[i] = original.rhs()[order.get(i)];
        }
        var ordered = new Problem(sorted, bounds, original.cost().clone());
        var solved =
                new Engine(
                                ordered,
                                pivots,
                                work - budget.used,
                                resolvableCosts,
                                refinedDirections,
                                refinedBasis)
                        .solve(SixMaxHistoryPhysicalConditionalRefinement.hash(ordered));
        budget.charge(solved.certificate().work().arithmeticWork());
        var point = solved.point().stream().mapToDouble(Double::doubleValue).toArray();
        var dual = new double[m];
        for (int i = 0; i < m; i++) dual[order.get(i)] = solved.dual().get(i);
        double primalViolation = 0, dualViolation = 0;
        for (int j = 0; j < n; j++) {
            primalViolation = Math.max(primalViolation, -point[j]);
            double column = 0;
            for (int i = 0; i < m; i++) column += original.matrix()[i][j] * dual[i];
            dualViolation = Math.max(dualViolation, original.cost()[j] - finite(column));
        }
        for (int i = 0; i < m; i++) {
            primalViolation =
                    Math.max(primalViolation, dot(original.matrix()[i], point) - original.rhs()[i]);
            dualViolation = Math.max(dualViolation, -dual[i]);
        }
        double value = dot(original.cost(), point), upper = dot(original.rhs(), dual);
        double gap = Math.abs(finite(upper - value));
        if (finite(primalViolation) > CERTIFICATE_TOLERANCE
                || finite(dualViolation) > CERTIFICATE_TOLERANCE
                || gap > CERTIFICATE_TOLERANCE)
            throw reject(Failure.NUMERICAL_FAILURE, "Original row-mapped LP certificate failed");
        var previous = solved.certificate().work();
        return new Result(
                point,
                dual,
                new Certificate(
                        refinedBasis
                                ? (resolvableCosts
                                        ? RESOLVABLE_REFINED_BASIS_ALGORITHM
                                        : ASCENDING_REFINED_BASIS_ALGORITHM)
                                : refinedDirections
                                        ? (resolvableCosts
                                                ? REFINED_DIRECTION_ALGORITHM
                                                : ASCENDING_REFINED_DIRECTION_ALGORITHM)
                                        : resolvableCosts
                                                ? RESOLVABLE_COST_ALGORITHM
                                                : ASCENDING_L1_ALGORITHM,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(original),
                        n,
                        m,
                        value,
                        upper,
                        primalViolation,
                        dualViolation,
                        gap,
                        new Work(
                                previous.phaseOnePivots(),
                                previous.cleanupPivots(),
                                previous.phaseTwoPivots(),
                                previous.basisFactorizations(),
                                budget.used,
                                previous.blandFallback())));
    }

    private static final class RowBudget {
        final long limit;
        long used;

        RowBudget(long limit) {
            this.limit = limit;
        }

        void charge(long amount) {
            if (amount < 0 || amount > limit - used)
                throw reject(Failure.WORK_LIMIT, "LP row-order work budget exceeded");
            used += amount;
        }
    }

    private static void dimensions(double[][] matrix, double[] rhs, double[] cost) {
        if (matrix == null
                || rhs == null
                || cost == null
                || cost.length < 1
                || cost.length > MAX_VARIABLES
                || rhs.length < 1
                || rhs.length > MAX_CONSTRAINTS
                || matrix.length != rhs.length)
            throw reject(Failure.INVALID_INPUT, "Bounded LP dimensions required");
        for (double[] row : matrix)
            if (row == null || row.length != cost.length)
                throw reject(Failure.INVALID_INPUT, "Ragged LP matrix");
    }

    private static Problem copy(double[][] matrix, double[] rhs, double[] cost) {
        if (matrix == null
                || rhs == null
                || cost == null
                || cost.length < 1
                || cost.length > MAX_VARIABLES
                || rhs.length < 1
                || rhs.length > MAX_CONSTRAINTS
                || matrix.length != rhs.length)
            throw reject(Failure.INVALID_INPUT, "Bounded LP dimensions required");
        var a = new double[rhs.length][];
        for (int i = 0; i < a.length; i++) {
            if (matrix[i] == null || matrix[i].length != cost.length)
                throw reject(Failure.INVALID_INPUT, "Ragged LP matrix");
            a[i] = matrix[i].clone();
            for (double v : a[i]) coefficient(v);
        }
        double[] b = rhs.clone(), c = cost.clone();
        for (double v : b) coefficient(v);
        for (double v : c) coefficient(v);
        return new Problem(a, b, c);
    }

    private static void coefficient(double v) {
        if (!Double.isFinite(v) || Math.abs(v) > MAX_COEFFICIENT)
            throw reject(Failure.INVALID_INPUT, "Finite bounded LP coefficients required");
    }

    private static Rejected reject(Failure reason, String detail) {
        return new Rejected(reason, detail);
    }

    private static double finite(double value) {
        if (!Double.isFinite(value))
            throw reject(Failure.NUMERICAL_FAILURE, "Nonfinite LP arithmetic");
        return value;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return finite(sum);
    }

    private static final class Engine {
        final Problem problem;
        final boolean resolvableCosts, refinedDirections, refinedBasis;
        final int n, m, total, pivotLimit;
        final long workLimit;
        final double[][] source;
        final double[] rhs;
        final int[] basis, signs;
        int phaseOne, cleanup, phaseTwo, factorizations;
        long work;
        boolean fallback;

        Engine(Problem problem, int pivotLimit, long workLimit) {
            this(problem, pivotLimit, workLimit, false);
        }

        Engine(Problem problem, int pivotLimit, long workLimit, boolean resolvableCosts) {
            this(problem, pivotLimit, workLimit, resolvableCosts, false);
        }

        Engine(
                Problem problem,
                int pivotLimit,
                long workLimit,
                boolean resolvableCosts,
                boolean refinedDirections) {
            this(problem, pivotLimit, workLimit, resolvableCosts, refinedDirections, false);
        }

        Engine(
                Problem problem,
                int pivotLimit,
                long workLimit,
                boolean resolvableCosts,
                boolean refinedDirections,
                boolean refinedBasis) {
            this.refinedBasis = refinedBasis;
            this.refinedDirections = refinedDirections;
            this.problem = problem;
            this.resolvableCosts = resolvableCosts;
            this.pivotLimit = pivotLimit;
            this.workLimit = workLimit;
            n = problem.cost().length;
            m = problem.rhs().length;
            int artificial = 0;
            for (double b : problem.rhs()) if (b < 0) artificial++;
            total = n + m + artificial;
            charge((long) m * total);
            source = new double[m][total];
            rhs = new double[m];
            basis = new int[m];
            signs = new int[m];
            int next = n + m;
            for (int i = 0; i < m; i++) {
                signs[i] = problem.rhs()[i] < 0 ? -1 : 1;
                rhs[i] = signs[i] * problem.rhs()[i];
                for (int j = 0; j < n; j++) source[i][j] = signs[i] * problem.matrix()[i][j];
                source[i][n + i] = signs[i];
                if (signs[i] == 1) basis[i] = n + i;
                else {
                    source[i][next] = 1;
                    basis[i] = next++;
                }
            }
        }

        void charge(long amount) {
            if (amount < 0 || amount > workLimit - work)
                throw reject(Failure.WORK_LIMIT, "LP arithmetic work budget exceeded");
            work += amount;
        }

        double[] costs(double[] objective) {
            var result = new double[m];
            for (int i = 0; i < m; i++) result[i] = objective[basis[i]];
            return result;
        }

        double[] column(int j) {
            var result = new double[m];
            for (int i = 0; i < m; i++) result[i] = source[i][j];
            return result;
        }

        void pivot(int row, int column, int phase) {
            if (phaseOne + cleanup + phaseTwo >= pivotLimit)
                throw reject(Failure.WORK_LIMIT, "LP pivot budget exceeded");
            if (phase == 0) phaseOne++;
            else if (phase == 1) cleanup++;
            else phaseTwo++;
            basis[row] = column;
        }

        double optimize(double[] objective, boolean excludeArtificial) {
            boolean largest = true;
            double objectiveScale = 0;
            for (double value : objective)
                objectiveScale = Math.max(objectiveScale, Math.abs(value));
            var seen = new HashSet<Integer>();
            while (true) {
                // A hash collision switches to Bland conservatively, never trusts a cycle away.
                if (!seen.add(Arrays.hashCode(basis))) {
                    largest = false;
                    fallback = true;
                }
                var lu = new Lu(this);
                var basic = refinedBasis ? lu.refinedDirection(rhs) : lu.solve(rhs);
                var dual =
                        refinedBasis
                                ? lu.refinedTranspose(costs(objective))
                                : lu.transpose(costs(objective));
                for (double value : basic)
                    if (value < -CERTIFICATE_TOLERANCE)
                        throw reject(Failure.NUMERICAL_FAILURE, "LP lost primal feasibility");
                boolean[] used = new boolean[total];
                for (int b : basis) used[b] = true;
                int end = excludeArtificial ? n + m : total;
                charge((long) m * end);
                boolean refined = resolvableCosts && excludeArtificial;
                if (refined) charge(4L * end + 4L * m);
                int entering = -1;
                double best = EPS;
                boolean unresolvedPositive = false;
                for (int j = 0; j < end; j++) {
                    if (used[j]) continue;
                    double reduced = objective[j];
                    double magnitude = Math.abs(objective[j]);
                    for (int i = 0; i < m; i++) {
                        double product = dual[i] * source[i][j];
                        reduced -= product;
                        magnitude += Math.abs(product);
                    }
                    finite(reduced);
                    // A small but resolvably positive cost is neither a stable pivot nor an
                    // optimum. In particular, tiny unbounded objectives must not return x=0.
                    // Include the basis objective's scale: a near-zero solved dual component
                    // still inherits rounding from the larger basis costs used to obtain it.
                    double roundoff =
                            finite(8.0 * m * Math.ulp(Math.max(objectiveScale, finite(magnitude))));
                    if (reduced > roundoff && reduced <= EPS) unresolvedPositive = true;
                    if (reduced > (refined ? roundoff : EPS)
                            && (entering == -1 || largest && reduced > best + PIVOT_EPS)) {
                        entering = j;
                        best = reduced;
                        if (!largest) break;
                    }
                }
                if (entering == -1) {
                    if (unresolvedPositive)
                        throw reject(
                                Failure.NUMERICAL_FAILURE, "Unresolved positive LP reduced cost");
                    return dot(costs(objective), basic);
                }
                var direction =
                        refinedDirections && excludeArtificial
                                ? lu.refinedDirection(column(entering))
                                : lu.solve(column(entering));
                int leaving = -1;
                double ratio = Double.POSITIVE_INFINITY;
                for (int i = 0; i < m; i++)
                    if (direction[i] > EPS) {
                        double candidate = finite(Math.max(0, basic[i]) / direction[i]);
                        // On the explicit path only exact ratio ties use Bland's index. A near
                        // tie must not choose a larger step and repeatedly revisit a basis.
                        double tolerance =
                                (refined || refinedBasis && excludeArtificial)
                                        ? 0
                                        : PIVOT_EPS * Math.max(1, Math.abs(candidate));
                        if (leaving == -1
                                || candidate < ratio - tolerance
                                || Math.abs(candidate - ratio) <= tolerance
                                        && basis[i] < basis[leaving]) {
                            leaving = i;
                            ratio = candidate;
                        }
                    }
                if (leaving == -1)
                    throw reject(
                            Failure.NO_CERTIFIED_OPTIMUM,
                            "LP has no bounded certified step (unbounded or numerical failure)");
                pivot(leaving, entering, excludeArtificial ? 2 : 0);
            }
        }

        Result solve(String hash) {
            var phaseOneCost = new double[total];
            for (int j = n + m; j < total; j++) phaseOneCost[j] = -1;
            if (optimize(phaseOneCost, false) < -CERTIFICATE_TOLERANCE)
                throw reject(
                        Failure.NO_CERTIFIED_OPTIMUM, "LP has no certified feasible phase-I point");
            for (int i = 0; i < m; i++)
                if (basis[i] >= n + m) {
                    var lu = new Lu(this);
                    if (Math.abs(lu.solve(rhs)[i]) > CERTIFICATE_TOLERANCE)
                        throw reject(Failure.NUMERICAL_FAILURE, "Nonzero artificial variable");
                    boolean[] used = new boolean[total];
                    for (int b : basis) used[b] = true;
                    int replacement = -1;
                    for (int j = 0; j < n + m; j++)
                        if (!used[j] && Math.abs(lu.solve(column(j))[i]) > EPS) {
                            replacement = j;
                            break;
                        }
                    // Signed slack columns span every row. Never discard an original constraint.
                    if (replacement == -1)
                        throw reject(Failure.NUMERICAL_FAILURE, "Unsafe artificial-basis cleanup");
                    pivot(i, replacement, 1);
                }
            var phaseTwoCost = new double[total];
            System.arraycopy(problem.cost(), 0, phaseTwoCost, 0, n);
            optimize(phaseTwoCost, true);
            var lu = new Lu(this);
            var basic = refinedBasis ? lu.refinedDirection(rhs) : lu.solve(rhs);
            var transformedDual = lu.transpose(costs(phaseTwoCost));
            var point = new double[n];
            var dual = new double[m];
            for (int i = 0; i < m; i++) {
                if (basis[i] < n) point[basis[i]] = Math.max(0, basic[i]);
                dual[i] = signs[i] * transformedDual[i];
            }
            charge((long) m * n * 2 + m + n);
            double primalViolation = 0, dualViolation = 0;
            for (int j = 0; j < n; j++) primalViolation = Math.max(primalViolation, -point[j]);
            for (int i = 0; i < m; i++) {
                primalViolation =
                        Math.max(
                                primalViolation,
                                dot(problem.matrix()[i], point) - problem.rhs()[i]);
                dualViolation = Math.max(dualViolation, -dual[i]);
            }
            for (int j = 0; j < n; j++) {
                double value = 0;
                for (int i = 0; i < m; i++) value += dual[i] * problem.matrix()[i][j];
                dualViolation = Math.max(dualViolation, problem.cost()[j] - finite(value));
            }
            double value = dot(problem.cost(), point), upper = dot(problem.rhs(), dual);
            double gap = Math.abs(finite(upper - value));
            if (primalViolation > CERTIFICATE_TOLERANCE
                    || dualViolation > CERTIFICATE_TOLERANCE
                    || gap > CERTIFICATE_TOLERANCE)
                throw reject(
                        Failure.NUMERICAL_FAILURE, "Original LP primal/dual certificate failed");
            return new Result(
                    point,
                    dual,
                    new Certificate(
                            ALGORITHM,
                            hash,
                            n,
                            m,
                            value,
                            upper,
                            primalViolation,
                            dualViolation,
                            gap,
                            new Work(phaseOne, cleanup, phaseTwo, factorizations, work, fallback)));
        }
    }

    /** Partial-pivot LU of the ORIGINAL basis, rebuilt rather than accumulating roundoff. */
    private static final class Lu {
        final Engine engine;
        final double[][] rows;
        final int[] permutation;

        Lu(Engine engine) {
            this.engine = engine;
            int m = engine.m;
            engine.charge((long) m * m * m + (long) m * m);
            engine.factorizations++;
            rows = new double[m][m];
            permutation = new int[m];
            for (int i = 0; i < m; i++) {
                permutation[i] = i;
                for (int j = 0; j < m; j++) rows[i][j] = engine.source[i][engine.basis[j]];
            }
            for (int k = 0; k < m; k++) {
                int selected = k;
                for (int i = k + 1; i < m; i++)
                    if (Math.abs(rows[i][k]) > Math.abs(rows[selected][k])) selected = i;
                if (Math.abs(finite(rows[selected][k])) < PIVOT_EPS)
                    throw reject(Failure.NUMERICAL_FAILURE, "Singular LP basis");
                if (selected != k) {
                    var temp = rows[k];
                    rows[k] = rows[selected];
                    rows[selected] = temp;
                    int p = permutation[k];
                    permutation[k] = permutation[selected];
                    permutation[selected] = p;
                }
                double[] pivot = rows[k];
                for (int i = k + 1; i < m; i++) {
                    double[] row = rows[i];
                    row[k] = finite(row[k] / pivot[k]);
                    double factor = row[k];
                    for (int j = k + 1; j < m; j++) row[j] -= factor * pivot[j];
                }
            }
        }

        /**
         * Fixed original-basis correction passes; every pass is charged before its arithmetic.
         * Final primal/dual certificates still use the copied ORIGINAL inequalities.
         */
        double[] refinedDirection(double[] rhs) {
            var x = solve(rhs);
            int m = rows.length;
            for (int pass = 0; pass < DIRECTION_REFINEMENT_PASSES; pass++) {
                engine.charge(20L * m * m);
                var residual = new double[m];
                for (int i = 0; i < m; i++) {
                    double sum = 0, error = 0;
                    for (int j = 0; j < m; j++) {
                        double a = engine.source[i][engine.basis[j]];
                        double p = a * x[j];
                        double next = sum + p;
                        error +=
                                Math.fma(a, x[j], -p)
                                        + (Math.abs(sum) >= Math.abs(p)
                                                ? (sum - next) + p
                                                : (p - next) + sum);
                        sum = next;
                    }
                    residual[i] = finite((rhs[i] - sum) - error);
                }
                var correction = solve(residual);
                for (int j = 0; j < m; j++) x[j] = finite(x[j] + correction[j]);
            }
            return x;
        }

        double[] solve(double[] rhs) {
            int m = rows.length;
            engine.charge((long) m * m * 2);
            var x = new double[m];
            for (int i = 0; i < m; i++) {
                double value = rhs[permutation[i]];
                for (int j = 0; j < i; j++) value -= rows[i][j] * x[j];
                x[i] = finite(value);
            }
            for (int i = m - 1; i >= 0; i--) {
                double value = x[i];
                for (int j = i + 1; j < m; j++) value -= rows[i][j] * x[j];
                x[i] = finite(value / rows[i][i]);
            }
            return x;
        }

        double[] refinedTranspose(double[] rhs) {
            var x = transpose(rhs);
            int m = rows.length;
            for (int pass = 0; pass < DIRECTION_REFINEMENT_PASSES; pass++) {
                engine.charge(20L * m * m);
                var residual = new double[m];
                for (int i = 0; i < m; i++) {
                    double sum = 0, error = 0;
                    for (int j = 0; j < m; j++) {
                        double a = engine.source[j][engine.basis[i]], p = a * x[j], next = sum + p;
                        error +=
                                Math.fma(a, x[j], -p)
                                        + (Math.abs(sum) >= Math.abs(p)
                                                ? (sum - next) + p
                                                : (p - next) + sum);
                        sum = next;
                    }
                    residual[i] = finite((rhs[i] - sum) - error);
                }
                var correction = transpose(residual);
                for (int i = 0; i < m; i++) x[i] = finite(x[i] + correction[i]);
            }
            return x;
        }

        double[] transpose(double[] rhs) {
            int m = rows.length;
            engine.charge((long) m * m * 2);
            var x = new double[m];
            for (int i = 0; i < m; i++) {
                double value = rhs[i];
                for (int j = 0; j < i; j++) value -= rows[j][i] * x[j];
                x[i] = finite(value / rows[i][i]);
            }
            for (int i = m - 1; i >= 0; i--) {
                double value = x[i];
                for (int j = i + 1; j < m; j++) value -= rows[j][i] * x[j];
                x[i] = finite(value);
            }
            var result = new double[m];
            for (int i = 0; i < m; i++) result[permutation[i]] = x[i];
            return result;
        }
    }
}
