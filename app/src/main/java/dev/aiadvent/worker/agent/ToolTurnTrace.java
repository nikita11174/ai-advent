package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolTurnTrace(String turnId, boolean toolRequested, String toolStatus, String turnStatus, String code,
                            JsonNode toolResult) {
    public ToolTurnTrace(String turnId, boolean toolRequested, String toolStatus, String turnStatus, String code) {
        this(turnId, toolRequested, toolStatus, turnStatus, code, null);
    }
}
