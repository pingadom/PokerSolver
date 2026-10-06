package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded deterministic proposals under a fixed policy; never remaps postflop rows or trains. */
public final class SixMaxContinuationMenuSearch {
    public record Settings(long firstFlopSeed, int seedCount, int histories, int flopsPerHistory) {
        public Settings {
            if (seedCount < 1
                    || seedCount > 16
                    || histories < 1
                    || histories > 4
                    || flopsPerHistory < 1
                    || flopsPerHistory > 4)
                throw new IllegalArgumentException(
                        "Search requires 1–16 seeds, 1–4 histories and 1–4 flops per history");
            Math.addExact(firstFlopSeed, seedCount - 1L);
        }
    }

    public record Attempt(
            long flopSeed,
            String status,
            String budgetResource,
            Long required,
            Long limit,
            SixMaxReachedContinuationStudy.SelectionAudit selectionAudit,
            SixMaxContinuationStudyBudget.Cost cost,
            SixMaxRetainedContinuationCoverage.Report coverage) {}

    public record Report(
            String interpretation,
            String preflopPolicyHash,
            Settings settings,
            SixMaxContinuationStudyBudget budget,
            SixMaxRetainedContinuationCoverage.Settings coverageSettings,
            String status,
            double headsUpProbability,
            double highestHistoryProbability,
            double requestedLastHistoryProbability,
            double maximumHeadsUpFractionWithRequestedHistories,
            List<String> feasibilityFailures,
            List<Attempt> attempts,
            List<SixMaxConnectedPreflopGame.Selection> proposedSelections) {
        public Report {
            feasibilityFailures = List.copyOf(feasibilityFailures);
            attempts = List.copyOf(attempts);
            proposedSelections = List.copyOf(proposedSelections);
        }
    }

    private SixMaxContinuationMenuSearch() {}

    public static Report search(
            SixMaxPreflopCheckdownGame source,
            CfrSolution policy,
            Settings settings,
            SixMaxContinuationStudyBudget budget,
            SixMaxRetainedContinuationCoverage.Settings coverageSettings) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(coverageSettings, "coverageSettings");
        if (coverageSettings.minimumMaterialCombosPerActiveSeat() < 2)
            throw new IllegalArgumentException(
                    "Diverse-pair search requires at least two material hands per active seat");
        if (source.chanceOutcomes(source.initialState()).size()
                > SixMaxConnectedPreflopGame.MAX_PRIVATE_DEALS)
            throw new IllegalArgumentException("Menu search exceeds connected private-deal limit");
        var preflop = SixMaxPreflopContinuationFeedback.preflopPolicy(policy);
        if (MultiPlayerStrategyCompletion.uniformAtUnseen(source, preflop, 200_000)
                        .addedInformationSets()
                != 0)
            throw new IllegalArgumentException("Menu search requires complete preflop rows");
        var audit =
                SixMaxPreflopContinuationAudit.assess(
                        source, preflop, settings.firstFlopSeed(), 20);
        double highest =
                audit.examples().isEmpty() ? 0 : audit.examples().getFirst().reachProbability();
        double last =
                audit.examples().size() < settings.histories()
                        ? 0
                        : audit.examples().get(settings.histories() - 1).reachProbability();
        double topMass =
                audit.examples().stream()
                        .limit(settings.histories())
                        .mapToDouble(SixMaxPreflopContinuationAudit.Example::reachProbability)
                        .sum();
        double upperFraction =
                audit.headsUpContinuationProbability() == 0
                        ? 0
                        : Math.min(1, topMass / audit.headsUpContinuationProbability());
        var failures = new ArrayList<String>();
        if (last < coverageSettings.minimumHistoryProbability())
            failures.add("INSUFFICIENT_MATERIAL_HISTORY_REACH");
        if (upperFraction < coverageSettings.minimumHeadsUpFraction())
            failures.add("INSUFFICIENT_POSSIBLE_HEADS_UP_COVERAGE");
        var attempts = new ArrayList<Attempt>();
        List<SixMaxConnectedPreflopGame.Selection> proposed = List.of();
        if (failures.isEmpty()) {
            for (int index = 0; index < settings.seedCount(); index++) {
                long seed = settings.firstFlopSeed() + index;
                try {
                    var plan =
                            SixMaxReachedContinuationStudy.select(
                                    source,
                                    preflop,
                                    settings.histories(),
                                    settings.flopsPerHistory(),
                                    seed,
                                    budget,
                                    SixMaxReachedContinuationStudy.SelectionSettings.diverse(
                                            coverageSettings.minimumComboMass()));
                    var coverage =
                            SixMaxRetainedContinuationCoverage.assess(
                                    plan.game(), preflop, coverageSettings);
                    attempts.add(
                            new Attempt(
                                    seed,
                                    coverage.criteriaMet() ? "MENU_FOUND" : "CONTENT_REJECTED",
                                    null,
                                    null,
                                    null,
                                    plan.selectionAudit(),
                                    budget.validate(plan.game()),
                                    coverage));
                    if (coverage.criteriaMet()) {
                        proposed = plan.game().selections();
                        break;
                    }
                } catch (SixMaxReachedContinuationStudy.NoEligibleMenu missing) {
                    attempts.add(
                            new Attempt(
                                    seed,
                                    "NO_DIVERSE_MENU",
                                    null,
                                    null,
                                    null,
                                    missing.audit(),
                                    null,
                                    null));
                } catch (SixMaxContinuationStudyBudget.Exceeded exceeded) {
                    attempts.add(
                            new Attempt(
                                    seed,
                                    "BUDGET_REJECTED",
                                    exceeded.resource(),
                                    exceeded.required(),
                                    exceeded.limit(),
                                    null,
                                    null,
                                    null));
                }
            }
        }
        return new Report(
                "Offline bounded greedy menu proposal under a fixed preflop policy. "
                        + "The reach bound uses the most reached histories, ignoring diversity and cost, so it is an upper bound. "
                        + "Flop seeds cannot change public-history reach. A failed finite seed/candidate window is not a proof that no menu exists. "
                        + "A proposed menu changes the game: it requires a new solve and new quality/coverage checks; "
                        + "old postflop rows and parent quality scores cannot be transferred. No private worlds are pruned.",
                SixMaxConnectedPostflopAudit.solutionHash(preflop),
                settings,
                budget,
                coverageSettings,
                !failures.isEmpty()
                        ? "FEASIBILITY_FAILED"
                        : proposed.isEmpty() ? "NO_FIT_IN_SEARCH_WINDOW" : "MENU_FOUND",
                audit.headsUpContinuationProbability(),
                highest,
                last,
                upperFraction,
                failures,
                attempts,
                proposed);
    }
}
