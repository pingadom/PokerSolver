package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Objects;

/** A BTN river response based on its own combo and the public state, never the dealt BB combo. */
public interface RiverCallPolicy {
    String definition();

    double callProbability(
            WeightedCombo button, List<Card> board, PublicRiverHistory publicRiverState);

    static RiverCallPolicy fixed(PhysicalActionBeliefAudit.ResponseModel model) {
        Objects.requireNonNull(model, "model");
        return new RiverCallPolicy() {
            @Override
            public String definition() {
                return model.name();
            }

            @Override
            public double callProbability(
                    WeightedCombo button, List<Card> board, PublicRiverHistory publicRiverState) {
                return model == PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL
                                || PhysicalRiverPairCallResponse.calls(button, board)
                        ? 1
                        : 0;
            }
        };
    }
}
