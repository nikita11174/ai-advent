package dev.aiadvent.worker.agent;

public record ToolTurnTrace(String turnId, boolean toolRequested, String toolStatus, String turnStatus, String code) {
}
