package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxRankTextureFlopGame.State;
import java.util.List;
import java.util.OptionalDouble;

/** A separately bound history-dependent public observation model with the shared one-bet rules. */
public final class SixMaxHistoryPhysicalFlopGame implements MultiPlayerCfrGame<State> {
    private final SixMaxHistoryPhysicalPayoffTable.Verified table;
    private final SixMaxOneBetFlopGame core;

    public SixMaxHistoryPhysicalFlopGame(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table)
            throws Exception {
        SixMaxHistoryPhysicalPayoffTable.validate(table.artifact(), source, parent);
        this.table = table;
        core =
                new SixMaxOneBetFlopGame(
                        source,
                        SixMaxHistoryPhysicalPayoffTable.view(table),
                        table.artifact().menu().selections());
        if (core.completeTreeStates() != table.artifact().completeTreeStates())
            throw new IllegalArgumentException("History game complete sizing differs");
    }

    public SixMaxHistoryPhysicalPayoffTable.Artifact payoffTable() {
        return table.artifact();
    }

    public SixMaxPreflopCheckdownGame sourceGame() {
        return core.sourceGame();
    }

    SixMaxOneBetFlopGame core() {
        return core;
    }

    public long completeTreeStates() {
        return core.completeTreeStates();
    }

    public CfrSolution checkdownBaseline(CfrSolution preflop) {
        return core.checkdownBaseline(preflop);
    }

    public List<SixMaxRankTextureFlopGame.Coverage> coverage() {
        return core.coverage();
    }

    public List<SixMaxRankTextureFlopGame.Selection> selections() {
        return core.selections();
    }

    public int playerCount() {
        return core.playerCount();
    }

    public State initialState() {
        return core.initialState();
    }

    public boolean isTerminal(State state) {
        return core.isTerminal(state);
    }

    public int currentPlayer(State state) {
        return core.currentPlayer(state);
    }

    public List<String> legalActions(State state) {
        return core.legalActions(state);
    }

    public String informationSet(State state) {
        return core.informationSet(state);
    }

    public State afterAction(State state, String action) {
        return core.afterAction(state, action);
    }

    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        return core.chanceOutcomes(state);
    }

    public double[] terminalUtilities(State state) {
        return core.terminalUtilities(state);
    }

    public OptionalDouble inactivePlayerUtility(State state, int player) {
        return core.inactivePlayerUtility(state, player);
    }
}
