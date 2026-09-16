package dev.aiadvent.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Component
class AgentSummaryStore {
    private final Path directory;
    private final ObjectMapper json;

    @Autowired
    AgentSummaryStore(@Value("${mentor.agent-summaries.directory:docs/local/agent-summaries}") Path directory,
                      ObjectMapper json) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
    }

    Optional<ConversationSummary> load(UUID dialogId) throws IOException {
        Path path = path(dialogId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            SummaryDocument document = json.readValue(path.toFile(), SummaryDocument.class);
            if (document == null) {
                throw new IllegalArgumentException("Summary document is empty.");
            }
            return Optional.of(new ConversationSummary(document.summarizedMessageCount(), document.summary()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Agent summary is malformed.", exception);
        }
    }

    void save(UUID dialogId, ConversationSummary summary) throws IOException {
        Path destination = path(dialogId);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, dialogId.toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),
                    new SummaryDocument(summary.summarizedMessageCount(), summary.summary()));
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

    record SummaryDocument(int summarizedMessageCount, String summary) {
    }
}
