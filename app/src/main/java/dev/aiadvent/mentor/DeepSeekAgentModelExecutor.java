package dev.aiadvent.mentor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
class DeepSeekAgentModelExecutor implements AgentModelExecutor {
    private final DeepSeekClient client;

    DeepSeekAgentModelExecutor(DeepSeekClient client) {
        this.client = client;
    }

    @Override
    public Completion complete(List<ConversationContext.Message> messages, AgentConfig config) throws DeepSeekException {
        DeepSeekClient.Completion completion = client.complete(messages, config.model(), config.temperature(), config.maxTokens());
        return new Completion(completion.content(), completion.usage());
    }
}
