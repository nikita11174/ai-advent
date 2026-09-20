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
        requireAllowed(current, command);
        Task updated = applyCommand(current, command, clock.instant());
        return tasks.update(updated);
    }

    public List<TaskAction> allowedActions(Task task) {
        TaskState state = task.state();
        if (state.status() == TaskStatus.PAUSED) {
            return List.of(TaskAction.RESUME);
        }
        if (state.status() != TaskStatus.ACTIVE || state.stage() == TaskStage.DONE) {
            return List.of();
        }
        return switch (state.stage()) {
            case PLANNING -> List.of(TaskAction.APPROVE_PLAN, TaskAction.UPDATE_CURRENT_STEP, TaskAction.PAUSE);
            case EXECUTION -> List.of(TaskAction.START_VALIDATION, TaskAction.UPDATE_CURRENT_STEP, TaskAction.PAUSE);
            case VALIDATION -> List.of(TaskAction.ACCEPT_VALIDATION, TaskAction.VALIDATION_FAILED,
                    TaskAction.UPDATE_CURRENT_STEP, TaskAction.PAUSE);
            case DONE -> List.of();
        };
    }

    private Task create(UUID id, String goal) throws IOException {
        Instant now = clock.instant();
        Task task = new Task(id, goal, new TaskState(TaskStage.PLANNING, PLAN_STEP, APPROVE_PLAN,
                TaskStatus.ACTIVE, 0), "", "", "", now, now);
        return tasks.create(task);
    }

    private static Task applyCommand(Task task, TaskCommand command, Instant now) {
        TaskState state = task.state();
        if (command instanceof TaskCommand.ApprovePlan approvePlan) {
            return next(task, new TaskState(TaskStage.EXECUTION, EXECUTION_STEP, UPDATE_EXECUTION,
                    TaskStatus.ACTIVE, state.revision() + 1), approvePlan.approvedPlan(), task.executionResult(), task.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.UpdateCurrentStep updateCurrentStep) {
            return next(task, new TaskState(state.stage(), updateCurrentStep.currentStep(), state.expectedAction(),
                    state.status(), state.revision() + 1), task.approvedPlan(), task.executionResult(), task.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.StartValidation startValidation) {
            if (task.approvedPlan().isBlank()) {
                throw new TaskTransitionException("INVALID_TRANSITION", "Validation requires an approved plan.");
            }
            return next(task, new TaskState(TaskStage.VALIDATION, VALIDATION_STEP, ACCEPT_VALIDATION,
                    TaskStatus.ACTIVE, state.revision() + 1), task.approvedPlan(), startValidation.executionResult(), "", now);
        }
        if (command instanceof TaskCommand.AcceptValidation acceptValidation) {
            return next(task, new TaskState(TaskStage.DONE, DONE_STEP, NO_ACTION,
                    TaskStatus.COMPLETED, state.revision() + 1), task.approvedPlan(), task.executionResult(), acceptValidation.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.ValidationFailed validationFailed) {
            return next(task, new TaskState(TaskStage.EXECUTION, "Rework required: " + validationFailed.reason(), UPDATE_EXECUTION,
                    TaskStatus.ACTIVE, state.revision() + 1), task.approvedPlan(), "", "", now);
        }
        if (command instanceof TaskCommand.Pause) {
            return next(task, new TaskState(state.stage(), state.currentStep(), state.expectedAction(),
                    TaskStatus.PAUSED, state.revision() + 1), task.approvedPlan(), task.executionResult(), task.validationEvidence(), now);
        }
        if (command instanceof TaskCommand.Resume) {
            return next(task, new TaskState(state.stage(), state.currentStep(), state.expectedAction(),
                    TaskStatus.ACTIVE, state.revision() + 1), task.approvedPlan(), task.executionResult(), task.validationEvidence(), now);
        }
        throw new IllegalArgumentException("Unsupported task command.");
    }

    private static Task next(Task task, TaskState state, String approvedPlan, String executionResult, String validationEvidence, Instant now) {
        return new Task(task.id(), task.goal(), state, approvedPlan, executionResult, validationEvidence, task.createdAt(), now);
    }

    private void requireAllowed(Task task, TaskCommand command) {
        TaskAction action = action(command);
        if (task.state().status() == TaskStatus.PAUSED && action != TaskAction.RESUME) {
            throw new TaskTransitionException("PAUSED", "Task is paused; resume it before changing its lifecycle.");
        }
        if (!allowedActions(task).contains(action)) {
            throw new TaskTransitionException("INVALID_TRANSITION", action + " is not available in "
                    + task.state().stage() + ".");
        }
    }

    private static TaskAction action(TaskCommand command) {
        if (command instanceof TaskCommand.ApprovePlan) return TaskAction.APPROVE_PLAN;
        if (command instanceof TaskCommand.UpdateCurrentStep) return TaskAction.UPDATE_CURRENT_STEP;
        if (command instanceof TaskCommand.StartValidation) return TaskAction.START_VALIDATION;
        if (command instanceof TaskCommand.AcceptValidation) return TaskAction.ACCEPT_VALIDATION;
        if (command instanceof TaskCommand.ValidationFailed) return TaskAction.VALIDATION_FAILED;
        if (command instanceof TaskCommand.Pause) return TaskAction.PAUSE;
        if (command instanceof TaskCommand.Resume) return TaskAction.RESUME;
        throw new IllegalArgumentException("Unsupported task command.");
    }

    public static class TaskRevisionMismatchException extends IllegalStateException {
        private final long actualRevision;

        public TaskRevisionMismatchException(UUID taskId, long expectedRevision, long actualRevision) {
            super("Task revision is stale for " + taskId + ": expected " + expectedRevision + ", actual " + actualRevision + ".");
            this.actualRevision = actualRevision;
        }

        public String code() {
            return "STALE_REVISION";
        }

        public long actualRevision() {
            return actualRevision;
        }
    }

    public static class TaskTransitionException extends IllegalStateException {
        private final String code;

        public TaskTransitionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
