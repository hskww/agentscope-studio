package io.agentscope.studio.friday.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only and in-place editing tools for local text files.
 */
public class FileTools {

    @Tool(
            name = "view_text_file",
            description = "View the content of a local text file.",
            readOnly = true)
    public String viewTextFile(
            @ToolParam(name = "file_path", description = "The absolute or relative path of the file.")
            String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path)) {
            return "The file does not exist: " + filePath;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to read the file: " + e.getMessage();
        }
    }

    @Tool(
            name = "insert_text_file",
            description = "Insert text into an existing local text file, either at the beginning, "
                    + "at the end, or before a given line number (1-indexed).")
    public String insertTextFile(
            @ToolParam(name = "file_path", description = "The path of the file to edit.")
            String filePath,
            @ToolParam(name = "text", description = "The text to insert.")
            String text,
            @ToolParam(name = "at_start", description = "Insert at the beginning of the file "
                    + "instead of the end.")
            Boolean atStart,
            @ToolParam(name = "line", description = "Insert before this 1-indexed line number. "
                    + "Ignored when at_start is true.")
            Integer line) {
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path)) {
            return "The file does not exist: " + filePath;
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            if (Boolean.TRUE.equals(atStart)) {
                lines.addAll(0, splitLines(text));
            } else if (line != null && line >= 1) {
                int index = Math.min(line - 1, lines.size());
                lines.addAll(index, splitLines(text));
            } else {
                lines.addAll(splitLines(text));
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
            return "Inserted " + splitLines(text).size() + " line(s) into " + filePath + ".";
        } catch (IOException e) {
            return "Failed to edit the file: " + e.getMessage();
        }
    }

    private static List<String> splitLines(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            result.add(line);
        }
        if (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
