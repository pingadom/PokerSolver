package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/**
 * Bounded linear-fractional conditional EV on the complete numerical opponent security face.
 * Certifies positive question reach before scaling; never discards low-reach opponent strategies to
 * manufacture a bound. Past hero actions are fixed, and their common perfect-recall reach cancels.
 * Opponent action likelihoods and the posterior are independently recomputed per witness.
 */
public final class FiniteTwoPlayerConditionalActionIntervals {
    public static final String ALGORITHM = "POSITIVE_REACH_CONDITIONAL_SECURITY_FACE_OWNED_LP/v1";
    public static final String CONDITIONED_ALGORITHM =
            "POSITIVE_REACH_UPPER_OBJECTIVE_SHIFT_OWNED_LP/v2";
    public static final String PHYSICAL_FLOP_ALGORITHM =
            "OWNED_PHYSICAL_FLOP_CONDITIONAL_REACH_RESOLVABLE_ENDPOINTS/v3";
    public static final String PHYSICAL_FLOP_REFINED_DIRECTION_ALGORITHM =
            "OWNED_PHYSICAL_FLOP_CONDITIONAL_REACH_REFINED_DIRECTION_ENDPOINTS/v5";
    public static final String PHYSICAL_FLOP_EXACT_POWER_TWO_ALGORITHM =
            "OWNED_PHYSICAL_FLOP_CONDITIONAL_REACH_EXACT_POWER_TWO_ENDPOINTS/v4";
    public static final String PHYSICAL_FLOP_ASCENDING_REFINED_ALGORITHM =
            "OWNED_PHYSICAL_FLOP_ASCENDING_REFINED_PARENT_AND_ENDPOINTS/v6";
    public static final String PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM =
            "OWNED_PHYSICAL_FLOP_ASCENDING_PARENT_REFINED_BASIS_ENDPOINTS/v7";
    public static final String SCOPE =
            "ACTION_CONDITIONED_POSTERIOR_OPTIMAL_HERO_CONTINUATION_TERMINAL_UTILITY/v1";
    public static final double DEFAULT_MINIMUM_REACH = .01;
    private static final double TOLERANCE = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public enum Failure {
        INSUFFICIENT_REACH,
        NUMERICAL_FAILURE
    }

    public static final class Rejected extends IllegalArgumentException {
        private final Failure reason;
        private final ReachFailureAudit reachFailure;

        private Rejected(Failure reason, String detail) {
            this(reason, detail, null);
        }

        private Rejected(Failure reason, String detail, ReachFailureAudit reachFailure) {
            super(detail);
            this.reason = reason;
            this.reachFailure = reachFailure;
        }

        public Failure reason() {
            return reason;
        }

        public Optional<ReachFailureAudit> reachFailure() {
            return Optional.ofNullable(reachFailure);
        }
    }

    public static final String REACH_FAILURE_ALGORITHM =
            "FULL_SECURITY_FACE_MINIMUM_REACH_REJECTION_OWNED_LP/v1";

    public enum ReachFailureStatus {
        WITNESS_BELOW_REQUIRED_FLOOR,
        CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR
    }

    /** Unnormalized masses remain meaningful even when question reach is exactly zero. */
    public record ReachMass(
            int rootIndex,
            String stateHash,
            int opponentSequence,
            double chanceWeight,
            double originalRealizationWeight,
            double behavioralWeight) {}

    public record ReachWitness(
            BoundedLinearProgram.Certificate certificate,
            List<Double> point,
            List<Double> dual,
            List<Double> opponentVariables,
            FlowAudit opponentFlow,
            String behavioralPolicyHash,
            double questionReach,
            List<ReachMass> masses,
            double globalHeroBestResponse) {
        public ReachWitness {
            point = List.copyOf(point);
            dual = List.copyOf(dual);
            opponentVariables = List.copyOf(opponentVariables);
            masses = List.copyOf(masses);
        }
    }

    /** Diagnostic evidence only: no posterior, conditional EV, or admission at zero reach. */
    public record ReachFailureAudit(
            String algorithm,
            boolean trainerAdmission,
            ReachFailureStatus status,
            Question question,
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
            ReachWitness minimumReachWitness,
            Work work) {
        public ReachFailureAudit {
            if (!REACH_FAILURE_ALGORITHM.equals(algorithm)
                    || trainerAdmission
                    || status == null
                    || question == null
                    || baseline == null
                    || heroProjection == null
                    || opponentProjection == null
                    || orientedPayoff == null
                    || denominator == null
                    || minimumReachWitness == null
                    || work == null
                    || !Double.isFinite(securitySlack)
                    || securitySlack < DEFAULT_SECURITY_SLACK
                    || securitySlack > 1e-4
                    || !Double.isFinite(requiredMinimumReach)
                    || requiredMinimumReach < .0001
                    || requiredMinimumReach > 1
                    || !Double.isFinite(globalHeroUpperValue)
                    || !Double.isFinite(certifiedReachLowerBound)
                    || certifiedReachLowerBound >= requiredMinimumReach
                    || status
                            != (minimumReachWitness.questionReach()
                                            < requiredMinimumReach - TOLERANCE
                                    ? ReachFailureStatus.WITNESS_BELOW_REQUIRED_FLOOR
                                    : ReachFailureStatus.CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR))
                throw new IllegalArgumentException("Invalid reach rejection identity");
            fixedHeroPast = List.copyOf(fixedHeroPast);
        }
    }

    public record Question(int hero, String informationSet, String action) {
        public Question {
            new FiniteTwoPlayerRootActionIntervals.Question(hero, informationSet, action);
        }
    }

    public record Linear(double constant, List<Double> cost) {
        public Linear {
            cost = List.copyOf(cost);
        }
    }

    public record Posterior(
            int rootIndex,
            String stateHash,
            int opponentSequence,
            double chanceWeight,
            double originalRealizationWeight,
            double behavioralWeight,
            double probability) {}

    public record Witness(
            BoundedLinearProgram.Certificate certificate,
            List<Double> point,
            List<Double> dual,
            double scaling,
            List<Double> opponentVariables,
            FlowAudit opponentFlow,
            String behavioralPolicyHash,
            double questionReach,
            List<Posterior> posterior,
            double posteriorTotalVariation,
            double globalHeroBestResponse,
            double conditionalHeroBestResponse) {
        public Witness {
            point = List.copyOf(point);
            dual = List.copyOf(dual);
            opponentVariables = List.copyOf(opponentVariables);
            posterior = List.copyOf(posterior);
        }
    }

    public record Audit(
            String algorithm,
            String scope,
            boolean trainerAdmission,
            Question question,
            List<OwnAction> fixedHeroPast,
            FiniteTwoPlayerAffineSequenceForm.Audit baseline,
            double securitySlack,
            double requiredMinimumReach,
            double globalHeroUpperValue,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit heroProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit opponentProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit orientedPayoff,
            String securityFaceHash,
            String scaledFaceHash,
            Linear denominator,
            double certifiedReachLowerBound,
            double certifiedReachUpperBound,
            Witness minimumReachWitness,
            Witness maximumReachWitness,
            List<Plan> numeratorPlans,
            double lowerUtility,
            double upperUtility,
            int upperPlan,
            Witness lowerWitness,
            List<Witness> upperWitnesses,
            Work work) {
        public Audit {
            fixedHeroPast = List.copyOf(fixedHeroPast);
            numeratorPlans = List.copyOf(numeratorPlans);
            upperWitnesses = List.copyOf(upperWitnesses);
        }
    }

    public static final class Result {
        private final Audit audit;
        private final Context context;

        private Result(Audit audit, Context context) {
            this.audit = audit;
            this.context = context;
        }

        public Audit audit() {
            return audit;
        }

        // Owned solve provenance for additional controls; never accepted from caller-built audits.
        Context context() {
            return context;
        }
    }

    record Root(
            Node node, double chanceWeight, int opponentSequence, List<OwnAction> opponentPast) {}

    record Context(
            Checked checked,
            List<Root> roots,
            FiniteTwoPlayerAffineSequenceForm.Result baseline,
            Flow opponent,
            SequenceFormAffineProjection.Projection projection,
            Face scaled,
            double upperObjectiveShift) {
        Context {
            roots = List.copyOf(roots);
        }
    }

    private FiniteTwoPlayerConditionalActionIntervals() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, Question question) throws Exception {
        return solve(game, question, DEFAULT_SECURITY_SLACK, DEFAULT_MINIMUM_REACH);
    }

    public static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
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

    // All controls only reduce existing limits. Baseline affine solves keep separate old budgets.
    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                compilerLimit,
                pivotLimit,
                arithmeticLimit,
                false);
    }

    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit,
            boolean conditioned)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                compilerLimit,
                pivotLimit,
                arithmeticLimit,
                conditioned,
                0);
    }

    // Only an owned connected game determines the physical measurement event; no caller scale.
    static Result solvePhysicalFlop(
            SixMaxSuppliedFlopBettingGame game,
            Question question,
            double securitySlack,
            double minimumReach)
            throws Exception {
        double mass = physicalFlopMass(game, question);
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                true,
                mass);
    }

    /** The caller selects this separate LP representation before starting the complete solve. */
    static Result solvePhysicalFlopExactPowerOfTwo(
            SixMaxSuppliedFlopBettingGame game,
            Question question,
            double securitySlack,
            double minimumReach)
            throws Exception {
        double mass = physicalFlopMass(game, question);
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                true,
                mass,
                true);
    }

    /** Explicit separate mode; never retries a rejected v4 solve. */
    static Result solvePhysicalFlopRefinedDirections(
            SixMaxSuppliedFlopBettingGame game,
            Question question,
            double securitySlack,
            double minimumReach)
            throws Exception {
        double mass = physicalFlopMass(game, question);
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                true,
                mass,
                true,
                true);
    }

    private static double physicalFlopMass(SixMaxSuppliedFlopBettingGame game, Question question) {
        if (!question.informationSet().startsWith(question.hero() + ":supplied-flop-one-bet:"))
            throw new IllegalArgumentException("Physical flop question required");
        double mass = game.handoffs().getFirst().physicalFlopMass();
        if (!(mass > 0)
                || !Double.isFinite(mass)
                || game.handoffs().stream().anyMatch(h -> h.physicalFlopMass() != mass))
            throw new IllegalArgumentException("Inconsistent owned physical flop support");
        return mass;
    }

    /** Explicit parent and endpoint representation; never retries a rejected prior mode. */
    static Result solvePhysicalFlopAscendingRefined(
            SixMaxSuppliedFlopBettingGame game,
            Question question,
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
                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                true,
                physicalFlopMass(game, question),
                false,
                false,
                true);
    }

    static Result solvePhysicalFlopRefinedBasis(
            SixMaxSuppliedFlopBettingGame game,
            Question question,
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
                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                true,
                physicalFlopMass(game, question),
                false,
                false,
                true,
                true);
    }

    private static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit,
            boolean conditioned,
            double physicalFlopMass)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                compilerLimit,
                pivotLimit,
                arithmeticLimit,
                conditioned,
                physicalFlopMass,
                false);
    }

    private static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit,
            boolean conditioned,
            double physicalFlopMass,
            boolean exactPowerOfTwoRows)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                compilerLimit,
                pivotLimit,
                arithmeticLimit,
                conditioned,
                physicalFlopMass,
                exactPowerOfTwoRows,
                false);
    }

    private static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit,
            boolean conditioned,
            double physicalFlopMass,
            boolean exactPowerOfTwoRows,
            boolean refinedDirections)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                compilerLimit,
                pivotLimit,
                arithmeticLimit,
                conditioned,
                physicalFlopMass,
                exactPowerOfTwoRows,
                refinedDirections,
                false);
    }

    private static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit,
            boolean conditioned,
            double physicalFlopMass,
            boolean exactPowerOfTwoRows,
            boolean refinedDirections,
            boolean ascendingRefined)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                compilerLimit,
                pivotLimit,
                arithmeticLimit,
                conditioned,
                physicalFlopMass,
                exactPowerOfTwoRows,
                refinedDirections,
                ascendingRefined,
                false);
    }

    private static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit,
            boolean conditioned,
            double physicalFlopMass,
            boolean exactPowerOfTwoRows,
            boolean refinedDirections,
            boolean ascendingRefined,
            boolean refinedBasis)
            throws Exception {
        if (refinedBasis && !ascendingRefined)
            throw new IllegalArgumentException("Invalid refined basis mode");
        Objects.requireNonNull(question);
        if (ascendingRefined
                && (!conditioned
                        || !(physicalFlopMass > 0)
                        || exactPowerOfTwoRows
                        || refinedDirections))
            throw new IllegalArgumentException("Invalid ascending refined physical mode");
        if (!Double.isFinite(securitySlack)
                || securitySlack < DEFAULT_SECURITY_SLACK
                || securitySlack > 1e-4
                || !Double.isFinite(minimumReach)
                || minimumReach < .0001
                || minimumReach > 1
                || pivotLimit < 0
                || pivotLimit > BoundedLinearProgram.MAX_PIVOTS
                || arithmeticLimit < 0
                || arithmeticLimit > BoundedLinearProgram.MAX_ARITHMETIC_WORK)
            throw new IllegalArgumentException("Invalid conditional interval limits");
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
                                                "Unknown conditional information set"));
        if (!info.actions().contains(question.action()))
            throw new IllegalArgumentException("Illegal conditional action");
        boolean first = question.hero() == checked.firstActor();
        var hero = first ? checked.firstFlow() : checked.secondFlow();
        var opponent = first ? checked.secondFlow() : checked.firstFlow();
        var roots = new ArrayList<Root>();
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
            throw new IllegalArgumentException("Conditional question has no chance support");
        if (physicalFlopMass > 0) {
            // Scale measurement only. The original snapshot, payoff matrix and complete opponent
            // security face below retain physical chance, all worlds and all original actions.
            budget.charge(4L * roots.size());
            for (int i = 0; i < roots.size(); i++) {
                var root = roots.get(i);
                roots.set(
                        i,
                        new Root(
                                root.node(),
                                root.chanceWeight() / physicalFlopMass,
                                root.opponentSequence(),
                                root.opponentPast()));
            }
        }
        var continuations = new TreeMap<String, List<String>>();
        for (var root : roots)
            collectContinuations(forced(root, question), question.hero(), continuations, budget);
        long count = 1;
        for (var actions : continuations.values()) {
            count *= actions.size();
            if (count > MAX_CONTINUATION_PLANS)
                throw new IllegalArgumentException("Conditional continuation plan budget exceeded");
        }
        budget.charge(count * checked.treeNodes());
        var choices = new ArrayList<Map<String, String>>();
        enumerate(new ArrayList<>(continuations.entrySet()), 0, new TreeMap<>(), choices);
        var baseline =
                ascendingRefined
                        ? FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(
                                checked.snapshot())
                        : physicalFlopMass > 0
                                ? FiniteTwoPlayerAffineSequenceForm.solveAscendingL1Rows(
                                        checked.snapshot())
                                : FiniteTwoPlayerAffineSequenceForm.solve(checked.snapshot());
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
        double[] denominatorCoefficients = new double[opponent.sequences().size()];
        for (var root : roots)
            denominatorCoefficients[root.opponentSequence()] += root.chanceWeight();
        var denominator = project(denominatorCoefficients, op.audit(), budget);
        var plans = new ArrayList<Plan>();
        for (var choice : choices) {
            double[] coefficients = new double[opponent.sequences().size()];
            for (var root : roots)
                planPayoff(
                        forced(root, question),
                        hero.actor(),
                        opponent,
                        choice,
                        root.opponentSequence(),
                        root.chanceWeight(),
                        coefficients);
            var linear = project(coefficients, op.audit(), budget);
            plans.add(new Plan(choice, linear.constant(), linear.cost()));
        }
        var lpBudget =
                refinedBasis
                        ? LpBudget.ascendingRefinedBasis(pivotLimit, arithmeticLimit)
                        : ascendingRefined
                                ? LpBudget.ascendingRefinedDirections(pivotLimit, arithmeticLimit)
                                : refinedDirections
                                        ? LpBudget.refinedDirections(pivotLimit, arithmeticLimit)
                                        : exactPowerOfTwoRows
                                                ? LpBudget.exactPowerOfTwoRows(
                                                        pivotLimit, arithmeticLimit)
                                                : new LpBudget(
                                                        pivotLimit,
                                                        arithmeticLimit,
                                                        physicalFlopMass > 0,
                                                        physicalFlopMass > 0);
        int n = face.matrix()[0].length;
        double[] reachCost = new double[n];
        for (int j = 0; j < op.variables(); j++) reachCost[j] = -denominator.cost().get(j);
        var minLp = lpBudget.solve(face.matrix(), face.rhs(), reachCost);
        // Use the original LP dual upper bound on max(-d), with conservative numerical padding.
        double reachLower = denominator.constant() - minLp.certificate().dualValue() - TOLERANCE;
        if (!Double.isFinite(reachLower))
            throw new Rejected(Failure.NUMERICAL_FAILURE, "Non-finite certified question reach");
        if (reachLower < minimumReach) {
            var counterexample =
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
            requireClose(
                    denominator.constant() - minLp.certificate().primalValue(),
                    counterexample.questionReach(),
                    "Independent rejected minimum reach");
            var work = budget.audit();
            throw new Rejected(
                    Failure.INSUFFICIENT_REACH,
                    "Full security face question reach is not certified above the declared floor",
                    new ReachFailureAudit(
                            REACH_FAILURE_ALGORITHM,
                            false,
                            counterexample.questionReach() < minimumReach - TOLERANCE
                                    ? ReachFailureStatus.WITNESS_BELOW_REQUIRED_FLOOR
                                    : ReachFailureStatus.CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR,
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
                            reachLower,
                            counterexample,
                            new Work(
                                    work.chargedUnits(),
                                    work.limit(),
                                    lpBudget.solves,
                                    lpBudget.pivots,
                                    lpBudget.work)));
        }
        for (int j = 0; j < reachCost.length; j++) reachCost[j] = -reachCost[j];
        var maxLp = lpBudget.solve(face.matrix(), face.rhs(), reachCost);
        double reachUpper = denominator.constant() + maxLp.certificate().dualValue() + TOLERANCE;
        var scaled = scaled(face, denominator, minimumReach, budget, plans.size());
        var minWitness =
                witness(
                        checked,
                        roots,
                        question,
                        baseline,
                        opponent,
                        op,
                        minLp,
                        false,
                        globalUpper + securitySlack,
                        minimumReach,
                        denominator,
                        plans,
                        budget);
        var maxWitness =
                witness(
                        checked,
                        roots,
                        question,
                        baseline,
                        opponent,
                        op,
                        maxLp,
                        false,
                        globalUpper + securitySlack,
                        minimumReach,
                        denominator,
                        plans,
                        budget);
        requireClose(
                denominator.constant() - minLp.certificate().primalValue(),
                minWitness.questionReach(),
                "Independent minimum reach");
        requireClose(
                denominator.constant() + maxLp.certificate().primalValue(),
                maxWitness.questionReach(),
                "Independent maximum reach");
        // dPrime=1 on the already certified scaled face. Shift the objective, not the face.
        double upperShift =
                conditioned
                        ? -maximumUtility(checked.snapshot().initialState(), hero.actor()) - 1
                        : 0;
        var upperWitnesses = new ArrayList<Witness>();
        double upper = Double.NEGATIVE_INFINITY;
        int upperPlan = -1;
        for (int i = 0; i < plans.size(); i++) {
            var plan = plans.get(i);
            double[] cost = new double[n + 1];
            for (int j = 0; j < op.variables(); j++) cost[j] = plan.cost().get(j);
            cost[n] = plan.constant();
            if (conditioned) {
                budget.charge(op.variables() + 1L);
                for (int j = 0; j < op.variables(); j++)
                    cost[j] += upperShift * denominator.cost().get(j);
                cost[n] += upperShift * denominator.constant();
            }
            var solved = lpBudget.solve(scaled.matrix(), scaled.rhs(), cost);
            var witness =
                    witness(
                            checked,
                            roots,
                            question,
                            baseline,
                            opponent,
                            op,
                            solved,
                            true,
                            globalUpper + securitySlack,
                            minimumReach,
                            denominator,
                            plans,
                            budget);
            double value = solved.certificate().primalValue() - upperShift;
            requireClose(value, scaledValue(plan, solved.point(), n), "Scaled upper objective");
            if (witness.conditionalHeroBestResponse() + TOLERANCE < value)
                throw new Rejected(
                        Failure.NUMERICAL_FAILURE, "Conditional response below chosen plan");
            upperWitnesses.add(witness);
            if (value > upper) {
                upper = value;
                upperPlan = i;
            }
        }
        double shift =
                conditioned
                        ? -upperShift
                        : maximumUtility(checked.snapshot().initialState(), hero.actor()) + 1;
        int rows = scaled.rhs().length + plans.size(), columns = n + 2;
        budget.charge((long) rows * columns + rows + columns);
        double[][] matrix = new double[rows][columns];
        double[] rhs = new double[rows], cost = new double[columns];
        for (int i = 0; i < scaled.rhs().length; i++) {
            System.arraycopy(scaled.matrix()[i], 0, matrix[i], 0, n + 1);
            rhs[i] = scaled.rhs()[i];
        }
        for (int i = 0; i < plans.size(); i++) {
            int row = scaled.rhs().length + i;
            for (int j = 0; j < op.variables(); j++) matrix[row][j] = plans.get(i).cost().get(j);
            matrix[row][n] = plans.get(i).constant();
            // s = shift - conditional utility >= 0. Maximize s, with each plan <= shift-s.
            // This is the same lower epigraph with a derived upper shift, avoiding negative
            // epigraph right-hand sides and their extra artificial variables in phase I.
            matrix[row][n + 1] = 1;
            rhs[row] = shift;
        }
        cost[n + 1] = 1;
        var lowerLp = lpBudget.solve(matrix, rhs, cost);
        double lower = shift - lowerLp.certificate().primalValue();
        var lowerWitness =
                witness(
                        checked,
                        roots,
                        question,
                        baseline,
                        opponent,
                        op,
                        lowerLp,
                        true,
                        globalUpper + securitySlack,
                        minimumReach,
                        denominator,
                        plans,
                        budget);
        requireClose(lower, lowerWitness.conditionalHeroBestResponse(), "Scaled lower epigraph");
        requireClose(
                upper,
                upperWitnesses.get(upperPlan).conditionalHeroBestResponse(),
                "Scaled upper witness");
        for (var witness : upperWitnesses)
            if (witness.conditionalHeroBestResponse() > upper + TOLERANCE)
                throw new Rejected(
                        Failure.NUMERICAL_FAILURE,
                        "Witness exceeds global conditional upper bound");
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper + TOLERANCE)
            throw new Rejected(Failure.NUMERICAL_FAILURE, "Invalid conditional utility interval");
        lower = Math.min(lower, upper);
        var work = budget.audit();
        return new Result(
                new Audit(
                        physicalFlopMass > 0
                                ? refinedBasis
                                        ? PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM
                                        : ascendingRefined
                                                ? PHYSICAL_FLOP_ASCENDING_REFINED_ALGORITHM
                                                : refinedDirections
                                                        ? PHYSICAL_FLOP_REFINED_DIRECTION_ALGORITHM
                                                        : exactPowerOfTwoRows
                                                                ? PHYSICAL_FLOP_EXACT_POWER_TWO_ALGORITHM
                                                                : PHYSICAL_FLOP_ALGORITHM
                                : conditioned ? CONDITIONED_ALGORITHM : ALGORITHM,
                        SCOPE,
                        false,
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
                        SixMaxHistoryPhysicalConditionalRefinement.hash(scaled),
                        denominator,
                        reachLower,
                        reachUpper,
                        minWitness,
                        maxWitness,
                        plans,
                        lower,
                        upper,
                        upperPlan,
                        lowerWitness,
                        upperWitnesses,
                        new Work(
                                work.chargedUnits(),
                                work.limit(),
                                lpBudget.solves,
                                lpBudget.pivots,
                                lpBudget.work)),
                new Context(checked, roots, baseline, opponent, op, scaled, upperShift));
    }

    private static Node forced(Root root, Question q) {
        return root.node().children().get(root.node().actions().indexOf(q.action()));
    }

    static void collect(
            Node node,
            Question q,
            Flow opponent,
            int os,
            double chance,
            List<OwnAction> opponentPast,
            List<Root> roots,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(1);
        if (node.actor() == q.hero() && node.key().equals(q.informationSet())) {
            if (chance > 0) roots.add(new Root(node, chance, os, opponentPast));
            return;
        }
        for (int i = 0; i < node.children().size(); i++) {
            int o = os;
            double p = chance;
            var history = opponentPast;
            if (node.actor() == -1) p *= node.probabilities().get(i);
            else if (node.actor() == opponent.actor()) {
                var action = new OwnAction(node.key(), node.actions().get(i));
                o = opponent.indices().get(action);
                var next = new ArrayList<>(opponentPast);
                next.add(action);
                history = List.copyOf(next);
            }
            collect(node.children().get(i), q, opponent, o, p, history, roots, budget);
        }
    }

    static Linear project(
            double[] coefficients,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit projection,
            SequenceFormAffineProjection.Budget budget) {
        int variables = projection.independentSequences().size();
        budget.charge((long) coefficients.length * (variables + 1));
        double constant = 0;
        double[] cost = new double[variables];
        for (int i = 0; i < coefficients.length; i++) {
            constant += coefficients[i] * projection.offset().get(i);
            for (int j = 0; j < variables; j++)
                cost[j] += coefficients[i] * projection.transform().get(i).get(j);
        }
        return new Linear(constant, Arrays.stream(cost).boxed().toList());
    }

    private static Face scaled(
            Face original,
            Linear denominator,
            double minimumReach,
            SequenceFormAffineProjection.Budget budget,
            int plans) {
        int n = original.matrix()[0].length, m = original.rhs().length;
        if (n + 2 > BoundedLinearProgram.MAX_VARIABLES
                || m + 3 + plans > BoundedLinearProgram.MAX_CONSTRAINTS)
            throw new IllegalArgumentException("Conditional interval LP dimensions exceed limits");
        budget.charge((long) (m + 3) * (n + 1) + m + 3);
        double[][] matrix = new double[m + 3][n + 1];
        double[] rhs = new double[m + 3];
        for (int i = 0; i < m; i++) {
            System.arraycopy(original.matrix()[i], 0, matrix[i], 0, n);
            matrix[i][n] = -original.rhs()[i];
        }
        // d0*t+dCost.w'=1. Nonnegative t is bounded using the already certified global floor.
        for (int j = 0; j < denominator.cost().size(); j++) {
            matrix[m][j] = denominator.cost().get(j);
            matrix[m + 1][j] = -denominator.cost().get(j);
        }
        matrix[m][n] = denominator.constant();
        matrix[m + 1][n] = -denominator.constant();
        rhs[m] = 1;
        rhs[m + 1] = -1;
        matrix[m + 2][n] = 1;
        rhs[m + 2] = 1 / minimumReach;
        return new Face(matrix, rhs, original.opponentVariables());
    }

    private static double scaledValue(Plan plan, List<Double> point, int scalingIndex) {
        double value = plan.constant() * point.get(scalingIndex);
        for (int j = 0; j < plan.cost().size(); j++) value += plan.cost().get(j) * point.get(j);
        return value;
    }

    private static double value(Linear linear, double[] variables) {
        double value = linear.constant();
        for (int j = 0; j < variables.length; j++) value += linear.cost().get(j) * variables[j];
        return value;
    }

    static Witness witness(
            Checked checked,
            List<Root> roots,
            Question q,
            FiniteTwoPlayerAffineSequenceForm.Result baseline,
            Flow opponent,
            SequenceFormAffineProjection.Projection projection,
            BoundedLinearProgram.Result solved,
            boolean scaled,
            double upper,
            double minimumReach,
            Linear denominator,
            List<Plan> plans,
            SequenceFormAffineProjection.Budget budget)
            throws Exception {
        int scalingIndex =
                projection.variables() + checkedFlow(checked, q.hero()).conservation().size();
        double scaling = scaled ? solved.point().get(scalingIndex) : 1;
        if (!Double.isFinite(scaling)
                || scaling <= 0
                || scaled && scaling > 1 / minimumReach + TOLERANCE)
            throw new Rejected(Failure.NUMERICAL_FAILURE, "Invalid fractional scaling");
        double[] variables = new double[projection.variables()];
        for (int j = 0; j < variables.length; j++) variables[j] = solved.point().get(j) / scaling;
        budget.charge((long) opponent.sequences().size() * (variables.length + 1) + roots.size());
        var realization =
                Arrays.stream(projection.reconstruct(variables))
                        .map(
                                v -> {
                                    if (v < -TOLERANCE)
                                        throw new Rejected(
                                                Failure.NUMERICAL_FAILURE,
                                                "Negative original opponent flow");
                                    return Math.max(0, v);
                                })
                        .boxed()
                        .toList();
        var strategy = new LinkedHashMap<>(baseline.strategy());
        var flow = behavior(opponent, realization, strategy);
        var policy = new CfrSolution(1, strategy);
        double global =
                MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy)
                        .bestResponseUtilitiesBb()
                        .get(q.hero());
        if (!Double.isFinite(global) || global > upper + TOLERANCE)
            throw new Rejected(
                    Failure.NUMERICAL_FAILURE,
                    "Behavioral conditional witness violates global security");
        double[] actual = new double[roots.size()], linear = new double[roots.size()];
        double reach = 0, linearReach = 0;
        for (int i = 0; i < roots.size(); i++) {
            var root = roots.get(i);
            actual[i] = root.chanceWeight();
            for (var action : root.opponentPast())
                actual[i] *= policy.strategy().get(action.key()).get(action.action());
            linear[i] = root.chanceWeight() * realization.get(root.opponentSequence());
            reach += actual[i];
            linearReach += linear[i];
        }
        if (!Double.isFinite(reach) || reach < minimumReach - TOLERANCE || reach > 1 + TOLERANCE)
            throw new Rejected(
                    Failure.NUMERICAL_FAILURE, "Invalid independently reached question mass");
        requireClose(value(denominator, variables), reach, "Original behavioral question reach");
        if (scaled) requireClose(1, reach * scaling, "Fractional denominator normalization");
        var posterior = new ArrayList<Posterior>();
        double tv = 0;
        for (int i = 0; i < roots.size(); i++) {
            var root = roots.get(i);
            double p = actual[i] / reach;
            tv += .5 * Math.abs(p - linear[i] / linearReach);
            posterior.add(
                    new Posterior(
                            i,
                            SixMaxHistoryPhysicalConditionalRefinement.hash(root.node()),
                            root.opponentSequence(),
                            root.chanceWeight(),
                            linear[i],
                            actual[i],
                            p));
        }
        if (!Double.isFinite(tv) || tv > TOLERANCE)
            throw new Rejected(
                    Failure.NUMERICAL_FAILURE,
                    "Behavioral posterior differs from original realization");
        var probabilities = new ArrayList<Double>();
        var children = new ArrayList<Node>();
        for (int i = 0; i < roots.size(); i++) {
            if (posterior.get(i).probability() > 0) {
                probabilities.add(posterior.get(i).probability());
                children.add(forced(roots.get(i), q));
            }
        }
        var conditional =
                new Snapshot(
                        checked.snapshot().playerCount(),
                        new Node(-1, "", List.of(), probabilities, children, List.of()));
        double local =
                MultiPlayerInformationSetBestResponse.assess(conditional, policy)
                        .bestResponseUtilitiesBb()
                        .get(q.hero());
        double numerator = Double.NEGATIVE_INFINITY;
        for (var plan : plans)
            numerator =
                    Math.max(numerator, value(new Linear(plan.constant(), plan.cost()), variables));
        requireClose(
                numerator / reach,
                local,
                "Independent action-conditioned information-set response");
        return new Witness(
                solved.certificate(),
                solved.point(),
                solved.dual(),
                scaling,
                Arrays.stream(variables).boxed().toList(),
                flow,
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                reach,
                posterior,
                tv,
                global,
                local);
    }

    private static Flow checkedFlow(Checked checked, int actor) {
        return actor == checked.firstActor() ? checked.firstFlow() : checked.secondFlow();
    }

    static ReachWitness reachWitness(
            Checked checked,
            List<Root> roots,
            Question question,
            FiniteTwoPlayerAffineSequenceForm.Result baseline,
            Flow opponent,
            SequenceFormAffineProjection.Projection projection,
            BoundedLinearProgram.Result solved,
            double upper,
            Linear denominator,
            SequenceFormAffineProjection.Budget budget)
            throws Exception {
        double[] variables = new double[projection.variables()];
        for (int j = 0; j < variables.length; j++) variables[j] = solved.point().get(j);
        budget.charge((long) opponent.sequences().size() * (variables.length + 1) + roots.size());
        var realization =
                Arrays.stream(projection.reconstruct(variables))
                        .map(
                                v -> {
                                    if (!Double.isFinite(v) || v < -TOLERANCE)
                                        throw new Rejected(
                                                Failure.NUMERICAL_FAILURE,
                                                "Invalid rejected opponent flow");
                                    return Math.max(0, v);
                                })
                        .boxed()
                        .toList();
        var strategy = new LinkedHashMap<>(baseline.strategy());
        var flow = behavior(opponent, realization, strategy);
        var policy = new CfrSolution(1, strategy);
        double global =
                MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy)
                        .bestResponseUtilitiesBb()
                        .get(question.hero());
        if (!Double.isFinite(global) || global > upper + TOLERANCE)
            throw new Rejected(
                    Failure.NUMERICAL_FAILURE, "Rejected reach witness violates global security");
        var masses = new ArrayList<ReachMass>();
        double reach = 0;
        for (int i = 0; i < roots.size(); i++) {
            var root = roots.get(i);
            double actual = root.chanceWeight();
            for (var action : root.opponentPast())
                actual *= policy.strategy().get(action.key()).get(action.action());
            double linear = root.chanceWeight() * realization.get(root.opponentSequence());
            requireClose(linear, actual, "Rejected original and behavioral world mass");
            reach += actual;
            masses.add(
                    new ReachMass(
                            i,
                            SixMaxHistoryPhysicalConditionalRefinement.hash(root.node()),
                            root.opponentSequence(),
                            root.chanceWeight(),
                            linear,
                            actual));
        }
        if (!Double.isFinite(reach) || reach < 0 || reach > 1 + TOLERANCE)
            throw new Rejected(Failure.NUMERICAL_FAILURE, "Invalid rejected question mass");
        requireClose(value(denominator, variables), reach, "Rejected behavioral question reach");
        return new ReachWitness(
                solved.certificate(),
                solved.point(),
                solved.dual(),
                Arrays.stream(variables).boxed().toList(),
                flow,
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                reach,
                masses,
                global);
    }
}
