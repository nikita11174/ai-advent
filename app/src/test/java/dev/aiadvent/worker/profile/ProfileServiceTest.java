package dev.aiadvent.worker.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.AgentMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileServiceTest {
    @TempDir
    Path directory;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsLoadsListsUpdatesAndRestoresProfilesAcrossServiceInstances() throws Exception {
        ProfileService first = service();
        Profile created = first.create("Code review", "Focus on risks.", "Concise", "Markdown");
        Profile second = first.create("Research", null, null, null);

        assertEquals(created, first.load(created.id()));
        assertEquals(2, first.list().size());

        Profile updated = first.update(created.id(), "Code review", "Focus on evidence.", "Detailed", "Bullets");
        ProfileService restored = service();

        assertEquals(updated, restored.load(created.id()));
        assertEquals("", restored.load(second.id()).instructions());
    }

    @Test
    void rejectsUnknownProfileId() {
        assertThrows(ProfileService.ProfileNotFoundException.class, () -> service().load(UUID.randomUUID()));
    }

    @Test
    void storesProfilesSeparatelyFromDialogAndMemoryData() throws Exception {
        ProfileService profiles = service();
        Profile profile = profiles.create("Personal", "Russian", "Brief", "Markdown");
        DialogStore dialogs = new DialogStore(directory.resolve("dialogs"), json);
        UUID dialogId = UUID.fromString(dialogs.create().id());
        new AgentMemoryStore(directory.resolve("memory"), json).upsert(dialogId, null,
                AgentMemory.Scope.SHORT_TERM, "fact", "value");
        dialogs.delete(dialogId.toString());

        assertEquals(profile, profiles.load(profile.id()));
        assertEquals(profile, service().load(profile.id()));
    }

    private ProfileService service() {
        return new ProfileService(new ProfileStore(directory.resolve("profiles"), json));
    }
}
