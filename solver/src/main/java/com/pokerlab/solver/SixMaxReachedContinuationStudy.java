package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Selects reached heads-up histories, without removing counterfactual private support. */
public final class SixMaxReachedContinuationStudy {
    public enum SelectionMode {
        REACH_FIRST,
        DIVERSE_ACTIVE_PAIRS
    }

    public record SelectionSettings(
            SelectionMode mode, int candidateLimit, double minimumComboMass) {
        public SelectionSettings {
            java.util.Objects.requireNonNull(mode, "mode");
            if (candidateLimit < 1 || candidateLimit > 20)
                throw new IllegalArgumentException("Candidate limit must be 1–20");
            if (!Double.isFinite(minimumComboMass)
                    || (mode == SelectionMode.DIVERSE_ACTIVE_PAIRS
                            ? minimumComboMass <= 0 || minimumComboMass > .5
                            : minimumComboMass != 0))
                throw new IllegalArgumentException(
                        "Diverse selection requires a combo mass in (0,.5]");
        }

        public static SelectionSettings reachFirst(int histories) {
            return new SelectionSettings(SelectionMode.REACH_FIRST, histories, 0);
        }

        public static SelectionSettings diverse(double minimumComboMass) {
            return new SelectionSettings(SelectionMode.DIVERSE_ACTIVE_PAIRS, 20, minimumComboMass);
        }
    }

    public record FlopHandMass(
            List<String> flop, Map<String, Double> first, Map<String, Double> second) {
        public FlopHandMass {
            flop = List.copyOf(flop);
            first = Map.copyOf(first);
            second = Map.copyOf(second);
        }
    }

    public record CandidateAssessment(
            int sourceReachRank,
            List<PublicAction> history,
            Seat firstToAct,
            Seat secondToAct,
            double sourceReachProbability,
            String disposition,
            List<FlopHandMass> flops) {
        public CandidateAssessment {
            history = List.copyOf(history);
            flops = List.copyOf(flops);
        }
    }

    public record SelectionAudit(SelectionSettings settings, List<CandidateAssessment> considered) {
        public SelectionAudit {
            considered = List.copyOf(considered);
        }
    }

    public record Reach(
            Map<String, Double> terminalProbability,
            Map<Integer, Double> checkdownProbabilityByLiveSeats,
            int reachedHeadsUpHistories,
            double headsUpProbability,
            double selectedHistoryProbability,
            double unselectedHeadsUpProbability,
            double fractionOfHeadsUpProbabilitySelected,
            double selectedPhysicalFlopProbability,
            double selectedHistoryOtherFlopsProbability) {
        public Reach {
            terminalProbability = Map.copyOf(terminalProbability);
            checkdownProbabilityByLiveSeats = Map.copyOf(checkdownProbabilityByLiveSeats);
        }
    }

    public record SelectedHistory(
            int sourceReachRank,
            double sourceReachProbability,
            int sourcePosteriorJointDeals,
            SixMaxConnectedPreflopGame.Coverage coverage) {}

    public record Plan(
            SixMaxConnectedPreflopGame game,
            long flopSelectionSeed,
            int compatibleDealFlops,
            Reach sourceReach,
            List<SelectedHistory> selectedHistories,
            SelectionAudit selectionAudit) {
        public Plan {
            selectedHistories = List.copyOf(selectedHistories);
        }
    }

    private SixMaxReachedContinuationStudy() {}

    public static Plan select(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            int maximumHistories,
            long flopSeed) {
        return select(
                base,
                source,
                maximumHistories,
                1,
                flopSeed,
                SixMaxContinuationStudyBudget.standard());
    }

    /** Nested, unique physical-flop menus; width one preserves the preceding study exactly. */
    public static Plan select(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            int maximumHistories,
            int flopsPerHistory,
            long flopSeed,
            SixMaxContinuationStudyBudget budget) {
        return select(
                base,
                source,
                maximumHistories,
                flopsPerHistory,
                flopSeed,
                budget,
                SelectionSettings.reachFirst(maximumHistories));
    }

    /** Diversity changes only the public menu; it never trims hidden private worlds. */
    public static Plan select(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            int maximumHistories,
            int flopsPerHistory,
            long flopSeed,
            SixMaxContinuationStudyBudget budget,
            SelectionSettings settings) {
        if (maximumHistories < 1 || maximumHistories > 4)
            throw new IllegalArgumentException("Select 1–4 reached histories");
        if (flopsPerHistory < 1 || flopsPerHistory > 4)
            throw new IllegalArgumentException("Select 1–4 physical flops per history");
        java.util.Objects.requireNonNull(budget, "budget");
        java.util.Objects.requireNonNull(settings, "settings");
        if (settings.candidateLimit() < maximumHistories)
            throw new IllegalArgumentException(
                    "Candidate limit must cover the requested histories");
        if (base.chanceOutcomes(base.initialState()).size()
                > SixMaxConnectedPreflopGame.MAX_PRIVATE_DEALS)
            throw new IllegalArgumentException(
                    "Connected study supports at most eight private deals");
        if (MultiPlayerStrategyCompletion.uniformAtUnseen(base, source, 200_000)
                        .addedInformationSets()
                != 0)
            throw new IllegalArgumentException(
                    "History selection requires a complete source policy");
        var audit =
                SixMaxPreflopContinuationAudit.assess(
                        base, source, flopSeed, settings.candidateLimit());
        if (audit.examples().isEmpty())
            throw new IllegalArgumentException("Source has no reached heads-up continuation");
        var selections = new ArrayList<SixMaxConnectedPreflopGame.Selection>();
        int pairs = 0;
        var chosenRanks = new ArrayList<Integer>();
        var assessments = new ArrayList<CandidateAssessment>();
        var activePairs = new java.util.HashSet<List<Seat>>();
        for (int rank = 0; rank < audit.examples().size(); rank++) {
            if (selections.size() == maximumHistories) break;
            var example = audit.examples().get(rank);
            var activePair = List.of(example.firstToAct(), example.secondToAct());
            if (settings.mode() == SelectionMode.DIVERSE_ACTIVE_PAIRS
                    && activePairs.contains(activePair)) {
                assessments.add(
                        new CandidateAssessment(
                                rank + 1,
                                example.history(),
                                example.firstToAct(),
                                example.secondToAct(),
                                example.reachProbability(),
                                "DUPLICATE_ACTIVE_PAIR",
                                List.of()));
                continue;
            }
            var support = SixMaxPolicyFlopTransition.counterfactualSupport(base, example.history());
            var boards = new ArrayList<List<Card>>();
            boards.add(example.sampledFlop().stream().map(Card::parse).toList());
            var posterior = new SixMaxPolicyFlopTransition(base, source, example.history());
            long historySeed = flopSeed + rank;
            for (int attempt = 1; boards.size() < flopsPerHistory; attempt++) {
                if (attempt > 10_000)
                    throw new IllegalStateException("Could not draw a unique physical-flop menu");
                var board =
                        posterior.sampleFlop(historySeed + attempt * 0x9e3779b97f4a7c15L).board();
                if (!boards.contains(board)) boards.add(board);
            }
            var masses = new ArrayList<FlopHandMass>();
            boolean material = true;
            for (var board : boards) {
                var conditioned = posterior.conditionOnFlop(board);
                var first = marginal(conditioned.deals(), example.firstToAct());
                var second = marginal(conditioned.deals(), example.secondToAct());
                masses.add(
                        new FlopHandMass(
                                board.stream().map(Card::compact).toList(), first, second));
                if (settings.mode() == SelectionMode.DIVERSE_ACTIVE_PAIRS
                        && (first.values().stream()
                                                .filter(p -> p >= settings.minimumComboMass())
                                                .count()
                                        < 2
                                || second.values().stream()
                                                .filter(p -> p >= settings.minimumComboMass())
                                                .count()
                                        < 2)) material = false;
            }
            assessments.add(
                    new CandidateAssessment(
                            rank + 1,
                            example.history(),
                            example.firstToAct(),
                            example.secondToAct(),
                            example.reachProbability(),
                            material ? "SELECTED" : "INSUFFICIENT_ACTIVE_HAND_MASS",
                            masses));
            if (!material) continue;
            activePairs.add(activePair);
            chosenRanks.add(rank);
            for (var board : boards) {
                pairs += support.conditionOnFlop(board).deals().size();
                budget.requireCompatiblePairs(pairs);
            }
            double flopBet = Math.min(support.potBb() * .5, support.remainingStackBb());
            double turnBet = (support.potBb() + 2 * flopBet) * .5;
            double riverBet =
                    (support.potBb()
                                    + 2 * flopBet
                                    + 2 * Math.min(turnBet, support.remainingStackBb() - flopBet))
                            * .5;
            selections.add(
                    new SixMaxConnectedPreflopGame.Selection(
                            example.history(), boards, flopBet, turnBet, riverBet));
        }
        if (settings.mode() == SelectionMode.DIVERSE_ACTIVE_PAIRS
                && selections.size() != maximumHistories)
            throw new IllegalArgumentException(
                    "Not enough distinct active pairs with material hands in the declared candidate window");
        var game = new SixMaxConnectedPreflopGame(base, selections);
        budget.validate(game);
        var selected = new ArrayList<SelectedHistory>();
        for (int rank : chosenRanks) {
            var example = audit.examples().get(rank);
            var coverage =
                    game.coverage().stream()
                            .filter(c -> c.actions().equals(example.history()))
                            .findFirst()
                            .orElseThrow();
            selected.add(
                    new SelectedHistory(
                            rank + 1,
                            example.reachProbability(),
                            example.posteriorJointDeals(),
                            coverage));
        }
        return new Plan(
                game,
                flopSeed,
                pairs,
                reach(game, source, audit),
                selected,
                new SelectionAudit(settings, assessments));
    }

    private static Map<String, Double> marginal(
            List<SixMaxPolicyFlopTransition.JointDeal> deals, Seat seat) {
        var weights = new java.util.LinkedHashMap<String, Double>();
        for (var deal : deals)
            weights.merge(deal.hands().get(seat.ordinal()).key(), deal.probability(), Double::sum);
        return Map.copyOf(weights);
    }

    public static Reach reach(SixMaxConnectedPreflopGame game, CfrSolution policy) {
        return reach(
                game, policy, SixMaxPreflopContinuationAudit.assess(game.source(), policy, 0, 1));
    }

    private static Reach reach(
            SixMaxConnectedPreflopGame game,
            CfrSolution policy,
            SixMaxPreflopContinuationAudit.Report audit) {
        double selected =
                game.selections().stream()
                        .mapToDouble(s -> SixMaxConnectedPreflopAudit.historyReach(game, policy, s))
                        .sum();
        double physical = SixMaxConnectedPreflopAudit.bettingProbability(game, policy);
        double headsUp = audit.headsUpContinuationProbability();
        if (selected > headsUp + 1e-10 || physical > selected + 1e-10)
            throw new IllegalStateException("Selected reach exceeds its parent reach");
        return new Reach(
                audit.terminalProbability(),
                audit.checkdownProbabilityByLiveSeats(),
                audit.reachedHeadsUpHistories(),
                headsUp,
                selected,
                Math.max(0, headsUp - selected),
                headsUp == 0 ? 0 : selected / headsUp,
                physical,
                Math.max(0, selected - physical));
    }
}
