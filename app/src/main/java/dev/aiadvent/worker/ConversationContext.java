package dev.aiadvent.worker;

import java.util.ArrayList;
import java.util.List;

final class ConversationContext {
    private List<Message> messages;

    ConversationContext(String systemPrompt) {
        this(List.of(new Message("system", systemPrompt)));
    }

    ConversationContext(List<Message> messages) {
        validate(messages);
        this.messages = List.copyOf(messages);
    }

    List<Message> withUserMessage(String input) {
        var request = new ArrayList<>(messages);
        request.add(new Message("user", input));
        return List.copyOf(request);
    }

    List<Message> snapshot() {
        return messages;
    }

    List<Message> withCompletedTurn(String input, String analysis) {
        var completed = new ArrayList<>(messages);
        completed.add(new Message("user", input));
        completed.add(new Message("assistant", analysis));
        return List.copyOf(completed);
    }

    void commit(List<Message> completed) {
        if (completed.size() != messages.size() + 2
                || !completed.subList(0, messages.size()).equals(messages)) {
            throw new IllegalArgumentException("Completed turn does not extend the current conversation.");
        }
        validate(completed);
        messages = List.copyOf(completed);
    }

    static void validate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Agent history must contain a system message.");
        }
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            String expectedRole = index == 0 ? "system" : index % 2 == 1 ? "user" : "assistant";
            if (message == null || !expectedRole.equals(message.role())
                    || message.content() == null || message.content().isBlank()) {
                throw new IllegalArgumentException("Agent history has invalid message ordering.");
            }
        }
    }

    record Message(String role, String content) {
    }
}
