package io.agentscope.studio.friday;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Push messages and lifecycle signals to the AgentScope Studio server.
 *
 * <p>Studio exposes two tRPC mutations for Friday:
 * {@code POST /trpc/pushMessageToFridayApp} and
 * {@code POST /trpc/pushFinishedSignalToFridayApp}.</p>
 */
public class StudioBridge {

    private static final int MAX_RETRY = 3;

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public StudioBridge(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                // Studio's Node server only speaks HTTP/1.1. With the JDK default
                // (HTTP/2) the client first tries the cleartext "Upgrade: h2c"
                // handshake, which the socket.io upgrade listener attached to the
                // Studio HTTP server swallows: the request never gets a response
                // and every push fails with "HTTP/1.1 header parser received no
                // bytes" / a timeout, so the chat stays silent.
                .version(HttpClient.Version.HTTP_1_1)
                // Never route a local Studio url through a system proxy.
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Push one assistant message to the studio.
     *
     * @param replyId  the id of the reply this message belongs to
     * @param msgId    the id of this message
     * @param name     the sender name
     * @param role     the message role, always {@code assistant} here
     * @param content  the content blocks, already in the studio block format
     */
    public void pushMessage(String replyId, String msgId, String name, String role,
                            List<Map<String, Object>> content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("id", msgId);
        msg.put("name", name);
        msg.put("role", role);
        msg.put("content", content);
        msg.put("metadata", new HashMap<String, Object>());
        msg.put("timestamp", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));

        Map<String, Object> body = new HashMap<>();
        body.put("replyId", replyId);
        body.put("msg", msg);

        post("/trpc/pushMessageToFridayApp", body);
    }

    /** Notify the studio that one reply is finished. */
    public void pushFinishedSignal(String replyId) {
        Map<String, Object> body = new HashMap<>();
        body.put("replyId", replyId);
        post("/trpc/pushFinishedSignalToFridayApp", body);
    }

    private void post(String path, Map<String, Object> body) {
        String payload;
        try {
            payload = mapper.writeValueAsString(body);
        } catch (Exception e) {
            System.err.println("[friday] Failed to serialize the studio payload: " + e.getMessage());
            return;
        }

        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
                System.err.println("[friday] Studio returned "
                        + response.statusCode() + " for " + path + ": " + response.body());
            } catch (Exception e) {
                // getMessage() is null for some JDK connect failures, so include
                // the exception type to keep the studio-side log readable.
                System.err.println("[friday] Failed to call " + path + ": "
                        + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " " + e.getMessage()));
            }
        }
        System.err.println("[friday] Give up pushing to " + path);
    }
}
