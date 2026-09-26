package com.pokerlab.api;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot;
import com.pokerlab.solver.RiverResearchTrainer;
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

/** Opt-in HTTP boundary for the bounded, validation-only river research drill. */
@RestController
@RequestMapping("/api/v1/trainer/research/river")
@ConditionalOnProperty(name = "pokerlab.trainer.river-research-enabled", havingValue = "true")
public class RiverResearchController {
    public record Metadata(
            String spotId,
            String spotHash,
            String packHash,
            String publicationStatus,
            PreflopAllInSpot.Seat firstSeat,
            PreflopAllInSpot.Seat secondSeat,
            List<Card> board,
            double potBb,
            double remainingStackBb,
            double betBb,
            List<String> firstRange,
            List<String> secondRange,
            double gameGapBb,
            int availableQuestions,
            String rakeModel,
            String bettingTree) {}

    public record QuestionResponse(String seed, RiverResearchTrainer.Question question) {}

    public record GradeRequest(
            @NotBlank String seed,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotBlank @Pattern(regexp = "[kbcf]") String action) {}

    private final RiverResearchTrainer trainer;

    public RiverResearchController(RiverResearchTrainer trainer) {
        this.trainer = trainer;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        var pack = trainer.pack();
        var spot = pack.spot();
        return response(
                new Metadata(
                        spot.id(),
                        pack.spotHash(),
                        trainer.packHash(),
                        pack.publicationStatus(),
                        spot.firstSeat(),
                        spot.secondSeat(),
                        spot.board(),
                        spot.potBb(),
                        spot.remainingStackBb(),
                        spot.betBb(),
                        spot.firstRange().stream().map(WeightedCombo::key).toList(),
                        spot.secondRange().stream().map(WeightedCombo::key).toList(),
                        pack.gameGapBb(),
                        trainer.availableQuestions(),
                        "NO_RAKE",
                        "SINGLE_BET_NO_RAISES"));
    }

    @GetMapping("/questions/{seed}")
    public ResponseEntity<QuestionResponse> question(@PathVariable("seed") String seed) {
        long parsed = parseSeed(seed);
        return response(new QuestionResponse(Long.toString(parsed), trainer.question(parsed)));
    }

    @PostMapping("/grade")
    public ResponseEntity<RiverResearchTrainer.Feedback> grade(
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
