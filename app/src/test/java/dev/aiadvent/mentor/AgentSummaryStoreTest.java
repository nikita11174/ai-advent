package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentSummaryStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsSummaryAndCoverage() throws Exception {
        var store = new AgentSummaryStore(directory, new ObjectMapper().findAndRegisterModules());
        UUID id = UUID.randomUUID();
        ConversationSummary summary = new ConversationSummary(3, "Старые решения и риски");

        store.save(id, summary);

        assertEquals(summary, store.load(id).orElseThrow());
        assertTrue(Files.exists(directory.resolve(id + ".json")));
    }

    @Test
    void malformedSummaryFailsExplicitly() throws Exception {
        var store = new AgentSummaryStore(directory, new ObjectMapper().findAndRegisterModules());
        UUID id = UUID.randomUUID();
        Files.writeString(directory.resolve(id + ".json"), "{not-json");

        assertThrows(java.io.IOException.class, () -> store.load(id));
    }
}
