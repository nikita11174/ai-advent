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
    private final ReentrantLock turnLock = new ReentrantLock();

    EngineeringReviewAgent(UUID dialogId, AgentConfig config, ConversationContext context,
                           DeepSeekClient client, AgentHistoryStore histories) {
        this.dialogId = dialogId;
        this.config = config;
        this.context = context;
        this.client = client;
        this.histories = histories;
    }

    String reply(String input) throws IOException, DeepSeekException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        if (!turnLock.tryLock()) {
            throw new BusyException();
        }
        try {
            String analysis = client.complete(context.withUserMessage(input), config.model(),
                    config.temperature(), config.maxTokens());
            List<ConversationContext.Message> completed = context.withCompletedTurn(input, analysis);
            histories.save(dialogId, completed);
            context.commit(completed);
            return analysis;
        } finally {
            turnLock.unlock();
        }
    }

    static class BusyException extends RuntimeException {
        BusyException() {
            super("Agent is already processing a message in this dialog.");
        }
    }
}
