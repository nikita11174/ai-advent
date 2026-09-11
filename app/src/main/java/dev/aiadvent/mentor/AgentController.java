package dev.aiadvent.mentor;

import org.springframework.http.HttpStatus;
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
            throws IOException, DeepSeekException {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        AgentReply reply = agents.reply(id, request.input(), request.contextMode(), request.recentMessageCount());
        return new AgentResponse(reply.analysis(), reply.metrics(), reply.summaryMetrics(), reply.contextMetadata());
    }

    @ExceptionHandler(DialogStore.DialogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ReviewController.ApiError notFound(DialogStore.DialogNotFoundException exception) {
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

    record AgentRequest(String input, ContextMode contextMode, Integer recentMessageCount) {
        AgentRequest(String input) {
            this(input, null, null);
        }
    }

    record AgentResponse(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                         ContextMetadata contextMetadata) {
    }
}
