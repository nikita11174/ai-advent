package dev.aiadvent.worker.profile;

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
public class ProfileStore {
    private final Path directory;
    private final ObjectMapper json;

    @Autowired
    public ProfileStore(@Value("${mentor.profiles.directory:docs/local/profiles}") Path directory, ObjectMapper json) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
    }

    public synchronized List<Profile> list() throws IOException {
        Files.createDirectories(directory);
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readUnchecked)
                    .sorted(Comparator.comparing(Profile::name))
                    .toList();
        }
    }

    public synchronized Profile load(UUID id) throws IOException {
        Path source = path(id);
        if (!Files.exists(source)) {
            throw new ProfileNotFoundException(id);
        }
        return read(source);
    }

    public synchronized Profile create(Profile profile) throws IOException {
        Path destination = path(profile.id());
        if (Files.exists(destination)) {
            throw new IllegalArgumentException("Profile already exists: " + profile.id());
        }
        write(destination, profile);
        return profile;
    }

    public synchronized Profile update(Profile profile) throws IOException {
        Path destination = path(profile.id());
        if (!Files.exists(destination)) {
            throw new ProfileNotFoundException(profile.id());
        }
        write(destination, profile);
        return profile;
    }

    private Profile read(Path source) throws IOException {
        try {
            return json.readValue(source.toFile(), Profile.class);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Profile document is malformed: " + source.getFileName() + ".", exception);
        }
    }

    private Profile readUnchecked(Path source) {
        try {
            return read(source);
        } catch (IOException exception) {
            throw new ProfileStorageException(exception);
        }
    }

    private void write(Path destination, Profile profile) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, profile.id().toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), profile);
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
            throw new IllegalArgumentException("Profile id is required.");
        }
        Path path = directory.resolve(id + ".json").normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid profile id.");
        }
        return path;
    }

    public static class ProfileNotFoundException extends IllegalArgumentException {
        public ProfileNotFoundException(UUID id) {
            super("Profile not found: " + id);
        }
    }

    static class ProfileStorageException extends RuntimeException {
        ProfileStorageException(IOException cause) {
            super(cause);
        }
    }
}
