package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentHistoryStoreTest {
    @TempDir
    Path directory;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsOrderedRawMessages() throws Exception {
        var store = new AgentHistoryStore(directory, json);
        UUID id = UUID.randomUUID();
        var messages = List.of(new ConversationContext.Message("system", "instruction"),
                new ConversationContext.Message("user", "first"), new ConversationContext.Message("assistant", "answer"),
                new ConversationContext.Message("user", "follow-up"));

        store.save(id, messages);

        assertEquals(messages, store.load(id).orElseThrow());
        String raw = Files.readString(directory.resolve(id + ".json"));
        assertTrue(raw.indexOf("\"system\"") < raw.indexOf("\"user\"")
                && raw.indexOf("\"user\"") < raw.indexOf("\"assistant\""));
    }

    @Test
    void missingHistoryIsEmptyAndMalformedHistoryFails() throws Exception {
        var store = new AgentHistoryStore(directory, json);
        assertTrue(store.load(UUID.randomUUID()).isEmpty());

        UUID malformed = UUID.randomUUID();
        Files.writeString(directory.resolve(malformed + ".json"),
                "{\"messages\":[{\"role\":\"user\",\"content\":\"invalid\"}]}");
        assertThrows(IOException.class, () -> store.load(malformed));
    }
}
