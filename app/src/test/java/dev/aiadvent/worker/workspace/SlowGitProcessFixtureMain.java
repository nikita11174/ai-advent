package dev.aiadvent.worker.workspace;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SlowGitProcessFixtureMain {
    private SlowGitProcessFixtureMain() { }

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[0]), "started");
        Thread.sleep(30_000);
    }
}
