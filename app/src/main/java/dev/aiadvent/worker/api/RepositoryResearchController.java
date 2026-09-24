package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aiadvent.worker.mcp.RepositoryResearchPipeline;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@RequestMapping("/api/repository-research")
final class RepositoryResearchController {
    private final RepositoryResearchPipeline pipeline;

    RepositoryResearchController(RepositoryResearchPipeline pipeline) { this.pipeline = pipeline; }

    @PostMapping
    RepositoryResearchPipeline.Result run(@RequestBody JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2 || !body.has("query") || !body.has("maxResults")
                || !body.path("query").isTextual() || !body.path("maxResults").isIntegralNumber()
                || !body.path("maxResults").canConvertToInt())
            throw new IllegalArgumentException("INVALID_ARGUMENTS");
        return pipeline.run(body.path("query").textValue(), body.path("maxResults").intValue());
    }

    @ExceptionHandler(RepositoryResearchPipeline.PipelineFailure.class)
    ResponseEntity<Failure> failure(RepositoryResearchPipeline.PipelineFailure e) {
        HttpStatus status = "INVALID_ARGUMENTS".equals(e.getMessage()) ? HttpStatus.BAD_REQUEST
                : "RESEARCH_DISABLED".equals(e.getMessage()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new Failure("FAILED", e.step(), e.stepsCompleted(), e.getMessage()));
    }

    @ExceptionHandler(RepositoryResearchPipeline.PipelineUnknown.class)
    ResponseEntity<Failure> unknown(RepositoryResearchPipeline.PipelineUnknown e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new Failure("UNKNOWN", e.step(), e.stepsCompleted(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Failure> invalid() {
        return ResponseEntity.badRequest().body(new Failure("FAILED", "SEARCH", 0, "INVALID_ARGUMENTS"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Failure> malformed() { return invalid(); }

    record Failure(String status, String failedStep, int stepsCompleted, String code) { }
}
