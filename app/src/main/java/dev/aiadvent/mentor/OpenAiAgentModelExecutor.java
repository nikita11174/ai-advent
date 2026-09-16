package dev.aiadvent.mentor;

import org.springframework.stereotype.Component;

@Component
class OpenAiAgentModelExecutor implements AgentModelExecutor {
    private final OpenAiResponsesClient client;

    OpenAiAgentModelExecutor(OpenAiResponsesClient client) {
        this.client = client;
    }

    @Override
    public Completion complete(AgentModelRequest request) throws ModelExecutionException {
        ModelProfile model = ModelProfile.resolve(request.model());
        OpenAiResponsesClient.Result result = client.complete(model, request.messages(),
                request.temperature(), request.maxTokens());
        if (result.error() != null) {
            throw new ModelExecutionException(result.error());
        }
        ModelProfile.Usage usage = result.usage();
        return new Completion(result.analysis(), new ProviderUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens()));
    }
}
