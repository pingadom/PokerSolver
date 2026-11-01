package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Assessment only; neither DTOs nor these checks confer qualification authority. */
public final class RootEnvelopePolicyChecks {
    static final String PUBLIC_SCOPE =
            "ORIGINAL_WHOLE_PUBLIC_ROOT_GAME_NOT_PRIVATE_HAND_CONDITIONED/v1";
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Check(
            int iterations,
            String solutionHash,
            MultiPlayerInformationSetBestResponse.Report fullParentQuality,
            String publicRootQualityScope,
            double publicRootReach,
            double publicRootNashConvBb,
            double actualQuestionReach,
            double counterfactualQuestionReach,
            double globalHeroBestResponseBb,
            double globalHeroUpperBb,
            double requiredSecuritySlackBb,
            double inclusionMarginBb,
            boolean insideDeclaredFace,
            boolean boundsChecked,
            Double maximumBoundViolationBb,
            Map<String, Double> actionUtilitiesBb,
            Map<String, Double> actionLossBb,
            double decisionRegretBb,
            List<String> failures) {
        public Check {
            actionUtilitiesBb = Collections.unmodifiableMap(new TreeMap<>(actionUtilitiesBb));
            actionLossBb = Collections.unmodifiableMap(new TreeMap<>(actionLossBb));
            failures = List.copyOf(failures);
        }
    }

    private RootEnvelopePolicyChecks() {}

    static List<SixMaxSuppliedIntervalFeedback.Move> moves(
            FiniteTwoPlayerRootSecurityEnvelope.Audit a, double limit) {
        SixMaxSuppliedIntervalFeedback.classify(0, 0, limit);
        if (a.actions().isEmpty()
                || a.actions().stream().map(x -> x.action()).distinct().count()
                        != a.actions().size())
            throw new IllegalArgumentException("Complete distinct envelope actions required");
        for (var x : a.actions())
            if (!Double.isFinite(x.lowerUtility())
                    || !Double.isFinite(x.upperUtility())
                    || x.lowerUtility() > x.upperUtility())
                throw new IllegalArgumentException("Finite conservative utility bounds required");
        return a.actions().stream()
                .map(
                        x -> {
                            double lower = 0, upper = 0;
                            for (var y : a.actions()) {
                                lower = Math.max(lower, y.lowerUtility() - x.upperUtility());
                                upper = Math.max(upper, y.upperUtility() - x.lowerUtility());
                            }
                            return new SixMaxSuppliedIntervalFeedback.Move(
                                    x.action(),
                                    x.lowerUtility(),
                                    x.upperUtility(),
                                    lower,
                                    upper,
                                    SixMaxSuppliedIntervalFeedback.classify(lower, upper, limit));
                        })
                .toList();
    }

    static List<String> reasons(
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Check> references) {
        var reasons = new TreeSet<String>();
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .WITHIN_LIMIT))
            reasons.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .OUTSIDE_LIMIT))
            reasons.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        primary.failures().forEach(f -> reasons.add("PRIMARY:" + f));
        for (var check : references)
            check.failures().forEach(f -> reasons.add(check.iterations() + ":" + f));
        return List.copyOf(reasons);
    }

    static Check assess(
            Checked game,
            FiniteTwoPlayerRootSecurityEnvelope.Audit a,
            CfrSolution policy,
            double limit)
            throws Exception {
        Objects.requireNonNull(policy);
        if (!FiniteTwoPlayerRootSecurityEnvelope.ALGORITHM.equals(a.algorithm())
                || !FiniteTwoPlayerRootSecurityEnvelope.SCOPE.equals(a.scope())
                || a.trainerAdmission()
                || a.securitySlack() != FiniteTwoPlayerRootActionIntervals.DEFAULT_SECURITY_SLACK
                || !SixMaxHeadsUpPreflopGame.hash(game.snapshot())
                        .equals(a.baseline().snapshotHash()))
            throw new IllegalArgumentException("Original root envelope game and scope required");
        var keys = new HashSet<String>();
        for (var info : game.infos()) {
            keys.add(info.key());
            var row = policy.strategy().get(info.key());
            if (row == null
                    || !row.keySet().equals(new HashSet<>(info.actions()))
                    || row.values().stream()
                            .anyMatch(p -> p == null || !Double.isFinite(p) || p < 0 || p > 1)
                    || Math.abs(row.values().stream().mapToDouble(Double::doubleValue).sum() - 1)
                            > 1e-9)
                throw new IllegalArgumentException(
                        "Complete normalized original policy rows required");
        }
        if (!keys.equals(policy.strategy().keySet()))
            throw new IllegalArgumentException("Foreign policy rows rejected");
        var roots = new ArrayList<Node>();
        var weights = new ArrayList<Double>();
        roots(game.snapshot().initialState(), a.question(), 1, false, roots, weights);
        double mass = weights.stream().mapToDouble(Double::doubleValue).sum();
        if (roots.isEmpty()
                || mass < FiniteTwoPlayerRootSecurityEnvelope.MINIMUM_REACH
                || Math.abs(mass - a.questionChanceMass()) > EPS
                || roots.size() != a.support().size())
            throw new IllegalArgumentException("Original positive root support required");
        var menu = roots.getFirst().actions();
        if (!menu.equals(a.actions().stream().map(x -> x.action()).toList()))
            throw new IllegalArgumentException("Complete original root menu required");
        for (int i = 0; i < roots.size(); i++) {
            var support = a.support().get(i);
            if (!menu.equals(roots.get(i).actions())
                    || support.rootIndex() != i
                    || Math.abs(support.chanceWeight() - weights.get(i)) > EPS
                    || !support.stateHash().equals(SixMaxHeadsUpPreflopGame.hash(roots.get(i))))
                throw new IllegalArgumentException("Original root world identity differs");
        }
        var moves = moves(a, limit);
        var quality = MultiPlayerInformationSetBestResponse.assess(game.snapshot(), policy);
        double global = quality.bestResponseUtilitiesBb().get(a.question().hero());
        double margin = a.globalHeroUpperValue() + a.securitySlack() - global;
        boolean included = margin >= -EPS;
        var failures = new TreeSet<String>();
        if (!included) failures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
        if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("FULL_PARENT_GAP");
        // At this public root there is no previous player action. The reached public
        // game is the original snapshot, not a game conditioned on hero's private hand.
        double publicGap = quality.nashConvBb();
        if (publicGap > SixMaxSuitDecisionStability.LOCAL_GAP_BB) failures.add("PUBLIC_ROOT_GAP");
        if (mass < FiniteTwoPlayerRootSecurityEnvelope.MINIMUM_REACH) {
            failures.add("INSUFFICIENT_ACTUAL_QUESTION_REACH");
            failures.add("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH");
        }
        var values = new TreeMap<String, Double>();
        var losses = new TreeMap<String, Double>();
        var probabilities = weights.stream().map(p -> p / mass).toList();
        for (int i = 0; i < menu.size(); i++) {
            int action = i;
            var forced =
                    new Snapshot(
                            game.snapshot().playerCount(),
                            new Node(
                                    -1,
                                    "",
                                    List.of(),
                                    probabilities,
                                    roots.stream().map(n -> n.children().get(action)).toList(),
                                    List.of()));
            values.put(
                    menu.get(i),
                    MultiPlayerInformationSetBestResponse.assess(forced, policy)
                            .bestResponseUtilitiesBb()
                            .get(a.question().hero()));
        }
        double best = Collections.max(values.values());
        values.forEach((action, value) -> losses.put(action, Math.max(0, best - value)));
        double regret =
                losses.entrySet().stream()
                        .mapToDouble(
                                e ->
                                        policy.strategy()
                                                        .get(a.question().informationSet())
                                                        .get(e.getKey())
                                                * e.getValue())
                        .sum();
        if (regret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
            failures.add("SELECTED_DECISION_REGRET");
        Double violation = null;
        if (included) {
            double worst = 0;
            for (var move : moves) {
                double value = values.get(move.action()), loss = losses.get(move.action());
                worst =
                        Math.max(
                                worst,
                                Math.max(
                                        Math.max(
                                                move.lowerEvBb() - value, value - move.upperEvBb()),
                                        Math.max(
                                                move.lowerLossBb() - loss,
                                                loss - move.upperLossBb())));
            }
            violation = worst;
            if (worst > EPS) failures.add("REFERENCE_OUTSIDE_DECISION_BOUNDS");
        }
        return new Check(
                policy.iterations(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                quality,
                PUBLIC_SCOPE,
                1,
                publicGap,
                mass,
                mass,
                global,
                a.globalHeroUpperValue(),
                a.securitySlack(),
                margin,
                included,
                included,
                violation,
                values,
                losses,
                regret,
                List.copyOf(failures));
    }

    private static void roots(
            Node n,
            FiniteTwoPlayerRootSecurityEnvelope.Question q,
            double chance,
            boolean previous,
            List<Node> roots,
            List<Double> weights) {
        if (n.actor() == q.hero() && n.key().equals(q.informationSet())) {
            if (previous)
                throw new IllegalArgumentException(
                        "Assessment requires question before every player action");
            roots.add(n);
            weights.add(chance);
        }
        for (int i = 0; i < n.children().size(); i++)
            roots(
                    n.children().get(i),
                    q,
                    n.actor() == -1 ? chance * n.probabilities().get(i) : chance,
                    previous || n.actor() >= 0,
                    roots,
                    weights);
    }
}
