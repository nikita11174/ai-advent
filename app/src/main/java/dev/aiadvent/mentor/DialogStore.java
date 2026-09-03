package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
class DialogStore {
    private final Path directory;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    DialogStore(@Value("${mentor.dialogs.directory:docs/local/mentor-dialogs}") Path directory,
                ObjectMapper json) {
        this(directory, json, Clock.systemUTC());
    }

    DialogStore(Path directory, ObjectMapper json, Clock clock) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
        this.clock = clock;
    }

    synchronized DialogDocument create() throws IOException {
        Instant now = clock.instant();
        var state = json.createObjectNode();
        state.putArray("exchanges");
        DialogDocument dialog = new DialogDocument(UUID.randomUUID().toString(), "Новый диалог", now, now,
                state);
        write(dialog);
        return dialog;
    }

    synchronized List<DialogSummary> list() throws IOException {
        ensureDirectory();
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readUnchecked)
                    .map(dialog -> new DialogSummary(dialog.id(), dialog.title(), dialog.createdAt(), dialog.updatedAt()))
                    .sorted(Comparator.comparing(DialogSummary::updatedAt).reversed())
                    .toList();
        }
    }

    synchronized DialogDocument load(String id) throws IOException {
        Path path = path(id);
        if (!Files.exists(path)) {
            throw new DialogNotFoundException(id);
        }
        return json.readValue(path.toFile(), DialogDocument.class);
    }

    synchronized DialogDocument update(String id, DialogUpdate update) throws IOException {
        DialogDocument existing = load(id);
        String title = update.title() == null || update.title().isBlank() ? existing.title() : update.title().trim();
        JsonNode state = update.state() == null ? existing.state() : update.state();
        DialogDocument updated = new DialogDocument(existing.id(), title, existing.createdAt(), clock.instant(), state);
        write(updated);
        return updated;
    }

    private void write(DialogDocument dialog) throws IOException {
        ensureDirectory();
        Path destination = path(dialog.id());
        Path temporary = Files.createTempFile(directory, dialog.id(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), dialog);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path(String id) {
        UUID.fromString(id);
        Path path = directory.resolve(id + ".json").normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid dialog id.");
        }
        return path;
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(directory);
    }

    private DialogDocument readUnchecked(Path path) {
        try {
            return json.readValue(path.toFile(), DialogDocument.class);
        } catch (IOException exception) {
            throw new DialogStorageException(exception);
        }
    }

    record DialogDocument(String id, String title, Instant createdAt, Instant updatedAt, JsonNode state) {
    }

    record DialogSummary(String id, String title, Instant createdAt, Instant updatedAt) {
    }

    record DialogUpdate(String title, JsonNode state) {
    }

    static class DialogNotFoundException extends IllegalArgumentException {
        DialogNotFoundException(String id) {
            super("Dialog not found: " + id);
        }
    }

    static class DialogStorageException extends RuntimeException {
        DialogStorageException(IOException cause) {
            super(cause);
        }
    }
}
