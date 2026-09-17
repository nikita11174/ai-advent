package dev.aiadvent.worker.agent;

import dev.aiadvent.worker.model.ProviderUsage;

public record TokenMetrics(long currentRequestTokens, long contextTokens, long responseTokens,
                    ProviderUsage providerUsage) {
    public TokenMetrics {
        if (currentRequestTokens < 0 || contextTokens < 0 || responseTokens < 0) {
            throw new IllegalArgumentException("Local token estimates must not be negative.");
        }
    }
}
