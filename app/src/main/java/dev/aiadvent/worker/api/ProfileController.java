package dev.aiadvent.worker.api;

import dev.aiadvent.worker.profile.Profile;
import dev.aiadvent.worker.profile.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
class ProfileController {
    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    List<Profile> list() throws IOException {
        return profiles.list();
    }

    @GetMapping("/{id}")
    Profile load(@PathVariable UUID id) throws IOException {
        return profiles.load(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Profile create(@RequestBody ProfileRequest request) throws IOException {
        return profiles.create(request.name(), request.instructions(), request.responseStyle(), request.responseFormat());
    }

    @PutMapping("/{id}")
    Profile update(@PathVariable UUID id, @RequestBody ProfileRequest request) throws IOException {
        return profiles.update(id, request.name(), request.instructions(), request.responseStyle(), request.responseFormat());
    }

    @ExceptionHandler(ProfileService.ProfileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(ProfileService.ProfileNotFoundException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    record ProfileRequest(String name, String instructions, String responseStyle, String responseFormat) {
    }
}
