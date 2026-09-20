package dev.aiadvent.worker.invariant;

import java.util.UUID;

public record Invariant(UUID id, InvariantScope scope, UUID taskId, String name, String rule) {
    public Invariant {
        if (id == null) {
            throw new IllegalArgumentException("Invariant id is required.");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Invariant scope is required.");
        }
        if (scope == InvariantScope.TASK && taskId == null) {
            throw new IllegalArgumentException("Task invariant requires taskId.");
        }
        if (scope == InvariantScope.USER && taskId != null) {
            throw new IllegalArgumentException("User invariant cannot have taskId.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Invariant name is required.");
        }
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("Invariant rule is required.");
        }
        name = name.trim();
        rule = rule.trim();
    }
}
