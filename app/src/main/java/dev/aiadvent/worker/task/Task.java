package dev.aiadvent.worker.task;

import java.time.Instant;
import java.util.UUID;

public record Task(UUID id, String goal, TaskState state, String approvedPlan, String executionResult, String validationEvidence,
                   Instant createdAt, Instant updatedAt) {
    public Task(UUID id, String goal, TaskState state, String approvedPlan, String validationEvidence,
                Instant createdAt, Instant updatedAt) {
        this(id, goal, state, approvedPlan, "", validationEvidence, createdAt, updatedAt);
    }

    public Task {
        if (id == null) {
            throw new IllegalArgumentException("Task id is required.");
        }
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("Task goal is required.");
        }
        if (state == null) {
            throw new IllegalArgumentException("Task state is required.");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Task timestamps are required.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Task update time cannot precede creation.");
        }
        goal = goal.trim();
        approvedPlan = emptyWhenMissing(approvedPlan);
        executionResult = emptyWhenMissing(executionResult);
        validationEvidence = emptyWhenMissing(validationEvidence);
    }

    private static String emptyWhenMissing(String value) {
        return value == null ? "" : value;
    }
}
