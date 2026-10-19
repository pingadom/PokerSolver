package com.pokerlab.solver;

import java.util.*;

/**
 * Bounded affine sequence form for exactly two active constant-sum decision makers. Eliminates
 * dependent own-action masses algebraically, then certifies both original LPs and independent best
 * responses over the complete immutable game snapshot. This numerical certificate does not grant
 * multiplayer equilibrium or trainer admission.
 */
public final class FiniteTwoPlayerAffineSequenceForm {
    /** Deterministic compiler loop-size budget, separate from either LP's work budget. */
    public static final long MAX_REDUCTION_WORK = 8_000_000;

    public enum Failure {
        INVALID_INPUT,
        WORK_LIMIT,
        NUMERICAL_FAILURE
    }

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

    static Rejected reject(Failure reason, String detail) {
        return new Rejected(reason, detail);
    }

    public record ReductionWork(long chargedUnits, long limit) {}

    public record ProjectionAudit(
            String originalFlowHash,
            String projectionHash,
            List<Integer> independentSequences,
            List<Integer> offset,
            List<List<Integer>> transform,
            List<List<Integer>> inequalities,
            List<Integer> rhs) {
        public ProjectionAudit {
            independentSequences = List.copyOf(independentSequences);
            offset = List.copyOf(offset);
            transform = transform.stream().map(List::copyOf).toList();
            inequalities = inequalities.stream().map(List::copyOf).toList();
            rhs = List.copyOf(rhs);
        }
    }

    public record ProjectedPayoffAudit(
            String originalPayoffHash,
            String reducedPayoffHash,
            double constant,
            List<Double> firstCost,
            List<Double> secondCost,
            List<List<Double>> matrix) {
        public ProjectedPayoffAudit {
            firstCost = List.copyOf(firstCost);
            secondCost = List.copyOf(secondCost);
            matrix = matrix.stream().map(List::copyOf).toList();
        }
    }

    public static final String ALGORITHM =
            "BOUNDED_PERFECT_RECALL_AFFINE_SEQUENCE_FORM_OWNED_LP/v1";
    public static final String ASCENDING_ROWS_ALGORITHM =
            "BOUNDED_PERFECT_RECALL_AFFINE_SEQUENCE_FORM_ASCENDING_L1_ORIGINAL_CERTIFICATES/v1";

    public static final String ASCENDING_REFINED_DIRECTIONS_ALGORITHM =
            "BOUNDED_PERFECT_RECALL_AFFINE_SEQUENCE_FORM_ASCENDING_L1_REFINED_DIRECTIONS_ORIGINAL_CERTIFICATES/v2";

    public record Audit(
            String algorithm,
            String snapshotHash,
            String originalReductionHash,
            String affineReductionHash,
            int treeNodes,
            int firstActor,
            int secondActor,
            double constantSum,
            List<FiniteTwoPlayerSequenceForm.InformationSet> informationSets,
            FiniteTwoPlayerSequenceForm.FlowAudit firstFlow,
            FiniteTwoPlayerSequenceForm.FlowAudit secondFlow,
            ProjectionAudit firstProjection,
            ProjectionAudit secondProjection,
            ProjectedPayoffAudit projectedPayoff,
            ReductionWork reductionWork,
            BoundedLinearProgram.Certificate firstLp,
            BoundedLinearProgram.Certificate secondLp,
            double lowerValue,
            double upperValue,
            String behavioralPolicyHash,
            MultiPlayerInformationSetBestResponse.Report behavioralQuality) {
        public Audit {
            informationSets = List.copyOf(informationSets);
        }
    }

    /** Serializable evidence only; constructing a witness does not grant a solved handle. */
    public record LpWitness(
            List<Double> point, List<Double> dual, BoundedLinearProgram.Certificate certificate) {
        public LpWitness {
            point = List.copyOf(point);
            dual = List.copyOf(dual);
            Objects.requireNonNull(certificate);
        }
    }

    /** Audit JSON or caller-supplied projections cannot manufacture a solved strategy handle. */
    public static final class Result {
        private final Map<String, Map<String, Double>> strategy;
        private final Audit audit;
        private final LpWitness firstLpWitness, secondLpWitness;

        private Result(
                CfrSolution policy,
                Audit audit,
                BoundedLinearProgram.Result firstLp,
                BoundedLinearProgram.Result secondLp) {
            strategy = policy.strategy();
            this.audit = audit;
            firstLpWitness = new LpWitness(firstLp.point(), firstLp.dual(), firstLp.certificate());
            secondLpWitness =
                    new LpWitness(secondLp.point(), secondLp.dual(), secondLp.certificate());
        }

        public LpWitness firstLpWitness() {
            return firstLpWitness;
        }

        public LpWitness secondLpWitness() {
            return secondLpWitness;
        }

        public Map<String, Map<String, Double>> strategy() {
            return strategy;
        }

        public Audit audit() {
            return audit;
        }
    }

    private FiniteTwoPlayerAffineSequenceForm() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game) throws Exception {
        return solve(game, SequenceFormAffineProjection.MAX_REDUCTION_WORK);
    }

    static <S> Result solve(MultiPlayerCfrGame<S> game, long workLimit) throws Exception {
        return solve(game, workLimit, false);
    }

    /** Explicit alternative row representation; never a fallback after a failed solve. */
    public static <S> Result solveAscendingL1Rows(MultiPlayerCfrGame<S> game) throws Exception {
        return solve(game, SequenceFormAffineProjection.MAX_REDUCTION_WORK, true);
    }

    /** Separately selected parent mode; preserves original modes, limits and rejection behavior. */
    public static <S> Result solveAscendingRefinedDirections(MultiPlayerCfrGame<S> game)
            throws Exception {
        return solveAscendingRefinedDirections(
                game, SequenceFormAffineProjection.MAX_REDUCTION_WORK);
    }

    static <S> Result solveAscendingRefinedDirections(MultiPlayerCfrGame<S> game, long workLimit)
            throws Exception {
        return solve(game, workLimit, true, true);
    }

    private static <S> Result solve(
            MultiPlayerCfrGame<S> game, long workLimit, boolean ascendingRows) throws Exception {
        return solve(game, workLimit, ascendingRows, false);
    }

    private static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            long workLimit,
            boolean ascendingRows,
            boolean refinedDirections)
            throws Exception {
        var budget = new SequenceFormAffineProjection.Budget(workLimit);
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        var f = checked.firstFlow();
        var s = checked.secondFlow();
        var fp = SequenceFormAffineProjection.build(f.sequences(), f.conservation(), budget);
        var sp = SequenceFormAffineProjection.build(s.sequences(), s.conservation(), budget);
        var payoff = SequenceFormAffineProjection.reduce(checked.payoff(), fp, sp, budget);
        var row =
                SequenceFormAffineProjection.optimize(
                        payoff, fp, sp, true, budget, ascendingRows, refinedDirections);
        var column =
                SequenceFormAffineProjection.optimize(
                        payoff, sp, fp, false, budget, ascendingRows, refinedDirections);
        double lower = payoff.constant() + row.certificate().primalValue(),
                upper = payoff.constant() - column.certificate().primalValue();
        final double tolerance = BoundedLinearProgram.CERTIFICATE_TOLERANCE;
        if (!Double.isFinite(lower)
                || !Double.isFinite(upper)
                || Math.abs(lower - upper) > tolerance)
            throw reject(Failure.NUMERICAL_FAILURE, "Affine LP bounds disagree");
        var strategy = new LinkedHashMap<String, Map<String, Double>>();
        var firstFlow =
                FiniteTwoPlayerSequenceForm.behavior(
                        f, SequenceFormAffineProjection.realization(fp, row, budget), strategy);
        var secondFlow =
                FiniteTwoPlayerSequenceForm.behavior(
                        s, SequenceFormAffineProjection.realization(sp, column, budget), strategy);
        // One is a strategy-container sentinel, not a training iteration or resumable checkpoint.
        var policy = new CfrSolution(1, strategy);
        var quality = MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy);
        int first = checked.firstActor(), second = checked.secondActor();
        double sum = checked.constantSum();
        if (!Double.isFinite(quality.nashConvBb())
                || quality.nashConvBb() > tolerance
                || Math.abs(quality.profileUtilitiesBb().get(first) - lower) > tolerance
                || Math.abs(quality.profileUtilitiesBb().get(second) - (sum - upper)) > tolerance
                || Math.abs(quality.bestResponseUtilitiesBb().get(first) - upper) > tolerance
                || Math.abs(sum - quality.bestResponseUtilitiesBb().get(second) - lower)
                        > tolerance)
            throw reject(
                    Failure.NUMERICAL_FAILURE,
                    "Independent original snapshot best-response certificate failed");
        String originalHash =
                SixMaxHistoryPhysicalConditionalRefinement.hash(
                        Map.of(
                                "firstActor",
                                first,
                                "secondActor",
                                second,
                                "informationSets",
                                checked.infos(),
                                "firstSequences",
                                f.sequences(),
                                "secondSequences",
                                s.sequences(),
                                "firstConservation",
                                f.conservation(),
                                "secondConservation",
                                s.conservation(),
                                "payoff",
                                checked.payoff()));
        String affineHash =
                SixMaxHistoryPhysicalConditionalRefinement.hash(
                        Map.of(
                                "originalReductionHash",
                                originalHash,
                                "firstProjection",
                                fp.audit(),
                                "secondProjection",
                                sp.audit(),
                                "projectedPayoff",
                                payoff,
                                "reductionWork",
                                budget.audit()));
        var audit =
                new Audit(
                        refinedDirections
                                ? ASCENDING_REFINED_DIRECTIONS_ALGORITHM
                                : ascendingRows ? ASCENDING_ROWS_ALGORITHM : ALGORITHM,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(checked.snapshot()),
                        originalHash,
                        affineHash,
                        checked.treeNodes(),
                        first,
                        second,
                        sum,
                        checked.infos(),
                        firstFlow,
                        secondFlow,
                        fp.audit(),
                        sp.audit(),
                        payoff,
                        budget.audit(),
                        row.certificate(),
                        column.certificate(),
                        lower,
                        upper,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        quality);
        return new Result(policy, audit, row, column);
    }
}
