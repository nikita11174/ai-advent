package dev.aiadvent.mentor;

import java.util.List;

interface AgentModelExecutor {
    Completion complete(List<ConversationContext.Message> messages, AgentConfig config) throws DeepSeekException;

    record Completion(String content, ProviderUsage usage) {
    }
}
