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
import dev.aiadvent.worker.agent.InvariantGuard;
import dev.aiadvent.worker.agent.ToolTurnException;
import dev.aiadvent.worker.agent.ToolTurnTrace;

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
                request.branchId(), request.taskId(), request.agentModelKey(), request.profileId(),
                Boolean.TRUE.equals(request.requireGitStatusTool()),
                Boolean.TRUE.equals(request.requireRepositoryMonitorRead()));
        return new AgentResponse(reply.analysis(), reply.metrics(), reply.summaryMetrics(), reply.factsMetrics(),
                reply.guardMetrics(), reply.contextMetadata(), request.agentModelKey() == null ? AgentModelCatalog.DEFAULT_KEY : request.agentModelKey(),
                reply.toolTrace());
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

    @ExceptionHandler(AgentDialogService.TaskSelectionMismatchException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError taskSelectionMismatch(AgentDialogService.TaskSelectionMismatchException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(AgentDialogService.TaskPausedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError pausedTask(AgentDialogService.TaskPausedException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(InvariantGuard.RejectedCandidateException.class)
    ResponseEntity<InvariantError> invariantRejected(InvariantGuard.RejectedCandidateException exception) {
        InvariantGuard.Outcome outcome = exception.outcome();
        String code = outcome.decision() == InvariantGuard.Decision.CONFLICT
                ? "INVARIANT_CONFLICT" : "INVARIANT_UNCERTAIN";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new InvariantError(code, outcome.invariantId(),
                exception.invariantName(), outcome.explanation(), outcome.compatibleContinuation(), null, null,
                exception.guardMetrics()));
    }

    @ExceptionHandler(InvariantGuard.GuardFailureException.class)
    ResponseEntity<InvariantError> invariantGuardFailure(InvariantGuard.GuardFailureException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new InvariantError("INVARIANT_CHECK_FAILED", null,
                null, exception.getMessage(), null, null, null, exception.guardMetrics()));
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

    @ExceptionHandler(ToolTurnException.class)
    ResponseEntity<?> toolFailure(ToolTurnException exception) {
        String code = exception.getMessage();
        if (exception.getCause() instanceof InvariantGuard.RejectedCandidateException rejected) {
            var outcome = rejected.outcome();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new InvariantError(code, outcome.invariantId(),
                    rejected.invariantName(), outcome.explanation(), outcome.compatibleContinuation(),
                    null, null, rejected.guardMetrics(), exception.trace()));
        }
        HttpStatus status = code.startsWith("INVARIANT_") ? HttpStatus.CONFLICT
                : code.equals("CONTEXT_LIMIT") ? HttpStatus.PAYLOAD_TOO_LARGE
                : code.equals("TOOL_DISABLED") || code.equals("TOOL_CAPABILITY_UNSUPPORTED")
                        || code.equals("INVALID_TOOL_ARGUMENTS") || code.equals("TOOL_NOT_ALLOWED")
                        ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new ToolError(code, exception.trace()));
    }

    @ExceptionHandler(ConversationAgent.MaintenanceMetricsException.class)
    ResponseEntity<?> maintenanceFailure(ConversationAgent.MaintenanceMetricsException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof InvariantGuard.RejectedCandidateException rejected) {
            InvariantGuard.Outcome outcome = rejected.outcome();
            String code = outcome.decision() == InvariantGuard.Decision.CONFLICT
                    ? "INVARIANT_CONFLICT" : "INVARIANT_UNCERTAIN";
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new InvariantError(code, outcome.invariantId(),
                    rejected.invariantName(), outcome.explanation(), outcome.compatibleContinuation(),
                    exception.summaryMetrics(), exception.factsMetrics(), exception.guardMetrics()));
        }
        if (cause instanceof InvariantGuard.GuardFailureException failure) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new InvariantError("INVARIANT_CHECK_FAILED", null,
                    null, failure.getMessage(), null, exception.summaryMetrics(), exception.factsMetrics(),
                    exception.guardMetrics()));
        }
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
                        UUID taskId, String agentModelKey, UUID profileId, Boolean requireGitStatusTool,
                        Boolean requireRepositoryMonitorRead) {
        AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount, String branchId,
                     UUID taskId, String agentModelKey, UUID profileId, Boolean requireGitStatusTool) {
            this(input, contextMode, recentMessageCount, branchId, taskId, agentModelKey, profileId,
                    requireGitStatusTool, false);
        }
        AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount, String branchId,
                     UUID taskId, String agentModelKey, UUID profileId) {
            this(input, contextMode, recentMessageCount, branchId, taskId, agentModelKey, profileId, false, false);
        }
        AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount, String branchId, UUID taskId) {
            this(input, contextMode, recentMessageCount, branchId, taskId, null, null, false, false);
        }
        AgentRequest(String input) {
            this(input, null, null, null, null, null, null, false, false);
        }
    }

    record AgentResponse(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                         java.util.List<TokenMetrics> factsMetrics, TokenMetrics guardMetrics,
                          ContextMetadata contextMetadata, String agentModelKey, ToolTurnTrace toolTrace) {
    }

    record ToolError(String code, ToolTurnTrace toolTrace) {
    }

    record AgentError(String error, String rawResponse, TokenMetrics summaryMetrics,
                      java.util.List<TokenMetrics> factsMetrics) {
    }

    record InvariantError(String code, UUID invariantId, String invariantName, String explanation,
                          String compatibleContinuation, TokenMetrics summaryMetrics,
                          java.util.List<TokenMetrics> factsMetrics, TokenMetrics guardMetrics,
                          ToolTurnTrace toolTrace) {
        InvariantError(String code, UUID invariantId, String invariantName, String explanation,
                       String compatibleContinuation, TokenMetrics summaryMetrics,
                       java.util.List<TokenMetrics> factsMetrics, TokenMetrics guardMetrics) {
            this(code, invariantId, invariantName, explanation, compatibleContinuation,
                    summaryMetrics, factsMetrics, guardMetrics, null);
        }
    }
}
