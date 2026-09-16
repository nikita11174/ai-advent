package dev.aiadvent.worker.memory;

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
import java.util.Optional;
import java.util.UUID;

@Component
public class StickyFactsStore {
    private final Path directory;
    private final ObjectMapper json;

    @Autowired
    public StickyFactsStore(@Value("${mentor.sticky-facts.directory:docs/local/agent-facts}") Path directory,
                     ObjectMapper json) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
    }

    public Optional<StickyFacts> load(UUID dialogId) throws IOException {
        Path path = path(dialogId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            FactsDocument document = json.readValue(path.toFile(), FactsDocument.class);
            if (document == null) {
                throw new IllegalArgumentException("Facts document is empty.");
            }
            return Optional.of(new StickyFacts(document.coveredUserMessageCount(), document.facts()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Sticky facts are malformed.", exception);
        }
    }

    public void save(UUID dialogId, StickyFacts facts) throws IOException {
        Files.createDirectories(directory);
        Path destination = path(dialogId);
        Path temporary = Files.createTempFile(directory, dialogId.toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),
                    new FactsDocument(facts.coveredUserMessageCount(), new LinkedHashMap<>(facts.facts())));
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path(UUID dialogId) {
        Path path = directory.resolve(dialogId + ".json").normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid dialog id.");
        }
        return path;
    }

    record FactsDocument(int coveredUserMessageCount, LinkedHashMap<String, String> facts) {
    }
}
