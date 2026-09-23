package com.pokerlab.core.showdown;

import com.pokerlab.core.hand.EvaluatedHand;
import java.util.Objects;

public record ShowdownResult(EvaluatedHand heroHand, EvaluatedHand villainHand, Winner winner) {

    public ShowdownResult {
        Objects.requireNonNull(heroHand, "heroHand must not be null");
        Objects.requireNonNull(villainHand, "villainHand must not be null");
        Objects.requireNonNull(winner, "winner must not be null");
    }
}
