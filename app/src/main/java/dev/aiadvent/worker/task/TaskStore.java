package dev.aiadvent.worker.task;

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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class TaskStore {
    private final Path directory;
    private final ObjectMapper json;

    @Autowired
    public TaskStore(@Value("${mentor.tasks.directory:docs/local/tasks}") Path directory, ObjectMapper json) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
    }

    public synchronized Task create(Task task) throws IOException {
        Path destination = path(task.id());
        if (Files.exists(destination)) {
            throw new IllegalArgumentException("Task already exists: " + task.id());
        }
        write(destination, task);
        return task;
    }

    public synchronized Task load(UUID id) throws IOException {
        Path source = path(id);
        if (!Files.exists(source)) {
            throw new TaskNotFoundException(id);
        }
        return read(source);
    }

    public synchronized List<Task> list() throws IOException {
        Files.createDirectories(directory);
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readUnchecked)
                    .sorted(Comparator.comparing(Task::updatedAt).reversed())
                    .toList();
        }
    }

    public synchronized Task update(Task task) throws IOException {
        Path destination = path(task.id());
        if (!Files.exists(destination)) {
            throw new TaskNotFoundException(task.id());
        }
        write(destination, task);
        return task;
    }

    private Task read(Path source) throws IOException {
        try {
            return json.readValue(source.toFile(), Task.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IOException("Task document is malformed: " + source.getFileName() + ".", exception);
        }
    }

    private Task readUnchecked(Path source) {
        try {
            return read(source);
        } catch (IOException exception) {
            throw new TaskStorageException(exception);
        }
    }

    private void write(Path destination, Task task) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, task.id().toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), task);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Task id is required.");
        }
        Path path = directory.resolve(id + ".json").normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid task id.");
        }
        return path;
    }

    public static class TaskNotFoundException extends IllegalArgumentException {
        public TaskNotFoundException(UUID id) {
            super("Task not found: " + id);
        }
    }

    static class TaskStorageException extends RuntimeException {
        TaskStorageException(IOException cause) {
            super(cause);
        }
    }
}
