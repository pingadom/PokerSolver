package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Joint extrema of decision loss on one owned, positive-reach numerical security face. */
public final class FiniteTwoPlayerConditionalDecisionLoss {
    public static final String ALGORITHM = "JOINT_CONDITIONAL_DECISION_LOSS_OWNED_LP/v1";
    public static final String SCOPE =
            "JOINT_OTHER_ACTION_MAX_MINUS_SELECTED_ACTION_MAX_CLAMPED_ZERO/v1";
    private static final double TOLERANCE = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Question(int hero, String informationSet) {
        public Question {
            new FiniteTwoPlayerConditionalActionIntervals.Question(
                    hero, informationSet, "validate");
        }
    }

    public record Action(String action, FiniteTwoPlayerConditionalActionIntervals.Audit interval) {}

    public record Witness(
            FiniteTwoPlayerConditionalActionIntervals.Witness selected,
            Map<String, Double> actionUtilities,
            double decisionLoss) {
        public Witness {
            actionUtilities = Collections.unmodifiableMap(new TreeMap<>(actionUtilities));
        }
    }

    /** Lower: selectedPlan >= 0. Upper: otherAction/otherPlan identify the comparison plan. */
    public record Control(
            int selectedPlan,
            String otherAction,
            int otherPlan,
            double objective,
            Witness witness) {}

    public record Move(
            String action,
            double lowerLoss,
            double upperLoss,
            double conservativeLowerLoss,
            double conservativeUpperLoss,
            int lowerControl,
            int upperControl,
            List<Control> lowerControls,
            List<Control> upperControls) {
        public Move {
            lowerControls = List.copyOf(lowerControls);
            upperControls = List.copyOf(upperControls);
        }
    }

    public record Audit(
            String algorithm,
            String scope,
            boolean trainerAdmission,
            Question question,
            double securitySlack,
            double minimumReach,
            String upperGapRepresentation,
            List<Action> actions,
            List<Move> moves,
            Work work) {
        public Audit {
            actions = List.copyOf(actions);
            moves = List.copyOf(moves);
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

    private record Other(String action, int index, Plan plan) {}

    private FiniteTwoPlayerConditionalDecisionLoss() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, Question question) throws Exception {
        return solve(
                game,
                question,
                DEFAULT_SECURITY_SLACK,
                FiniteTwoPlayerConditionalActionIntervals.DEFAULT_MINIMUM_REACH);
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

    // Interval reach/EV controls and every joint-loss control share these existing limits.
    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        Objects.requireNonNull(question);
        // Validate limits before consulting callbacks; no controls may enlarge old caps.
        new SequenceFormAffineProjection.Budget(compilerLimit);
        if (pivotLimit < 0
                || pivotLimit > BoundedLinearProgram.MAX_PIVOTS
                || arithmeticLimit < 0
                || arithmeticLimit > BoundedLinearProgram.MAX_ARITHMETIC_WORK
                || !Double.isFinite(securitySlack)
                || securitySlack < DEFAULT_SECURITY_SLACK
                || securitySlack > 1e-4
                || !Double.isFinite(minimumReach)
                || minimumReach < .0001
                || minimumReach > 1)
            throw new IllegalArgumentException("Invalid joint loss limits");
        var checked = checked(game);
        var info =
                checked.infos().stream()
                        .filter(
                                i ->
                                        i.actor() == question.hero()
                                                && i.key().equals(question.informationSet()))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("Unknown joint loss question"));
        var owned = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Result>();
        long compiler = 0, arithmetic = 0;
        int solves = 0, pivots = 0;
        for (String action : info.actions()) {
            var result =
                    FiniteTwoPlayerConditionalActionIntervals.solve(
                            checked.snapshot(),
                            new FiniteTwoPlayerConditionalActionIntervals.Question(
                                    question.hero(), question.informationSet(), action),
                            securitySlack,
                            minimumReach,
                            compilerLimit - compiler,
                            pivotLimit - pivots,
                            arithmeticLimit - arithmetic);
            owned.add(result);
            var work = result.audit().work();
            compiler += work.compilerUnits();
            arithmetic += work.intervalLpArithmeticWork();
            solves += work.intervalLpSolves();
            pivots += work.intervalLpPivots();
        }
        var first = owned.getFirst();
        var context = first.context();
        var shared = first.audit();
        for (var result : owned) {
            var audit = result.audit();
            if (!shared.baseline().equals(audit.baseline())
                    || !shared.denominator().equals(audit.denominator())
                    || !shared.securityFaceHash().equals(audit.securityFaceHash())
                    || !shared.scaledFaceHash().equals(audit.scaledFaceHash())
                    || !shared.heroProjection().equals(audit.heroProjection())
                    || !shared.opponentProjection().equals(audit.opponentProjection())
                    || !shared.fixedHeroPast().equals(audit.fixedHeroPast()))
                throw new IllegalStateException("Owned action controls do not share one face");
        }
        var budget = new SequenceFormAffineProjection.Budget(compilerLimit - compiler);
        var lp = new LpBudget(pivotLimit - pivots, arithmeticLimit - arithmetic);
        var actions = new ArrayList<Action>();
        for (int i = 0; i < owned.size(); i++)
            actions.add(new Action(info.actions().get(i), owned.get(i).audit()));
        var moves = new ArrayList<Move>();
        int totalPlans = actions.stream().mapToInt(a -> a.interval().numeratorPlans().size()).sum();
        int columns = context.scaled().matrix()[0].length + 1;
        for (var action : actions) {
            int selected = action.interval().numeratorPlans().size();
            if (columns + 1 > BoundedLinearProgram.MAX_VARIABLES
                    || context.scaled().rhs().length + Math.max(selected, totalPlans - selected)
                            > BoundedLinearProgram.MAX_CONSTRAINTS)
                throw new IllegalArgumentException(
                        "Joint loss LP dimensions exceed existing limits");
        }
        for (var selected : actions) {
            var others = new ArrayList<Other>();
            double otherLower = Double.NEGATIVE_INFINITY, otherUpper = Double.NEGATIVE_INFINITY;
            for (var other : actions)
                if (!other.action().equals(selected.action())) {
                    otherLower = Math.max(otherLower, other.interval().lowerUtility());
                    otherUpper = Math.max(otherUpper, other.interval().upperUtility());
                    for (int j = 0; j < other.interval().numeratorPlans().size(); j++)
                        others.add(
                                new Other(
                                        other.action(),
                                        j,
                                        other.interval().numeratorPlans().get(j)));
                }
            if (others.isEmpty()) {
                moves.add(new Move(selected.action(), 0, 0, 0, 0, -1, -1, List.of(), List.of()));
                continue;
            }
            var lowerControls = new ArrayList<Control>();
            var upperControls = new ArrayList<Control>();
            double lower = Double.POSITIVE_INFINITY, upper = Double.NEGATIVE_INFINITY;
            int lowerControl = -1, upperControl = -1;
            var plans = selected.interval().numeratorPlans();
            // min_x max(0,max_b l_b - max_j l_sj) = min_j min_x max(0,max_b(l_b-l_sj)).
            for (int j = 0; j < plans.size(); j++) {
                var problem = copy(context.scaled(), others.size(), 1, budget);
                int start = context.scaled().rhs().length;
                for (int i = 0; i < others.size(); i++) {
                    difference(
                            others.get(i).plan(),
                            plans.get(j),
                            problem.matrix()[start + i],
                            1,
                            columns - 2,
                            budget);
                    problem.matrix()[start + i][columns - 1] = -1;
                }
                double[] cost = new double[columns];
                cost[columns - 1] = -1;
                var solved = lp.solve(problem.matrix(), problem.rhs(), cost);
                double value = -solved.certificate().primalValue();
                var witness =
                        witness(context, question, selected.action(), actions, solved, budget);
                if (witness.decisionLoss() > value + TOLERANCE)
                    throw new IllegalStateException(
                            "Actual joint lower-control loss exceeds planned epigraph");
                lowerControls.add(new Control(j, "", -1, value, witness));
                if (value < lower) {
                    lower = value;
                    lowerControl = j;
                }
            }
            // max_x R_s = max(0,max_b max_x min_j(l_b-l_sj)). Keep signed gaps before clamping.
            for (var other : others) {
                var problem = copy(context.scaled(), plans.size(), 2, budget);
                int start = context.scaled().rhs().length;
                for (int j = 0; j < plans.size(); j++) {
                    difference(
                            other.plan(),
                            plans.get(j),
                            problem.matrix()[start + j],
                            -1,
                            columns - 2,
                            budget);
                    problem.matrix()[start + j][columns - 1] = 1;
                    problem.matrix()[start + j][columns] = -1;
                }
                double[] cost = new double[columns + 1];
                cost[columns - 1] = 1;
                cost[columns] = -1;
                var solved = lp.solve(problem.matrix(), problem.rhs(), cost);
                double value = solved.certificate().primalValue();
                var witness =
                        witness(context, question, selected.action(), actions, solved, budget);
                if (witness.decisionLoss() + TOLERANCE < value)
                    throw new IllegalStateException(
                            "Actual joint upper-control loss below planned gap");
                upperControls.add(new Control(-1, other.action(), other.index(), value, witness));
                if (value > upper) {
                    upper = value;
                    upperControl = upperControls.size() - 1;
                }
            }
            lower = Math.max(0, lower);
            upper = Math.max(0, upper);
            requireClose(
                    lower,
                    lowerControls.get(lowerControl).witness().decisionLoss(),
                    "Joint lower endpoint witness");
            requireClose(
                    upper,
                    upperControls.get(upperControl).witness().decisionLoss(),
                    "Joint upper endpoint witness");
            double conservativeLower = Math.max(0, otherLower - selected.interval().upperUtility());
            double conservativeUpper = Math.max(0, otherUpper - selected.interval().lowerUtility());
            if (!Double.isFinite(lower)
                    || !Double.isFinite(upper)
                    || lower > upper + TOLERANCE
                    || lower < conservativeLower - TOLERANCE
                    || upper > conservativeUpper + TOLERANCE)
                throw new IllegalStateException(
                        "Joint loss interval is inconsistent with action bounds");
            for (var control : lowerControls) checkInside(control.witness(), lower, upper);
            for (var control : upperControls) checkInside(control.witness(), lower, upper);
            moves.add(
                    new Move(
                            selected.action(),
                            Math.min(lower, upper),
                            upper,
                            conservativeLower,
                            conservativeUpper,
                            lowerControl,
                            upperControl,
                            lowerControls,
                            upperControls));
        }
        return new Result(
                new Audit(
                        ALGORITHM,
                        SCOPE,
                        false,
                        question,
                        securitySlack,
                        minimumReach,
                        "SIGNED_DIFFERENCE_OF_TWO_NONNEGATIVE_VARIABLES/v1",
                        actions,
                        moves,
                        new Work(
                                compiler + budget.audit().chargedUnits(),
                                compilerLimit,
                                solves + lp.solves,
                                pivots + lp.pivots,
                                arithmetic + lp.work)));
    }

    private static void checkInside(Witness witness, double lower, double upper) {
        if (witness.decisionLoss() < lower - TOLERANCE
                || witness.decisionLoss() > upper + TOLERANCE)
            throw new IllegalStateException("Joint witness lies outside endpoint controls");
    }

    private static Face copy(
            Face scaled, int extra, int auxiliaries, SequenceFormAffineProjection.Budget budget) {
        int rows = scaled.rhs().length + extra, columns = scaled.matrix()[0].length + auxiliaries;
        budget.charge((long) rows * columns + rows + columns);
        var matrix = new double[rows][columns];
        var rhs = new double[rows];
        for (int i = 0; i < scaled.rhs().length; i++) {
            System.arraycopy(scaled.matrix()[i], 0, matrix[i], 0, columns - auxiliaries);
            rhs[i] = scaled.rhs()[i];
        }
        return new Face(matrix, rhs, scaled.opponentVariables());
    }

    private static void difference(
            Plan other,
            Plan selected,
            double[] row,
            double sign,
            int scalingIndex,
            SequenceFormAffineProjection.Budget budget) {
        budget.charge(other.cost().size() + 1);
        for (int j = 0; j < other.cost().size(); j++)
            row[j] = sign * (other.cost().get(j) - selected.cost().get(j));
        row[scalingIndex] = sign * (other.constant() - selected.constant());
    }

    private static Witness witness(
            FiniteTwoPlayerConditionalActionIntervals.Context context,
            Question question,
            String selected,
            List<Action> actions,
            BoundedLinearProgram.Result solved,
            SequenceFormAffineProjection.Budget budget)
            throws Exception {
        var utilities = new TreeMap<String, Double>();
        FiniteTwoPlayerConditionalActionIntervals.Witness chosen = null;
        for (var action : actions) {
            var audit = action.interval();
            var checked =
                    FiniteTwoPlayerConditionalActionIntervals.witness(
                            context.checked(),
                            context.roots(),
                            new FiniteTwoPlayerConditionalActionIntervals.Question(
                                    question.hero(), question.informationSet(), action.action()),
                            context.baseline(),
                            context.opponent(),
                            context.projection(),
                            solved,
                            true,
                            audit.globalHeroUpperValue() + audit.securitySlack(),
                            audit.requiredMinimumReach(),
                            audit.denominator(),
                            audit.numeratorPlans(),
                            budget);
            utilities.put(action.action(), checked.conditionalHeroBestResponse());
            if (action.action().equals(selected)) chosen = checked;
        }
        double best = Collections.max(utilities.values());
        return new Witness(
                Objects.requireNonNull(chosen),
                utilities,
                Math.max(0, best - utilities.get(selected)));
    }
}
