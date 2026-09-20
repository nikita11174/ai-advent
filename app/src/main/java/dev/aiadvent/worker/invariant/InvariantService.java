package dev.aiadvent.worker.invariant;

import dev.aiadvent.worker.task.TaskService;
import dev.aiadvent.worker.task.TaskStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class InvariantService {
    private final InvariantStore invariants;
    private final TaskService tasks;

    public InvariantService(InvariantStore invariants, TaskService tasks) {
        this.invariants = invariants;
        this.tasks = tasks;
    }

    public List<Invariant> list() throws IOException {
        return invariants.list();
    }

    public Invariant create(InvariantScope scope, UUID taskId, String name, String rule) throws IOException {
        validateTask(scope, taskId);
        return invariants.create(new Invariant(UUID.randomUUID(), scope, taskId, name, rule));
    }

    public Invariant update(UUID id, InvariantScope scope, UUID taskId, String name, String rule) throws IOException {
        validateTask(scope, taskId);
        return invariants.update(new Invariant(id, scope, taskId, name, rule));
    }

    public List<Invariant> effective(UUID taskId) throws IOException {
        if (taskId != null && tasks.find(taskId).isEmpty()) {
            throw new TaskStore.TaskNotFoundException(taskId);
        }
        return invariants.list().stream()
                .filter(invariant -> invariant.scope() == InvariantScope.USER
                        || (taskId != null && invariant.taskId().equals(taskId)))
                .sorted(Comparator.comparing(Invariant::scope).thenComparing(Invariant::id))
                .toList();
    }

    private void validateTask(InvariantScope scope, UUID taskId) throws IOException {
        if (scope == null) {
            throw new IllegalArgumentException("Invariant scope is required.");
        }
        if (scope == InvariantScope.TASK) {
            if (taskId == null) {
                throw new IllegalArgumentException("Task invariant requires taskId.");
            }
            if (tasks.find(taskId).isEmpty()) {
                throw new TaskStore.TaskNotFoundException(taskId);
            }
        } else if (taskId != null) {
            throw new IllegalArgumentException("User invariant cannot have taskId.");
        }
    }
}
