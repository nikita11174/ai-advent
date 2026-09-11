package dev.aiadvent.mentor;

import java.util.ArrayList;
import java.util.List;

final class ConversationContext {
    private final List<Message> messages = new ArrayList<>();

    ConversationContext(String systemPrompt) {
        messages.add(new Message("system", systemPrompt));
    }

    List<Message> withUserMessage(String input) {
        var request = new ArrayList<>(messages);
        request.add(new Message("user", input));
        return List.copyOf(request);
    }

    void commit(String input, String analysis) {
        messages.add(new Message("user", input));
        messages.add(new Message("assistant", analysis));
    }

    record Message(String role, String content) {
    }
}
