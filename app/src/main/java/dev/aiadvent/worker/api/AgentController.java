package dev.aiadvent.worker.api;

import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.dialog.AgentBranchStore;
import dev.aiadvent.worker.context.ContextMode;
import dev.aiadvent.worker.context.ContextLimitExceededException;
import dev.aiadvent.worker.model.AgentModelCatalog;
import dev.aiadvent.worker.model.ModelExecutionException;
import dev.aiadvent.worker.profile.ProfileService;
import dev.aiadvent.worker.agent.AgentDialogService;
import dev.aiadvent.worker.agent.AgentReply;
import dev.aiadvent.worker.agent.ContextMetadata;
import dev.aiadvent.worker.agent.ConversationAgent;
import dev.aiadvent.worker.agent.TokenMetrics;

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
                request.branchId(), request.taskId(), request.agentModelKey(), request.profileId());
        return new AgentResponse(reply.analysis(), reply.metrics(), reply.summaryMetrics(), reply.factsMetrics(),
                reply.contextMetadata(), request.agentModelKey() == null ? AgentModelCatalog.DEFAULT_KEY : request.agentModelKey());
    }

    @ExceptionHandler(DialogStore.DialogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(DialogStore.DialogNotFoundException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(AgentBranchStore.BranchNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError branchNotFound(AgentBranchStore.BranchNotFoundException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(ProfileService.ProfileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError profileNotFound(ProfileService.ProfileNotFoundException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(ConversationAgent.BusyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError busy(ConversationAgent.BusyException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(ContextLimitExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    ApiError contextLimit(ContextLimitExceededException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(ModelExecutionException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ApiError modelFailure(ModelExecutionException exception) {
        return new ApiError(exception.getMessage(), exception.rawResponse());
    }

    @ExceptionHandler(ConversationAgent.MaintenanceMetricsException.class)
    ResponseEntity<AgentError> maintenanceFailure(ConversationAgent.MaintenanceMetricsException exception) {
        Throwable cause = exception.getCause();
        HttpStatus status = cause instanceof ContextLimitExceededException
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : cause instanceof ModelExecutionException ? HttpStatus.BAD_GATEWAY
                : cause instanceof IOException ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;
        String rawResponse = cause instanceof ModelExecutionException modelException ? modelException.rawResponse() : null;
        return ResponseEntity.status(status).body(new AgentError(cause.getMessage(), rawResponse,
                exception.summaryMetrics(), exception.factsMetrics()));
    }

    record AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount, String branchId,
                        UUID taskId, String agentModelKey, UUID profileId) {
        AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount, String branchId, UUID taskId) {
            this(input, contextMode, recentMessageCount, branchId, taskId, null, null);
        }
        AgentRequest(String input) {
            this(input, null, null, null, null, null, null);
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
