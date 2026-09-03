package dev.aiadvent.mentor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/review")
public class ReviewController {
    private final DeepSeekClient deepSeekClient;

    ReviewController(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    @PostMapping
    Object review(@RequestBody ReviewRequest request) throws DeepSeekException {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        ReviewMode mode = request.mode() == null ? ReviewMode.FREE : request.mode();
        if (mode == ReviewMode.FREE) {
            if (request.controls() != null) {
                throw new IllegalArgumentException("controls are only allowed in CONTROLLED mode.");
            }
            return new FreeReviewResponse(deepSeekClient.analyze(request.input()));
        }

        ReviewControls controls = (request.controls() == null ? ReviewControls.defaults() : request.controls()).validated();
        ControlledAnalysis analysis = deepSeekClient.analyzeControlled(request.input(), controls);
        return new ControlledReviewResponse(analysis.review(), analysis.rawResponse());
    }

    record ReviewRequest(String input, ReviewMode mode, ReviewControls controls) {
    }

    record FreeReviewResponse(String analysis) {
    }

    record ControlledReviewResponse(ControlledReview review, String rawResponse) {
    }

    record ApiError(String error, String rawResponse) {
    }

    @RestControllerAdvice
    static class ApiExceptionHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        ApiError invalidInput(IllegalArgumentException exception) {
            return new ApiError(exception.getMessage(), null);
        }

        @ExceptionHandler(DeepSeekException.class)
        @ResponseStatus(HttpStatus.BAD_GATEWAY)
        ApiError deepSeekFailure(DeepSeekException exception) {
            return new ApiError(exception.getMessage(), exception.rawResponse());
        }

        @ExceptionHandler(java.io.IOException.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        ApiError storageFailure(java.io.IOException exception) {
            return new ApiError("Local dialog storage is unavailable.", null);
        }
    }
}
