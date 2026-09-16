package dev.aiadvent.worker.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentMemoryStoreTest {
    @TempDir
    Path directory;

    @Test
    void isolatesScopesAndRestoresAllOfThemAfterRestart() throws Exception {
        UUID dialogA = UUID.randomUUID();
        UUID dialogB = UUID.randomUUID();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        AgentMemoryStore store = store();

        store.upsert(dialogA, taskA, AgentMemory.Scope.SHORT_TERM, "codeword", "SATURN");
        store.upsert(dialogA, taskA, AgentMemory.Scope.WORKING, "database", "PostgreSQL");
        store.upsert(dialogA, taskA, AgentMemory.Scope.LONG_TERM, "language", "Java");

        AgentMemory.Snapshot sameTask = new AgentMemoryStore(directory, json()).load(dialogB, taskA);
        assertTrue(sameTask.shortTerm().isEmpty());
        assertEquals("PostgreSQL", sameTask.working().get("database"));
        assertEquals("Java", sameTask.longTerm().get("language"));
        AgentMemory.Snapshot otherTask = new AgentMemoryStore(directory, json()).load(dialogB, taskB);
        assertTrue(otherTask.shortTerm().isEmpty());
        assertTrue(otherTask.working().isEmpty());
        assertEquals("Java", otherTask.longTerm().get("language"));
        AgentMemory.Snapshot restored = new AgentMemoryStore(directory, json()).load(dialogA, taskA);
        assertEquals("SATURN", restored.shortTerm().get("codeword"));
        assertEquals("PostgreSQL", restored.working().get("database"));
        assertEquals("Java", restored.longTerm().get("language"));
    }

    @Test
    void upsertTouchesOnlySelectedScopeAndReplacesTheSelectedKey() throws Exception {
        UUID dialogId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentMemoryStore store = store();
        store.upsert(dialogId, taskId, AgentMemory.Scope.SHORT_TERM, "key", "first");
        byte[] shortBefore = Files.readAllBytes(directory.resolve("short-term").resolve(dialogId + ".json"));

        store.upsert(dialogId, taskId, AgentMemory.Scope.WORKING, "key", "working");
        assertArrayEquals(shortBefore, Files.readAllBytes(directory.resolve("short-term").resolve(dialogId + ".json")));
        assertFalse(Files.exists(directory.resolve("long-term.json")));
        store.upsert(dialogId, taskId, AgentMemory.Scope.SHORT_TERM, "key", "second");

        AgentMemory.Snapshot snapshot = store.load(dialogId, taskId);
        assertEquals(java.util.Map.of("key", "second"), snapshot.shortTerm());
        assertEquals(java.util.Map.of("key", "working"), snapshot.working());
    }

    @Test
    void rejectsWorkingWithoutTaskAndBoundedInvalidInput() {
        AgentMemoryStore store = store();
        UUID dialogId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> store.upsert(dialogId, null,
                AgentMemory.Scope.WORKING, "key", "value"));
        assertThrows(IllegalArgumentException.class, () -> store.upsert(dialogId, null,
                AgentMemory.Scope.SHORT_TERM, " ", "value"));
        assertThrows(IllegalArgumentException.class, () -> store.upsert(dialogId, null,
                AgentMemory.Scope.LONG_TERM, "key", " "));
        assertThrows(IllegalArgumentException.class, () -> store.upsert(dialogId, null,
                AgentMemory.Scope.LONG_TERM, "x".repeat(AgentMemory.MAX_KEY_LENGTH + 1), "value"));
        assertThrows(IllegalArgumentException.class, () -> store.upsert(dialogId, null,
                AgentMemory.Scope.LONG_TERM, "key", "x".repeat(AgentMemory.MAX_VALUE_LENGTH + 1)));
    }

    @Test
    void malformedPersistedStateFailsExplicitly() throws Exception {
        UUID dialogId = UUID.randomUUID();
        Path file = directory.resolve("short-term").resolve(dialogId + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"entries\":{\"\":\"invalid\"}}");

        IOException failure = assertThrows(IOException.class, () -> store().load(dialogId, null));

        assertTrue(failure.getMessage().contains("malformed"));
    }

    private AgentMemoryStore store() {
        return new AgentMemoryStore(directory, json());
    }

    private ObjectMapper json() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
