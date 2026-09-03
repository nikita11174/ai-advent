package dev.aiadvent.mentor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {
    private final DeepSeekClient deepSeekClient;

    ReviewController(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    @PostMapping
    ReviewResponse review(@RequestBody ReviewRequest request) throws DeepSeekException {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        return new ReviewResponse(deepSeekClient.analyze(request.input()));
    }

    record ReviewRequest(String input) {
    }

    record ReviewResponse(String analysis) {
    }

    @RestControllerAdvice
    static class ApiExceptionHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        Map<String, String> invalidInput(IllegalArgumentException exception) {
            return Map.of("error", exception.getMessage());
        }

        @ExceptionHandler(DeepSeekException.class)
        @ResponseStatus(HttpStatus.BAD_GATEWAY)
        Map<String, String> deepSeekFailure(DeepSeekException exception) {
            return Map.of("error", exception.getMessage());
        }
    }
}
