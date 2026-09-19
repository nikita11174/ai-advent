package dev.aiadvent.worker.api;

import dev.aiadvent.worker.profile.Profile;
import dev.aiadvent.worker.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ProfileService profiles;

    @Test
    void listsAndLoadsProfiles() throws Exception {
        Profile first = profile("Code review");
        Profile second = profile("Research");
        when(profiles.list()).thenReturn(List.of(first, second));
        when(profiles.load(first.id())).thenReturn(first);

        mvc.perform(get("/api/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].name").value("Research"));
        mvc.perform(get("/api/profiles/{id}", first.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.id().toString()));
    }

    @Test
    void createsAndUpdatesProfile() throws Exception {
        Profile created = profile("Code review");
        Profile updated = new Profile(created.id(), "Code review", "Evidence", "Detailed", "Bullets");
        when(profiles.create("Code review", "Risks", "Brief", "Markdown")).thenReturn(created);
        when(profiles.update(created.id(), "Code review", "Evidence", "Detailed", "Bullets")).thenReturn(updated);

        mvc.perform(post("/api/profiles").contentType("application/json")
                        .content("{\"name\":\"Code review\",\"instructions\":\"Risks\",\"responseStyle\":\"Brief\",\"responseFormat\":\"Markdown\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Code review"));
        mvc.perform(put("/api/profiles/{id}", created.id()).contentType("application/json")
                        .content("{\"name\":\"Code review\",\"instructions\":\"Evidence\",\"responseStyle\":\"Detailed\",\"responseFormat\":\"Bullets\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStyle").value("Detailed"));
    }

    @Test
    void returnsNotFoundForUnknownProfile() throws Exception {
        UUID id = UUID.randomUUID();
        when(profiles.load(id)).thenThrow(new ProfileService.ProfileNotFoundException(id));

        mvc.perform(get("/api/profiles/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Profile not found: " + id));
    }

    private static Profile profile(String name) {
        return new Profile(UUID.randomUUID(), name, "Instructions", "Brief", "Markdown");
    }
}
