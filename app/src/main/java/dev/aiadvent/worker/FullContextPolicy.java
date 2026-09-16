package dev.aiadvent.worker;

import java.util.ArrayList;
import java.util.List;

final class FullContextPolicy implements ContextPolicy {
    @Override
    public List<ConversationContext.Message> build(List<ConversationContext.Message> rawMessages,
                                                    String input, ConversationSummary summary, StickyFacts facts,
                                                    int recentMessageCount) {
        var messages = new ArrayList<>(rawMessages);
        messages.add(new ConversationContext.Message("user", input));
        return List.copyOf(messages);
    }
}
