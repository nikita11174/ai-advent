package dev.aiadvent.worker.invariant;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.task.Task;
import dev.aiadvent.worker.task.TaskService;
import dev.aiadvent.worker.task.TaskStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvariantServiceTest {
    @TempDir
    Path directory;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistsAndRestoresUserAndSelectedTaskRulesWithoutLeakingAnotherTask() throws Exception {
        TaskService tasks = tasks();
        Task first = tasks.create("Prepare Java/PostgreSQL engineering solution");
        Task second = tasks.create("Other task");
        InvariantService firstService = service(tasks);
        Invariant user = firstService.create(InvariantScope.USER, null, "Local stack", "Use Java 21.");
        Invariant task = firstService.create(InvariantScope.TASK, first.id(), "Persistence", "Keep PostgreSQL.");
        Invariant other = firstService.create(InvariantScope.TASK, second.id(), "Other", "Use SQLite.");

        InvariantService restored = service(tasks());
        assertEquals(java.util.List.of(user, other), restored.effective(second.id()));
        assertEquals(java.util.List.of(user, task), restored.effective(first.id()));
        assertEquals(java.util.List.of(user), restored.effective(null));
    }

    @Test
    void rejectsInvalidTaskReferencesAndInvalidScopeCombinations() throws Exception {
        InvariantService service = service(tasks());
        assertThrows(TaskStore.TaskNotFoundException.class,
                () -> service.create(InvariantScope.TASK, UUID.randomUUID(), "Persistence", "Keep PostgreSQL."));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(InvariantScope.TASK, null, "Persistence", "Keep PostgreSQL."));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(InvariantScope.USER, UUID.randomUUID(), "Local", "Use Java."));
    }

    private InvariantService service(TaskService tasks) {
        return new InvariantService(new InvariantStore(directory.resolve("invariants"), json), tasks);
    }

    private TaskService tasks() {
        return new TaskService(new TaskStore(directory.resolve("tasks"), json));
    }
}
