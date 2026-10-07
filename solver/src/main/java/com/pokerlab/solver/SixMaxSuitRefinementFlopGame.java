package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxRankTextureFlopGame.Coverage;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.Selection;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.State;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Separately declared partial suit observation; state/selection shapes reuse the one-bet engine.
 */
public final class SixMaxSuitRefinementFlopGame implements MultiPlayerCfrGame<State> {
    private final SixMaxSuitRefinementPayoffTable.Artifact table;
    private final SixMaxOneBetFlopGame core;

    public SixMaxSuitRefinementFlopGame(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            List<Selection> selections)
            throws Exception {
        SixMaxSuitRefinementPayoffTable.validate(table, source, parent);
        this.table = table;
        core =
                new SixMaxOneBetFlopGame(
                        source, SixMaxSuitRefinementPayoffTable.view(table), selections);
    }

    public SixMaxPreflopCheckdownGame sourceGame() {
        return core.sourceGame();
    }

    SixMaxSuitRefinementPayoffTable.Artifact payoffTable() {
        return table;
    }

    SixMaxOneBetFlopGame core() {
        return core;
    }

    public List<Selection> selections() {
        return core.selections();
    }

    public long completeTreeStates() {
        return core.completeTreeStates();
    }

    public List<Coverage> coverage() {
        return core.coverage();
    }

    public CfrSolution checkdownBaseline(CfrSolution preflop) {
        return core.checkdownBaseline(preflop);
    }

    @Override
    public int playerCount() {
        return core.playerCount();
    }

    @Override
    public State initialState() {
        return core.initialState();
    }

    @Override
    public boolean isTerminal(State state) {
        return core.isTerminal(state);
    }

    @Override
    public int currentPlayer(State state) {
        return core.currentPlayer(state);
    }

    @Override
    public List<String> legalActions(State state) {
        return core.legalActions(state);
    }

    @Override
    public String informationSet(State state) {
        return core.informationSet(state);
    }

    @Override
    public State afterAction(State state, String action) {
        return core.afterAction(state, action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        return core.chanceOutcomes(state);
    }

    @Override
    public double[] terminalUtilities(State state) {
        return core.terminalUtilities(state);
    }

    @Override
    public OptionalDouble inactivePlayerUtility(State state, int player) {
        return core.inactivePlayerUtility(state, player);
    }
}
