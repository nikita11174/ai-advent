package dev.aiadvent.mentor;

import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

final class EngineeringReviewAgent {
    private final AgentConfig config;
    private final ConversationContext context;
    private final DeepSeekClient client;
    private final UUID dialogId;
    private final AgentHistoryStore histories;
    private final ApproximateTokenEstimator tokenEstimator;
    private final ReentrantLock turnLock = new ReentrantLock();

    EngineeringReviewAgent(UUID dialogId, AgentConfig config, ConversationContext context,
                           DeepSeekClient client, AgentHistoryStore histories, ApproximateTokenEstimator tokenEstimator) {
        this.dialogId = dialogId;
        this.config = config;
        this.context = context;
        this.client = client;
        this.histories = histories;
        this.tokenEstimator = tokenEstimator;
    }

    AgentReply reply(String input) throws IOException, DeepSeekException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        if (!turnLock.tryLock()) {
            throw new BusyException();
        }
        try {
            List<ConversationContext.Message> outbound = context.withUserMessage(input);
            long contextTokens = tokenEstimator.estimateMessages(outbound);
            if (config.contextTokenLimit() != null && contextTokens > config.contextTokenLimit()) {
                throw new ContextLimitExceededException(contextTokens, config.contextTokenLimit());
            }
            DeepSeekClient.Completion completion = client.complete(outbound, config.model(),
                    config.temperature(), config.maxTokens());
            String analysis = completion.content();
            List<ConversationContext.Message> completed = context.withCompletedTurn(input, analysis);
            histories.save(dialogId, completed);
            context.commit(completed);
            return new AgentReply(analysis, new TokenMetrics(tokenEstimator.estimateText(input), contextTokens,
                    tokenEstimator.estimateText(analysis), completion.usage()));
        } finally {
            turnLock.unlock();
        }
    }

    static class BusyException extends RuntimeException {
        BusyException() {
            super("Agent is already processing a message in this dialog.");
        }
    }

    static class ContextLimitExceededException extends RuntimeException {
        ContextLimitExceededException(long contextTokens, int contextTokenLimit) {
            super("Estimated context limit exceeded: " + contextTokens + " > " + contextTokenLimit + ".");
        }
    }
}
