package dev.aiadvent.worker.context;

import dev.aiadvent.worker.dialog.ConversationContext;
import dev.aiadvent.worker.memory.ConversationSummary;
import dev.aiadvent.worker.memory.StickyFacts;
import dev.aiadvent.worker.memory.ConversationSummary;
import dev.aiadvent.worker.memory.StickyFacts;
import java.util.List;

public interface ContextPolicy {
    List<ConversationContext.Message> build(List<ConversationContext.Message> rawMessages,
                                            String input, ConversationSummary summary, StickyFacts facts,
                                            int recentMessageCount);
}
