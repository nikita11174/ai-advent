package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class AgentHistoryStore {
    private final Path directory;
    private final ObjectMapper json;

    @Autowired
    AgentHistoryStore(@Value("${mentor.agent-histories.directory:docs/local/agent-histories}") Path directory,
                      ObjectMapper json) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
    }

    Optional<List<ConversationContext.Message>> load(UUID dialogId) throws IOException {
        Path path = path(dialogId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            HistoryDocument document = json.readValue(path.toFile(), HistoryDocument.class);
            ConversationContext.validate(document.messages());
            return Optional.of(List.copyOf(document.messages()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Agent history is malformed.", exception);
        }
    }

    void save(UUID dialogId, List<ConversationContext.Message> messages) throws IOException {
        ConversationContext.validate(messages);
        ensureDirectory();
        Path destination = path(dialogId);
        Path temporary = Files.createTempFile(directory, dialogId.toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), new HistoryDocument(messages));
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

    private void ensureDirectory() throws IOException {
        Files.createDirectories(directory);
    }

    record HistoryDocument(List<ConversationContext.Message> messages) {
    }
}
