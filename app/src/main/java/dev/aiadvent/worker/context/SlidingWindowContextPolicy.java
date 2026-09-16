package dev.aiadvent.worker.context;

import dev.aiadvent.worker.dialog.ConversationContext;
import dev.aiadvent.worker.memory.ConversationSummary;
import dev.aiadvent.worker.memory.StickyFacts;
import java.util.ArrayList;
import java.util.List;

public final class SlidingWindowContextPolicy implements ContextPolicy {
    @Override
    public List<ConversationContext.Message> build(List<ConversationContext.Message> rawMessages,
                                                    String input, ConversationSummary summary, StickyFacts facts,
                                                    int recentMessageCount) {
        List<ConversationContext.Message> committed = rawMessages.subList(1, rawMessages.size());
        int recentStart = Math.max(0, committed.size() - recentMessageCount);
        var messages = new ArrayList<ConversationContext.Message>();
        messages.add(rawMessages.getFirst());
        messages.addAll(committed.subList(recentStart, committed.size()));
        messages.add(new ConversationContext.Message("user", input));
        return List.copyOf(messages);
    }
}
