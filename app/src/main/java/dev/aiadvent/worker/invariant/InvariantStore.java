package dev.aiadvent.worker.invariant;

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
public class InvariantStore {
    private final Path directory;
    private final ObjectMapper json;

    @Autowired
    public InvariantStore(@Value("${mentor.invariants.directory:docs/local/invariants}") Path directory, ObjectMapper json) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
    }

    public synchronized List<Invariant> list() throws IOException {
        Files.createDirectories(directory);
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readUnchecked)
                    .sorted(Comparator.comparing(Invariant::scope).thenComparing(Invariant::id))
                    .toList();
        }
    }

    public synchronized Invariant load(UUID id) throws IOException {
        Path source = path(id);
        if (!Files.exists(source)) {
            throw new InvariantNotFoundException(id);
        }
        return read(source);
    }

    public synchronized Invariant create(Invariant invariant) throws IOException {
        Path destination = path(invariant.id());
        if (Files.exists(destination)) {
            throw new IllegalArgumentException("Invariant already exists: " + invariant.id());
        }
        write(destination, invariant);
        return invariant;
    }

    public synchronized Invariant update(Invariant invariant) throws IOException {
        Path destination = path(invariant.id());
        if (!Files.exists(destination)) {
            throw new InvariantNotFoundException(invariant.id());
        }
        write(destination, invariant);
        return invariant;
    }

    private Invariant read(Path source) throws IOException {
        try {
            return json.readValue(source.toFile(), Invariant.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IOException("Invariant document is malformed: " + source.getFileName() + ".", exception);
        }
    }

    private Invariant readUnchecked(Path source) {
        try {
            return read(source);
        } catch (IOException exception) {
            throw new InvariantStorageException(exception);
        }
    }

    private void write(Path destination, Invariant invariant) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, invariant.id().toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), invariant);
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
            throw new IllegalArgumentException("Invariant id is required.");
        }
        Path path = directory.resolve(id + ".json").normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid invariant id.");
        }
        return path;
    }

    public static class InvariantNotFoundException extends IllegalArgumentException {
        public InvariantNotFoundException(UUID id) {
            super("Invariant not found: " + id);
        }
    }

    static class InvariantStorageException extends RuntimeException {
        InvariantStorageException(IOException cause) {
            super(cause);
        }
    }
}
