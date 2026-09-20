package dev.aiadvent.worker.task;

public enum TaskAction {
    APPROVE_PLAN,
    UPDATE_CURRENT_STEP,
    START_VALIDATION,
    ACCEPT_VALIDATION,
    VALIDATION_FAILED,
    PAUSE,
    RESUME
}
