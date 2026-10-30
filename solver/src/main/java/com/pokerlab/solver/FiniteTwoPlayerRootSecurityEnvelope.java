package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Conservative root action bounds using a certified complete pure hero policy guarantee. */
public final class FiniteTwoPlayerRootSecurityEnvelope {
    public static final String ALGORITHM =
            "ROOT_FIXED_PURE_POLICY_GLOBAL_SECURITY_OUTER_ENVELOPE/v1";
    public static final String SCOPE =
            "CONSERVATIVE_OPTIMAL_CONTINUATION_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS/v1";
    public static final String POLICY = "OWNED_BASELINE_GREEDY_COMPLETE_HERO_POLICY/v1";
    public static final String CONTROL_POLICY = "EXPLICIT_COMPLETE_PURE_HERO_POLICY_CONTROL/v1";

    public static final class Rejected extends IllegalArgumentException {
        private Rejected(String detail, Throwable cause) {
            super(detail, cause);
        }
    }

    public static final double MINIMUM_REACH = .0001;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Question(int hero, String informationSet) {
        public Question {
            if (hero < 0 || informationSet == null || !informationSet.startsWith(hero + ":"))
                throw new IllegalArgumentException("Actor-prefixed root information set required");
        }
    }

    public record Linear(double constant, List<Double> cost) {
        public Linear {
            cost = List.copyOf(cost);
        }
    }

    public record Support(int rootIndex, double chanceWeight, String stateHash) {}

    public record Witness(
            String domain,
            BoundedLinearProgram.Certificate certificate,
            List<Double> point,
            List<Double> dual,
            FlowAudit opponentFlow,
            String behavioralPolicyHash,
            double globalFixedHeroUtility,
            double questionFixedHeroUtility,
            double evaluatedFixedPolicyUtility,
            double optimalContinuationUtility,
            double globalHeroBestResponse) {
        public Witness {
            point = List.copyOf(point);
            dual = List.copyOf(dual);
        }
    }

    public record Action(
            String action,
            Linear fixedContinuation,
            double lowerUtility,
            double upperUtility,
            double literalUtilityCeiling,
            Witness lowerWitness) {}

    public record Work(
            long compilerUnits,
            long compilerLimit,
            int lpSolves,
            int lpPivots,
            long lpArithmeticWork) {}

    public record Audit(
            String algorithm,
            String scope,
            boolean trainerAdmission,
            Question question,
            FiniteTwoPlayerAffineSequenceForm.Audit baseline,
            String fixedPolicyAlgorithm,
            Map<String, String> fixedHeroChoices,
            List<Support> support,
            double questionChanceMass,
            double securitySlack,
            double globalHeroUpperValue,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit heroProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit opponentProjection,
            FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit orientedPayoff,
            Linear globalFixedPolicy,
            Linear questionFixedPolicy,
            Witness guaranteeWitness,
            Witness questionUpperWitness,
            double guaranteedGlobalLower,
            double fixedQuestionUpper,
            double securityAllowance,
            double commonActionUpper,
            List<Action> actions,
            Work work) {
        public Audit {
            fixedHeroChoices = Collections.unmodifiableMap(new TreeMap<>(fixedHeroChoices));
            support = List.copyOf(support);
            actions = List.copyOf(actions);
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

    private record Root(Node node, double mass) {}

    private FiniteTwoPlayerRootSecurityEnvelope() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, Question question) throws Exception {
        try {
            return solve(
                    game,
                    question,
                    null,
                    FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                    BoundedLinearProgram.MAX_PIVOTS,
                    BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        } catch (IllegalStateException failure) {
            throw new Rejected(
                    "Root envelope verification failed: " + failure.getMessage(), failure);
        }
    }

    // Diagnostic controls may supply a different complete pure policy or lower work limits.
    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question q,
            Map<String, String> supplied,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        Objects.requireNonNull(q);
        if (pivotLimit < 0
                || pivotLimit > BoundedLinearProgram.MAX_PIVOTS
                || arithmeticLimit < 0
                || arithmeticLimit > BoundedLinearProgram.MAX_ARITHMETIC_WORK)
            throw new IllegalArgumentException(
                    "Only reduced original LP work limits are permitted");
        var budget = new SequenceFormAffineProjection.Budget(compilerLimit);
        var checked = checked(game);
        if (q.hero() != checked.firstActor() && q.hero() != checked.secondActor())
            throw new IllegalArgumentException("Active root actor required");
        var roots = new ArrayList<Root>();
        var inside = new HashSet<String>();
        var outside = new HashSet<String>();
        scope(
                checked.snapshot().initialState(),
                q,
                1,
                false,
                false,
                roots,
                inside,
                outside,
                budget);
        double mass = roots.stream().mapToDouble(Root::mass).sum();
        if (!Double.isFinite(mass) || mass < MINIMUM_REACH || mass > 1 + EPS || roots.isEmpty())
            throw new IllegalArgumentException("Positive original-prior root reach required");
        if (inside.stream().anyMatch(outside::contains))
            throw new IllegalArgumentException(
                    "Root deviation shares hero information with another observation");
        var baseline =
                FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(
                        checked.snapshot());
        boolean first = q.hero() == checked.firstActor();
        var hero = first ? checked.firstFlow() : checked.secondFlow();
        var opponent = first ? checked.secondFlow() : checked.firstFlow();
        var choices = new TreeMap<String, String>();
        for (var info : hero.infos()) {
            String best = info.actions().getFirst();
            for (String a : info.actions())
                if (baseline.strategy().get(info.key()).get(a)
                        > baseline.strategy().get(info.key()).get(best)) best = a;
            choices.put(info.key(), best);
        }
        if (supplied != null) {
            if (!supplied.keySet().equals(choices.keySet()))
                throw new IllegalArgumentException("Complete hero-only pure policy required");
            for (var info : hero.infos())
                if (!info.actions().contains(supplied.get(info.key())))
                    throw new IllegalArgumentException("Illegal fixed hero action");
            choices = new TreeMap<>(supplied);
        }
        var hp = SequenceFormAffineProjection.build(hero.sequences(), hero.conservation(), budget);
        var op =
                SequenceFormAffineProjection.build(
                        opponent.sequences(), opponent.conservation(), budget);
        budget.charge((long) hero.sequences().size() * opponent.sequences().size());
        var original = new double[hero.sequences().size()][opponent.sequences().size()];
        orientedPayoff(
                checked.snapshot().initialState(), hero, opponent, 0, 0, 1, original, budget);
        var payoff = SequenceFormAffineProjection.reduce(original, hp, op, budget);
        double upper =
                first
                        ? baseline.audit().upperValue()
                        : checked.constantSum() - baseline.audit().lowerValue();
        double slack = DEFAULT_SECURITY_SLACK;
        var face = face(payoff, hp.audit(), op.audit(), upper + slack, budget);
        var global =
                linear(
                        List.of(new Root(checked.snapshot().initialState(), 1)),
                        choices,
                        hero.actor(),
                        opponent,
                        op.audit(),
                        budget);
        var normalized = roots.stream().map(r -> new Root(r.node(), r.mass() / mass)).toList();
        var conditional = linear(normalized, choices, hero.actor(), opponent, op.audit(), budget);
        var lp = LpBudget.phaseOneRefinedBasis(pivotLimit, arithmeticLimit);
        double[][] all =
                op.audit().inequalities().stream()
                        .map(row -> row.stream().mapToDouble(Number::doubleValue).toArray())
                        .toArray(double[][]::new);
        double[] allRhs = op.audit().rhs().stream().mapToDouble(Number::doubleValue).toArray();
        var guarantee = lp.solve(all, allRhs, cost(global, op.variables(), -1));
        double lower = global.constant() - guarantee.certificate().dualValue() - EPS;
        double gap = upper + slack - lower;
        if (!Double.isFinite(gap) || gap < 0)
            throw new IllegalStateException("Invalid certified global security allowance");
        var qUpper =
                lp.solve(face.matrix(), face.rhs(), cost(conditional, face.matrix()[0].length, 1));
        double fixedUpper = conditional.constant() + qUpper.certificate().dualValue() + EPS;
        double allowance = gap / mass, common = fixedUpper + allowance;
        var qSnapshot = snapshot(checked, normalized);
        var gw =
                witness(
                        "ALL_OPPONENT_FLOWS",
                        checked,
                        hero,
                        opponent,
                        op,
                        choices,
                        guarantee,
                        global,
                        checked.snapshot(),
                        qSnapshot,
                        upper + slack,
                        budget);
        var qw =
                witness(
                        "FULL_OPPONENT_SECURITY_FACE",
                        checked,
                        hero,
                        opponent,
                        op,
                        choices,
                        qUpper,
                        conditional,
                        qSnapshot,
                        qSnapshot,
                        upper + slack,
                        budget);
        if (qw.optimalContinuationUtility() > common + EPS)
            throw new IllegalStateException("Global envelope fails independent root response");
        if (qw.globalFixedHeroUtility()
                        + mass * (qw.optimalContinuationUtility() - qw.questionFixedHeroUtility())
                > qw.globalHeroBestResponse() + EPS)
            throw new IllegalStateException(
                    "Root deviation cannot be composed into the complete game");
        var actions = new ArrayList<Action>();
        for (String action : roots.getFirst().node().actions()) {
            var forced =
                    normalized.stream()
                            .map(
                                    r ->
                                            new Root(
                                                    r.node()
                                                            .children()
                                                            .get(
                                                                    r.node()
                                                                            .actions()
                                                                            .indexOf(action)),
                                                    r.mass()))
                            .toList();
            var form = linear(forced, choices, hero.actor(), opponent, op.audit(), budget);
            var solved =
                    lp.solve(face.matrix(), face.rhs(), cost(form, face.matrix()[0].length, -1));
            double lo = form.constant() - solved.certificate().dualValue() - EPS;
            double ceiling =
                    forced.stream()
                            .mapToDouble(r -> ceiling(r.node(), hero.actor()))
                            .max()
                            .orElseThrow();
            double hi = Math.min(common, ceiling + EPS);
            var w =
                    witness(
                            "FULL_OPPONENT_SECURITY_FACE",
                            checked,
                            hero,
                            opponent,
                            op,
                            choices,
                            solved,
                            form,
                            snapshot(checked, forced),
                            qSnapshot,
                            upper + slack,
                            budget);
            if (!Double.isFinite(lo)
                    || !Double.isFinite(hi)
                    || lo > hi
                    || w.optimalContinuationUtility() < lo - EPS
                    || w.optimalContinuationUtility() > hi + EPS)
                throw new IllegalStateException(
                        "Conservative action interval failed independent continuation checks");
            if (w.globalFixedHeroUtility()
                            + mass * (w.optimalContinuationUtility() - w.questionFixedHeroUtility())
                    > w.globalHeroBestResponse() + EPS)
                throw new IllegalStateException(
                        "Forced root deviation cannot be composed into the complete game");
            actions.add(new Action(action, form, lo, hi, ceiling, w));
        }
        var support = new ArrayList<Support>();
        for (int i = 0; i < roots.size(); i++)
            support.add(
                    new Support(
                            i,
                            roots.get(i).mass(),
                            SixMaxHeadsUpPreflopGame.hash(roots.get(i).node())));
        var work = budget.audit();
        return new Result(
                new Audit(
                        ALGORITHM,
                        SCOPE,
                        false,
                        q,
                        baseline.audit(),
                        supplied == null ? POLICY : CONTROL_POLICY,
                        choices,
                        support,
                        mass,
                        slack,
                        upper,
                        hp.audit(),
                        op.audit(),
                        payoff,
                        global,
                        conditional,
                        gw,
                        qw,
                        lower,
                        fixedUpper,
                        allowance,
                        common,
                        actions,
                        new Work(
                                work.chargedUnits(), work.limit(), lp.solves, lp.pivots, lp.work)));
    }

    private static void scope(
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
        boolean match = n.actor() == q.hero() && n.key().equals(q.informationSet());
        if (match) {
            if (previous)
                throw new IllegalArgumentException("Question must precede every player action");
            selected = true;
            roots.add(new Root(n, chance));
        }
        if (n.actor() == q.hero()) (selected ? inside : outside).add(n.key());
        for (int i = 0; i < n.children().size(); i++)
            scope(
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

    private static Linear linear(
            List<Root> roots,
            Map<String, String> choices,
            int hero,
            Flow opponent,
            FiniteTwoPlayerAffineSequenceForm.ProjectionAudit projection,
            SequenceFormAffineProjection.Budget budget) {
        var coefficients = new double[opponent.sequences().size()];
        for (var r : roots)
            fixed(r.node(), hero, opponent, choices, 0, r.mass(), coefficients, budget);
        budget.charge((long) coefficients.length * (projection.independentSequences().size() + 1));
        double c = 0;
        var cost = new double[projection.independentSequences().size()];
        for (int i = 0; i < coefficients.length; i++) {
            c += coefficients[i] * projection.offset().get(i);
            for (int j = 0; j < cost.length; j++)
                cost[j] += coefficients[i] * projection.transform().get(i).get(j);
        }
        return new Linear(c, Arrays.stream(cost).boxed().toList());
    }

    private static void fixed(
            Node n,
            int hero,
            Flow opponent,
            Map<String, String> choices,
            int sequence,
            double probability,
            double[] coefficients,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(1);
        if (n.actor() == -2) {
            coefficients[sequence] += probability * n.utilities().get(hero);
            return;
        }
        if (n.actor() == hero) {
            int a = n.actions().indexOf(choices.get(n.key()));
            if (a < 0) throw new IllegalArgumentException("Missing complete hero choice");
            fixed(
                    n.children().get(a),
                    hero,
                    opponent,
                    choices,
                    sequence,
                    probability,
                    coefficients,
                    budget);
        } else
            for (int i = 0; i < n.children().size(); i++)
                fixed(
                        n.children().get(i),
                        hero,
                        opponent,
                        choices,
                        n.actor() == -1
                                ? sequence
                                : opponent.indices()
                                        .get(new OwnAction(n.key(), n.actions().get(i))),
                        n.actor() == -1 ? probability * n.probabilities().get(i) : probability,
                        coefficients,
                        budget);
    }

    private static double[] cost(Linear linear, int width, int sign) {
        var result = new double[width];
        for (int i = 0; i < linear.cost().size(); i++) result[i] = sign * linear.cost().get(i);
        return result;
    }

    private static Snapshot snapshot(Checked checked, List<Root> roots) {
        return new Snapshot(
                checked.snapshot().playerCount(),
                new Node(
                        -1,
                        "",
                        List.of(),
                        roots.stream().map(Root::mass).toList(),
                        roots.stream().map(Root::node).toList(),
                        List.of()));
    }

    private static double ceiling(Node n, int hero) {
        return n.actor() == -2
                ? n.utilities().get(hero)
                : n.children().stream().mapToDouble(c -> ceiling(c, hero)).max().orElseThrow();
    }

    private static Witness witness(
            String domain,
            Checked checked,
            Flow hero,
            Flow opponent,
            SequenceFormAffineProjection.Projection op,
            Map<String, String> choices,
            BoundedLinearProgram.Result solved,
            Linear form,
            Snapshot evaluated,
            Snapshot question,
            double upper,
            SequenceFormAffineProjection.Budget budget)
            throws Exception {
        var strategy = new LinkedHashMap<String, Map<String, Double>>();
        for (var info : hero.infos()) {
            var row = new LinkedHashMap<String, Double>();
            for (String a : info.actions()) row.put(a, a.equals(choices.get(info.key())) ? 1. : 0.);
            strategy.put(info.key(), row);
        }
        budget.charge((long) opponent.sequences().size() * (op.variables() + 1));
        var original =
                Arrays.stream(
                                op.reconstruct(
                                        solved.point().subList(0, op.variables()).stream()
                                                .mapToDouble(Double::doubleValue)
                                                .toArray()))
                        .boxed()
                        .toList();
        var flow = behavior(opponent, original, strategy, budget);
        var policy = new CfrSolution(1, strategy);
        double global =
                MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), policy)
                        .bestResponseUtilitiesBb()
                        .get(hero.actor());
        if (domain.equals("FULL_OPPONENT_SECURITY_FACE") && global > upper + EPS)
            throw new IllegalStateException("Envelope witness violates original security face");
        double value = MultiPlayerStrategyEvaluator.utilities(evaluated, policy)[hero.actor()];
        double linear = form.constant();
        for (int i = 0; i < form.cost().size(); i++)
            linear += form.cost().get(i) * solved.point().get(i);
        requireClose(linear, value, "Fixed complete-policy objective");
        double fixedGlobal =
                MultiPlayerStrategyEvaluator.utilities(checked.snapshot(), policy)[hero.actor()];
        double fixedQuestion =
                MultiPlayerStrategyEvaluator.utilities(question, policy)[hero.actor()];
        double optimal =
                MultiPlayerInformationSetBestResponse.assess(evaluated, policy)
                        .bestResponseUtilitiesBb()
                        .get(hero.actor());
        if (optimal < value - EPS)
            throw new IllegalStateException("Fixed policy exceeds optimal continuation");
        return new Witness(
                domain,
                solved.certificate(),
                solved.point(),
                solved.dual(),
                flow,
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                fixedGlobal,
                fixedQuestion,
                value,
                optimal,
                global);
    }

    // Original-flow check without a conditional posterior or a small-parent ratio fallback.
    private static FlowAudit behavior(
            Flow flow,
            List<Double> original,
            Map<String, Map<String, Double>> strategy,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(original.size());
        var point = new ArrayList<Double>();
        for (double x : original) {
            if (!Double.isFinite(x) || x < -EPS || x > 1 + EPS)
                throw new IllegalStateException("Invalid original envelope flow");
            point.add(Math.max(0, x));
        }
        double residual = Math.abs(original.getFirst() - 1);
        var rows = new LinkedHashMap<String, Map<String, Double>>();
        for (var row : flow.conservation()) {
            budget.charge(row.childSequences().size() + 1L);
            double total = 0, signed = 0;
            for (int i : row.childSequences()) {
                total += point.get(i);
                signed += original.get(i);
            }
            residual =
                    Math.max(
                            residual,
                            Math.max(
                                    Math.abs(signed - original.get(row.parentSequence())),
                                    Math.abs(total - point.get(row.parentSequence()))));
            var probabilities = new LinkedHashMap<String, Double>();
            for (int i : row.childSequences())
                probabilities.put(
                        flow.sequences().get(i).action(),
                        total > 0 ? point.get(i) / total : 1. / row.childSequences().size());
            rows.put(row.key(), probabilities);
        }
        double difference = Math.abs(original.getFirst() - 1);
        for (var info : flow.infos()) {
            budget.charge(info.ownHistory().size() + info.actions().size() + 1L);
            double p = 1;
            for (var past : info.ownHistory()) p *= rows.get(past.key()).get(past.action());
            for (String a : info.actions())
                difference =
                        Math.max(
                                difference,
                                Math.abs(
                                        p * rows.get(info.key()).get(a)
                                                - original.get(
                                                        flow.indices()
                                                                .get(
                                                                        new OwnAction(
                                                                                info.key(), a)))));
        }
        if (residual > EPS || difference > EPS)
            throw new IllegalStateException("Envelope behavior changes original realization");
        strategy.putAll(rows);
        return new FlowAudit(flow.actor(), flow.sequences(), flow.conservation(), point, residual);
    }
}
