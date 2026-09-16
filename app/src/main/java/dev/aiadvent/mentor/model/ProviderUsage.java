package dev.aiadvent.mentor.model;

public record ProviderUsage(Long promptTokens, Long completionTokens, Long totalTokens) {
    public ProviderUsage {
        if ((promptTokens != null && promptTokens < 0)
                || (completionTokens != null && completionTokens < 0)
                || (totalTokens != null && totalTokens < 0)) {
            throw new IllegalArgumentException("Provider token usage must not be negative.");
        }
    }
}
