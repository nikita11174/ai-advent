package dev.aiadvent.mentor;

import java.util.concurrent.locks.ReentrantLock;

final class EngineeringReviewAgent {
    private final AgentConfig config;
    private final ConversationContext context;
    private final DeepSeekClient client;
    private final ReentrantLock turnLock = new ReentrantLock();

    EngineeringReviewAgent(AgentConfig config, DeepSeekClient client) {
        this.config = config;
        this.context = new ConversationContext(config.systemPrompt());
        this.client = client;
    }

    String reply(String input) throws DeepSeekException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        if (!turnLock.tryLock()) {
            throw new BusyException();
        }
        try {
            String analysis = client.complete(context.withUserMessage(input), config.model(),
                    config.temperature(), config.maxTokens());
            context.commit(input, analysis);
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
