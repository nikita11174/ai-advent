package dev.aiadvent.worker.api;

import dev.aiadvent.worker.task.Task;
import dev.aiadvent.worker.task.TaskCommand;
import dev.aiadvent.worker.task.TaskAction;
import dev.aiadvent.worker.task.TaskService;
import dev.aiadvent.worker.task.TaskStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    List<TaskResponse> list() throws IOException {
        return tasks.list().stream().map(this::response).toList();
    }

    @GetMapping("/{id}")
    TaskResponse load(@PathVariable UUID id) throws IOException {
        return response(tasks.load(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskResponse create(@RequestBody CreateRequest request) throws IOException {
        return response(tasks.create(request.goal()));
    }

    @PostMapping("/adopt")
    @ResponseStatus(HttpStatus.CREATED)
    TaskResponse adopt(@RequestBody AdoptRequest request) throws IOException {
        return response(tasks.adopt(request.id(), request.goal()));
    }

    @PostMapping("/{id}/actions")
    TaskResponse apply(@PathVariable UUID id, @RequestBody ActionRequest request) throws IOException {
        if (request == null || request.expectedRevision() == null) {
            throw new IllegalArgumentException("Expected revision is required.");
        }
        return response(tasks.apply(id, command(request), request.expectedRevision()));
    }

    @ExceptionHandler(TaskStore.TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(TaskStore.TaskNotFoundException exception) {
        return new ApiError(null, exception.getMessage(), null);
    }

    @ExceptionHandler(TaskService.TaskRevisionMismatchException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError stale(TaskService.TaskRevisionMismatchException exception) {
        return new ApiError(exception.code(), exception.getMessage(), exception.actualRevision());
    }

    @ExceptionHandler(TaskService.TaskTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError transition(TaskService.TaskTransitionException exception) {
        return new ApiError(exception.code(), exception.getMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidRequest(IllegalArgumentException exception) {
        return new ApiError(null, exception.getMessage(), null);
    }

    record CreateRequest(String goal) {
    }

    record AdoptRequest(UUID id, String goal) {
    }

    record ActionRequest(TaskAction action, Long expectedRevision, String approvedPlan, String currentStep,
                         String executionResult, String validationEvidence, String failureReason) {
    }

    record ApiError(String code, String error, Long actualRevision) {
    }

    private static TaskCommand command(ActionRequest request) {
        if (request == null || request.action() == null) {
            throw new IllegalArgumentException("Task action is required.");
        }
        return switch (request.action()) {
            case APPROVE_PLAN -> new TaskCommand.ApprovePlan(request.approvedPlan());
            case UPDATE_CURRENT_STEP -> new TaskCommand.UpdateCurrentStep(request.currentStep());
            case START_VALIDATION -> new TaskCommand.StartValidation(request.executionResult());
            case ACCEPT_VALIDATION -> new TaskCommand.AcceptValidation(request.validationEvidence());
            case VALIDATION_FAILED -> new TaskCommand.ValidationFailed(request.failureReason());
            case PAUSE -> new TaskCommand.Pause();
            case RESUME -> new TaskCommand.Resume();
        };
    }

    private TaskResponse response(Task task) {
        return new TaskResponse(task.id(), task.goal(), task.state(), task.approvedPlan(), task.executionResult(),
                task.validationEvidence(), task.createdAt(), task.updatedAt(), tasks.allowedActions(task));
    }

    record TaskResponse(UUID id, String goal, dev.aiadvent.worker.task.TaskState state, String approvedPlan,
                        String executionResult, String validationEvidence, Instant createdAt, Instant updatedAt,
                        List<TaskAction> allowedActions) {
    }
}
