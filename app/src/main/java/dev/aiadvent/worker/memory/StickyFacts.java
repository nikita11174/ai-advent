package dev.aiadvent.worker.memory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record StickyFacts(int coveredUserMessageCount, Map<String, String> facts) {
    public StickyFacts {
        if (coveredUserMessageCount < 0 || facts == null) {
            throw new IllegalArgumentException("Sticky facts state is invalid.");
        }
        var copy = new LinkedHashMap<String, String>();
        facts.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                throw new IllegalArgumentException("Sticky fact keys and values must not be empty.");
            }
            copy.put(key, value);
        });
        facts = Collections.unmodifiableMap(copy);
    }

    public static StickyFacts empty() {
        return new StickyFacts(0, Map.of());
    }

    public String asPrompt() {
        if (facts.isEmpty()) {
            return "Sticky facts: none";
        }
        StringBuilder result = new StringBuilder("Sticky facts:\n");
        facts.forEach((key, value) -> result.append("- ").append(key).append(": ").append(value).append('\n'));
        return result.toString().stripTrailing();
    }
}
