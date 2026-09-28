package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aiadvent.worker.mcp.RepositoryResearchPipeline;
import dev.aiadvent.worker.mcp.WorkspaceToolRuntime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@RequestMapping("/api/repository-research")
final class RepositoryResearchController {
    private final RepositoryResearchPipeline pipeline;
    private final WorkspaceToolRuntime runtime;

    RepositoryResearchController(RepositoryResearchPipeline pipeline, WorkspaceToolRuntime runtime) {
        this.pipeline = pipeline; this.runtime = runtime;
    }

    @GetMapping("/reports/{reportRef}")
    Report readReport(@PathVariable String reportRef) { return new Report(reportRef, runtime.readReport(reportRef)); }

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

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ReportError> reportFailure(IllegalStateException e) {
        String code = e.getMessage();
        HttpStatus status = switch (code) {
            case "INVALID_REPORT_REF" -> HttpStatus.BAD_REQUEST;
            case "REPORT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "RESEARCH_DISABLED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ReportError(status == HttpStatus.BAD_GATEWAY ? "REPORT_UNAVAILABLE" : code));
    }

    record Failure(String status, String failedStep, int stepsCompleted, String code) { }
    record Report(String reportRef, String content) { }
    record ReportError(String code) { }
}
