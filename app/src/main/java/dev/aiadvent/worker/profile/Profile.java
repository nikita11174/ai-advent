package dev.aiadvent.worker.profile;

import java.util.UUID;

public record Profile(UUID id, String name, String instructions, String responseStyle, String responseFormat) {
    public Profile {
        if (id == null) {
            throw new IllegalArgumentException("Profile id is required.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Profile name is required.");
        }
        name = name.trim();
        instructions = emptyWhenMissing(instructions);
        responseStyle = emptyWhenMissing(responseStyle);
        responseFormat = emptyWhenMissing(responseFormat);
    }

    private static String emptyWhenMissing(String value) {
        return value == null ? "" : value;
    }
}
