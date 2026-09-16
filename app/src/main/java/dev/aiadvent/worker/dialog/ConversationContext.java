package dev.aiadvent.worker.dialog;

import java.util.ArrayList;
import java.util.List;

public final class ConversationContext {
    private List<Message> messages;

    public ConversationContext(String systemPrompt) {
        this(List.of(new Message("system", systemPrompt)));
    }

    public ConversationContext(List<Message> messages) {
        validate(messages);
        this.messages = List.copyOf(messages);
    }

    public List<Message> withUserMessage(String input) {
        var request = new ArrayList<>(messages);
        request.add(new Message("user", input));
        return List.copyOf(request);
    }

    public List<Message> snapshot() {
        return messages;
    }

    public List<Message> withCompletedTurn(String input, String analysis) {
        var completed = new ArrayList<>(messages);
        completed.add(new Message("user", input));
        completed.add(new Message("assistant", analysis));
        return List.copyOf(completed);
    }

    public void commit(List<Message> completed) {
        if (completed.size() != messages.size() + 2
                || !completed.subList(0, messages.size()).equals(messages)) {
            throw new IllegalArgumentException("Completed turn does not extend the current conversation.");
        }
        validate(completed);
        messages = List.copyOf(completed);
    }

    public static void validate(List<Message> messages) {
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

    public record Message(String role, String content) {
    }
}
