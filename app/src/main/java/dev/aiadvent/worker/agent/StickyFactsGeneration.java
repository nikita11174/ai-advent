package dev.aiadvent.worker.agent;

import dev.aiadvent.worker.memory.StickyFacts;
public record StickyFactsGeneration(StickyFacts facts, TokenMetrics metrics) {
}
