package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerConditionalActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Original full-face question reach, without conditioning, posterior division or action EV. */
public final class FiniteTwoPlayerReachBounds {
    public static final String ALGORITHM = "FULL_SECURITY_FACE_QUESTION_REACH_BOUNDS_OWNED_LP/v1";
    private static final double TOLERANCE = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public enum Status {
        CERTIFIED_ALL_BELOW_REQUIRED_FLOOR,
        CERTIFIED_ALL_AT_OR_ABOVE_REQUIRED_FLOOR,
        VERIFIED_WITNESSES_ON_BOTH_SIDES,
        UNRESOLVED_AT_CERTIFICATE_TOLERANCE
    }

    public record Audit(
            String algorithm,
            boolean trainerAdmission,
            Status status,
            FiniteTwoPlayerConditionalActionIntervals.Question question,
            List<OwnAction> fixedHeroPast,
            FiniteTwoPlayerAffineSequenceForm.Audit baseline,
            double securitySlack,
            double requiredMinimumReach,
            double globalHeroUpperValue,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit heroProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit opponentProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit orientedPayoff,
            String securityFaceHash,
            Linear denominator,
            double certifiedReachLowerBound,
            double certifiedReachUpperBound,
            ReachWitness minimumReachWitness,
            ReachWitness maximumReachWitness,
            Work work) {
        public Audit {
            if (!ALGORITHM.equals(algorithm)
                    || trainerAdmission
                    || question == null
                    || baseline == null
                    || heroProjection == null
                    || opponentProjection == null
                    || orientedPayoff == null
                    || denominator == null
                    || work == null
                    || securityFaceHash == null
                    || !securityFaceHash.matches("[0-9a-f]{64}")
                    || !Double.isFinite(globalHeroUpperValue)
                    || !Double.isFinite(certifiedReachLowerBound)
                    || !Double.isFinite(certifiedReachUpperBound)
                    || certifiedReachLowerBound > certifiedReachUpperBound
                    || minimumReachWitness == null
                    || maximumReachWitness == null
                    || !Double.isFinite(minimumReachWitness.questionReach())
                    || !Double.isFinite(maximumReachWitness.questionReach())
                    || minimumReachWitness.questionReach() < 0
                    || maximumReachWitness.questionReach() > 1 + TOLERANCE
                    || minimumReachWitness.questionReach()
                            > maximumReachWitness.questionReach() + TOLERANCE
                    || certifiedReachLowerBound > minimumReachWitness.questionReach() + TOLERANCE
                    || certifiedReachUpperBound < maximumReachWitness.questionReach() - TOLERANCE
                    || work.intervalLpSolves() != 2)
                throw new IllegalArgumentException("Invalid reach bounds identity");
            limits(
                    securitySlack,
                    requiredMinimumReach,
                    BoundedLinearProgram.MAX_PIVOTS,
                    BoundedLinearProgram.MAX_ARITHMETIC_WORK);
            if (status
                    != classify(
                            certifiedReachLowerBound,
                            certifiedReachUpperBound,
                            minimumReachWitness.questionReach(),
                            maximumReachWitness.questionReach(),
                            requiredMinimumReach))
                throw new IllegalArgumentException("Reach bounds classification differs");
            fixedHeroPast = List.copyOf(fixedHeroPast);
        }
    }

    public static final class Result {
        private final Audit audit;

        private Result(Audit audit) {
            this.audit = audit;
        }

        public Audit audit() {
            return audit;
        }
    }

    private FiniteTwoPlayerReachBounds() {}

    private static Status classify(
            double lower, double upper, double min, double max, double floor) {
        if (upper < floor) return Status.CERTIFIED_ALL_BELOW_REQUIRED_FLOOR;
        if (lower >= floor) return Status.CERTIFIED_ALL_AT_OR_ABOVE_REQUIRED_FLOOR;
        if (min < floor - TOLERANCE && max > floor + TOLERANCE)
            return Status.VERIFIED_WITNESSES_ON_BOTH_SIDES;
        return Status.UNRESOLVED_AT_CERTIFICATE_TOLERANCE;
    }

    private static void limits(double slack, double floor, int pivots, long work) {
        if (!Double.isFinite(slack)
                || slack < DEFAULT_SECURITY_SLACK
                || slack > 1e-4
                || !Double.isFinite(floor)
                || floor < .0001
                || floor > 1
                || pivots < 0
                || pivots > BoundedLinearProgram.MAX_PIVOTS
                || work < 0
                || work > BoundedLinearProgram.MAX_ARITHMETIC_WORK)
            throw new IllegalArgumentException("Invalid reach bounds limits");
    }

    public static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            FiniteTwoPlayerConditionalActionIntervals.Question question,
            double securitySlack,
            double minimumReach)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    // Test controls only reduce existing limits; both endpoints share one budget.
    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            FiniteTwoPlayerConditionalActionIntervals.Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        Objects.requireNonNull(question);
        limits(securitySlack, minimumReach, pivotLimit, arithmeticLimit);
        var budget = new SequenceFormAffineProjection.Budget(compilerLimit);
        var checked = checked(game);
        var info =
                checked.infos().stream()
                        .filter(
                                i ->
                                        i.key().equals(question.informationSet())
                                                && i.actor() == question.hero())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown reach information set"));
        if (!info.actions().contains(question.action()))
            throw new IllegalArgumentException("Illegal reach question action");
        boolean first = question.hero() == checked.firstActor();
        var hero = first ? checked.firstFlow() : checked.secondFlow();
        var opponent = first ? checked.secondFlow() : checked.firstFlow();
        var roots = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Root>();
        collect(
                checked.snapshot().initialState(),
                question,
                opponent,
                0,
                1,
                List.of(),
                roots,
                budget);
        if (roots.isEmpty())
            throw new IllegalArgumentException("Reach question has no chance support");
        var baseline = FiniteTwoPlayerAffineSequenceForm.solve(checked.snapshot());
        double globalUpper =
                first
                        ? baseline.audit().upperValue()
                        : checked.constantSum() - baseline.audit().lowerValue();
        var hp = SequenceFormAffineProjection.build(hero.sequences(), hero.conservation(), budget);
        var op =
                SequenceFormAffineProjection.build(
                        opponent.sequences(), opponent.conservation(), budget);
        budget.charge((long) hero.sequences().size() * opponent.sequences().size());
        double[][] original = new double[hero.sequences().size()][opponent.sequences().size()];
        orientedPayoff(
                checked.snapshot().initialState(), hero, opponent, 0, 0, 1, original, budget);
        var payoff = SequenceFormAffineProjection.reduce(original, hp, op, budget);
        var face = face(payoff, hp.audit(), op.audit(), globalUpper + securitySlack, budget);
        double[] coefficients = new double[opponent.sequences().size()];
        for (var root : roots) coefficients[root.opponentSequence()] += root.chanceWeight();
        var denominator = project(coefficients, op.audit(), budget);
        var lpBudget = new LpBudget(pivotLimit, arithmeticLimit);
        double[] cost = new double[face.matrix()[0].length];
        for (int j = 0; j < op.variables(); j++) cost[j] = -denominator.cost().get(j);
        var minLp = lpBudget.solve(face.matrix(), face.rhs(), cost);
        for (int j = 0; j < cost.length; j++) cost[j] = -cost[j];
        var maxLp = lpBudget.solve(face.matrix(), face.rhs(), cost);
        var min =
                reachWitness(
                        checked,
                        roots,
                        question,
                        baseline,
                        opponent,
                        op,
                        minLp,
                        globalUpper + securitySlack,
                        denominator,
                        budget);
        var max =
                reachWitness(
                        checked,
                        roots,
                        question,
                        baseline,
                        opponent,
                        op,
                        maxLp,
                        globalUpper + securitySlack,
                        denominator,
                        budget);
        requireClose(
                denominator.constant() - minLp.certificate().primalValue(),
                min.questionReach(),
                "Independent minimum question reach");
        requireClose(
                denominator.constant() + maxLp.certificate().primalValue(),
                max.questionReach(),
                "Independent maximum question reach");
        double lower = denominator.constant() - minLp.certificate().dualValue() - TOLERANCE;
        double upper = denominator.constant() + maxLp.certificate().dualValue() + TOLERANCE;
        var charged = budget.audit();
        return new Result(
                new Audit(
                        ALGORITHM,
                        false,
                        classify(
                                lower,
                                upper,
                                min.questionReach(),
                                max.questionReach(),
                                minimumReach),
                        question,
                        info.ownHistory(),
                        baseline.audit(),
                        securitySlack,
                        minimumReach,
                        globalUpper,
                        hp.audit(),
                        op.audit(),
                        payoff,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(face),
                        denominator,
                        lower,
                        upper,
                        min,
                        max,
                        new Work(
                                charged.chargedUnits(),
                                charged.limit(),
                                lpBudget.solves,
                                lpBudget.pivots,
                                lpBudget.work)));
    }
}
