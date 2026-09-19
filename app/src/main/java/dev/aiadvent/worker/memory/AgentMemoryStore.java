package dev.aiadvent.worker.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentMemoryStore {
    private final Path root;
    private final ObjectMapper json;

    @Autowired
    public AgentMemoryStore(@Value("${mentor.agent-memory.directory:docs/local/agent-memory}") Path root,
                     ObjectMapper json) {
        this.root = root.toAbsolutePath().normalize();
        this.json = json;
    }

    public synchronized AgentMemory.Snapshot load(UUID dialogId, UUID taskId) throws IOException {
        return new AgentMemory.Snapshot(taskId,
                loadEntries(path(AgentMemory.Scope.SHORT_TERM, dialogId)),
                taskId == null ? Map.of() : loadEntries(path(AgentMemory.Scope.WORKING, taskId)),
                loadEntries(path(AgentMemory.Scope.LONG_TERM, null)));
    }

    public synchronized AgentMemory.Snapshot upsert(UUID dialogId, UUID taskId, AgentMemory.Scope scope,
                                             String key, String value) throws IOException {
        if (scope == null) {
            throw new IllegalArgumentException("Memory scope is required.");
        }
        if (scope == AgentMemory.Scope.WORKING && taskId == null) {
            throw new IllegalArgumentException("taskId is required for WORKING memory.");
        }
        AgentMemory.validateKey(key);
        AgentMemory.validateValue(value);
        UUID scopeId = switch (scope) {
            case SHORT_TERM -> dialogId;
            case WORKING -> taskId;
            case LONG_TERM -> null;
        };
        Path destination = path(scope, scopeId);
        var entries = new LinkedHashMap<>(loadEntries(destination));
        if (!entries.containsKey(key) && entries.size() == AgentMemory.MAX_ENTRIES) {
            throw new IllegalArgumentException("Memory scope cannot exceed " + AgentMemory.MAX_ENTRIES + " entries.");
        }
        entries.put(key, value);
        save(destination, entries);
        return load(dialogId, taskId);
    }

    public synchronized void deleteShortTerm(UUID dialogId) throws IOException {
        Files.deleteIfExists(path(AgentMemory.Scope.SHORT_TERM, dialogId));
    }

    private Map<String, String> loadEntries(Path source) throws IOException {
        if (!Files.exists(source)) {
            return Map.of();
        }
        try {
            MemoryDocument document = json.readValue(source.toFile(), MemoryDocument.class);
            if (document == null) {
                throw new IllegalArgumentException("Memory document is empty.");
            }
            return AgentMemory.immutableEntries(document.entries());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IOException("Agent memory is malformed: " + source.getFileName() + ".", exception);
        }
    }

    private void save(Path destination, Map<String, String> entries) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),
                    new MemoryDocument(new LinkedHashMap<>(entries)));
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path(AgentMemory.Scope scope, UUID id) {
        Path path = switch (scope) {
            case SHORT_TERM -> root.resolve("short-term").resolve(id + ".json");
            case WORKING -> root.resolve("working").resolve(id + ".json");
            case LONG_TERM -> root.resolve("long-term.json");
        };
        path = path.normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid memory path.");
        }
        return path;
    }

    record MemoryDocument(LinkedHashMap<String, String> entries) {
    }
}
