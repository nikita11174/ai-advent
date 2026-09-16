package dev.aiadvent.worker;

import java.util.List;

record ControlledReview(String summary, List<Finding> findings, String recommendation) {
    record Finding(String severity, String title, String reason) {
    }
}
