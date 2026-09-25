package com.pokerlab.api;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.TurnRiverResearchTrainer;
import com.pokerlab.solver.WeightedCombo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opt-in HTTP boundary for the validation-only two-street research drill. */
@RestController
@RequestMapping("/api/v1/trainer/research/turn-river")
@ConditionalOnProperty(name = "pokerlab.trainer.turn-river-research-enabled", havingValue = "true")
public class TurnRiverResearchController {
    public record Metadata(
            String spotHash,
            String packHash,
            String publicationStatus,
            List<Card> turnBoard,
            double potBb,
            double remainingStackBb,
            double turnBetBb,
            double riverBetBb,
            List<String> firstRange,
            List<String> secondRange,
            double gameGapBb,
            int availableTurnQuestions,
            int availableRiverQuestions,
            String rakeModel,
            String bettingTree) {}

    public record QuestionResponse(String seed, TurnRiverResearchTrainer.Question question) {}

    public record GradeRequest(
            @NotBlank String seed,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotBlank @Pattern(regexp = "[kbcf]") String action) {}

    private final TurnRiverResearchTrainer trainer;

    public TurnRiverResearchController(TurnRiverResearchTrainer trainer) {
        this.trainer = trainer;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        var pack = trainer.pack();
        var spot = pack.spot();
        return response(
                new Metadata(
                        pack.spotHash(),
                        trainer.packHash(),
                        pack.publicationStatus(),
                        spot.turnBoard(),
                        spot.potBb(),
                        spot.remainingStackBb(),
                        spot.turnBetBb(),
                        spot.riverBetBb(),
                        spot.firstRange().stream().map(WeightedCombo::key).toList(),
                        spot.secondRange().stream().map(WeightedCombo::key).toList(),
                        pack.gameGapBb(),
                        trainer.availableTurnQuestions(),
                        trainer.availableRiverQuestions(),
                        "NO_RAKE",
                        "SINGLE_BET_EACH_STREET_NO_RAISES"));
    }

    @GetMapping("/questions/{seed}")
    public ResponseEntity<QuestionResponse> question(@PathVariable("seed") String seed) {
        long parsed = parseSeed(seed);
        return response(new QuestionResponse(Long.toString(parsed), trainer.question(parsed)));
    }

    @PostMapping("/grade")
    public ResponseEntity<TurnRiverResearchTrainer.Feedback> grade(
            @Valid @RequestBody GradeRequest request) {
        return response(
                trainer.grade(parseSeed(request.seed()), request.packHash(), request.action()));
    }

    private static long parseSeed(String seed) {
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Seed must be a signed 64-bit integer", exception);
        }
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
