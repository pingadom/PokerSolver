package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Trains independent fixed-opponent responses, selects a candidate on validation deals, then
 * evaluates the preselected candidate on a separate confirmation stream. The other confirmation
 * scores describe seed/budget sensitivity; they must not be used to reselect a winner.
 */
public final class PhysicalResponseStabilityAudit {
    public enum PolicyVariant {
        AVERAGE,
        FINAL_REGRET
    }

    private record Draft(
            int budget,
            long seed,
            PolicyVariant variant,
            FixedOpponentResponseCfr.Result trained,
            CfrSolution policy,
            PhysicalResponseHeldOutAudit.Report validation) {}

    public record Candidate(
            int responseIterations,
            long responseSeed,
            PolicyVariant variant,
            int learnedInformationSets,
            long missingOpponentQueries,
            int missingOpponentInformationSets,
            PhysicalResponseHeldOutAudit.Report validation,
            PhysicalResponseHeldOutAudit.Report confirmation) {}

    public record Report(
            String gameHash,
            int targetPlayer,
            long validationSeed,
            long confirmationSeed,
            int selectedIndex,
            List<Candidate> candidates) {
        public Candidate selected() {
            return candidates.get(selectedIndex);
        }
    }

    private PhysicalResponseStabilityAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution baseline,
            int target,
            List<Integer> responseBudgets,
            List<Long> responseSeeds,
            int validationTrials,
            int confirmationTrials,
            long validationSeed,
            long confirmationSeed) {
        return assess(
                game,
                baseline,
                target,
                responseBudgets,
                responseSeeds,
                validationTrials,
                confirmationTrials,
                validationSeed,
                confirmationSeed,
                List.of(PolicyVariant.AVERAGE));
    }

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution baseline,
            int target,
            List<Integer> responseBudgets,
            List<Long> responseSeeds,
            int validationTrials,
            int confirmationTrials,
            long validationSeed,
            long confirmationSeed,
            List<PolicyVariant> variants) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(responseBudgets, "responseBudgets");
        Objects.requireNonNull(responseSeeds, "responseSeeds");
        Objects.requireNonNull(variants, "variants");
        if (target != 0 && target != 1)
            throw new IllegalArgumentException("Target player must be 0 or 1");
        if (responseBudgets.isEmpty() || responseSeeds.isEmpty() || variants.isEmpty())
            throw new IllegalArgumentException(
                    "Expected nonempty response budgets, seeds and variants");
        if ((long) responseBudgets.size() * responseSeeds.size() * variants.size() > 12)
            throw new IllegalArgumentException("At most 12 response candidates are supported");
        if (new HashSet<>(responseBudgets).size() != responseBudgets.size()
                || new HashSet<>(responseSeeds).size() != responseSeeds.size()
                || new HashSet<>(variants).size() != variants.size())
            throw new IllegalArgumentException(
                    "Response budgets, seeds and variants must be distinct");
        if (variants.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Response variants cannot be null");
        if (validationSeed == confirmationSeed
                || responseSeeds.contains(validationSeed)
                || responseSeeds.contains(confirmationSeed))
            throw new IllegalArgumentException(
                    "Training, validation and confirmation seeds differ");
        if (validationTrials < 2
                || validationTrials > 1_000_000
                || confirmationTrials < 2
                || confirmationTrials > 1_000_000)
            throw new IllegalArgumentException("Each evaluation needs 2-1000000 trials");
        for (Integer budget : responseBudgets) {
            if (budget == null || budget < 1 || budget > 1_000_000)
                throw new IllegalArgumentException("Response budgets must be in 1-1000000");
        }
        if (responseSeeds.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Response seeds cannot be null");

        List<Draft> drafts = new ArrayList<>();
        int selectedIndex = -1;
        double bestValidationGain = Double.NEGATIVE_INFINITY;
        for (int budget : responseBudgets) {
            for (long seed : responseSeeds) {
                var trained =
                        new FixedOpponentResponseCfr<>(game, baseline, target, seed).solve(budget);
                for (var variant : variants) {
                    var policy =
                            variant == PolicyVariant.AVERAGE
                                    ? trained.response()
                                    : trained.finalRegretPolicy();
                    var validation =
                            PhysicalResponseHeldOutAudit.assess(
                                    game,
                                    baseline,
                                    policy,
                                    target,
                                    validationTrials,
                                    validationSeed);
                    // Selection is frozen before the confirmation stream is evaluated.
                    if (validation.responseGainBb() > bestValidationGain) {
                        bestValidationGain = validation.responseGainBb();
                        selectedIndex = drafts.size();
                    }
                    drafts.add(new Draft(budget, seed, variant, trained, policy, validation));
                }
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        for (var draft : drafts) {
            var confirmation =
                    PhysicalResponseHeldOutAudit.assess(
                            game,
                            baseline,
                            draft.policy(),
                            target,
                            confirmationTrials,
                            confirmationSeed);
            candidates.add(
                    new Candidate(
                            draft.budget(),
                            draft.seed(),
                            draft.variant(),
                            draft.policy().strategy().size(),
                            draft.trained().missingFixedOpponentQueries(),
                            draft.trained().missingFixedOpponentInformationSets(),
                            draft.validation(),
                            confirmation));
        }
        return new Report(
                game.contentHash(),
                target,
                validationSeed,
                confirmationSeed,
                selectedIndex,
                List.copyOf(candidates));
    }
}
