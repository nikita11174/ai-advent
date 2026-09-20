package dev.aiadvent.worker.task;

public sealed interface TaskCommand permits TaskCommand.ApprovePlan, TaskCommand.UpdateCurrentStep,
        TaskCommand.StartValidation, TaskCommand.AcceptValidation, TaskCommand.ValidationFailed, TaskCommand.Pause, TaskCommand.Resume {

    record ApprovePlan(String approvedPlan) implements TaskCommand {
        public ApprovePlan {
            requireText(approvedPlan, "Approved plan");
            approvedPlan = approvedPlan.trim();
        }
    }

    record UpdateCurrentStep(String currentStep) implements TaskCommand {
        public UpdateCurrentStep {
            requireText(currentStep, "Current step");
            currentStep = currentStep.trim();
        }
    }

    record StartValidation(String executionResult) implements TaskCommand {
        public StartValidation {
            requireText(executionResult, "Execution result");
            executionResult = executionResult.trim();
        }
    }

    record AcceptValidation(String validationEvidence) implements TaskCommand {
        public AcceptValidation {
            requireText(validationEvidence, "Validation evidence");
            validationEvidence = validationEvidence.trim();
        }
    }

    record ValidationFailed(String reason) implements TaskCommand {
        public ValidationFailed {
            requireText(reason, "Validation failure reason");
            reason = reason.trim();
        }
    }

    record Pause() implements TaskCommand {
    }

    record Resume() implements TaskCommand {
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
    }
}
