package dev.aiadvent.worker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.AgentMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServiceTest {
    @TempDir
    Path directory;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsPlanningTaskAndRestoresItFromAnotherServiceInstance() throws Exception {
        TaskService first = service();

        Task created = first.create("Prepare Java/PostgreSQL engineering solution");
        Task restored = service().load(created.id());

        assertEquals(created, restored);
        assertNotNull(created.id());
        assertEquals(TaskStage.PLANNING, created.state().stage());
        assertEquals(TaskStatus.ACTIVE, created.state().status());
        assertEquals(0, created.state().revision());
        assertTrue(Files.exists(directory.resolve(created.id() + ".json")));
    }

    @Test
    void persistsHappyPathArtifactsAndCompletion() throws Exception {
        TaskService tasks = service();
        Task created = tasks.create("Prepare Java/PostgreSQL engineering solution");

        Task execution = tasks.apply(created.id(), new TaskCommand.ApprovePlan("Keep PostgreSQL persistence."), 0);
        Task validation = tasks.apply(created.id(), new TaskCommand.StartValidation(), 1);
        Task completed = tasks.apply(created.id(), new TaskCommand.AcceptValidation("All checks passed."), 2);
        Task restored = service().load(created.id());

        assertEquals(TaskStage.EXECUTION, execution.state().stage());
        assertEquals(1, execution.state().revision());
        assertEquals("Keep PostgreSQL persistence.", validation.approvedPlan());
        assertEquals(TaskStage.VALIDATION, validation.state().stage());
        assertEquals(2, validation.state().revision());
        assertEquals(TaskStage.DONE, completed.state().stage());
        assertEquals(TaskStatus.COMPLETED, completed.state().status());
        assertEquals(3, completed.state().revision());
        assertEquals("All checks passed.", completed.validationEvidence());
        assertEquals(completed, restored);
    }

    @Test
    void persistsCurrentStepAndExpectedAction() throws Exception {
        TaskService tasks = service();
        Task created = tasks.create("Prepare solution");

        Task updated = tasks.apply(created.id(), new TaskCommand.UpdateCurrentStep("Draft the implementation plan"), 0);
        Task restored = service().load(created.id());

        assertEquals("Draft the implementation plan", updated.state().currentStep());
        assertEquals(created.state().expectedAction(), updated.state().expectedAction());
        assertEquals(updated.state(), restored.state());
    }

    @Test
    void pausesAndResumesEachUnfinishedStageWithoutLosingProgress() throws Exception {
        assertPauseResume(TaskStage.PLANNING, service().create("Plan task"));

        TaskService executionTasks = service();
        Task execution = executionTasks.apply(executionTasks.create("Execute task").id(),
                new TaskCommand.ApprovePlan("Approved plan"), 0);
        assertPauseResume(TaskStage.EXECUTION, execution);

        TaskService validationTasks = service();
        UUID validationId = validationTasks.create("Validate task").id();
        validationTasks.apply(validationId, new TaskCommand.ApprovePlan("Approved plan"), 0);
        Task validation = validationTasks.apply(validationId, new TaskCommand.StartValidation(), 1);
        assertPauseResume(TaskStage.VALIDATION, validation);
    }

    @Test
    void rejectsCompletedAndStaleOrInvalidCommandsWithoutWriting() throws Exception {
        TaskService tasks = service();
        UUID id = tasks.create("Finish task").id();
        tasks.apply(id, new TaskCommand.ApprovePlan("Approved plan"), 0);
        tasks.apply(id, new TaskCommand.StartValidation(), 1);
        Task completed = tasks.apply(id, new TaskCommand.AcceptValidation("Passed"), 2);

        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(id, new TaskCommand.Pause(), completed.state().revision()));
        assertUnchanged(tasks, completed);
        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(id, new TaskCommand.Resume(), completed.state().revision()));
        assertUnchanged(tasks, completed);
        assertThrows(TaskService.TaskRevisionMismatchException.class,
                () -> tasks.apply(id, new TaskCommand.UpdateCurrentStep("stale"), 0));
        assertUnchanged(tasks, completed);
    }

    @Test
    void rejectsHappyPathCommandsOutsideTheirStageWithoutPartialWrite() throws Exception {
        TaskService tasks = service();
        Task created = tasks.create("Plan task");

        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(created.id(), new TaskCommand.AcceptValidation("none"), 0));
        assertUnchanged(tasks, created);
        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(created.id(), new TaskCommand.Resume(), 0));
        assertUnchanged(tasks, created);
    }

    @Test
    void explicitlyAdoptsOneLegacyUuidWithoutCreatingAnotherTask() throws Exception {
        TaskService tasks = service();
        UUID legacyScope = UUID.randomUUID();
        AgentMemoryStore memories = new AgentMemoryStore(directory.resolve("memory"), json);
        UUID dialogId = UUID.randomUUID();
        memories.upsert(dialogId, legacyScope, AgentMemory.Scope.WORKING, "database", "PostgreSQL");
        Path workingFile = directory.resolve("memory").resolve("working").resolve(legacyScope + ".json");
        String beforeAdoption = Files.readString(workingFile);

        Task adopted = tasks.adopt(legacyScope, "Adopt legacy memory scope");

        assertEquals(legacyScope, adopted.id());
        assertThrows(IllegalArgumentException.class, () -> tasks.adopt(legacyScope, "Duplicate"));
        assertEquals(beforeAdoption, Files.readString(workingFile));
    }

    @Test
    void rejectsMalformedPersistedTaskDocument() throws Exception {
        UUID id = UUID.randomUUID();
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(id + ".json"), "{}");

        assertThrows(java.io.IOException.class, () -> service().load(id));
    }

    private void assertPauseResume(TaskStage expectedStage, Task task) throws Exception {
        TaskService tasks = service();
        Task paused = tasks.apply(task.id(), new TaskCommand.Pause(), task.state().revision());
        Task resumed = tasks.apply(task.id(), new TaskCommand.Resume(), paused.state().revision());

        assertEquals(expectedStage, paused.state().stage());
        assertEquals(TaskStatus.PAUSED, paused.state().status());
        assertEquals(task.state().currentStep(), paused.state().currentStep());
        assertEquals(task.state().expectedAction(), paused.state().expectedAction());
        assertEquals(task.approvedPlan(), paused.approvedPlan());
        assertEquals(task.validationEvidence(), paused.validationEvidence());
        assertEquals(TaskStatus.ACTIVE, resumed.state().status());
        assertEquals(task.state().currentStep(), resumed.state().currentStep());
        assertEquals(task.state().expectedAction(), resumed.state().expectedAction());
        assertEquals(task.approvedPlan(), resumed.approvedPlan());
        assertEquals(task.validationEvidence(), resumed.validationEvidence());
        assertEquals(task.state().revision() + 2, resumed.state().revision());
    }

    private void assertUnchanged(TaskService tasks, Task expected) throws Exception {
        assertEquals(expected, tasks.load(expected.id()));
    }

    private TaskService service() {
        return new TaskService(new TaskStore(directory, json), clock);
    }
}
