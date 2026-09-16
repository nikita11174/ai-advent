package dev.aiadvent.mentor;

import org.springframework.stereotype.Component;

@Component
class DeepSeekAgentModelExecutor implements AgentModelExecutor {
    private final DeepSeekTransport transport;

    DeepSeekAgentModelExecutor(DeepSeekTransport transport) {
        this.transport = transport;
    }

    @Override
    public Completion complete(AgentModelRequest request) throws ModelExecutionException {
        try {
            DeepSeekTransport.Completion completion = transport.complete(request);
            return new Completion(completion.content(), completion.usage());
        } catch (DeepSeekException exception) {
            throw new ModelExecutionException(exception.getMessage(), exception.rawResponse(), exception);
        }
    }
}
