package io.agentscope.studio.friday.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AgentScope documentation retrieval tools.
 *
 * <p>The Python version introspects the installed {@code agentscope} package at
 * runtime. Java cannot do that cheaply, so {@code view_agentscope_library}
 * exposes a curated catalog of the AgentScope Java 2.0 packages and their main
 * entry points instead.</p>
 */
public class AgentScopeDocsTools {

    private static final String README_URL =
            "https://raw.githubusercontent.com/agentscope-ai/agentscope-java/main/README.md";
    private static final String FAQ_URL =
            "https://raw.githubusercontent.com/agentscope-ai/agentscope-java/main/docs/faq.md";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String readme;
    private String faq;

    @Tool(
            name = "view_agentscope_readme",
            description = "View the README of the AgentScope Java repository, which contains the "
                    + "brief introduction, quickstart and module list.",
            readOnly = true)
    public String viewAgentscopeReadme() {
        if (readme == null) {
            readme = download(README_URL);
        }
        return readme;
    }

    @Tool(
            name = "view_agentscope_faq",
            description = "View the AgentScope Java FAQ.",
            readOnly = true)
    public String viewAgentscopeFaq() {
        if (faq == null) {
            faq = download(FAQ_URL);
        }
        return faq;
    }

    @Tool(
            name = "view_agentscope_library",
            description = "View the AgentScope Java library structure. Given a module or class "
                    + "name, return its documentation and main entry points. Pass "
                    + "\"agentscope\" to list all top-level packages.",
            readOnly = true)
    public String viewAgentscopeLibrary(
            @ToolParam(name = "module", description = "A module, package or class path, e.g. "
                    + "\"agentscope\", \"agentscope.core.tool\" or \"ReActAgent\".")
            String module) {
        String query = module == null || module.isBlank() ? "agentscope" : module.trim();

        if ("agentscope".equals(query)) {
            StringBuilder builder = new StringBuilder("The main packages of AgentScope Java 2.0:");
            for (Map.Entry<String, String> entry : LIBRARY.entrySet()) {
                builder.append("\n- ").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            builder.append("\nCall this function again with one of the names above for details.");
            return builder.toString();
        }

        for (Map.Entry<String, String> entry : LIBRARY.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(query)
                    || entry.getKey().endsWith("." + query)
                    || entry.getKey().toLowerCase().endsWith("." + query.toLowerCase())) {
                return entry.getKey() + "\n" + entry.getValue();
            }
        }

        return "Module '" + query + "' not found. Use \"agentscope\" to list the top-level packages.";
    }

    private String download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            return "Failed to download " + url + " (HTTP " + response.statusCode() + ").";
        } catch (Exception e) {
            return "Failed to download " + url + ": " + e.getMessage();
        }
    }

    private static final Map<String, String> LIBRARY = new LinkedHashMap<>();

    static {
        LIBRARY.put("io.agentscope.core",
                "Core abstractions: Agent interface, message model (Msg / ContentBlock), the "
                        + "event stream (AgentEvent), middleware, permission system and state store.");
        LIBRARY.put("ReActAgent",
                "io.agentscope.core.ReActAgent - the reasoning-acting loop. "
                        + "ReActAgent.builder().name(...).sysPrompt(...).model(\"dashscope:qwen-plus\")"
                        + ".toolkit(toolkit).maxIters(50).enableMetaTool(true).build(); "
                        + "agent.call(msgs, ctx) returns Mono<Msg>, agent.streamEvents(msgs, ctx) "
                        + "returns Flux<AgentEvent>.");
        LIBRARY.put("io.agentscope.core.tool",
                "Toolkit, @Tool / @ToolParam reflective tools, ToolBase, ToolGroup and MCP clients. "
                        + "toolkit.registerTool(object) registers every @Tool method.");
        LIBRARY.put("io.agentscope.core.tool.file",
                "ReadFileTool and WriteFileTool - file access scoped to a base directory.");
        LIBRARY.put("io.agentscope.core.tool.coding",
                "ShellCommandTool - run shell commands.");
        LIBRARY.put("io.agentscope.core.tool.builtin",
                "TodoTools - a structured task list for the current session.");
        LIBRARY.put("io.agentscope.core.middleware",
                "MiddlewareBase with the onAgent / onReasoning / onActing / onModelCall / "
                        + "onSystemPrompt / onAgentStateReady extension points.");
        LIBRARY.put("io.agentscope.core.model",
                "Model, ChatModelBase, ModelRegistry and ModelCreationContext. "
                        + "ModelRegistry.resolve(\"deepseek:deepseek-chat\", context) builds a model "
                        + "from a provider:model string.");
        LIBRARY.put("io.agentscope.core.state",
                "AgentState, AgentStateStore and JsonFileAgentStateStore - conversation "
                        + "persistence keyed by (userId, sessionId).");
        LIBRARY.put("io.agentscope.harness",
                "HarnessAgent - ReActAgent plus workspace, long-term memory, context "
                        + "compaction, skills, subagents and session recovery.");
        LIBRARY.put("io.agentscope.extensions.model",
                "Provider extensions: dashscope, openai (also serves deepseek), anthropic, "
                        + "gemini, ollama. Each ships its ChatModel, Credential and Formatters.");
    }
}
