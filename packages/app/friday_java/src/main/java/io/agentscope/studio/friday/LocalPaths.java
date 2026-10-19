package io.agentscope.studio.friday;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolve the local directory used by AgentScope Studio to keep Friday's data.
 */
public final class LocalPaths {

    public static final String NAME_STUDIO = "AgentScope-Studio";
    public static final String NAME_APP = "Friday";
    public static final String FRIDAY_SESSION_ID = "session";

    private LocalPaths() {
    }

    /**
     * Return the absolute path of {@code filename} inside the local Friday directory.
     * The directory is created when it does not exist yet.
     */
    public static Path getLocalFilePath(String filename) {
        String os = System.getProperty("os.name", "");
        String home = System.getProperty("user.home");
        Path base;
        if (os.startsWith("Windows")) {
            String appData = System.getenv("APPDATA");
            base = Paths.get(appData == null ? home : appData, NAME_STUDIO);
        } else if (os.contains("Darwin") || os.contains("Mac")) {
            base = Paths.get(home, "Library", "Application Support", NAME_STUDIO);
        } else {
            base = Paths.get(home, ".local", "share", NAME_STUDIO);
        }
        Path appDir = base.resolve(NAME_APP);
        try {
            Files.createDirectories(appDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the Friday directory: " + appDir, e);
        }
        return appDir.resolve(filename == null ? "" : filename);
    }
}
