package dev.aiadvent.worker.profile;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {
    private final ProfileStore profiles;

    public ProfileService(ProfileStore profiles) {
        this.profiles = profiles;
    }

    public List<Profile> list() throws IOException {
        return profiles.list();
    }

    public Profile load(UUID id) throws IOException {
        try {
            return profiles.load(id);
        } catch (ProfileStore.ProfileNotFoundException exception) {
            throw new ProfileNotFoundException(id);
        }
    }

    public Profile create(String name, String instructions, String responseStyle, String responseFormat) throws IOException {
        return profiles.create(new Profile(UUID.randomUUID(), name, instructions, responseStyle, responseFormat));
    }

    public Profile update(UUID id, String name, String instructions, String responseStyle, String responseFormat)
            throws IOException {
        try {
            return profiles.update(new Profile(id, name, instructions, responseStyle, responseFormat));
        } catch (ProfileStore.ProfileNotFoundException exception) {
            throw new ProfileNotFoundException(id);
        }
    }

    public static class ProfileNotFoundException extends IllegalArgumentException {
        public ProfileNotFoundException(UUID id) {
            super("Profile not found: " + id);
        }
    }
}
