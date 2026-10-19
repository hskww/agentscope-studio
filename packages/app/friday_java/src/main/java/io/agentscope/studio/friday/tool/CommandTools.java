package io.agentscope.studio.friday.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Shell and python execution tools, the Java counterpart of
 * {@code agentscope.tool.execute_shell_command} / {@code execute_python_code}.
 */
public class CommandTools {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    @Tool(
            name = "execute_shell_command",
            description = "Execute a shell command in the local environment and return its "
                    + "combined stdout/stderr output. Use it to inspect the file system, run "
                    + "builds or scripts.")
    public String executeShellCommand(
            @ToolParam(name = "command", description = "The shell command to execute.")
            String command,
            @ToolParam(name = "timeout", description = "Timeout in seconds, defaults to 300.")
            Integer timeout) {
        return run(command, timeout);
    }

    @Tool(
            name = "execute_python_code",
            description = "Execute a snippet of python code with the default python interpreter "
                    + "and return its combined stdout/stderr output.")
    public String executePythonCode(
            @ToolParam(name = "code", description = "The python code to execute.")
            String code,
            @ToolParam(name = "timeout", description = "Timeout in seconds, defaults to 300.")
            Integer timeout) {
        try {
            Path temp = Files.createTempFile("friday-", ".py");
            Files.writeString(temp, code, StandardCharsets.UTF_8);
            String command = pythonExecutable() + " " + temp.toAbsolutePath();
            String output = run(command, timeout);
            Files.deleteIfExists(temp);
            return output;
        } catch (IOException e) {
            return "Failed to execute the python code: " + e.getMessage();
        }
    }

    private static String run(String command, Integer timeout) {
        int seconds = (timeout == null || timeout <= 0) ? DEFAULT_TIMEOUT_SECONDS : timeout;
        try {
            ProcessBuilder builder;
            if (isWindows()) {
                builder = new ProcessBuilder("cmd", "/c", command);
            } else {
                builder = new ProcessBuilder("bash", "-c", command);
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return output + "\n[error] The command timed out after " + seconds + " seconds.";
            }
            return output.isEmpty() ? "[no output, exit code " + process.exitValue() + "]" : output;
        } catch (Exception e) {
            return "Failed to execute the command: " + e.getMessage();
        }
    }

    private static String pythonExecutable() {
        String python = System.getenv("FRIDAY_PYTHON");
        if (python != null && !python.isEmpty()) {
            return python;
        }
        return isWindows() ? "python" : "python3";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }
}
