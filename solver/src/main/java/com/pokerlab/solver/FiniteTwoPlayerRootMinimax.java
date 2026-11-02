package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.math.BigInteger;
import java.util.*;

/** One direct epigraph LP for the minimum optimal continuation value on the whole root face. */
public final class FiniteTwoPlayerRootMinimax {
    public static final String ALGORITHM = "ROOT_CONSTANT_REACH_REDUCED_PLANS_MINIMAX_EPIGRAPH/v1";
    public static final String SCOPE =
            "MINIMUM_OPTIMAL_ROOT_CONTINUATION_TERMINAL_UTILITY_NOT_FULL_ACTION_INTERVAL/v1";
    public static final double MINIMUM_REACH = .0001;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public enum Failure {
        NON_ROOT_QUESTION,
        INSUFFICIENT_REACH,
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

    /** Both endpoints bound the minimum; upperBound is NOT an all-strategy action EV ceiling. */
    public record MinimumBracket(double lowerBound, double upperBound, double witnessUtility) {}

    public record Support(
            double originalChanceWeight, double posteriorProbability, String stateHash) {}

    public record Work(
            long compilerUnits,
            long compilerLimit,
            int endpointLpSolves,
            int endpointPivots,
            long endpointArithmeticWork) {}

    public record OriginalProblem(
            List<List<Double>> matrix, List<Double> rhs, List<Double> cost, double utilityShift) {
        public OriginalProblem {
            matrix = matrix.stream().map(List::copyOf).toList();
            rhs = List.copyOf(rhs);
            cost = List.copyOf(cost);
        }
    }

    public record Audit(
            String algorithm,
            String scope,
            boolean trainerAdmission,
            Question question,
            double originalQuestionReach,
            double requiredMinimumReach,
            double securitySlack,
            FiniteTwoPlayerAffineSequenceForm.Audit baseline,
            FiniteTwoPlayerAffineSequenceForm.LpWitness firstParentWitness,
            FiniteTwoPlayerAffineSequenceForm.LpWitness secondParentWitness,
            double globalHeroUpperValue,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit heroProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit opponentProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit orientedPayoff,
            String securityFaceHash,
            List<Support> support,
            BigInteger cartesianPlans,
            BigInteger reducedPlans,
            List<Plan> plans,
            OriginalProblem problem,
            MinimumBracket minimum,
            Witness witness,
            Work work) {
        public Audit {
            support = List.copyOf(support);
            plans = List.copyOf(plans);
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

    private record Root(Node node, double chance) {}

    private FiniteTwoPlayerRootMinimax() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, Question question) throws Exception {
        return solve(game, question, DEFAULT_SECURITY_SLACK, MINIMUM_REACH);
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

    // Diagnostic controls only reduce original caps. No alternate solve is attempted after failure.
    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question q,
            double slack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        try {
            return calculate(
                    game, q, slack, minimumReach, compilerLimit, pivotLimit, arithmeticLimit);
        } catch (IllegalStateException failure) {
            var rejected =
                    new Rejected(
                            Failure.NUMERICAL_FAILURE,
                            "Root minimax verification failed: " + failure.getMessage());
            rejected.initCause(failure);
            throw rejected;
        }
    }

    private static <S> Result calculate(
            MultiPlayerCfrGame<S> game,
            Question q,
            double slack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        Objects.requireNonNull(q);
        if (!Double.isFinite(slack)
                || slack < DEFAULT_SECURITY_SLACK
                || slack > 1e-4
                || !Double.isFinite(minimumReach)
                || minimumReach < MINIMUM_REACH
                || minimumReach > 1
                || pivotLimit < 0
                || pivotLimit > BoundedLinearProgram.MAX_PIVOTS
                || arithmeticLimit < 0
                || arithmeticLimit > BoundedLinearProgram.MAX_ARITHMETIC_WORK)
            throw new IllegalArgumentException("Invalid original root minimax limits");
        var budget = new SequenceFormAffineProjection.Budget(compilerLimit);
        var checked = checked(game);
        if (q.hero() != checked.firstActor() && q.hero() != checked.secondActor())
            throw new IllegalArgumentException("Active root actor required");
        var roots = new ArrayList<Root>();
        var inside = new HashSet<String>();
        var outside = new HashSet<String>();
        collect(
                checked.snapshot().initialState(),
                q,
                1,
                false,
                false,
                roots,
                inside,
                outside,
                budget);
        if (roots.isEmpty())
            throw new Rejected(
                    Failure.NON_ROOT_QUESTION, "Question must precede every player action");
        if (inside.stream().anyMatch(outside::contains))
            throw new Rejected(
                    Failure.NON_ROOT_QUESTION,
                    "Root continuation shares hero information with another observation");
        double mass = roots.stream().mapToDouble(Root::chance).sum();
        if (!Double.isFinite(mass) || mass < minimumReach || mass > 1 + EPS)
            throw new Rejected(
                    Failure.INSUFFICIENT_REACH,
                    "Original chance reach is below the required floor");
        var forest = new ArrayList<Root>();
        for (var root : roots) {
            int action = root.node().actions().indexOf(q.action());
            if (action < 0) throw new IllegalArgumentException("Illegal forced root action");
            forest.add(new Root(root.node().children().get(action), root.chance() / mass));
        }
        var compiled =
                ReducedHeroContinuations.compile(
                        forest.stream().map(Root::node).toList(),
                        q.hero(),
                        checked.treeNodes(),
                        budget);
        var baseline =
                FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(
                        checked.snapshot());
        boolean first = q.hero() == checked.firstActor();
        var hero = first ? checked.firstFlow() : checked.secondFlow();
        var opponent = first ? checked.secondFlow() : checked.firstFlow();
        double globalUpper =
                first
                        ? baseline.audit().upperValue()
                        : checked.constantSum() - baseline.audit().lowerValue();
        var hp = SequenceFormAffineProjection.build(hero.sequences(), hero.conservation(), budget);
        var op =
                SequenceFormAffineProjection.build(
                        opponent.sequences(), opponent.conservation(), budget);
        budget.charge((long) hero.sequences().size() * opponent.sequences().size());
        var original = new double[hero.sequences().size()][opponent.sequences().size()];
        orientedPayoff(
                checked.snapshot().initialState(), hero, opponent, 0, 0, 1, original, budget);
        var payoff = SequenceFormAffineProjection.reduce(original, hp, op, budget);
        var face = face(payoff, hp.audit(), op.audit(), globalUpper + slack, budget);
        var plans = new ArrayList<Plan>();
        for (var choice : compiled.choices()) {
            var coefficients = new double[opponent.sequences().size()];
            for (var root : forest)
                planPayoff(
                        root.node(),
                        hero.actor(),
                        opponent,
                        choice,
                        0,
                        root.chance(),
                        coefficients);
            budget.charge((long) coefficients.length * (op.variables() + 1));
            double constant = 0;
            var cost = new double[op.variables()];
            for (int i = 0; i < coefficients.length; i++) {
                constant += coefficients[i] * op.audit().offset().get(i);
                for (int j = 0; j < cost.length; j++)
                    cost[j] += coefficients[i] * op.audit().transform().get(i).get(j);
            }
            plans.add(new Plan(choice, constant, Arrays.stream(cost).boxed().toList()));
        }
        // v = utility + shift >= 0. Maximize -v, subject to every pure continuation <= v-shift.
        double shift = maximumUtility(checked.snapshot().initialState(), hero.actor()) + 1;
        int columns = face.matrix()[0].length + 1, rows = face.rhs().length + plans.size();
        budget.charge((long) rows * columns + rows + columns);
        var matrix = new double[rows][columns];
        var rhs = new double[rows];
        var cost = new double[columns];
        for (int i = 0; i < face.rhs().length; i++) {
            System.arraycopy(face.matrix()[i], 0, matrix[i], 0, columns - 1);
            rhs[i] = face.rhs()[i];
        }
        for (int i = 0; i < plans.size(); i++) {
            var plan = plans.get(i);
            int row = face.rhs().length + i;
            for (int j = 0; j < op.variables(); j++) matrix[row][j] = plan.cost().get(j);
            matrix[row][columns - 1] = -1;
            rhs[row] = -plan.constant() - shift;
        }
        cost[columns - 1] = -1;
        var lp = LpBudget.phaseOneRefinedBasis(pivotLimit, arithmeticLimit);
        var solved = lp.solve(matrix, rhs, cost);
        var conditional =
                new Snapshot(
                        checked.snapshot().playerCount(),
                        new Node(
                                -1,
                                "",
                                List.of(),
                                forest.stream().map(Root::chance).toList(),
                                forest.stream().map(Root::node).toList(),
                                List.of()));
        var strategy = new LinkedHashMap<>(baseline.strategy());
        var flow =
                behavior(
                        opponent,
                        SequenceFormAffineProjection.realization(op, solved, budget),
                        strategy);
        var policy = new CfrSolution(1, strategy);
        double global =
                MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy)
                        .bestResponseUtilitiesBb()
                        .get(hero.actor());
        double local =
                MultiPlayerInformationSetBestResponse.assess(conditional, policy)
                        .bestResponseUtilitiesBb()
                        .get(hero.actor());
        if (!Double.isFinite(global) || global > globalUpper + slack + EPS)
            throw new IllegalStateException("Minimax behavioral witness violates global security");
        double objective = -solved.certificate().primalValue() - shift;
        requireClose(objective, local, "Independent root information-set response");
        double best = Double.NEGATIVE_INFINITY;
        for (var plan : plans) {
            double value = plan.constant();
            for (int j = 0; j < plan.cost().size(); j++)
                value += plan.cost().get(j) * solved.point().get(j);
            best = Math.max(best, value);
        }
        requireClose(best, local, "Complete reduced continuation maximum");
        double lower = -solved.certificate().dualValue() - shift - EPS;
        double upper = Math.max(objective, local) + EPS;
        if (!Double.isFinite(lower)
                || !Double.isFinite(upper)
                || lower > upper
                || local < lower - EPS
                || local > upper + EPS)
            throw new IllegalStateException("Invalid minimax endpoint bracket");
        var witness =
                new Witness(
                        solved.certificate(),
                        solved.point(),
                        solved.dual(),
                        flow,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        global,
                        local);
        var support = new ArrayList<Support>();
        for (var root : roots)
            support.add(
                    new Support(
                            root.chance(),
                            root.chance() / mass,
                            SixMaxHeadsUpPreflopGame.hash(root.node())));
        var problem =
                new OriginalProblem(
                        Arrays.stream(matrix)
                                .map(row -> Arrays.stream(row).boxed().toList())
                                .toList(),
                        Arrays.stream(rhs).boxed().toList(),
                        Arrays.stream(cost).boxed().toList(),
                        shift);
        return new Result(
                new Audit(
                        ALGORITHM,
                        SCOPE,
                        false,
                        q,
                        mass,
                        minimumReach,
                        slack,
                        baseline.audit(),
                        baseline.firstLpWitness(),
                        baseline.secondLpWitness(),
                        globalUpper,
                        hp.audit(),
                        op.audit(),
                        payoff,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(face),
                        support,
                        compiled.cartesianPlans(),
                        compiled.reducedPlans(),
                        plans,
                        problem,
                        new MinimumBracket(lower, upper, local),
                        witness,
                        new Work(
                                budget.audit().chargedUnits(),
                                compilerLimit,
                                lp.solves,
                                lp.pivots,
                                lp.work)));
    }

    private static void collect(
            Node n,
            Question q,
            double chance,
            boolean previous,
            boolean selected,
            List<Root> roots,
            Set<String> inside,
            Set<String> outside,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(1);
        if (n.actor() == q.hero() && n.key().equals(q.informationSet())) {
            if (previous)
                throw new Rejected(
                        Failure.NON_ROOT_QUESTION,
                        "Opponent-dependent or later question is unsupported");
            selected = true;
            roots.add(new Root(n, chance));
        }
        if (n.actor() == q.hero()) (selected ? inside : outside).add(n.key());
        for (int i = 0; i < n.children().size(); i++)
            collect(
                    n.children().get(i),
                    q,
                    n.actor() == -1 ? chance * n.probabilities().get(i) : chance,
                    previous || n.actor() >= 0,
                    selected,
                    roots,
                    inside,
                    outside,
                    budget);
    }
}
