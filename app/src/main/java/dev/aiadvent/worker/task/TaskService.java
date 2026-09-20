package dev.aiadvent.worker.task;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskService {
    private static final String PLAN_STEP = "Prepare and approve a plan";
    private static final String APPROVE_PLAN = "Approve the plan";
    private static final String EXECUTION_STEP = "Execute the approved plan";
    private static final String UPDATE_EXECUTION = "Update the current step or start validation";
    private static final String VALIDATION_STEP = "Validate the execution result";
    private static final String ACCEPT_VALIDATION = "Accept validation";
    private static final String DONE_STEP = "Validation accepted";
    private static final String NO_ACTION = "No further action";

    private final TaskStore tasks;
    private final Clock clock;

    @Autowired
    public TaskService(TaskStore tasks) {
        this(tasks, Clock.systemUTC());
    }

    TaskService(TaskStore tasks, Clock clock) {
        this.tasks = tasks;
        this.clock = clock;
    }

    public Task create(String goal) throws IOException {
        return create(UUID.randomUUID(), goal);
    }

    public Task adopt(UUID id, String goal) throws IOException {
        return create(id, goal);
    }

    public Task load(UUID id) throws IOException {
        return tasks.load(id);
    }

    public List<Task> list() throws IOException {
        return tasks.list();
    }

    public Optional<Task> find(UUID id) throws IOException {
        try {
            return Optional.of(tasks.load(id));
        } catch (TaskStore.TaskNotFoundException exception) {
            return Optional.empty();
        }
    }

    public synchronized Task apply(UUID taskId, TaskCommand command, long expectedRevision) throws IOException {
        if (command == null) {
            throw new IllegalArgumentException("Task command is required.");
        }
        Task current = tasks.load(taskId);
        if (expectedRevision != current.state().revision()) {
            throw new TaskRevisionMismatchException(taskId, expectedRevision, current.state().revision());
        }
        Task updated = applyCommand(current, command, clock.instant());
        return tasks.update(updated);
    }

    private Task create(UUID id, String goal) throws IOException {
        Instant now = clock.instant();
        Task task = new Task(id, goal, new TaskState(TaskStage.PLANNING, PLAN_STEP, APPROVE_PLAN,
                TaskStatus.ACTIVE, 0), "", "", now, now);
        return tasks.create(task);
    }

    private static Task applyCommand(Task task, TaskCommand command, Instant now) {
        TaskState state = task.state();
        if (command instanceof TaskCommand.ApprovePlan approvePlan) {
            requireActiveStage(state, TaskStage.PLANNING, "Plan approval");
            return next(task, new TaskState(TaskStage.EXECUTION, EXECUTION_STEP, UPDATE_EXECUTION,
                    TaskStatus.ACTIVE, state.revision() + 1), approvePlan.approvedPlan(), task.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.UpdateCurrentStep updateCurrentStep) {
            requireActiveUnfinished(state, "Current step update");
            return next(task, new TaskState(state.stage(), updateCurrentStep.currentStep(), state.expectedAction(),
                    state.status(), state.revision() + 1), task.approvedPlan(), task.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.StartValidation) {
            requireActiveStage(state, TaskStage.EXECUTION, "Validation start");
            return next(task, new TaskState(TaskStage.VALIDATION, VALIDATION_STEP, ACCEPT_VALIDATION,
                    TaskStatus.ACTIVE, state.revision() + 1), task.approvedPlan(), task.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.AcceptValidation acceptValidation) {
            requireActiveStage(state, TaskStage.VALIDATION, "Validation acceptance");
            return next(task, new TaskState(TaskStage.DONE, DONE_STEP, NO_ACTION,
                    TaskStatus.COMPLETED, state.revision() + 1), task.approvedPlan(), acceptValidation.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.Pause) {
            requireActiveUnfinished(state, "Pause");
            return next(task, new TaskState(state.stage(), state.currentStep(), state.expectedAction(),
                    TaskStatus.PAUSED, state.revision() + 1), task.approvedPlan(), task.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.Resume) {
            if (state.stage() == TaskStage.DONE) {
                throw new TaskTransitionException("Completed tasks cannot be resumed.");
            }
            if (state.status() != TaskStatus.PAUSED) {
                throw new TaskTransitionException("Only paused tasks can be resumed.");
            }
            return next(task, new TaskState(state.stage(), state.currentStep(), state.expectedAction(),
                    TaskStatus.ACTIVE, state.revision() + 1), task.approvedPlan(), task.validationEvidence(), now);
        }
        throw new IllegalArgumentException("Unsupported task command.");
    }

    private static Task next(Task task, TaskState state, String approvedPlan, String validationEvidence, Instant now) {
        return new Task(task.id(), task.goal(), state, approvedPlan, validationEvidence, task.createdAt(), now);
    }

    private static void requireActiveStage(TaskState state, TaskStage stage, String action) {
        requireActiveUnfinished(state, action);
        if (state.stage() != stage) {
            throw new TaskTransitionException(action + " is not available in " + state.stage() + ".");
        }
    }

    private static void requireActiveUnfinished(TaskState state, String action) {
        if (state.stage() == TaskStage.DONE || state.status() == TaskStatus.COMPLETED) {
            throw new TaskTransitionException("Completed tasks cannot be changed.");
        }
        if (state.status() != TaskStatus.ACTIVE) {
            throw new TaskTransitionException(action + " requires an active task.");
        }
    }

    public static class TaskRevisionMismatchException extends IllegalStateException {
        public TaskRevisionMismatchException(UUID taskId, long expectedRevision, long actualRevision) {
            super("Task revision is stale for " + taskId + ": expected " + expectedRevision + ", actual " + actualRevision + ".");
        }
    }

    public static class TaskTransitionException extends IllegalStateException {
        public TaskTransitionException(String message) {
            super(message);
        }
    }
}
