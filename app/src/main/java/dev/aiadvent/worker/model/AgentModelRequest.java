package dev.aiadvent.worker.model;

import java.util.List;

public record AgentModelRequest(List<AgentModelMessage> messages, String model, Double temperature, Integer maxTokens) {
    public AgentModelRequest {
        messages = List.copyOf(messages);
    }
}
