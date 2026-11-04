package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Root-action minimum via a sequence-flow dual, without enumerating pure continuations. */
public final class FiniteTwoPlayerRootSequenceMinimax {
    public static final String ALGORITHM = "ROOT_CONSTANT_REACH_SEQUENCE_DUAL_MINIMAX/v1";
    public static final String SCOPE =
            "MINIMUM_OPTIMAL_ROOT_CONTINUATION_SEQUENCE_DUAL_NOT_FULL_ACTION_INTERVAL/v1";
    public static final double MINIMUM_REACH = .0001;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public enum Failure {
        NON_ROOT_QUESTION,
        INSUFFICIENT_REACH,
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

    public record HeroDomain(int forcedSequence, List<List<Double>> matrix, List<Double> rhs) {
        public HeroDomain {
            matrix = matrix.stream().map(List::copyOf).toList();
            rhs = List.copyOf(rhs);
        }
    }

    public record Problem(
            List<List<Double>> matrix,
            List<Double> rhs,
            List<Double> cost,
            double objectiveConstant,
            int securityRows,
            int opponentVariables,
            int globalDualVariables,
            int continuationDualVariables) {
        public Problem {
            matrix = matrix.stream().map(List::copyOf).toList();
            rhs = List.copyOf(rhs);
            cost = List.copyOf(cost);
        }
    }

    public record Work(
            long compilerUnits,
            long compilerLimit,
            int endpointLpSolves,
            int endpointPivots,
            long endpointArithmeticWork,
            int enumeratedContinuationPlans) {}

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
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit conditionalPayoff,
            String securityFaceHash,
            List<FiniteTwoPlayerRootMinimax.Support> support,
            HeroDomain heroDomain,
            Problem problem,
            FiniteTwoPlayerRootMinimax.MinimumBracket minimum,
            Witness witness,
            FlowAudit continuationFlow,
            String continuationPolicyHash,
            double continuationUtility,
            Work work) {
        public Audit {
            support = List.copyOf(support);
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

    private FiniteTwoPlayerRootSequenceMinimax() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, Question question) throws Exception {
        return solve(
                game,
                question,
                DEFAULT_SECURITY_SLACK,
                MINIMUM_REACH,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    // Test controls can only reduce original budgets or strengthen the original reach floor.
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
        } catch (IllegalStateException cause) {
            var rejected =
                    new Rejected(
                            Failure.NUMERICAL_FAILURE,
                            "Root sequence minimax verification failed: " + cause.getMessage());
            rejected.initCause(cause);
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
            throw new IllegalArgumentException("Invalid original root sequence minimax limits");
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
        if (roots.isEmpty() || inside.stream().anyMatch(outside::contains))
            throw new Rejected(
                    Failure.NON_ROOT_QUESTION, "A disjoint chance-only root question is required");
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
        budget.charge(2L * hero.sequences().size() * opponent.sequences().size());
        var original = new double[hero.sequences().size()][opponent.sequences().size()];
        orientedPayoff(
                checked.snapshot().initialState(), hero, opponent, 0, 0, 1, original, budget);
        var payoff = SequenceFormAffineProjection.reduce(original, hp, op, budget);
        var face = face(payoff, hp.audit(), op.audit(), globalUpper + slack, budget);
        int forced = hero.indices().get(new OwnAction(q.informationSet(), q.action()));
        var conditionalMatrix = new double[hero.sequences().size()][opponent.sequences().size()];
        for (var root : forest)
            orientedPayoff(
                    root.node(),
                    hero,
                    opponent,
                    forced,
                    0,
                    root.chance(),
                    conditionalMatrix,
                    budget);
        var conditionalPayoff =
                SequenceFormAffineProjection.reduce(conditionalMatrix, hp, op, budget);
        int z = hp.variables(), w = op.variables(), globalDual = hp.audit().rhs().size();
        int localDual = globalDual + 2, columns = face.matrix()[0].length + localDual;
        int rows = face.rhs().length + z;
        if (columns > BoundedLinearProgram.MAX_VARIABLES
                || rows > BoundedLinearProgram.MAX_CONSTRAINTS)
            throw new IllegalArgumentException("Original root sequence LP dimensions exceeded");
        budget.charge((long) localDual * (z + 1) + (long) rows * columns + rows + columns);
        var localMatrix = new double[localDual][z];
        var localRhs = new double[localDual];
        for (int i = 0; i < globalDual; i++) {
            for (int j = 0; j < z; j++) localMatrix[i][j] = hp.audit().inequalities().get(i).get(j);
            localRhs[i] = hp.audit().rhs().get(i);
        }
        for (int j = 0; j < z; j++) {
            localMatrix[globalDual][j] = hp.audit().transform().get(forced).get(j);
            localMatrix[globalDual + 1][j] = -localMatrix[globalDual][j];
        }
        localRhs[globalDual] = 1 - hp.audit().offset().get(forced);
        localRhs[globalDual + 1] = -localRhs[globalDual];
        var matrix = new double[rows][columns];
        var rhs = new double[rows];
        var cost = new double[columns];
        for (int i = 0; i < face.rhs().length; i++) {
            System.arraycopy(face.matrix()[i], 0, matrix[i], 0, face.matrix()[i].length);
            rhs[i] = face.rhs()[i];
        }
        for (int i = 0; i < z; i++) {
            int row = face.rhs().length + i;
            for (int j = 0; j < w; j++) matrix[row][j] = conditionalPayoff.matrix().get(i).get(j);
            for (int j = 0; j < localDual; j++)
                matrix[row][w + globalDual + j] = -localMatrix[j][i];
            rhs[row] = -conditionalPayoff.firstCost().get(i);
        }
        for (int j = 0; j < w; j++) cost[j] = -conditionalPayoff.secondCost().get(j);
        for (int j = 0; j < localDual; j++) cost[w + globalDual + j] = -localRhs[j];
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
        budget.charge((long) hero.sequences().size() * (z + 1));
        double[] heroMass =
                hp.reconstruct(
                        solved.dual().subList(face.rhs().length, rows).stream()
                                .mapToDouble(Double::doubleValue)
                                .toArray());
        var realization = new ArrayList<Double>();
        for (double value : heroMass) {
            if (value < -EPS)
                throw new IllegalStateException("Invalid original continuation realization");
            realization.add(Math.max(0, value));
        }
        var continuationFlow = behavior(hero, realization, strategy);
        requireClose(
                1, continuationFlow.realization().get(forced), "Forced original sequence mass");
        var policy = new CfrSolution(1, strategy);
        double global =
                MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy)
                        .bestResponseUtilitiesBb()
                        .get(hero.actor());
        var response = MultiPlayerInformationSetBestResponse.assess(conditional, policy);
        double local = response.bestResponseUtilitiesBb().get(hero.actor());
        double profile = response.profileUtilitiesBb().get(hero.actor());
        double objective = conditionalPayoff.constant() - solved.certificate().primalValue();
        if (!Double.isFinite(global) || global > globalUpper + slack + EPS)
            throw new IllegalStateException(
                    "Sequence minimax behavioral witness violates global security");
        requireClose(objective, local, "Independent root information-set response");
        requireClose(local, profile, "Original sequence continuation witness");
        double lower = conditionalPayoff.constant() - solved.certificate().dualValue() - EPS;
        double upper = Math.max(objective, local) + EPS;
        if (!Double.isFinite(lower)
                || !Double.isFinite(upper)
                || lower > upper
                || local < lower - EPS
                || local > upper + EPS)
            throw new IllegalStateException("Invalid sequence minimax bracket");
        var witness =
                new Witness(
                        solved.certificate(),
                        solved.point(),
                        solved.dual(),
                        flow,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        global,
                        local);
        var support = new ArrayList<FiniteTwoPlayerRootMinimax.Support>();
        for (var root : roots)
            support.add(
                    new FiniteTwoPlayerRootMinimax.Support(
                            root.chance(),
                            root.chance() / mass,
                            SixMaxHeadsUpPreflopGame.hash(root.node())));
        var domain = new HeroDomain(forced, doubles(localMatrix), doubles(localRhs));
        var problem =
                new Problem(
                        doubles(matrix),
                        doubles(rhs),
                        doubles(cost),
                        conditionalPayoff.constant(),
                        face.rhs().length,
                        w,
                        globalDual,
                        localDual);
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
                        conditionalPayoff,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(face),
                        support,
                        domain,
                        problem,
                        new FiniteTwoPlayerRootMinimax.MinimumBracket(lower, upper, local),
                        witness,
                        continuationFlow,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        profile,
                        new Work(
                                budget.audit().chargedUnits(),
                                compilerLimit,
                                lp.solves,
                                lp.pivots,
                                lp.work,
                                0)));
    }

    private static List<Double> doubles(double[] row) {
        return Arrays.stream(row).boxed().toList();
    }

    private static List<List<Double>> doubles(double[][] matrix) {
        return Arrays.stream(matrix).map(FiniteTwoPlayerRootSequenceMinimax::doubles).toList();
    }

    private static void collect(
            Node node,
            Question q,
            double chance,
            boolean previous,
            boolean selected,
            List<Root> roots,
            Set<String> inside,
            Set<String> outside,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(1);
        if (node.actor() == q.hero() && node.key().equals(q.informationSet())) {
            if (previous)
                throw new Rejected(
                        Failure.NON_ROOT_QUESTION,
                        "Opponent-dependent or later question is unsupported");
            selected = true;
            roots.add(new Root(node, chance));
        }
        if (node.actor() == q.hero()) (selected ? inside : outside).add(node.key());
        for (int i = 0; i < node.children().size(); i++)
            collect(
                    node.children().get(i),
                    q,
                    node.actor() == -1 ? chance * node.probabilities().get(i) : chance,
                    previous || node.actor() >= 0,
                    selected,
                    roots,
                    inside,
                    outside,
                    budget);
    }
}
