package io.agentscope.studio.friday.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File writing tool. It is only registered when the user grants the write
 * permission in the Friday settings.
 */
public class WriteFileTools {

    @Tool(
            name = "write_text_file",
            description = "Write text content into a local file. The parent directories are "
                    + "created automatically and an existing file is overwritten.")
    public String writeTextFile(
            @ToolParam(name = "file_path", description = "The path of the file to write.")
            String filePath,
            @ToolParam(name = "content", description = "The text content to write.")
            String content) {
        Path path = Paths.get(filePath);
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return "Successfully wrote " + content.length() + " character(s) to " + path.toAbsolutePath() + ".";
        } catch (IOException e) {
            return "Failed to write the file: " + e.getMessage();
        }
    }
}
