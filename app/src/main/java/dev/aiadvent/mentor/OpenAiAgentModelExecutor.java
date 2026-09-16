package dev.aiadvent.mentor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
class OpenAiAgentModelExecutor implements AgentModelExecutor {
    private final OpenAiResponsesClient client;

    OpenAiAgentModelExecutor(OpenAiResponsesClient client) {
        this.client = client;
    }

    @Override
    public Completion complete(List<ConversationContext.Message> messages, AgentConfig config) throws DeepSeekException {
        ModelProfile model = ModelProfile.resolve(config.model());
        OpenAiResponsesClient.Result result = client.complete(model, messages, config.temperature(), config.maxTokens());
        if (result.error() != null) {
            throw new DeepSeekException(result.error());
        }
        ModelProfile.Usage usage = result.usage();
        return new Completion(result.analysis(), new ProviderUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens()));
    }
}
