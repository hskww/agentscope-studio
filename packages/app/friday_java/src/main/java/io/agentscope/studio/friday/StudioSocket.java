package io.agentscope.studio.friday;

import io.socket.client.IO;
import io.socket.client.Socket;

import java.net.URI;

/**
 * The socket.io connection to AgentScope Studio, used for realtime steering.
 *
 * <p>Studio emits {@code interrupt} on the {@code /friday} namespace when the
 * user asks to stop the current reply. This is the Java counterpart of
 * {@code utils/connect.py} in the Python implementation.</p>
 */
public class StudioSocket {

    private static final String FRIDAY_NAMESPACE = "/friday";

    private final String url;
    private final Runnable onInterrupt;
    private Socket socket;

    public StudioSocket(String url, Runnable onInterrupt) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.onInterrupt = onInterrupt;
    }

    public void connect() {
        try {
            IO.Options options = new IO.Options();
            options.reconnection = true;
            options.reconnectionAttempts = 5;
            options.reconnectionDelay = 5000;
            options.reconnectionDelayMax = 3000;
            options.transports = new String[]{"websocket"};

            socket = IO.socket(URI.create(url + FRIDAY_NAMESPACE), options);

            socket.on(Socket.EVENT_CONNECT,
                    args -> System.out.println("[friday] Connected to the studio at " + url));
            socket.on(Socket.EVENT_DISCONNECT,
                    args -> System.out.println("[friday] Disconnected from the studio at " + url));
            socket.on("interrupt", args -> {
                System.out.println("[friday] Interrupt received, stopping the agent...");
                try {
                    onInterrupt.run();
                } catch (Exception e) {
                    System.err.println("[friday] Failed to interrupt the agent: " + e.getMessage());
                }
            });

            socket.connect();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to connect to the studio at " + url
                            + ". Please check if the studio is running and the URL is correct.", e);
        }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.close();
        }
    }
}
