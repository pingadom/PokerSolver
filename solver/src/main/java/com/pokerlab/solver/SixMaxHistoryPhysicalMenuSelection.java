package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.Selection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Deterministic frozen-policy density heuristic, not a quality or optimality certificate. */
public final class SixMaxHistoryPhysicalMenuSelection {
    public static final String ALGORITHM = "FROZEN_REACH_PER_COUNTERFACTUAL_STATE_GREEDY/v1";
    public static final int MAX_EVIDENCE_BYTES = 256 * 1024;
    public static final double HISTORY_THRESHOLD = .0001, COMBO_THRESHOLD = .05;

    public record Evidence(
            String algorithm,
            String publicationStatus,
            String sourcePackHash,
            String parentRankTableHash,
            String frozenPreflopPolicyHash,
            String menuHash,
            double requestedAllHeadsUpFraction,
            double selectedMaterialWholeGameReach,
            double allHeadsUpProbability,
            double selectedMaterialAllHeadsUpFraction,
            SixMaxHistoryPhysicalPayoffTable.Sizing sizing,
            SixMaxHistoryPhysicalPayoffTable.Menu menu) {
        public Evidence {
            if (!ALGORITHM.equals(algorithm) || !"VALIDATION_ONLY".equals(publicationStatus))
                throw new IllegalArgumentException("Unsupported menu selection identity");
            for (var hash :
                    List.of(sourcePackHash, parentRankTableHash, frozenPreflopPolicyHash, menuHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid menu evidence hash");
            for (double p :
                    List.of(
                            requestedAllHeadsUpFraction,
                            selectedMaterialWholeGameReach,
                            allHeadsUpProbability,
                            selectedMaterialAllHeadsUpFraction))
                if (!Double.isFinite(p) || p <= 0 || p > 1 + 1e-12)
                    throw new IllegalArgumentException("Invalid menu evidence reach");
            Objects.requireNonNull(menu, "menu");
            Objects.requireNonNull(sizing, "sizing");
            if (selectedMaterialWholeGameReach > allHeadsUpProbability + 1e-12
                    || selectedMaterialAllHeadsUpFraction < requestedAllHeadsUpFraction
                    || Math.abs(
                                    selectedMaterialWholeGameReach / allHeadsUpProbability
                                            - selectedMaterialAllHeadsUpFraction)
                            > 1e-12
                    || sizing.histories() != menu.selections().size())
                throw new IllegalArgumentException("Inconsistent menu selection evidence");
        }
    }

    private record Board(List<String> cards, int rank, int worlds) {}

    private record Candidate(int history, Board board, double reach) {
        long rawStates() {
            return 9L * Integer.bitCount(board.worlds());
        }
    }

    private SixMaxHistoryPhysicalMenuSelection() {}

    public static Evidence select(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            CfrSolution reference,
            List<Selection> selections,
            double requestedFraction)
            throws Exception {
        if (!Double.isFinite(requestedFraction) || requestedFraction <= 0 || requestedFraction > 1)
            throw new IllegalArgumentException("Positive coverage fraction required");
        // Constructing the coarse game checks that every selected history is a valid HU leaf.
        var base = new SixMaxRankTextureFlopGame(source, parent, selections);
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(reference);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        base.sourceGame(), pre, SixMaxOneBetFlopGame.MAX_COMPLETE_STATES);
        if (complete.addedInformationSets() != 0
                || !complete.solution().strategy().keySet().equals(pre.strategy().keySet()))
            throw new IllegalArgumentException(
                    "Menu selection requires a complete frozen preflop policy");
        var roots = base.sourceGame().chanceOutcomes(base.sourceGame().initialState());
        var ranks = new HashMap<SixMaxRankTexturePayoffTable.Signal, Integer>();
        for (int r = 0; r < parent.signals().size(); r++) ranks.put(parent.signals().get(r), r);
        var worlds = new HashMap<List<String>, Integer>();
        for (int d = 0; d < parent.deals().size(); d++)
            worlds.put(parent.deals().get(d).hands(), d);
        var boards = new TreeMap<String, Board>();
        for (int d = 0; d < roots.size(); d++) {
            var deck =
                    SixMaxConditionalPayoffEnumeration.undealt(
                            base.sourceGame().dealtHands(roots.get(d).state()));
            long[] control = new long[parent.signals().size()];
            for (int a = 0; a < deck.size() - 2; a++)
                for (int b = a + 1; b < deck.size() - 1; b++)
                    for (int c = b + 1; c < deck.size(); c++) {
                        var cards = List.of(deck.get(a), deck.get(b), deck.get(c));
                        int rank =
                                ranks.get(
                                        SixMaxRankTexturePayoffTable.Signal.from(
                                                cards.get(0), cards.get(1), cards.get(2)));
                        var text = cards.stream().map(Card::compact).sorted().toList();
                        String key = String.join("", text);
                        var previous = boards.get(key);
                        boards.put(
                                key,
                                new Board(
                                        text,
                                        rank,
                                        (previous == null ? 0 : previous.worlds()) | (1 << d)));
                        control[rank]++;
                    }
            if (!Arrays.stream(control).boxed().toList().equals(parent.deals().get(d).flopCounts()))
                throw new IllegalStateException("Independent menu board count control differs");
        }
        var candidates = new ArrayList<Candidate>();
        for (int h = 0; h < selections.size(); h++) {
            if (!SixMaxTextureConditionalAudit.hasReach(
                    base.sourceGame(), pre, selections.get(h).history())) continue;
            var transition =
                    new SixMaxPolicyFlopTransition(
                            base.sourceGame(), pre, selections.get(h).history());
            if (transition.reachProbability() < HISTORY_THRESHOLD) continue;
            double[] q = new double[roots.size()];
            for (var deal : transition.deals())
                q[worlds.get(deal.hands().stream().map(WeightedCombo::key).toList())] =
                        deal.probability();
            for (var board : boards.values()) {
                double mass = 0;
                var first = new HashMap<String, Double>();
                var second = new HashMap<String, Double>();
                for (int d = 0; d < q.length; d++)
                    if ((board.worlds() & (1 << d)) != 0) {
                        mass += q[d];
                        var hands = parent.deals().get(d).hands();
                        first.merge(
                                hands.get(transition.firstToAct().ordinal()), q[d], Double::sum);
                        second.merge(
                                hands.get(transition.secondToAct().ordinal()), q[d], Double::sum);
                    }
                final double normal = mass;
                if (mass == 0
                        || first.values().stream()
                                        .filter(p -> p / normal >= COMBO_THRESHOLD)
                                        .count()
                                < 2
                        || second.values().stream()
                                        .filter(p -> p / normal >= COMBO_THRESHOLD)
                                        .count()
                                < 2) continue;
                candidates.add(
                        new Candidate(h, board, transition.reachProbability() * mass / 9880));
            }
        }
        candidates.sort(
                Comparator.comparingDouble((Candidate c) -> c.reach() / c.rawStates())
                        .reversed()
                        .thenComparingInt(Candidate::history)
                        .thenComparing(c -> String.join("", c.board().cards())));
        double allHeadsUp =
                SixMaxMaterialContinuationFeasibility.assess(
                                base.sourceGame(),
                                pre,
                                SixMaxMaterialContinuationFeasibility.Settings.researchDefault())
                        .headsUpProbability();
        if (allHeadsUp == 0)
            throw new IllegalArgumentException("No heads-up reach in reference policy");
        double reach = 0;
        var reveals = new ArrayList<SixMaxHistoryPhysicalPayoffTable.Reveal>();
        for (var candidate : candidates) {
            if (reach / allHeadsUp >= requestedFraction) break;
            if (reveals.size() == SixMaxHistoryPhysicalPayoffTable.MAX_REVELATIONS)
                throw new IllegalArgumentException(
                        "Requested coverage exceeds bounded revelation menu");
            reveals.add(
                    new SixMaxHistoryPhysicalPayoffTable.Reveal(
                            candidate.history(), candidate.board().cards()));
            reach += candidate.reach();
        }
        if (reach / allHeadsUp < requestedFraction)
            throw new IllegalArgumentException(
                    "Insufficient material physical coverage in reference policy");
        var menu =
                new SixMaxHistoryPhysicalPayoffTable.Menu(
                        selections, reveals.stream().sorted().toList());
        var sizing = SixMaxHistoryPhysicalPayoffTable.sizing(source, parent, menu);
        return new Evidence(
                ALGORITHM,
                "VALIDATION_ONLY",
                parent.sourcePackHash(),
                SixMaxRankTexturePayoffTable.hash(parent),
                SixMaxConnectedPostflopAudit.solutionHash(pre),
                SixMaxHistoryPhysicalPayoffTable.hashValue(menu),
                requestedFraction,
                reach,
                allHeadsUp,
                reach / allHeadsUp,
                sizing,
                menu);
    }

    public static void write(Path path, Evidence evidence) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(evidence).getBytes(StandardCharsets.UTF_8),
                MAX_EVIDENCE_BYTES);
    }

    /** Regenerate every candidate/count and the deterministic menu from the bound frozen policy. */
    public static Evidence replay(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            CfrSolution reference)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, MAX_EVIDENCE_BYTES),
                                Evidence.class);
        if (!saved.sourcePackHash().equals(parent.sourcePackHash())
                || !saved.parentRankTableHash().equals(SixMaxRankTexturePayoffTable.hash(parent))
                || !saved.frozenPreflopPolicyHash()
                        .equals(
                                SixMaxConnectedPostflopAudit.solutionHash(
                                        SixMaxPreflopContinuationFeedback.preflopPolicy(reference)))
                || !saved.menuHash()
                        .equals(SixMaxHistoryPhysicalPayoffTable.hashValue(saved.menu())))
            throw new IllegalArgumentException("Menu selection evidence lineage differs");
        var expected =
                select(
                        source,
                        parent,
                        reference,
                        saved.menu().selections(),
                        saved.requestedAllHeadsUpFraction());
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Frozen-policy menu selection replay differs");
        return expected;
    }
}
