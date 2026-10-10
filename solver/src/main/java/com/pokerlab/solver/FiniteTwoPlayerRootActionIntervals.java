package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/**
 * Conditional root-action utility over an explicitly approximate opponent security face. The
 * opponent must remain globally optimal within the reported slack; hero may choose one complete
 * continuation per information set. This diagnostic never grants trainer admission. Later questions
 * are deliberately unsupported: their opponent-dependent posterior is not linear.
 */
public final class FiniteTwoPlayerRootActionIntervals {
    public static final String ALGORITHM = "ROOT_ACTION_APPROXIMATE_SECURITY_FACE_OWNED_LP/v1";
    public static final String SCOPE =
            "ROOT_CHANCE_POSTERIOR_OPTIMAL_HERO_CONTINUATION_TERMINAL_UTILITY/v1";
    public static final int MAX_CONTINUATION_PLANS = 64;
    public static final double DEFAULT_SECURITY_SLACK = 1e-8;
    private static final double TOLERANCE = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    /** informationSet includes the actor prefix, exactly as in the owned solver audit. */
    public record Question(int hero, String informationSet, String action) {
        public Question {
            if (hero < 0
                    || informationSet == null
                    || action == null
                    || !informationSet.startsWith(hero + ":")
                    || action.isBlank())
                throw new IllegalArgumentException("Invalid root interval question");
        }
    }

    public record Plan(Map<String, String> continuation, double constant, List<Double> cost) {
        public Plan {
            continuation = Collections.unmodifiableMap(new TreeMap<>(continuation));
            cost = List.copyOf(cost);
        }
    }

    /**
     * Original opponent flow, independently checked behavioral security and conditional response.
     */
    public record Witness(
            BoundedLinearProgram.Certificate certificate,
            List<Double> point,
            List<Double> dual,
            FlowAudit opponentFlow,
            String behavioralPolicyHash,
            double globalHeroBestResponse,
            double conditionalHeroBestResponse) {
        public Witness {
            point = List.copyOf(point);
            dual = List.copyOf(dual);
        }
    }

    public record Work(
            long compilerUnits,
            long compilerLimit,
            int intervalLpSolves,
            int intervalLpPivots,
            long intervalLpArithmeticWork) {}

    public record Audit(
            String algorithm,
            String scope,
            boolean trainerAdmission,
            Question question,
            FiniteTwoPlayerAffineSequenceForm.Audit baseline,
            double questionChanceMass,
            double securitySlack,
            double globalHeroUpperValue,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit heroProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit opponentProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit orientedPayoff,
            String securityFaceHash,
            List<Plan> plans,
            double lowerUtility,
            double upperUtility,
            int upperPlan,
            Witness lowerWitness,
            List<Witness> upperWitnesses,
            Work work) {
        public Audit {
            plans = List.copyOf(plans);
            upperWitnesses = List.copyOf(upperWitnesses);
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

    private record Root(Node node, double probability) {}

    private record Face(double[][] matrix, double[] rhs, int opponentVariables) {}

    private FiniteTwoPlayerRootActionIntervals() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, Question question) throws Exception {
        return solve(game, question, DEFAULT_SECURITY_SLACK);
    }

    public static <S> Result solve(
            MultiPlayerCfrGame<S> game, Question question, double securitySlack) throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    // Test controls only lower existing caps. Baseline solve retains its separate existing budgets.
    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        Objects.requireNonNull(question);
        if (!Double.isFinite(securitySlack)
                || securitySlack < DEFAULT_SECURITY_SLACK
                || securitySlack > 1e-4
                || pivotLimit < 0
                || pivotLimit > BoundedLinearProgram.MAX_PIVOTS
                || arithmeticLimit < 0
                || arithmeticLimit > BoundedLinearProgram.MAX_ARITHMETIC_WORK)
            throw new IllegalArgumentException("Invalid root interval limits or security slack");
        var budget = new SequenceFormAffineProjection.Budget(compilerLimit);
        var checked = checked(game);
        if (question.hero() != checked.firstActor() && question.hero() != checked.secondActor())
            throw new IllegalArgumentException("Question must belong to an active actor");
        var roots = new ArrayList<Root>();
        collectRoots(checked.snapshot().initialState(), question, 1, roots, budget);
        double mass = roots.stream().mapToDouble(Root::probability).sum();
        if (!Double.isFinite(mass) || mass <= 0)
            throw new IllegalArgumentException("Question must precede every player action");
        var forest = new ArrayList<Root>();
        for (var root : roots) {
            int index = root.node().actions().indexOf(question.action());
            if (index < 0) throw new IllegalArgumentException("Illegal root action");
            forest.add(new Root(root.node().children().get(index), root.probability() / mass));
        }
        var continuations = new TreeMap<String, List<String>>();
        for (var root : forest)
            collectContinuations(root.node(), question.hero(), continuations, budget);
        long count = 1;
        for (var actions : continuations.values()) {
            count *= actions.size();
            if (count > MAX_CONTINUATION_PLANS)
                throw new IllegalArgumentException("Root continuation plan budget exceeded");
        }
        // Precharge bounded traversal work before allocating the pure-plan product.
        budget.charge(count * checked.treeNodes());
        var choices = new ArrayList<Map<String, String>>();
        enumerate(new ArrayList<>(continuations.entrySet()), 0, new TreeMap<>(), choices);
        var baseline = FiniteTwoPlayerAffineSequenceForm.solve(checked.snapshot());
        boolean first = question.hero() == checked.firstActor();
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
        double[][] oriented = new double[hero.sequences().size()][opponent.sequences().size()];
        orientedPayoff(
                checked.snapshot().initialState(), hero, opponent, 0, 0, 1, oriented, budget);
        var payoff = SequenceFormAffineProjection.reduce(oriented, hp, op, budget);
        var face = face(payoff, hp.audit(), op.audit(), globalUpper + securitySlack, budget);
        var plans = new ArrayList<Plan>();
        for (var choice : choices) {
            double[] coefficients = new double[opponent.sequences().size()];
            for (var root : forest)
                planPayoff(
                        root.node(),
                        hero.actor(),
                        opponent,
                        choice,
                        0,
                        root.probability(),
                        coefficients);
            budget.charge((long) coefficients.length * (op.variables() + 1));
            double constant = 0;
            double[] cost = new double[op.variables()];
            for (int i = 0; i < coefficients.length; i++) {
                constant += coefficients[i] * op.audit().offset().get(i);
                for (int j = 0; j < cost.length; j++)
                    cost[j] += coefficients[i] * op.audit().transform().get(i).get(j);
            }
            plans.add(new Plan(choice, constant, Arrays.stream(cost).boxed().toList()));
        }
        var conditional =
                new Snapshot(
                        checked.snapshot().playerCount(),
                        new Node(
                                -1,
                                "",
                                List.of(),
                                forest.stream().map(Root::probability).toList(),
                                forest.stream().map(Root::node).toList(),
                                List.of()));
        var lpBudget = new LpBudget(pivotLimit, arithmeticLimit);
        var upperWitnesses = new ArrayList<Witness>();
        double upper = Double.NEGATIVE_INFINITY;
        int upperPlan = -1;
        for (int i = 0; i < plans.size(); i++) {
            var plan = plans.get(i);
            double[] cost = new double[face.matrix()[0].length];
            for (int j = 0; j < op.variables(); j++) cost[j] = plan.cost().get(j);
            var solved = lpBudget.solve(face.matrix(), face.rhs(), cost);
            var witness =
                    witness(
                            checked,
                            conditional,
                            baseline,
                            opponent,
                            op,
                            solved,
                            globalUpper + securitySlack,
                            budget,
                            plans);
            double value = plan.constant() + solved.certificate().primalValue();
            requireClose(value, evaluate(plan, solved.point()), "Upper plan objective");
            if (witness.conditionalHeroBestResponse() + TOLERANCE < value)
                throw new IllegalStateException("Conditional best response below selected plan");
            upperWitnesses.add(witness);
            if (value > upper) {
                upper = value;
                upperPlan = i;
            }
        }
        // Epigraph v=utility+shift is nonnegative even when every continuation loses chips.
        double shift = maximumUtility(checked.snapshot().initialState(), hero.actor()) + 1;
        int columns = face.matrix()[0].length + 1, rows = face.rhs().length + plans.size();
        budget.charge((long) rows * columns + rows + columns);
        double[][] matrix = new double[rows][columns];
        double[] rhs = new double[rows], cost = new double[columns];
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
        var lowerLp = lpBudget.solve(matrix, rhs, cost);
        double lower = -lowerLp.certificate().primalValue() - shift;
        var lowerWitness =
                witness(
                        checked,
                        conditional,
                        baseline,
                        opponent,
                        op,
                        lowerLp,
                        globalUpper + securitySlack,
                        budget,
                        plans);
        requireClose(lower, lowerWitness.conditionalHeroBestResponse(), "Lower epigraph witness");
        requireClose(
                upper,
                upperWitnesses.get(upperPlan).conditionalHeroBestResponse(),
                "Upper witness");
        for (var witness : upperWitnesses)
            if (witness.conditionalHeroBestResponse() > upper + TOLERANCE)
                throw new IllegalStateException("Witness exceeds certified global upper interval");
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper + TOLERANCE)
            throw new IllegalStateException("Invalid root utility interval");
        // Preserve interval ordering when a constant action differs only by final-bit roundoff.
        lower = Math.min(lower, upper);
        var work = budget.audit();
        return new Result(
                new Audit(
                        ALGORITHM,
                        SCOPE,
                        false,
                        question,
                        baseline.audit(),
                        mass,
                        securitySlack,
                        globalUpper,
                        hp.audit(),
                        op.audit(),
                        payoff,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(face),
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
                                lpBudget.work)));
    }

    private static void collectRoots(
            Node node,
            Question q,
            double probability,
            List<Root> roots,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(1);
        if (node.actor() == -1) {
            for (int i = 0; i < node.children().size(); i++)
                collectRoots(
                        node.children().get(i),
                        q,
                        probability * node.probabilities().get(i),
                        roots,
                        budget);
        } else if (node.actor() == q.hero()
                && node.key().equals(q.informationSet())
                && probability > 0) roots.add(new Root(node, probability));
    }

    private static void collectContinuations(
            Node node,
            int hero,
            Map<String, List<String>> infos,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(1);
        if (node.actor() == hero) infos.put(node.key(), node.actions());
        for (var child : node.children()) collectContinuations(child, hero, infos, budget);
    }

    private static void enumerate(
            List<Map.Entry<String, List<String>>> infos,
            int index,
            TreeMap<String, String> choice,
            List<Map<String, String>> result) {
        if (index == infos.size()) {
            result.add(Map.copyOf(choice));
            return;
        }
        var info = infos.get(index);
        for (String action : info.getValue()) {
            choice.put(info.getKey(), action);
            enumerate(infos, index + 1, choice, result);
        }
        choice.remove(info.getKey());
    }

    private static void orientedPayoff(
            Node node,
            Flow hero,
            Flow opponent,
            int hs,
            int os,
            double probability,
            double[][] payoff,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(1);
        if (node.actor() == -2) {
            payoff[hs][os] += probability * node.utilities().get(hero.actor());
            return;
        }
        for (int i = 0; i < node.children().size(); i++) {
            int h = hs, o = os;
            double p = probability;
            if (node.actor() == -1) p *= node.probabilities().get(i);
            else if (node.actor() == hero.actor())
                h = hero.indices().get(new OwnAction(node.key(), node.actions().get(i)));
            else o = opponent.indices().get(new OwnAction(node.key(), node.actions().get(i)));
            orientedPayoff(node.children().get(i), hero, opponent, h, o, p, payoff, budget);
        }
    }

    private static void planPayoff(
            Node node,
            int hero,
            Flow opponent,
            Map<String, String> plan,
            int os,
            double probability,
            double[] payoff) {
        if (node.actor() == -2) {
            payoff[os] += probability * node.utilities().get(hero);
            return;
        }
        if (node.actor() == hero) {
            int action = node.actions().indexOf(plan.get(node.key()));
            planPayoff(node.children().get(action), hero, opponent, plan, os, probability, payoff);
        } else
            for (int i = 0; i < node.children().size(); i++) {
                int o = os;
                double p = probability;
                if (node.actor() == -1) p *= node.probabilities().get(i);
                else o = opponent.indices().get(new OwnAction(node.key(), node.actions().get(i)));
                planPayoff(node.children().get(i), hero, opponent, plan, o, p, payoff);
            }
    }

    private static Face face(
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit payoff,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit hero,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit opponent,
            double upper,
            SequenceFormAffineProjection.Budget budget) {
        int w = opponent.independentSequences().size(), z = hero.independentSequences().size();
        int dual = hero.rhs().size(), rows = z + opponent.rhs().size() + 1, columns = w + dual;
        if (columns + 1 > BoundedLinearProgram.MAX_VARIABLES
                || rows + MAX_CONTINUATION_PLANS > BoundedLinearProgram.MAX_CONSTRAINTS)
            throw new IllegalArgumentException("Root interval LP dimensions exceed budget");
        budget.charge((long) rows * columns + rows);
        double[][] matrix = new double[rows][columns];
        double[] rhs = new double[rows];
        // Dw-P'mu<=-a, Qw<=q, b.w+p.mu<=upper-c0.
        for (int i = 0; i < z; i++) {
            for (int j = 0; j < w; j++) matrix[i][j] = payoff.matrix().get(i).get(j);
            for (int j = 0; j < dual; j++) matrix[i][w + j] = -hero.inequalities().get(j).get(i);
            rhs[i] = -payoff.firstCost().get(i);
        }
        for (int i = 0; i < opponent.rhs().size(); i++) {
            for (int j = 0; j < w; j++) matrix[z + i][j] = opponent.inequalities().get(i).get(j);
            rhs[z + i] = opponent.rhs().get(i);
        }
        for (int j = 0; j < w; j++) matrix[rows - 1][j] = payoff.secondCost().get(j);
        for (int j = 0; j < dual; j++) matrix[rows - 1][w + j] = hero.rhs().get(j);
        rhs[rows - 1] = upper - payoff.constant();
        return new Face(matrix, rhs, w);
    }

    private static Witness witness(
            Checked checked,
            Snapshot conditional,
            FiniteTwoPlayerAffineSequenceForm.Result baseline,
            Flow opponent,
            SequenceFormAffineProjection.Projection projection,
            BoundedLinearProgram.Result solved,
            double upper,
            SequenceFormAffineProjection.Budget budget,
            List<Plan> plans)
            throws Exception {
        var strategy = new LinkedHashMap<>(baseline.strategy());
        var flow =
                behavior(
                        opponent,
                        SequenceFormAffineProjection.realization(projection, solved, budget),
                        strategy);
        var policy = new CfrSolution(1, strategy);
        int hero =
                opponent.actor() == checked.firstActor()
                        ? checked.secondActor()
                        : checked.firstActor();
        double global =
                MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy)
                        .bestResponseUtilitiesBb()
                        .get(hero);
        double local =
                MultiPlayerInformationSetBestResponse.assess(conditional, policy)
                        .bestResponseUtilitiesBb()
                        .get(hero);
        if (!Double.isFinite(global) || global > upper + TOLERANCE)
            throw new IllegalStateException("Behavioral witness violates global security face");
        double compiled =
                plans.stream()
                        .mapToDouble(plan -> evaluate(plan, solved.point()))
                        .max()
                        .orElseThrow();
        requireClose(compiled, local, "Independent conditional information-set response");
        return new Witness(
                solved.certificate(),
                solved.point(),
                solved.dual(),
                flow,
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                global,
                local);
    }

    private static double evaluate(Plan plan, List<Double> point) {
        double value = plan.constant();
        for (int i = 0; i < plan.cost().size(); i++) value += plan.cost().get(i) * point.get(i);
        return value;
    }

    private static double maximumUtility(Node node, int hero) {
        if (node.actor() == -2) return Math.abs(node.utilities().get(hero));
        return node.children().stream()
                .mapToDouble(child -> maximumUtility(child, hero))
                .max()
                .orElseThrow();
    }

    private static void requireClose(double expected, double actual, String detail) {
        if (!Double.isFinite(expected)
                || !Double.isFinite(actual)
                || Math.abs(expected - actual) > TOLERANCE)
            throw new IllegalStateException(detail + " failed");
    }

    private static final class LpBudget {
        final int pivotLimit;
        final long workLimit;
        int solves, pivots;
        long work;

        LpBudget(int pivotLimit, long workLimit) {
            this.pivotLimit = pivotLimit;
            this.workLimit = workLimit;
        }

        BoundedLinearProgram.Result solve(double[][] matrix, double[] rhs, double[] cost)
                throws Exception {
            var result =
                    BoundedLinearProgram.solve(
                            matrix, rhs, cost, pivotLimit - pivots, workLimit - work);
            solves++;
            pivots += result.certificate().work().pivots();
            work += result.certificate().work().arithmeticWork();
            return result;
        }
    }
}
