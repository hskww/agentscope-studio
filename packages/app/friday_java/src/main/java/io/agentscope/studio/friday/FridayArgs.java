package io.agentscope.studio.friday;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

/**
 * Command line arguments of the Friday application.
 *
 * <p>The argument names are kept identical to the Python implementation
 * ({@code packages/app/friday/args.py}) so that AgentScope Studio can drive
 * this application with the exact same command line.</p>
 */
public final class FridayArgs {

    /** Raw user query, a JSON string (either a plain string or a list of content blocks). */
    public final String query;

    /** Base url of the AgentScope Studio server, e.g. http://localhost:3000. */
    public final String studioUrl;

    /** Model provider: dashscope / openai / anthropic / gemini / ollama / deepseek. */
    public final String llmProvider;

    /** The model name used to generate replies. */
    public final String modelName;

    /** The API key of the model provider. */
    public final String apiKey;

    /** Whether Friday is allowed to write local files. */
    public final boolean writePermission;

    /** Extra keyword arguments passed to the model client. */
    public final Map<String, Object> clientKwargs;

    /** Extra keyword arguments passed to the generate call. */
    public final Map<String, Object> generateKwargs;

    private FridayArgs(
            String query,
            String studioUrl,
            String llmProvider,
            String modelName,
            String apiKey,
            boolean writePermission,
            Map<String, Object> clientKwargs,
            Map<String, Object> generateKwargs) {
        this.query = query;
        this.studioUrl = studioUrl;
        this.llmProvider = llmProvider;
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.writePermission = writePermission;
        this.clientKwargs = clientKwargs;
        this.generateKwargs = generateKwargs;
    }

    public static FridayArgs parse(String[] argv) {
        Map<String, String> raw = new java.util.LinkedHashMap<>();
        for (int i = 0; i < argv.length; i++) {
            String key = argv[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + key);
            }
            String name = key.substring(2);
            if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                raw.put(name, argv[i + 1]);
                i++;
            } else {
                raw.put(name, "true");
            }
        }

        String studioUrl = require(raw, "studio_url");
        // The studio passes the url without scheme sometimes; keep it as-is.
        return new FridayArgs(
                require(raw, "query"),
                studioUrl,
                require(raw, "llmProvider"),
                require(raw, "modelName"),
                raw.getOrDefault("apiKey", ""),
                Boolean.parseBoolean(raw.getOrDefault("writePermission", "false")),
                parseJsonMap(raw.get("clientKwargs")),
                parseJsonMap(raw.get("generateKwargs")));
    }

    private static String require(Map<String, String> raw, String name) {
        String value = raw.get(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: --" + name);
        }
        return value;
    }

    private static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed =
                    new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON object for kwargs: " + json, e);
        }
    }
}
