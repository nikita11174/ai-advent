package dev.aiadvent.worker.agent;

import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.StickyFacts;
import dev.aiadvent.worker.context.ContextMode;
import java.util.List;

public record ContextMetadata(ContextMode mode, int recentMessageCount, String summary,
                       int summarizedMessageCount, StickyFacts facts, List<AgentMemory.Usage> memoryUsed) {
    ContextMetadata(ContextMode mode, int recentMessageCount, String summary,
                    int summarizedMessageCount, StickyFacts facts) {
        this(mode, recentMessageCount, summary, summarizedMessageCount, facts, List.of());
    }
}
