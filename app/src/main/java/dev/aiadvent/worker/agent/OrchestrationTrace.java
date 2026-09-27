package dev.aiadvent.worker.agent;

import java.util.List;

public record OrchestrationTrace(String turnId, String status, List<Step> steps) {
    public record Step(int number, String server, String tool, String status, String testId, String runId) { }
}
