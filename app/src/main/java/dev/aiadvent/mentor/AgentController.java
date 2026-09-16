package dev.aiadvent.mentor;

import dev.aiadvent.mentor.model.AgentModelCatalog;
import dev.aiadvent.mentor.model.ModelExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/dialogs/{id}/agent/messages")
class AgentController {
    private final AgentDialogService agents;

    AgentController(AgentDialogService agents) {
        this.agents = agents;
    }

    @PostMapping
    AgentResponse reply(@PathVariable UUID id, @RequestBody AgentRequest request)
            throws IOException, ModelExecutionException {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        AgentReply reply = agents.reply(id, request.input(), request.contextMode(), request.recentMessageCount(),
                request.branchId(), request.taskId(), request.agentModelKey());
        return new AgentResponse(reply.analysis(), reply.metrics(), reply.summaryMetrics(), reply.factsMetrics(),
                reply.contextMetadata(), request.agentModelKey() == null ? AgentModelCatalog.DEFAULT_KEY : request.agentModelKey());
    }

    @ExceptionHandler(DialogStore.DialogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ReviewController.ApiError notFound(DialogStore.DialogNotFoundException exception) {
        return new ReviewController.ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(AgentBranchStore.BranchNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ReviewController.ApiError branchNotFound(AgentBranchStore.BranchNotFoundException exception) {
        return new ReviewController.ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(EngineeringReviewAgent.BusyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ReviewController.ApiError busy(EngineeringReviewAgent.BusyException exception) {
        return new ReviewController.ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(EngineeringReviewAgent.ContextLimitExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    ReviewController.ApiError contextLimit(EngineeringReviewAgent.ContextLimitExceededException exception) {
        return new ReviewController.ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(ModelExecutionException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ReviewController.ApiError modelFailure(ModelExecutionException exception) {
        return new ReviewController.ApiError(exception.getMessage(), exception.rawResponse());
    }

    @ExceptionHandler(EngineeringReviewAgent.MaintenanceMetricsException.class)
    ResponseEntity<AgentError> maintenanceFailure(EngineeringReviewAgent.MaintenanceMetricsException exception) {
        Throwable cause = exception.getCause();
        HttpStatus status = cause instanceof EngineeringReviewAgent.ContextLimitExceededException
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : cause instanceof ModelExecutionException ? HttpStatus.BAD_GATEWAY
                : cause instanceof IOException ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;
        String rawResponse = cause instanceof ModelExecutionException modelException ? modelException.rawResponse() : null;
        return ResponseEntity.status(status).body(new AgentError(cause.getMessage(), rawResponse,
                exception.summaryMetrics(), exception.factsMetrics()));
    }

    record AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount, String branchId,
                        UUID taskId, String agentModelKey) {
        AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount, String branchId, UUID taskId) {
            this(input, contextMode, recentMessageCount, branchId, taskId, null);
        }
        AgentRequest(String input) {
            this(input, null, null, null, null, null);
        }
    }

    record AgentResponse(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                         java.util.List<TokenMetrics> factsMetrics,
                          ContextMetadata contextMetadata, String agentModelKey) {
    }

    record AgentError(String error, String rawResponse, TokenMetrics summaryMetrics,
                      java.util.List<TokenMetrics> factsMetrics) {
    }
}
