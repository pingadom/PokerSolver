package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerBehaviorFloor.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Explicit epsilon-dependent excess-mass representation; preserves the earlier floor solver. */
public final class FiniteTwoPlayerSlackFloor {
    public static final String ALGORITHM = "BOUNDED_BEHAVIOR_FLOOR_SLACK_SEQUENCE_FORM_OWNED_LP/v1";

    public record ProjectionAudit(
            String originalFlowHash,
            String projectionHash,
            List<Integer> freeSequences,
            List<Integer> conservationOrder,
            List<Double> offset,
            List<List<Double>> transform,
            List<List<Double>> inequalities,
            List<Double> rhs,
            double maximumConservationCoefficientResidual) {
        public ProjectionAudit {
            freeSequences = List.copyOf(freeSequences);
            conservationOrder = List.copyOf(conservationOrder);
            offset = List.copyOf(offset);
            transform = transform.stream().map(List::copyOf).toList();
            inequalities = inequalities.stream().map(List::copyOf).toList();
            rhs = List.copyOf(rhs);
        }
    }

    public record BehaviorAudit(
            FlowAudit original,
            double minimumBehaviorProbability,
            double maximumRelativeFloorViolation,
            double maximumRelativeConservationResidual,
            double maximumAffineReconstructionError) {}

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
            ProjectionAudit firstProjection,
            ProjectionAudit secondProjection,
            BehaviorAudit firstBehavior,
            BehaviorAudit secondBehavior,
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

    private FiniteTwoPlayerSlackFloor() {}

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
        var first = SequenceFormFloorSlackProjection.build(checked.firstFlow(), floor, budget);
        var second = SequenceFormFloorSlackProjection.build(checked.secondFlow(), floor, budget);
        var payoff =
                SequenceFormFloorSlackProjection.reduce(checked.payoff(), first, second, budget);
        var a = SequenceFormFloorSlackProjection.optimize(payoff, first, second, true, budget);
        var b = SequenceFormFloorSlackProjection.optimize(payoff, second, first, false, budget);
        double lower = payoff.constant() + a.certificate().primalValue(),
                upper = payoff.constant() - b.certificate().primalValue();
        double tolerance = BoundedLinearProgram.CERTIFICATE_TOLERANCE;
        if (!Double.isFinite(lower)
                || !Double.isFinite(upper)
                || Math.abs(lower - upper) > tolerance)
            throw reject(Failure.NUMERICAL_FAILURE, "Slack floor LP bounds disagree");
        var strategy = new LinkedHashMap<String, Map<String, Double>>();
        var f = SequenceFormFloorSlackProjection.behavior(first, a.point(), strategy, budget);
        var s = SequenceFormFloorSlackProjection.behavior(second, b.point(), strategy, budget);
        var policy = new CfrSolution(1, strategy);
        var quality = SequenceFormFloorResponse.assess(checked, policy, floor);
        var original = MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy);
        int fa = checked.firstActor(), sa = checked.secondActor();
        double sum = checked.constantSum();
        if (quality.nashConvBb() > tolerance
                || Math.abs(quality.profileUtilitiesBb().get(fa) - lower) > tolerance
                || Math.abs(quality.profileUtilitiesBb().get(sa) - (sum - upper)) > tolerance
                || Math.abs(quality.bestResponseUtilitiesBb().get(fa) - upper) > tolerance
                || Math.abs(sum - quality.bestResponseUtilitiesBb().get(sa) - lower) > tolerance)
            throw reject(Failure.NUMERICAL_FAILURE, "Independent slack floor response failed");
        return new Result(
                policy,
                new Audit(
                        ALGORITHM,
                        floor,
                        SixMaxHeadsUpPreflopGame.hash(checked.snapshot()),
                        SixMaxHeadsUpPreflopGame.hash(
                                Map.of(
                                        "floor",
                                        floor,
                                        "first",
                                        first.audit(),
                                        "second",
                                        second.audit(),
                                        "payoff",
                                        payoff)),
                        checked.treeNodes(),
                        fa,
                        sa,
                        sum,
                        checked.infos(),
                        first.audit(),
                        second.audit(),
                        f,
                        s,
                        payoff,
                        a.certificate(),
                        b.certificate(),
                        new CompilationWork(budget.audit().chargedUnits(), budget.audit().limit()),
                        lower,
                        upper,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        quality,
                        original));
    }
}
