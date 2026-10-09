package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Explicit behavioral-floor constrained maxmin; original-game quality is measured separately. */
public final class FiniteTwoPlayerBehaviorFloor {
    public static final String ALGORITHM =
            "BOUNDED_BEHAVIOR_FLOOR_AFFINE_SEQUENCE_FORM_OWNED_LP/v1";
    public static final double MIN_FLOOR = 1e-6, MAX_FLOOR = .01;
    public static final long MAX_COMPILATION_WORK = 8_000_000;
    private static final double TOL = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public enum Failure {
        INVALID_INPUT,
        WORK_LIMIT,
        NUMERICAL_FAILURE
    }

    public static final class Rejected extends IllegalArgumentException {
        private final Failure reason;

        private Rejected(Failure reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Failure reason() {
            return reason;
        }
    }

    static Rejected reject(Failure reason, String message) {
        return new Rejected(reason, message);
    }

    static void requireFloor(double floor) {
        if (!Double.isFinite(floor) || floor < MIN_FLOOR || floor > MAX_FLOOR)
            throw reject(Failure.INVALID_INPUT, "Behavior floor must be in [1e-6, 0.01]");
    }

    public record CompilationWork(long chargedUnits, long limit) {}

    public record FloorConstraint(int parentSequence, int childSequence) {}

    public record FloorFlow(
            int actor,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit projection,
            List<FloorConstraint> floorConstraints,
            List<List<Double>> inequalities,
            List<Double> rhs,
            double minimumBehaviorProbability,
            double maximumRelativeFloorViolation,
            double maximumRelativeConservationResidual) {
        public FloorFlow {
            floorConstraints = List.copyOf(floorConstraints);
            inequalities = inequalities.stream().map(List::copyOf).toList();
            rhs = List.copyOf(rhs);
        }
    }

    public record ConstrainedQuality(
            List<Double> profileUtilitiesBb,
            List<Double> bestResponseUtilitiesBb,
            List<Double> deviationGainsBb,
            double nashConvBb,
            List<Map<String, String>> maximizingIntents) {
        public ConstrainedQuality {
            profileUtilitiesBb = List.copyOf(profileUtilitiesBb);
            bestResponseUtilitiesBb = List.copyOf(bestResponseUtilitiesBb);
            deviationGainsBb = List.copyOf(deviationGainsBb);
            maximizingIntents = maximizingIntents.stream().map(Map::copyOf).toList();
        }
    }

    public record Audit(
            String algorithm,
            double minimumActionProbability,
            String snapshotHash,
            String reductionHash,
            int treeNodes,
            int firstActor,
            int secondActor,
            double constantSum,
            List<InformationSet> informationSets,
            FlowAudit firstFlow,
            FlowAudit secondFlow,
            FloorFlow firstFloorFlow,
            FloorFlow secondFloorFlow,
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit projectedPayoff,
            BoundedLinearProgram.Certificate firstLp,
            BoundedLinearProgram.Certificate secondLp,
            CompilationWork compilationWork,
            double constrainedLowerValue,
            double constrainedUpperValue,
            String behavioralPolicyHash,
            ConstrainedQuality constrainedQuality,
            MultiPlayerInformationSetBestResponse.Report originalGameQuality) {
        public Audit {
            informationSets = List.copyOf(informationSets);
        }
    }

    public static final class Result {
        private final Map<String, Map<String, Double>> strategy;
        private final Audit audit;

        private Result(CfrSolution policy, Audit audit) {
            strategy = policy.strategy();
            this.audit = audit;
        }

        public Map<String, Map<String, Double>> strategy() {
            return strategy;
        }

        public Audit audit() {
            return audit;
        }
    }

    private record System(
            Flow original,
            SequenceFormAffineProjection.Projection projection,
            double[][] matrix,
            double[] rhs,
            List<FloorConstraint> constraints) {}

    private record Behavior(FlowAudit original, FloorFlow floor) {}

    private FiniteTwoPlayerBehaviorFloor() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, double floor) throws Exception {
        return solve(game, floor, MAX_COMPILATION_WORK);
    }

    static <S> Result solve(MultiPlayerCfrGame<S> game, double floor, long limit) throws Exception {
        requireFloor(floor);
        try {
            return solveChecked(
                    FiniteTwoPlayerSequenceForm.checked(game),
                    floor,
                    new SequenceFormAffineProjection.Budget(limit));
        } catch (FiniteTwoPlayerAffineSequenceForm.Rejected failure) {
            throw reject(Failure.valueOf(failure.reason().name()), failure.getMessage());
        }
    }

    private static Result solveChecked(
            Checked checked, double floor, SequenceFormAffineProjection.Budget budget)
            throws Exception {
        var fp =
                SequenceFormAffineProjection.build(
                        checked.firstFlow().sequences(),
                        checked.firstFlow().conservation(),
                        budget);
        var sp =
                SequenceFormAffineProjection.build(
                        checked.secondFlow().sequences(),
                        checked.secondFlow().conservation(),
                        budget);
        var first = constraints(checked.firstFlow(), fp, floor, budget);
        var second = constraints(checked.secondFlow(), sp, floor, budget);
        var payoff = SequenceFormAffineProjection.reduce(checked.payoff(), fp, sp, budget);
        var a = optimize(payoff, first, second, true, budget);
        var b = optimize(payoff, second, first, false, budget);
        double lower = payoff.constant() + a.certificate().primalValue(),
                upper = payoff.constant() - b.certificate().primalValue();
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || Math.abs(lower - upper) > TOL)
            throw reject(Failure.NUMERICAL_FAILURE, "Constrained LP bounds disagree");
        var strategy = new LinkedHashMap<String, Map<String, Double>>();
        var f = behavior(first, a.point(), floor, strategy, budget);
        var s = behavior(second, b.point(), floor, strategy, budget);
        var policy = new CfrSolution(1, strategy);
        var constrained = SequenceFormFloorResponse.assess(checked, policy, floor);
        var original = MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy);
        int fa = checked.firstActor(), sa = checked.secondActor();
        double sum = checked.constantSum();
        if (constrained.nashConvBb() > TOL
                || Math.abs(constrained.profileUtilitiesBb().get(fa) - lower) > TOL
                || Math.abs(constrained.profileUtilitiesBb().get(sa) - (sum - upper)) > TOL
                || Math.abs(constrained.bestResponseUtilitiesBb().get(fa) - upper) > TOL
                || Math.abs(sum - constrained.bestResponseUtilitiesBb().get(sa) - lower) > TOL)
            throw reject(
                    Failure.NUMERICAL_FAILURE, "Independent constrained snapshot response failed");
        var audit =
                new Audit(
                        ALGORITHM,
                        floor,
                        SixMaxHeadsUpPreflopGame.hash(checked.snapshot()),
                        SixMaxHeadsUpPreflopGame.hash(
                                Map.of(
                                        "floor",
                                        floor,
                                        "firstProjection",
                                        fp.audit(),
                                        "secondProjection",
                                        sp.audit(),
                                        "firstInequalities",
                                        first.matrix(),
                                        "firstRhs",
                                        first.rhs(),
                                        "secondInequalities",
                                        second.matrix(),
                                        "secondRhs",
                                        second.rhs(),
                                        "payoff",
                                        payoff)),
                        checked.treeNodes(),
                        fa,
                        sa,
                        sum,
                        checked.infos(),
                        f.original(),
                        s.original(),
                        f.floor(),
                        s.floor(),
                        payoff,
                        a.certificate(),
                        b.certificate(),
                        new CompilationWork(budget.audit().chargedUnits(), budget.audit().limit()),
                        lower,
                        upper,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        constrained,
                        original);
        return new Result(policy, audit);
    }

    private static System constraints(
            Flow flow,
            SequenceFormAffineProjection.Projection projection,
            double floor,
            SequenceFormAffineProjection.Budget budget) {
        int rows = flow.sequences().size() - 1, columns = projection.variables();
        budget.charge((long) rows * (columns + 1));
        var matrix = new double[rows][columns];
        var rhs = new double[rows];
        var constraints = new ArrayList<FloorConstraint>();
        int row = 0;
        var p = projection.audit();
        // Every child >= epsilon * parent. With root=1 and the proved acyclic original
        // conservation graph, these inequalities imply positivity of ALL original masses.
        for (var conservation : flow.conservation())
            for (int child : conservation.childSequences()) {
                int parent = conservation.parentSequence();
                for (int j = 0; j < columns; j++)
                    matrix[row][j] =
                            floor * p.transform().get(parent).get(j)
                                    - p.transform().get(child).get(j);
                rhs[row] = p.offset().get(child) - floor * p.offset().get(parent);
                constraints.add(new FloorConstraint(parent, child));
                row++;
            }
        if (row != rows) throw reject(Failure.INVALID_INPUT, "Incomplete floor constraints");
        return new System(flow, projection, matrix, rhs, List.copyOf(constraints));
    }

    private static BoundedLinearProgram.Result optimize(
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit payoff,
            System own,
            System other,
            boolean row,
            SequenceFormAffineProjection.Budget budget)
            throws Exception {
        int primal = own.projection().variables(),
                dual = other.rhs().length,
                inequalities = other.projection().variables();
        int variables = primal + dual, rows = inequalities + own.rhs().length;
        if (variables > BoundedLinearProgram.MAX_VARIABLES
                || rows > BoundedLinearProgram.MAX_CONSTRAINTS)
            throw reject(
                    Failure.INVALID_INPUT,
                    "Constrained affine form exceeds unchanged LP dimensions");
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
            java.lang.System.arraycopy(own.matrix()[i], 0, matrix[inequalities + i], 0, primal);
            rhs[inequalities + i] = own.rhs()[i];
        }
        return BoundedLinearProgram.solve(matrix, rhs, cost);
    }

    private static Behavior behavior(
            System system,
            List<Double> point,
            double floor,
            Map<String, Map<String, Double>> strategy,
            SequenceFormAffineProjection.Budget budget) {
        var flow = system.original();
        var projection = system.projection();
        budget.charge((long) flow.sequences().size() * (projection.variables() + 1));
        var mass =
                projection.reconstruct(
                        point.subList(0, projection.variables()).stream()
                                .mapToDouble(Double::doubleValue)
                                .toArray());
        var all = Arrays.stream(mass).boxed().toList();
        double min = 1, relativeFloor = 0, relativeFlow = 0;
        for (double v : all)
            if (!Double.isFinite(v) || v < 0)
                throw reject(Failure.NUMERICAL_FAILURE, "Invalid reconstructed floor realization");
        for (int i = 0; i < flow.infos().size(); i++) {
            var info = flow.infos().get(i);
            var c = flow.conservation().get(i);
            double parent = all.get(c.parentSequence()), total = 0;
            if (parent < Double.MIN_NORMAL)
                throw reject(
                        Failure.NUMERICAL_FAILURE,
                        "Positive floor cannot use zero-own-reach fallback");
            var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < c.childSequences().size(); j++) {
                double probability = all.get(c.childSequences().get(j)) / parent;
                total += probability;
                row.put(info.actions().get(j), probability);
            }
            relativeFlow = Math.max(relativeFlow, Math.abs(total - 1));
            if (!Double.isFinite(total) || Math.abs(total - 1) > TOL)
                throw reject(Failure.NUMERICAL_FAILURE, "Relative behavioral conservation failed");
            for (var entry : row.entrySet()) {
                double p = entry.getValue() / total;
                if (!Double.isFinite(p) || p > 1 + TOL)
                    throw reject(Failure.NUMERICAL_FAILURE, "Invalid floored behavior");
                min = Math.min(min, p);
                relativeFloor = Math.max(relativeFloor, Math.max(0, (floor - p) / floor));
                entry.setValue(p);
            }
            strategy.put(info.key(), Map.copyOf(row));
        }
        if (relativeFloor > 1e-6)
            throw reject(Failure.NUMERICAL_FAILURE, "Relative behavior floor certificate failed");
        double residual = Math.abs(all.getFirst() - 1);
        for (var c : flow.conservation()) {
            double v = -all.get(c.parentSequence());
            for (int child : c.childSequences()) v += all.get(child);
            residual = Math.max(residual, Math.abs(v));
        }
        if (residual > TOL)
            throw reject(Failure.NUMERICAL_FAILURE, "Original realization certificate failed");
        return new Behavior(
                new FlowAudit(flow.actor(), flow.sequences(), flow.conservation(), all, residual),
                new FloorFlow(
                        flow.actor(),
                        projection.audit(),
                        system.constraints(),
                        Arrays.stream(system.matrix())
                                .map(r -> Arrays.stream(r).boxed().toList())
                                .toList(),
                        Arrays.stream(system.rhs()).boxed().toList(),
                        min,
                        relativeFloor,
                        relativeFlow));
    }
}
