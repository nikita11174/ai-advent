package dev.aiadvent.mentor;

import java.util.ArrayList;
import java.util.List;

final class SummaryRecentContextPolicy implements ContextPolicy {
    @Override
    public List<ConversationContext.Message> build(List<ConversationContext.Message> rawMessages,
                                                    String input, ConversationSummary summary,
                                                    int recentMessageCount) {
        List<ConversationContext.Message> committed = rawMessages.subList(1, rawMessages.size());
        int recentStart = Math.max(0, committed.size() - recentMessageCount);
        var messages = new ArrayList<ConversationContext.Message>();
        messages.add(rawMessages.get(0));
        if (recentStart > 0) {
            if (summary == null) {
                throw new IllegalArgumentException("Summary is required for older conversation messages.");
            }
            messages.add(new ConversationContext.Message("system", summary.summary()));
        }
        messages.addAll(committed.subList(recentStart, committed.size()));
        messages.add(new ConversationContext.Message("user", input));
        return List.copyOf(messages);
    }
}
