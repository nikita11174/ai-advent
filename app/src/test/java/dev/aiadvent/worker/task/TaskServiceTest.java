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
import java.util.List;

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
        Task validation = tasks.apply(created.id(), new TaskCommand.StartValidation("Implementation completed."), 1);
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
        Task validation = validationTasks.apply(validationId, new TaskCommand.StartValidation("Implementation completed."), 1);
        assertPauseResume(TaskStage.VALIDATION, validation);
    }

    @Test
    void rejectsCompletedAndStaleOrInvalidCommandsWithoutWriting() throws Exception {
        TaskService tasks = service();
        UUID id = tasks.create("Finish task").id();
        tasks.apply(id, new TaskCommand.ApprovePlan("Approved plan"), 0);
        tasks.apply(id, new TaskCommand.StartValidation("Implementation completed."), 1);
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
        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(id, new TaskCommand.ApprovePlan("again"), completed.state().revision()));
        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(id, new TaskCommand.StartValidation("again"), completed.state().revision()));
        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(id, new TaskCommand.AcceptValidation("again"), completed.state().revision()));
        assertThrows(TaskService.TaskTransitionException.class,
                () -> tasks.apply(id, new TaskCommand.ValidationFailed("again"), completed.state().revision()));
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
    void requiresFreshExecutionResultAfterValidationFailure() throws Exception {
        TaskService tasks = service();
        UUID id = tasks.create("Rework task").id();
        Task execution = tasks.apply(id, new TaskCommand.ApprovePlan("Approved plan"), 0);

        assertThrows(IllegalArgumentException.class,
                () -> tasks.apply(id, new TaskCommand.StartValidation(" "), execution.state().revision()));
        assertUnchanged(tasks, execution);
        Task validation = tasks.apply(id, new TaskCommand.StartValidation("First result"), 1);
        Task rework = tasks.apply(id, new TaskCommand.ValidationFailed("Concurrent update failed"), 2);

        assertEquals(TaskStage.EXECUTION, rework.state().stage());
        assertEquals("Rework required: Concurrent update failed", rework.state().currentStep());
        assertEquals("Approved plan", rework.approvedPlan());
        assertEquals("", rework.executionResult());
        assertEquals("", rework.validationEvidence());
        assertEquals(3, rework.state().revision());
        assertThrows(IllegalArgumentException.class,
                () -> tasks.apply(id, new TaskCommand.StartValidation(" "), rework.state().revision()));
        assertUnchanged(tasks, rework);
        Task secondValidation = tasks.apply(id, new TaskCommand.StartValidation("Fixed result"), 3);
        Task completed = tasks.apply(id, new TaskCommand.AcceptValidation("Checks passed"), 4);
        assertEquals("Fixed result", secondValidation.executionResult());
        assertEquals(TaskStage.DONE, completed.state().stage());
    }

    @Test
    void exposesOnlyActionsAllowedByTheSamePolicy() throws Exception {
        TaskService tasks = service();
        Task planning = tasks.create("Policy task");
        assertEquals(List.of(TaskAction.APPROVE_PLAN, TaskAction.UPDATE_CURRENT_STEP, TaskAction.PAUSE), tasks.allowedActions(planning));
        Task execution = tasks.apply(planning.id(), new TaskCommand.ApprovePlan("Plan"), 0);
        assertEquals(List.of(TaskAction.START_VALIDATION, TaskAction.UPDATE_CURRENT_STEP, TaskAction.PAUSE), tasks.allowedActions(execution));
        Task validation = tasks.apply(execution.id(), new TaskCommand.StartValidation("Result"), 1);
        assertEquals(List.of(TaskAction.ACCEPT_VALIDATION, TaskAction.VALIDATION_FAILED, TaskAction.UPDATE_CURRENT_STEP, TaskAction.PAUSE),
                tasks.allowedActions(validation));
        Task paused = tasks.apply(validation.id(), new TaskCommand.Pause(), 2);
        assertEquals(List.of(TaskAction.RESUME), tasks.allowedActions(paused));
        Task resumed = tasks.apply(paused.id(), new TaskCommand.Resume(), 3);
        Task completed = tasks.apply(resumed.id(), new TaskCommand.AcceptValidation("Evidence"), 4);
        assertEquals(List.of(), tasks.allowedActions(completed));
    }

    @Test
    void rejectsASecondDialogActionWithTheRevisionItReadBeforeAnotherAction() throws Exception {
        TaskService tasks = service();
        Task readByA = tasks.create("Shared task");
        Task readByB = tasks.load(readByA.id());

        Task advancedByA = tasks.apply(readByA.id(), new TaskCommand.ApprovePlan("Plan"), readByA.state().revision());

        TaskService.TaskRevisionMismatchException stale = assertThrows(TaskService.TaskRevisionMismatchException.class,
                () -> tasks.apply(readByB.id(), new TaskCommand.Pause(), readByB.state().revision()));
        assertEquals("STALE_REVISION", stale.code());
        assertEquals(advancedByA.state().revision(), stale.actualRevision());
        assertUnchanged(tasks, advancedByA);
    }

    @Test
    void loadsLegacyTaskWithoutExecutionResultAndAllowsExistingValidationToComplete() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(id + ".json"), """
                {"id":"%s","goal":"Legacy validation","state":{"stage":"VALIDATION","currentStep":"Validate","expectedAction":"Accept validation","status":"ACTIVE","revision":2},"approvedPlan":"Plan","validationEvidence":"","createdAt":"%s","updatedAt":"%s"}
                """.formatted(id, now, now));

        Task legacy = service().load(id);
        Task completed = service().apply(id, new TaskCommand.AcceptValidation("Legacy evidence"), 2);

        assertEquals("", legacy.executionResult());
        assertEquals(TaskStage.DONE, completed.state().stage());
        assertEquals("Legacy evidence", completed.validationEvidence());
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
        assertEquals(task.executionResult(), paused.executionResult());
        assertEquals(task.validationEvidence(), paused.validationEvidence());
        assertEquals(TaskStatus.ACTIVE, resumed.state().status());
        assertEquals(task.state().currentStep(), resumed.state().currentStep());
        assertEquals(task.state().expectedAction(), resumed.state().expectedAction());
        assertEquals(task.approvedPlan(), resumed.approvedPlan());
        assertEquals(task.executionResult(), resumed.executionResult());
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
