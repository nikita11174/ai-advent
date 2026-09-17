package dev.aiadvent.worker.review;

import java.util.List;

public record ControlledReview(String summary, List<Finding> findings, String recommendation) {
    record Finding(String severity, String title, String reason) {
    }
}
