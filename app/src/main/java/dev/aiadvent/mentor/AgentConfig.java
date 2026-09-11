package dev.aiadvent.mentor;

import java.util.Set;

record AgentConfig(String model, String systemPrompt, Double temperature, Integer maxTokens) {
    AgentConfig {
        if (model == null || model.isBlank() || systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("Agent model and system prompt must not be empty.");
        }
        if (temperature != null && !Set.of(0.0, 0.7, 1.2).contains(temperature)) {
            throw new IllegalArgumentException("Temperature must be 0, 0.7 or 1.2.");
        }
        if (maxTokens != null && (maxTokens < 100 || maxTokens > 2000)) {
            throw new IllegalArgumentException("maxTokens must be between 100 and 2000.");
        }
    }

    static AgentConfig defaults() {
        return new AgentConfig("deepseek-v4-flash", """
                You are an engineering review mentor.
                Analyze the provided code or engineering question.
                Explain potential engineering risks clearly and concisely.
                Respond in Russian.
                Format the response using Markdown when it improves readability.""", null, null);
    }
}
