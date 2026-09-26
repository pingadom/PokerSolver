package com.pokerlab.api;

import com.pokerlab.solver.PreflopAllInSpot;
import com.pokerlab.solver.PreflopDrillSession;
import com.pokerlab.solver.PreflopTrainer;
import com.pokerlab.solver.PreflopTrainer.Action;
import com.pokerlab.solver.WeightedCombo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

/** Explicitly opt-in HTTP boundary for validation-only drills. It never runs the solver. */
@RestController
@RequestMapping("/api/v1/trainer/research")
@ConditionalOnProperty(name = "pokerlab.trainer.research-enabled", havingValue = "true")
public class ResearchTrainerController {
    public record Metadata(
            String spotId,
            String spotHash,
            String packHash,
            String publicationStatus,
            PreflopAllInSpot.Seat heroSeat,
            PreflopAllInSpot.Seat opponentSeat,
            List<String> heroRange,
            List<String> opponentRange,
            double effectiveStackBb,
            String rakeModel,
            String payoffMethod,
            double estimatedGameGapBb,
            double maximumCalledPayoffStandardErrorBb,
            int sessionLength) {}

    public record QuestionResponse(
            String seed, String packHash, PreflopTrainer.Question question) {}

    public record SessionQuestionResponse(
            String sessionSeed, int index, String packHash, PreflopTrainer.Question question) {}

    public record GradedSessionQuestion(
            SessionQuestionResponse question, PreflopTrainer.Feedback feedback) {}

    public record SessionReviewResponse(
            String packHash,
            List<GradedSessionQuestion> attempts,
            double totalEvLossBb,
            double averageEvLossBb) {}

    public record GradeRequest(
            @NotBlank String seed,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotNull PreflopTrainer.Action action) {}

    public record SessionGradeRequest(
            @NotBlank String sessionSeed,
            @NotNull @Min(0) @Max(9) Integer index,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotNull PreflopTrainer.Action action) {}

    public record SessionReviewRequest(
            @NotBlank String sessionSeed,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotNull @Size(min = 10, max = 10) List<@NotNull Action> actions) {}

    private final PreflopTrainer trainer;
    private final PreflopDrillSession session;

    public ResearchTrainerController(PreflopTrainer trainer) {
        this.trainer = trainer;
        session = new PreflopDrillSession(trainer);
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        var pack = trainer.solutionPack();
        var spot = pack.spot();
        return response(
                new Metadata(
                        spot.id(),
                        pack.spotHash(),
                        trainer.packHash(),
                        pack.publicationStatus(),
                        spot.firstSeat(),
                        spot.secondSeat(),
                        spot.firstRange().stream().map(WeightedCombo::key).toList(),
                        spot.secondRange().stream().map(WeightedCombo::key).toList(),
                        spot.effectiveStackBb(),
                        "NO_RAKE",
                        pack.payoffMethod(),
                        pack.estimatedGameGapBb(),
                        pack.maximumCalledPayoffStandardErrorBb(),
                        PreflopDrillSession.LENGTH));
    }

    @GetMapping("/questions/{seed}")
    public ResponseEntity<QuestionResponse> question(@PathVariable("seed") String seed) {
        PreflopTrainer.Question question = trainer.question(parseSeed(seed));
        return response(new QuestionResponse(seed, trainer.packHash(), question));
    }

    @PostMapping("/grade")
    public ResponseEntity<PreflopTrainer.Feedback> grade(@Valid @RequestBody GradeRequest request) {
        PreflopTrainer.Question question = trainer.question(parseSeed(request.seed()));
        requirePack(request.packHash());
        return response(trainer.grade(question, request.action()));
    }

    @GetMapping("/sessions/{seed}/questions/{index}")
    public ResponseEntity<SessionQuestionResponse> sessionQuestion(
            @PathVariable("seed") String seed, @PathVariable("index") int index) {
        long parsed = parseSeed(seed);
        return response(
                new SessionQuestionResponse(
                        Long.toString(parsed),
                        index,
                        trainer.packHash(),
                        session.question(parsed, index)));
    }

    @PostMapping("/sessions/grade")
    public ResponseEntity<GradedSessionQuestion> sessionGrade(
            @Valid @RequestBody SessionGradeRequest request) {
        requirePack(request.packHash());
        long seed = parseSeed(request.sessionSeed());
        var attempt = session.grade(seed, request.index(), request.action());
        return response(
                new GradedSessionQuestion(
                        new SessionQuestionResponse(
                                Long.toString(seed),
                                request.index(),
                                trainer.packHash(),
                                attempt.question()),
                        attempt.feedback()));
    }

    @PostMapping("/sessions/review")
    public ResponseEntity<SessionReviewResponse> sessionReview(
            @Valid @RequestBody SessionReviewRequest request) {
        requirePack(request.packHash());
        long seed = parseSeed(request.sessionSeed());
        var review = session.review(seed, request.actions());
        return response(
                new SessionReviewResponse(
                        trainer.packHash(),
                        review.attempts().stream()
                                .map(
                                        attempt ->
                                                new GradedSessionQuestion(
                                                        new SessionQuestionResponse(
                                                                Long.toString(seed),
                                                                attempt.index(),
                                                                trainer.packHash(),
                                                                attempt.question()),
                                                        attempt.feedback()))
                                .toList(),
                        review.totalEvLossBb(),
                        review.averageEvLossBb()));
    }

    private void requirePack(String candidate) {
        if (!trainer.packHash().equals(candidate))
            throw new IllegalArgumentException("Solution pack has changed; start a new session");
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
