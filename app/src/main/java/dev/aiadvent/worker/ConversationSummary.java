package dev.aiadvent.worker;

record ConversationSummary(int summarizedMessageCount, String summary) {
    ConversationSummary {
        if (summarizedMessageCount < 1 || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Conversation summary must cover messages and contain text.");
        }
    }
}
