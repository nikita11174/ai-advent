package dev.aiadvent.mentor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/dialogs")
class DialogController {
    private final DialogStore store;

    DialogController(DialogStore store) {
        this.store = store;
    }

    @PostMapping
    DialogStore.DialogDocument create() throws IOException {
        return store.create();
    }

    @GetMapping
    List<DialogStore.DialogSummary> list() throws IOException {
        return store.list();
    }

    @GetMapping("/{id}")
    DialogStore.DialogDocument load(@PathVariable String id) throws IOException {
        return store.load(id);
    }

    @PutMapping("/{id}")
    DialogStore.DialogDocument update(@PathVariable String id, @RequestBody DialogStore.DialogUpdate update)
            throws IOException {
        return store.update(id, update);
    }
}
