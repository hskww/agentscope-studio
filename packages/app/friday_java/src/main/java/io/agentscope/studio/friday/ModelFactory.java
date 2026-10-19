package io.agentscope.studio.friday;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

import java.util.Locale;
import java.util.Map;

/**
 * Build the {@link Model} instance from the Friday configuration.
 *
 * <p>Every provider is resolved through {@link ModelRegistry} with the
 * {@code provider:model} id. The API key, base url and other extra arguments
 * come from the command line instead of environment variables, so they are
 * passed through {@link ModelCreationContext}.</p>
 *
 * <p>DeepSeek is OpenAI-compatible, so it is served by
 * {@code agentscope-extensions-model-openai} and resolved with the
 * {@code deepseek:} prefix.</p>
 */
public final class ModelFactory {

    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    private ModelFactory() {
    }

    public static Model create(FridayArgs args) {
        String provider = args.llmProvider.toLowerCase(Locale.ROOT);
        String modelName = args.modelName;

        Map<String, Object> clientKwargs = args.clientKwargs;
        String baseUrl = firstString(clientKwargs, "base_url", "baseUrl", "baseURL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = defaultBaseUrl(provider);
        }

        ModelCreationContext.Builder context = ModelCreationContext.builder().stream(true);
        // Ollama and local OpenAI-compatible servers do not need an API key.
        if (args.apiKey != null && !args.apiKey.isBlank()) {
            context.apiKey(args.apiKey);
        }

        if (baseUrl != null && !baseUrl.isEmpty()) {
            context.baseUrl(baseUrl);
        }

        // Remaining client kwargs and all generate kwargs are forwarded as
        // provider specific options.
        for (Map.Entry<String, Object> entry : clientKwargs.entrySet()) {
            String key = entry.getKey();
            if ("base_url".equals(key) || "baseUrl".equals(key) || "baseURL".equals(key)) {
                continue;
            }
            context.option(key, entry.getValue());
        }
        for (Map.Entry<String, Object> entry : args.generateKwargs.entrySet()) {
            context.option(entry.getKey(), entry.getValue());
        }

        return ModelRegistry.resolve(provider + ":" + modelName, context.build());
    }

    private static String defaultBaseUrl(String provider) {
        switch (provider) {
            case "deepseek":
                return DEEPSEEK_BASE_URL;
            case "ollama":
                return OLLAMA_BASE_URL;
            default:
                return null;
        }
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
