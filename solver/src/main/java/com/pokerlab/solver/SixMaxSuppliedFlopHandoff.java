package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import java.util.*;

/** Owned physical support at a supplied-prior non-all-in boundary; no source-policy reach. */
public final class SixMaxSuppliedFlopHandoff {
    public static final String MODEL = "SUPPLIED_JOINT_COUNTERFACTUAL_FIXED_FLOP_CHECKDOWN/v1";
    public static final String BELIEFS =
            "ORIGINAL_JOINT_SUPPORT_CONDITIONED_ONLY_ON_PHYSICAL_FLOP/v1";
    private static final List<Seat> ORDER =
            List.of(Seat.SB, Seat.BB, Seat.UTG, Seat.HJ, Seat.CO, Seat.BTN);

    public record World(
            int originalDealIndex,
            List<String> sixDealtCombos,
            double originalJointProbability,
            double physicalFlopProbabilityGivenDeal,
            double privateGivenPhysicalFlopWeight,
            ExactDeadCardFlopShowdown.Counts counts,
            List<Double> checkdownUtilitiesBb) {
        public World {
            sixDealtCombos = List.copyOf(sixDealtCombos);
            checkdownUtilitiesBb = List.copyOf(checkdownUtilitiesBb);
            if (originalDealIndex < 0
                    || sixDealtCombos.size() != 6
                    || counts == null
                    || !(originalJointProbability > 0)
                    || originalJointProbability > 1
                    || !Double.isFinite(originalJointProbability)
                    || physicalFlopProbabilityGivenDeal
                            != 1.0 / ExactDeadCardFlopShowdown.FLOPS_PER_DEAL
                    || !(privateGivenPhysicalFlopWeight > 0)
                    || privateGivenPhysicalFlopWeight > 1
                    || !Double.isFinite(privateGivenPhysicalFlopWeight)
                    || checkdownUtilitiesBb.size() != 6
                    || checkdownUtilitiesBb.stream().anyMatch(x -> !Double.isFinite(x)))
                throw new IllegalArgumentException("Invalid supplied flop world");
        }
    }

    public record Audit(
            String model,
            String beliefs,
            String sourceHistoryReachStatus,
            boolean trainerAdmission,
            SixMaxSuppliedRangePreflopGame.Binding sourceBinding,
            String publicHistory,
            List<String> continuationActions,
            List<SixMaxPreflopBetting.Move> completePreflopHistory,
            List<String> flop,
            Seat firstToAct,
            Seat secondToAct,
            double potBb,
            List<Double> committedBb,
            List<Double> remainingStackBb,
            List<Boolean> folded,
            double compatibleOriginalPriorMass,
            double physicalFlopMass,
            long enumeratedRunouts,
            long handEvaluations,
            List<World> worlds) {
        public Audit {
            continuationActions = List.copyOf(continuationActions);
            completePreflopHistory = List.copyOf(completePreflopHistory);
            flop = ExactDeadCardFlopShowdown.canonicalFlop(flop);
            committedBb = List.copyOf(committedBb);
            remainingStackBb = List.copyOf(remainingStackBb);
            folded = List.copyOf(folded);
            worlds = List.copyOf(worlds);
            if (!MODEL.equals(model)
                    || !BELIEFS.equals(beliefs)
                    || !SixMaxSuppliedRangePreflopGame.HISTORY_REACH.equals(
                            sourceHistoryReachStatus)
                    || trainerAdmission
                    || sourceBinding == null
                    || publicHistory == null
                    || firstToAct == null
                    || secondToAct == null
                    || firstToAct == secondToAct
                    || committedBb.size() != 6
                    || remainingStackBb.size() != 6
                    || folded.size() != 6
                    || !Double.isFinite(potBb)
                    || potBb <= 0
                    || !(compatibleOriginalPriorMass > 0)
                    || compatibleOriginalPriorMass > 1 + 1e-12
                    || physicalFlopMass
                            != compatibleOriginalPriorMass
                                    / ExactDeadCardFlopShowdown.FLOPS_PER_DEAL
                    || worlds.isEmpty()
                    || enumeratedRunouts != worlds.size() * ExactDeadCardFlopShowdown.RUNOUTS
                    || handEvaluations != 2 * enumeratedRunouts)
                throw new IllegalArgumentException("Invalid supplied flop handoff identity");
        }
    }

    private final Audit audit;
    private final Map<Integer, World> byOriginalDeal;

    private SixMaxSuppliedFlopHandoff(Audit audit) {
        this.audit = audit;
        var rows = new HashMap<Integer, World>();
        for (var world : audit.worlds()) rows.put(world.originalDealIndex(), world);
        byOriginalDeal = Map.copyOf(rows);
    }

    public Audit audit() {
        return audit;
    }

    Optional<World> world(int originalDealIndex) {
        return Optional.ofNullable(byOriginalDeal.get(originalDealIndex));
    }

    public static SixMaxSuppliedFlopHandoff capture(
            SixMaxSuppliedRangePreflopGame game, List<String> actions, List<String> flop) {
        Objects.requireNonNull(game);
        actions = List.copyOf(actions);
        var physicalFlop = ExactDeadCardFlopShowdown.canonicalFlop(flop);
        if (actions.size() > FiniteTwoPlayerSequenceForm.MAX_DEPTH)
            throw new IllegalArgumentException("Bounded continuation required");
        var states = new ArrayList<State>();
        for (var prior : game.prior()) {
            var state = new State(prior.dealIndex(), "");
            for (String action : actions) state = game.afterAction(state, action);
            states.add(state);
        }
        var publicState = game.publicBettingState(states.getFirst());
        if (publicState.status() != SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED
                || publicState.liveSeats().size() != 2
                || publicState.liveSeats().stream().anyMatch(publicState::isAllIn))
            throw new IllegalArgumentException("A two-active non-all-in boundary is required");
        var first =
                ORDER.stream().filter(publicState.liveSeats()::contains).findFirst().orElseThrow();
        var second =
                publicState.liveSeats().stream().filter(s -> s != first).findFirst().orElseThrow();
        if (publicState.committedBb(first) != publicState.committedBb(second))
            throw new IllegalArgumentException("Matched commitments required");
        int mask = (1 << first.ordinal()) | (1 << second.ordinal());
        var compatible =
                states.stream()
                        .filter(
                                s ->
                                        ExactDeadCardFlopShowdown.compatible(
                                                game.dealtHands(s), physicalFlop))
                        .toList();
        if (compatible.isEmpty())
            throw new IllegalArgumentException("Flop is blocked by every supplied world");
        double support =
                compatible.stream()
                        .mapToDouble(s -> game.prior().get(s.dealIndex()).conditionalProbability())
                        .sum();
        double[] committed =
                Arrays.stream(Seat.values()).mapToDouble(publicState::committedBb).toArray();
        var worlds = new ArrayList<World>();
        for (var state : compatible) {
            var prior = game.prior().get(state.dealIndex());
            var counts =
                    ExactDeadCardFlopShowdown.enumerate(game.dealtHands(state), mask, physicalFlop);
            var settled =
                    AllInSidePots.settle(
                            committed,
                            mask,
                            0,
                            m -> {
                                if (m != mask)
                                    throw new IllegalArgumentException(
                                            "Unexpected flop showdown subset");
                                return counts.estimate();
                            },
                            CashRakeRule.none(),
                            true);
            worlds.add(
                    new World(
                            state.dealIndex(),
                            prior.dealtCombos(),
                            prior.conditionalProbability(),
                            1.0 / ExactDeadCardFlopShowdown.FLOPS_PER_DEAL,
                            prior.conditionalProbability() / support,
                            counts,
                            Arrays.stream(settled.utilitiesBb()).boxed().toList()));
        }
        return new SixMaxSuppliedFlopHandoff(
                new Audit(
                        MODEL,
                        BELIEFS,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        false,
                        game.binding(),
                        states.getFirst().publicHistory(),
                        actions,
                        publicState.history(),
                        physicalFlop,
                        first,
                        second,
                        publicState.potBb(),
                        Arrays.stream(committed).boxed().toList(),
                        Arrays.stream(Seat.values()).map(publicState::remainingStackBb).toList(),
                        Arrays.stream(Seat.values()).map(publicState::isFolded).toList(),
                        support,
                        support / ExactDeadCardFlopShowdown.FLOPS_PER_DEAL,
                        worlds.size() * ExactDeadCardFlopShowdown.RUNOUTS,
                        worlds.size() * 2 * ExactDeadCardFlopShowdown.RUNOUTS,
                        worlds));
    }
}
