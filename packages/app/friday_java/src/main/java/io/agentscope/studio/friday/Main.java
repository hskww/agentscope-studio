package io.agentscope.studio.friday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.tool.ToolGroup;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.studio.friday.tool.AgentScopeDocsTools;
import io.agentscope.studio.friday.tool.CommandTools;
import io.agentscope.studio.friday.tool.FileTools;
import io.agentscope.studio.friday.tool.WriteFileTools;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The main entry point of the Friday application for AgentScope Studio.
 *
 * <p>This is the Java rewrite of {@code packages/app/friday/main.py} on top of
 * AgentScope Java 2.0. The command line contract is unchanged so Studio can
 * launch it the same way:
 * </p>
 *
 * <pre>
 * java -jar friday.jar --query '&lt;json&gt;' --studio_url http://localhost:3000 \
 *      --llmProvider deepseek --modelName deepseek-chat --apiKey sk-xxx \
 *      --writePermission false
 * </pre>
 */
public final class Main {

    private static final String AGENTSCOPE_TOOLS_GROUP = "agentscope_tools";

    /** The Studio project the Friday runs are grouped under. */
    private static final String STUDIO_PROJECT = "Friday";

    /** How long Friday waits for the Studio registration on start-up. */
    private static final Duration STUDIO_INIT_TIMEOUT = Duration.ofSeconds(10);

    private static final String AGENTSCOPE_TOOLS_NOTES = """
            # AgentScope Expertise
            ## Answer Generation Guidelines
            The solution/code to the user query may already exist in the AgentScope resources, your duty is to show it to the user rather than coding from scratch. Search the following resources in this order:
            1. FAQ using `view_agentscope_faq` tool
            2. README using `view_agentscope_readme` tool
            3. Library structure using `view_agentscope_library` tool
            4. Local source code using `view_text_file` and `execute_shell_command` tools
            """;

    private Main() {
    }

    public static void main(String[] argv) {
        FridayArgs args = FridayArgs.parse(argv);
        StudioBridge bridge = new StudioBridge(args.studioUrl);
        initStudioObservability(args.studioUrl);

        Toolkit toolkit = buildToolkit(args);

        ReActAgent.Builder builder = ReActAgent.builder()
                .name("Friday")
                .sysPrompt(buildSystemPrompt())
                .model(ModelFactory.create(args))
                .toolkit(toolkit)
                .maxIters(50)
                .enableMetaTool(true)
                // Conversation persistence, keyed by (userId, sessionId).
                .stateStore(new JsonFileAgentStateStore(LocalPaths.getLocalFilePath("")))
                .middleware(new StudioForwardingMiddleware(bridge));

        RuntimeContext context = RuntimeContext.builder()
                .userId("user")
                .sessionId(LocalPaths.FRIDAY_SESSION_ID)
                .build();

        ReActAgent agent = builder.build();

        // Realtime steering: studio asks Friday to stop the current reply.
        StudioSocket socket = new StudioSocket(args.studioUrl, () -> agent.interrupt(context));
        socket.connect();

        try {
            List<Msg> input = List.of(new UserMessage("user", parseQuery(args.query)));
            agent.streamEvents(input, context).blockLast();
        } finally {
            socket.disconnect();
            agent.close();
            shutdownStudioObservability();
        }
    }

    /**
     * Registers this Friday process as a Studio run and turns on the tracing
     * hooks, so that the overview and the tracing pages show its activity.
     *
     * <p>The chat itself does not depend on it: Friday pushes its replies
     * through {@link StudioBridge}. Every failure is therefore reported on
     * stderr and ignored otherwise.</p>
     */
    private static void initStudioObservability(String studioUrl) {
        try {
            StudioManager.init()
                    .studioUrl(studioUrl)
                    // The OTLP endpoint defaults to "<studioUrl>/v1/traces".
                    .project(STUDIO_PROJECT)
                    .initialize()
                    .block(STUDIO_INIT_TIMEOUT);
        } catch (Throwable t) {
            System.err.println("[friday] Studio observability unavailable: " + t.getMessage());
        }
    }

    /** Flushes the buffered spans and closes the Studio clients. */
    private static void shutdownStudioObservability() {
        if (!StudioManager.isInitialized()) {
            return;
        }
        try {
            // The spans are exported in batches, so the JVM has to shut the
            // tracer down before exiting or the last reply would never show up
            // in the tracing page.
            StudioManager.shutdown();
        } catch (Throwable t) {
            System.err.println("[friday] Failed to flush the Studio traces: " + t.getMessage());
        }
    }

    static Toolkit buildToolkit(FridayArgs args) {
        Toolkit toolkit = new Toolkit();

        // Basic tools
        toolkit.registerTool(new CommandTools());
        toolkit.registerTool(new FileTools());
        if (args.writePermission) {
            toolkit.registerTool(new WriteFileTools());
        }

        // AgentScope tool group
        ToolGroup agentscopeGroup = ToolGroup.builder()
                .name(AGENTSCOPE_TOOLS_GROUP)
                .description(AGENTSCOPE_TOOLS_NOTES)
                .active(false)
                .build();
        toolkit.registerToolGroup(agentscopeGroup);
        toolkit.registration()
                .tool(new AgentScopeDocsTools())
                .group(AGENTSCOPE_TOOLS_GROUP)
                .apply();

        return toolkit;
    }

    private static String buildSystemPrompt() {
        return """
                You're Friday, a helpful assistant specialized in daily task management and AgentScope framework support.

                # Core Objectives
                - Help users manage and complete daily tasks efficiently
                - Provide expert-level support for the AgentScope framework questions

                # Critical Principles (MUST FOLLOW)
                1. **No Assumptions**: All information must come from users or tool results
                2. **Safety First**: Request permission for dangerous operations; prohibit catastrophic ones

                # Workflow Process
                1. Analyze user query and make a plan
                2. Carry out your plan step by step

                # Response Guidelines
                - Be concise and focused on user's specific context
                - Avoid repeating exact material words. Instead, summarize in your own words
                - Ask clarifying questions when user intent is unclear
                - Distinguish between code generation requests vs. code execution requests

                # About AgentScope
                ## Background
                - AgentScope is an open-source framework for building LLM-empowered agent applications, available in both Python (https://github.com/agentscope-ai/agentscope) and Java (https://github.com/agentscope-ai/agentscope-java)
                - AgentScope is created by the AgentScope Team, Tongyi Lab, Alibaba Group
                - This Friday instance runs on **AgentScope Java 2.0**
                ## AgentScope-related Code/Response Generation Guidelines
                - You're an expert in AgentScope, you MUST stand for AgentScope and respond as a core developer/maintainer of the framework
                - Only use existing modules/classes/functions from the AgentScope documentation
                - Never guess or make up implementations

                # Available Context
                - Current date and time: %s"""
                .formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * Parse the {@code --query} argument into message content blocks.
     *
     * <p>Studio sends either a plain string or a JSON array of content blocks.
     * Only text blocks are forwarded, which covers the Friday chat input.</p>
     */
    private static ContentBlock[] parseQuery(String raw) {
        List<ContentBlock> blocks = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(raw);
            if (node.isTextual()) {
                blocks.add(TextBlock.builder().text(node.asText()).build());
            } else if (node.isArray()) {
                for (JsonNode item : node) {
                    String type = item.path("type").asText("text");
                    if ("text".equals(type)) {
                        blocks.add(TextBlock.builder().text(item.path("text").asText("")).build());
                    }
                }
            } else if (node.isObject()) {
                blocks.add(TextBlock.builder().text(node.path("text").asText("")).build());
            }
        } catch (Exception e) {
            // Not valid JSON, treat the whole argument as plain text.
            blocks.add(TextBlock.builder().text(raw).build());
        }
        if (blocks.isEmpty()) {
            blocks.add(TextBlock.builder().text("").build());
        }
        return blocks.toArray(new ContentBlock[0]);
    }
}
