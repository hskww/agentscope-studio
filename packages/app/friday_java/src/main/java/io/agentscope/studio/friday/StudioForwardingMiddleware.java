package io.agentscope.studio.friday;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Forward the messages produced during one reply to AgentScope Studio.
 *
 * <p>The Python implementation registers a {@code pre_print} hook that pushes
 * every message the agent prints and a {@code post_reply} hook that pushes the
 * finished signal. AgentScope Java 2.0 exposes the same lifecycle through
 * middleware, so this class rebuilds complete messages from the event stream
 * and pushes them through {@link StudioBridge}.</p>
 */
public class StudioForwardingMiddleware implements MiddlewareBase {

    private final StudioBridge bridge;
    private final ObjectMapper mapper = new ObjectMapper();

    // Mutable state, one middleware instance serves one reply at a time in Friday.
    private String replyId;
    private String agentName;

    private final List<Map<String, Object>> reasoningBlocks = new ArrayList<>();
    private final StringBuilder textBuffer = new StringBuilder();
    private final StringBuilder thinkingBuffer = new StringBuilder();

    private String pendingToolCallId;
    private String pendingToolCallName;
    private final StringBuilder toolCallArgs = new StringBuilder();

    private String pendingResultId;
    private String pendingResultName;
    private final StringBuilder resultOutput = new StringBuilder();

    public StudioForwardingMiddleware(StudioBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        agentName = agent == null ? "Friday" : agent.getName();
        return next.apply(input)
                .doOnNext(this::onEvent)
                .doOnError(e -> finishReply());
    }

    private void onEvent(AgentEvent event) {
        if (event instanceof AgentStartEvent start) {
            replyId = start.getReplyId();
            reset();
            return;
        }

        if (event instanceof TextBlockDeltaEvent delta) {
            textBuffer.append(delta.getDelta() == null ? "" : delta.getDelta());
        } else if (event instanceof TextBlockEndEvent) {
            flushText();
        } else if (event instanceof ThinkingBlockDeltaEvent delta) {
            thinkingBuffer.append(delta.getDelta() == null ? "" : delta.getDelta());
        } else if (event instanceof ThinkingBlockEndEvent) {
            flushThinking();
        } else if (event instanceof ToolCallStartEvent start) {
            pendingToolCallId = start.getToolCallId();
            pendingToolCallName = start.getToolCallName();
            toolCallArgs.setLength(0);
        } else if (event instanceof ToolCallDeltaEvent delta) {
            toolCallArgs.append(delta.getDelta() == null ? "" : delta.getDelta());
        } else if (event instanceof ToolCallEndEvent) {
            flushToolUse();
        } else if (event instanceof ToolResultStartEvent start) {
            pendingResultId = start.getToolCallId();
            pendingResultName = start.getToolCallName();
            resultOutput.setLength(0);
        } else if (event instanceof ToolResultTextDeltaEvent delta) {
            resultOutput.append(delta.getDelta() == null ? "" : delta.getDelta());
        } else if (event instanceof ToolResultEndEvent) {
            flushToolResult();
        }

        if (event instanceof ModelCallEndEvent) {
            flushText();
            flushThinking();
            pushReasoningMessage();
        }

        if (event instanceof AgentEndEvent) {
            flushText();
            flushThinking();
            pushReasoningMessage();
            finishReply();
        }
    }

    private void reset() {
        reasoningBlocks.clear();
        textBuffer.setLength(0);
        thinkingBuffer.setLength(0);
        toolCallArgs.setLength(0);
        resultOutput.setLength(0);
        pendingToolCallId = null;
        pendingToolCallName = null;
        pendingResultId = null;
        pendingResultName = null;
    }

    private void flushText() {
        if (textBuffer.length() == 0) {
            return;
        }
        reasoningBlocks.add(textBlock(textBuffer.toString()));
        textBuffer.setLength(0);
    }

    private void flushThinking() {
        if (thinkingBuffer.length() == 0) {
            return;
        }
        Map<String, Object> block = new HashMap<>();
        block.put("type", "thinking");
        block.put("thinking", thinkingBuffer.toString());
        reasoningBlocks.add(block);
        thinkingBuffer.setLength(0);
    }

    private void flushToolUse() {
        if (pendingToolCallId == null) {
            return;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_use");
        block.put("id", pendingToolCallId);
        block.put("name", pendingToolCallName == null ? "" : pendingToolCallName);
        block.put("input", parseJson(toolCallArgs.toString()));
        reasoningBlocks.add(block);
        pendingToolCallId = null;
        pendingToolCallName = null;
        toolCallArgs.setLength(0);
    }

    private void flushToolResult() {
        if (pendingResultId == null) {
            return;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_result");
        block.put("id", pendingResultId);
        block.put("name", pendingResultName == null ? "" : pendingResultName);
        block.put("output", resultOutput.toString());
        // Tool results are rendered as their own message, exactly like the
        // Python ReActAgent prints them.
        pushMessage(List.of(block));
        pendingResultId = null;
        pendingResultName = null;
        resultOutput.setLength(0);
    }

    private void pushReasoningMessage() {
        if (reasoningBlocks.isEmpty()) {
            return;
        }
        pushMessage(new ArrayList<>(reasoningBlocks));
        reasoningBlocks.clear();
    }

    private void pushMessage(List<Map<String, Object>> content) {
        if (replyId == null || content == null || content.isEmpty()) {
            return;
        }
        bridge.pushMessage(replyId, UUID.randomUUID().toString(), agentName, "assistant", content);
    }

    private void finishReply() {
        if (replyId == null) {
            return;
        }
        bridge.pushFinishedSignal(replyId);
    }

    private Object parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return mapper.readValue(raw, Map.class);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", raw);
            return fallback;
        }
    }

    private static Map<String, Object> textBlock(String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "text");
        block.put("text", text);
        return block;
    }
}
