package dev.aiadvent.worker.task;

public sealed interface TaskCommand permits TaskCommand.ApprovePlan, TaskCommand.UpdateCurrentStep,
        TaskCommand.StartValidation, TaskCommand.AcceptValidation, TaskCommand.Pause, TaskCommand.Resume {

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

    record StartValidation() implements TaskCommand {
    }

    record AcceptValidation(String validationEvidence) implements TaskCommand {
        public AcceptValidation {
            requireText(validationEvidence, "Validation evidence");
            validationEvidence = validationEvidence.trim();
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
