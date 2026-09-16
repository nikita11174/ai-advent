package dev.aiadvent.mentor;

import dev.aiadvent.mentor.model.ProviderUsage;

record TokenMetrics(long currentRequestTokens, long contextTokens, long responseTokens,
                    ProviderUsage providerUsage) {
    TokenMetrics {
        if (currentRequestTokens < 0 || contextTokens < 0 || responseTokens < 0) {
            throw new IllegalArgumentException("Local token estimates must not be negative.");
        }
    }
}
