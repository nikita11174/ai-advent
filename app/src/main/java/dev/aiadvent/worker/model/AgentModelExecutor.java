package dev.aiadvent.worker.model;

public interface AgentModelExecutor {
    Completion complete(AgentModelRequest request) throws ModelExecutionException;

    record Completion(String content, ProviderUsage usage) {
    }
}
