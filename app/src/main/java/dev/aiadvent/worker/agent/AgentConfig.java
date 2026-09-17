package dev.aiadvent.worker.agent;

import dev.aiadvent.worker.model.AgentModelCatalog;

import java.util.Set;

public record AgentConfig(String model, String systemPrompt, Double temperature, Integer maxTokens,
                   Integer contextTokenLimit) {
    public AgentConfig {
        if (model == null || model.isBlank() || systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("Agent model and system prompt must not be empty.");
        }
        if (temperature != null && !Set.of(0.0, 0.7, 1.2).contains(temperature)) {
            throw new IllegalArgumentException("Temperature must be 0, 0.7 or 1.2.");
        }
        if (maxTokens != null && (maxTokens < 100 || maxTokens > 2000)) {
            throw new IllegalArgumentException("maxTokens must be between 100 and 2000.");
        }
        if (contextTokenLimit != null && contextTokenLimit < 1) {
            throw new IllegalArgumentException("contextTokenLimit must be positive.");
        }
    }

    public static AgentConfig defaults() {
        return defaults(null);
    }

    public static AgentConfig defaults(Integer contextTokenLimit) {
        return new AgentConfig(AgentModelCatalog.DEFAULT_MODEL, """
                You are an engineering review mentor.
                Analyze the provided code or engineering question.
                Explain potential engineering risks clearly and concisely.
                Respond in Russian.
                Format the response using Markdown when it improves readability.""", null, null, contextTokenLimit);
    }

    AgentConfig withModel(String selectedModel) {
        return new AgentConfig(selectedModel, systemPrompt, temperature, maxTokens, contextTokenLimit);
    }
}
