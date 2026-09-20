package dev.aiadvent.worker.task;

public record TaskState(TaskStage stage, String currentStep, String expectedAction, TaskStatus status, long revision) {
    public TaskState {
        if (stage == null) {
            throw new IllegalArgumentException("Task stage is required.");
        }
        if (currentStep == null || currentStep.isBlank()) {
            throw new IllegalArgumentException("Task current step is required.");
        }
        if (expectedAction == null || expectedAction.isBlank()) {
            throw new IllegalArgumentException("Task expected action is required.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Task status is required.");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("Task revision cannot be negative.");
        }
        if ((stage == TaskStage.DONE) != (status == TaskStatus.COMPLETED)) {
            throw new IllegalArgumentException("DONE tasks must be completed.");
        }
        currentStep = currentStep.trim();
        expectedAction = expectedAction.trim();
    }
}
