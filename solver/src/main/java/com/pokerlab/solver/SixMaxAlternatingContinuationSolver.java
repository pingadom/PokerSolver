package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Keeps the last audited policy; a failed whole round cannot replace it. */
public final class SixMaxAlternatingContinuationSolver {
    public record Settings(
            int maximumRounds,
            int preflopIterations,
            int postflopIterations,
            double conditionalGapTargetBb,
            double minimumParentImprovementBb) {
        public Settings {
            if (maximumRounds < 1
                    || maximumRounds > 3
                    || preflopIterations < 1
                    || preflopIterations > 3000
                    || postflopIterations < 1
                    || postflopIterations > 500)
                throw new IllegalArgumentException(
                        "Require 1–3 rounds, 1–3000 preflop and 1–500 postflop iterations");
            SixMaxAlternatingQualityGate.assess(
                    0, 0, 0, true, conditionalGapTargetBb, minimumParentImprovementBb);
        }
    }

    public record Audit(
            String solutionHash,
            SixMaxConnectedPreflopAudit.Quality parentQuality,
            double maximumConditionalGapBb,
            boolean everySelectedBranchReached,
            SixMaxReachedContinuationStudy.Reach reach) {}

    public record Round(
            int number,
            String acceptedInputHash,
            SixMaxPreflopContinuationFeedback.Report preflopFeedback,
            double maximumConditionalGapAfterPreflopBb,
            SixMaxConditionalPostflopRefinement.Report postflopRefinement,
            Audit candidateAudit,
            SixMaxAlternatingQualityGate.Decision decision,
            String retainedSolutionHash) {}

    public record Report(
            Settings settings,
            Audit initialAudit,
            Audit retainedAudit,
            int acceptedRounds,
            String stopReason,
            List<Round> rounds) {
        public Report {
            rounds = List.copyOf(rounds);
        }
    }

    public record Result(CfrSolution retainedPolicy, Report report) {}

    private SixMaxAlternatingContinuationSolver() {}

    public static Audit audit(
            SixMaxConnectedPreflopGame game,
            CfrSolution policy,
            SixMaxContinuationStudyBudget budget) {
        Objects.requireNonNull(budget, "budget").validate(game);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, policy, budget.maximumCompleteTreeStates());
        if (complete.addedInformationSets() != 0)
            throw new IllegalArgumentException(
                    "Alternating rounds require an explicitly completed policy");
        var conditional = SixMaxConnectedPreflopAudit.conditionalPostflop(game, policy);
        int expected = game.selections().stream().mapToInt(s -> s.flops().size()).sum();
        return new Audit(
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, policy)),
                conditional.stream().mapToDouble(b -> b.bestResponse().gap()).max().orElse(0),
                conditional.size() == expected,
                SixMaxReachedContinuationStudy.reach(game, policy));
    }

    public static Result solve(
            SixMaxConnectedPreflopGame game,
            CfrSolution initial,
            Settings settings,
            SixMaxContinuationStudyBudget budget,
            Consumer<String> progress,
            BiConsumer<CfrSolution, Round> accepted) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(accepted, "accepted");
        var initialAudit = audit(game, initial, budget);
        if (!initialAudit.everySelectedBranchReached()
                || initialAudit.maximumConditionalGapBb() > settings.conditionalGapTargetBb())
            throw new IllegalArgumentException(
                    "Initial retained policy must meet the conditional target on every selected branch");
        var retained = initial;
        var retainedAudit = initialAudit;
        var rounds = new ArrayList<Round>();
        int acceptedRounds = 0;
        String stopReason = "ROUND_LIMIT_REACHED";
        for (int n = 1; n <= settings.maximumRounds(); n++) {
            final int number = n;
            progress.accept("round=" + n + " stage=PREFLOP_FEEDBACK");
            var feedback =
                    SixMaxPreflopContinuationFeedback.solve(
                            game, retained, settings.preflopIterations(), budget);
            progress.accept("round=" + n + " stage=POSTFLOP_AT_CHANGED_RANGES");
            var post =
                    SixMaxConditionalPostflopRefinement.refine(
                            game,
                            feedback.candidate(),
                            settings.postflopIterations(),
                            budget,
                            b ->
                                    progress.accept(
                                            "round="
                                                    + number
                                                    + " flop="
                                                    + b.flop()
                                                    + " status="
                                                    + b.status()
                                                    + " gap="
                                                    + (b.after() == null
                                                            ? "n/a"
                                                            : b.after().gap())));
            double maximum =
                    post.report().branches().stream()
                            .filter(b -> b.after() != null)
                            .mapToDouble(b -> b.after().gap())
                            .max()
                            .orElse(0);
            boolean allReached =
                    post.report().branches().stream().allMatch(b -> b.status().equals("REFINED"));
            var candidateAudit =
                    new Audit(
                            post.report().candidateSolutionHash(),
                            post.report().candidateQuality(),
                            maximum,
                            allReached,
                            feedback.report().candidateReach());
            var decision =
                    SixMaxAlternatingQualityGate.assess(
                            retainedAudit.parentQuality().nashConvBb(),
                            candidateAudit.parentQuality().nashConvBb(),
                            maximum,
                            allReached,
                            settings.conditionalGapTargetBb(),
                            settings.minimumParentImprovementBb());
            var round =
                    new Round(
                            n,
                            retainedAudit.solutionHash(),
                            feedback.report(),
                            feedback.report().candidateConditionalPostflop().stream()
                                    .mapToDouble(b -> b.bestResponse().gap())
                                    .max()
                                    .orElse(0),
                            post.report(),
                            candidateAudit,
                            decision,
                            decision.accepted()
                                    ? candidateAudit.solutionHash()
                                    : retainedAudit.solutionHash());
            rounds.add(round);
            progress.accept(
                    "round="
                            + n
                            + " decision="
                            + decision.status()
                            + " retained_hash="
                            + round.retainedSolutionHash());
            if (!decision.accepted()) {
                stopReason = decision.status();
                break;
            }
            retained = post.candidate();
            retainedAudit = candidateAudit;
            acceptedRounds++;
            accepted.accept(retained, round);
        }
        return new Result(
                retained,
                new Report(
                        settings, initialAudit, retainedAudit, acceptedRounds, stopReason, rounds));
    }
}
