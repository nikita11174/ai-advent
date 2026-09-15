package dev.aiadvent.mentor;

import java.util.List;

record ContextMetadata(ContextMode mode, int recentMessageCount, String summary,
                       int summarizedMessageCount, StickyFacts facts, List<AgentMemory.Usage> memoryUsed) {
    ContextMetadata(ContextMode mode, int recentMessageCount, String summary,
                    int summarizedMessageCount, StickyFacts facts) {
        this(mode, recentMessageCount, summary, summarizedMessageCount, facts, List.of());
    }
}
