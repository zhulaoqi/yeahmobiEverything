package com.yeahmobi.everything.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight local HTTP server for receiving Feishu OAuth 2.0 callback.
 * <p>
 * When the user clicks "飞书登录", the system browser opens the Feishu
 * authorization page. After the user approves, Feishu redirects to
 * {@code http://localhost:8080/auth/feishu/callback?code=xxx&state=xxx}.
 * This server captures the authorization code and passes it to the
 * application via a callback.
 * </p>
 * <p>
 * The server automatically shuts down after receiving the callback
 * or when explicitly stopped.
 * </p>
 */
public class OAuthCallbackServer {

    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackServer.class);

    /** Default port for the OAuth callback server. */
    static final int DEFAULT_PORT = 8080;

    /** The callback path that Feishu redirects to. */
    static final String CALLBACK_PATH = "/auth/feishu/callback";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private ExecutorService shutdownExecutor;
    private final int port;
    private final Consumer<String> onCodeReceived;
    private volatile boolean running;

    /**
     * Creates an OAuthCallbackServer on the default port.
     *
     * @param onCodeReceived callback invoked with the authorization code
     */
    public OAuthCallbackServer(Consumer<String> onCodeReceived) {
        this(DEFAULT_PORT, onCodeReceived);
    }

    /**
     * Creates an OAuthCallbackServer on the specified port.
     *
     * @param port           the port to listen on
     * @param onCodeReceived callback invoked with the authorization code
     */
    public OAuthCallbackServer(int port, Consumer<String> onCodeReceived) {
        this.port = port;
        this.onCodeReceived = onCodeReceived;
    }

    /**
     * Starts the HTTP server. If a server is already running, it will
     * be stopped first.
     *
     * @throws IOException if the server cannot bind to the port
     */
    public synchronized void start() throws IOException {
        if (running) {
            stop();
        }

        serverExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "oauth-callback-server");
            t.setDaemon(true);
            return t;
        });

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(CALLBACK_PATH, this::handleCallback);
        server.setExecutor(serverExecutor);
        server.start();
        running = true;

        log.info("OAuth callback server started on port {}", port);
    }

    /**
     * Stops the HTTP server and shuts down all executors.
     */
    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            running = false;
            log.info("OAuth callback server stopped.");
        }

        // Shut down server executor
        if (serverExecutor != null) {
            serverExecutor.shutdown();
            try {
                if (!serverExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    serverExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                serverExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            serverExecutor = null;
        }

        // Shut down auto-shutdown executor
        if (shutdownExecutor != null) {
            shutdownExecutor.shutdown();
            try {
                if (!shutdownExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    shutdownExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                shutdownExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            shutdownExecutor = null;
        }
    }

    /**
     * Returns whether the server is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Handles the OAuth callback request.
     * Extracts the 'code' query parameter and invokes the callback.
     */
    private void handleCallback(HttpExchange exchange) throws IOException {
        try {
            URI requestUri = exchange.getRequestURI();
            String query = requestUri.getQuery();
            String code = extractQueryParam(query, "code");

            if (code != null && !code.isBlank()) {
                // Respond with a success page
                String html = buildSuccessHtml();
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }

                log.info("OAuth callback received with authorization code.");

                // Notify the application
                if (onCodeReceived != null) {
                    onCodeReceived.accept(code);
                }

                // Auto-stop the server after a short delay
                shutdownExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "oauth-server-shutdown");
                    t.setDaemon(true);
                    return t;
                });
                shutdownExecutor.execute(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    stop();
                });

            } else {
                // No code parameter - respond with error
                String html = buildErrorHtml("未收到授权码，请重试飞书登录。");
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(400, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        } catch (Exception e) {
            log.warn("Error handling OAuth callback", e);
            String html = buildErrorHtml("处理授权回调时发生错误：" + e.getMessage());
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Extracts a query parameter value from a query string.
     *
     * @param query the query string (e.g., "code=abc&state=xyz")
     * @param param the parameter name to extract
     * @return the parameter value, or null if not found
     */
    static String extractQueryParam(String query, String param) {
        if (query == null || param == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Builds a success HTML page shown in the browser after OAuth callback.
     */
    private String buildSuccessHtml() {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <title>飞书登录成功</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                            background-color: #f6f8fa;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                            color: #1f2328;
                        }
                        .container {
                            text-align: center;
                            background: #ffffff;
                            border: 1px solid #d0d7de;
                            border-radius: 12px;
                            padding: 48px;
                            max-width: 420px;
                        }
                        .icon {
                            font-size: 48px;
                            margin-bottom: 16px;
                        }
                        h1 {
                            font-size: 24px;
                            font-weight: 600;
                            margin-bottom: 8px;
                            color: #1f883d;
                        }
                        p {
                            font-size: 14px;
                            color: #656d76;
                            line-height: 1.6;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="icon">&#10004;</div>
                        <h1>授权成功</h1>
                        <p>飞书登录已完成，请返回 Yeahmobi Everything 应用。</p>
                        <p style="margin-top: 12px; font-size: 12px; color: #8b949e;">此页面可安全关闭。</p>
                    </div>
                    <script>setTimeout(function(){ window.close(); }, 3000);</script>
                </body>
                </html>
                """;
    }

    /**
     * Builds an error HTML page.
     */
    private String buildErrorHtml(String message) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <title>授权失败</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                            background-color: #f6f8fa;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                            color: #1f2328;
                        }
                        .container {
                            text-align: center;
                            background: #ffffff;
                            border: 1px solid #d0d7de;
                            border-radius: 12px;
                            padding: 48px;
                            max-width: 420px;
                        }
                        .icon { font-size: 48px; margin-bottom: 16px; }
                        h1 { font-size: 24px; font-weight: 600; margin-bottom: 8px; color: #cf222e; }
                        p { font-size: 14px; color: #656d76; line-height: 1.6; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="icon">&#10006;</div>
                        <h1>授权失败</h1>
                        <p>%s</p>
                    </div>
                </body>
                </html>
                """.formatted(message);
    }
}
