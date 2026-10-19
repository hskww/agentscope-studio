package io.agentscope.studio.friday;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.studio.friday.tool.AgentScopeDocsTools;
import io.agentscope.studio.friday.tool.CommandTools;
import io.agentscope.studio.friday.tool.FileTools;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FridaySmokeTest {

    @Test
    void parsesCommandLineArguments() {
        FridayArgs args = FridayArgs.parse(new String[]{
                "--query", "\"hello\"",
                "--studio_url", "http://localhost:3000",
                "--llmProvider", "deepseek",
                "--modelName", "deepseek-chat",
                "--apiKey", "sk-test",
                "--writePermission", "true",
                "--clientKwargs", "{\"base_url\":\"https://api.deepseek.com\"}",
                "--generateKwargs", "{\"temperature\":0.2}",
        });

        assertEquals("deepseek", args.llmProvider);
        assertEquals("deepseek-chat", args.modelName);
        assertTrue(args.writePermission);
        assertEquals("https://api.deepseek.com", args.clientKwargs.get("base_url"));
        assertEquals(0.2, args.generateKwargs.get("temperature"));
    }

    @Test
    void resolvesDeepSeekModel() {
        Model model = ModelRegistry.resolve(
                "deepseek:deepseek-chat",
                ModelCreationContext.builder()
                        .apiKey("sk-test")
                        .baseUrl("https://api.deepseek.com")
                        .stream(true)
                        .build());
        assertNotNull(model);
    }

    @Test
    void buildsFridayAgent() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new CommandTools());
        toolkit.registerTool(new FileTools());
        toolkit.registerTool(new AgentScopeDocsTools());

        Model model = ModelRegistry.resolve(
                "dashscope:qwen-plus",
                ModelCreationContext.builder().apiKey("sk-test").stream(true).build());

        Path stateDir = Path.of(System.getProperty("java.io.tmpdir"), "friday-test-state");
        assertTrue(Files.isDirectory(stateDir.getParent()));

        try (ReActAgent agent = ReActAgent.builder()
                .name("Friday")
                .sysPrompt("You are Friday.")
                .model(model)
                .toolkit(toolkit)
                .maxIters(50)
                .enableMetaTool(true)
                .stateStore(new JsonFileAgentStateStore(stateDir))
                .middleware(new StudioForwardingMiddleware(new StudioBridge("http://localhost:3000")))
                .build()) {
            assertNotNull(agent);
            assertEquals("Friday", agent.getName());
        }
    }

    @Test
    void registersAgentScopeToolsIntoTheirOwnGroup() {
        FridayArgs args = FridayArgs.parse(new String[]{
                "--query", "\"hello\"",
                "--studio_url", "http://localhost:3000",
                "--llmProvider", "deepseek",
                "--modelName", "deepseek-chat",
                "--apiKey", "sk-test",
                "--writePermission", "false",
        });

        Toolkit toolkit = Main.buildToolkit(args);
        io.agentscope.core.tool.ToolGroup group = toolkit.getToolGroup("agentscope_tools");

        assertNotNull(group, "the agentscope_tools group must be registered");
        assertFalse(group.isActive(), "the agentscope_tools group starts inactive");
        for (String name : new String[]{
                "view_agentscope_readme", "view_agentscope_faq", "view_agentscope_library"}) {
            assertTrue(group.getTools().contains(name), name + " must belong to agentscope_tools");
        }
        // The write tool is only registered with the write permission.
        assertFalse(toolkit.getToolNames().contains("write_text_file"));
        assertTrue(toolkit.getToolNames().contains("execute_shell_command"));
    }

    @Test
    void resolvesLocalFridayDirectory() {
        Path path = LocalPaths.getLocalFilePath("");
        assertTrue(Files.isDirectory(path));
        assertFalse(path.toString().isEmpty());
    }
}
