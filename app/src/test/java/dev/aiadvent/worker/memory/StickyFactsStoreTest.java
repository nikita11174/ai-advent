package dev.aiadvent.worker.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StickyFactsStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsCoverageAndOrderedFactsAcrossStoreInstances() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        UUID id = UUID.randomUUID();
        var facts = new StickyFacts(3, Map.of("project", "Helios", "deadline", "31 October"));

        new StickyFactsStore(directory, json).save(id, facts);

        assertEquals(facts, new StickyFactsStore(directory, json).load(id).orElseThrow());
    }

    @Test
    void malformedFactsFailExplicitly() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        UUID id = UUID.randomUUID();
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(id + ".json"), "{not-json");

        assertThrows(java.io.IOException.class, () -> new StickyFactsStore(directory, json).load(id));
    }
}
