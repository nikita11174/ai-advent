package dev.aiadvent.mentor.model;

public interface AgentModelExecutor {
    Completion complete(AgentModelRequest request) throws ModelExecutionException;

    record Completion(String content, ProviderUsage usage) {
    }
}
