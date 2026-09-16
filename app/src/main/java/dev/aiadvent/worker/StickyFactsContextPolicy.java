package dev.aiadvent.worker;

import java.util.ArrayList;
import java.util.List;

final class StickyFactsContextPolicy implements ContextPolicy {
    @Override
    public List<ConversationContext.Message> build(List<ConversationContext.Message> rawMessages,
                                                    String input, ConversationSummary summary, StickyFacts facts,
                                                    int recentMessageCount) {
        List<ConversationContext.Message> committed = rawMessages.subList(1, rawMessages.size());
        int recentStart = Math.max(0, committed.size() - recentMessageCount);
        var messages = new ArrayList<ConversationContext.Message>();
        messages.add(rawMessages.getFirst());
        messages.add(new ConversationContext.Message("system", facts.asPrompt()));
        messages.addAll(committed.subList(recentStart, committed.size()));
        messages.add(new ConversationContext.Message("user", input));
        return List.copyOf(messages);
    }
}
