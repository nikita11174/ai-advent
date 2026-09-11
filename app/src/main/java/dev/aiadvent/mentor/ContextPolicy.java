package dev.aiadvent.mentor;

import java.util.List;

interface ContextPolicy {
    List<ConversationContext.Message> build(List<ConversationContext.Message> rawMessages,
                                            String input, ConversationSummary summary,
                                            int recentMessageCount);
}
