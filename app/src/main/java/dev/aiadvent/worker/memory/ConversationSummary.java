package dev.aiadvent.worker.memory;

public record ConversationSummary(int summarizedMessageCount, String summary) {
    public ConversationSummary {
        if (summarizedMessageCount < 1 || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Conversation summary must cover messages and contain text.");
        }
    }
}
