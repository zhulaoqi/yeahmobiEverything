package com.yeahmobi.everything.agentscope.tools;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.Config;
import io.agentscope.core.tool.Toolkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Minimal MCP bridge toolkit via HTTP JSON-RPC.
 *
 * <p>Expected MCP server protocol:
 * - tools/list (JSON-RPC)
 * - tools/call (JSON-RPC)</p>
 */
public class McpBridgeTool extends Toolkit {

    private static final Gson GSON = new Gson();
    private final Config config;

    public McpBridgeTool() {
        this(Config.getInstance());
    }

    public McpBridgeTool(Config config) {
        super();
        this.config = config;
    }

    /**
     * List available tools from MCP server.
     */
    public String mcpListTools() {
        if (!config.isAgentScopeMcpEnabled()) {
            return "MCP is disabled. Set agentscope.mcp.enabled=true";
        }
        String endpoint = config.getAgentScopeMcpServerUrl();
        if (endpoint == null || endpoint.isBlank()) {
            return "MCP server url is empty. Set agentscope.mcp.server.url";
        }

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", UUID.randomUUID().toString());
        request.addProperty("method", "tools/list");
        request.add("params", new JsonObject());

        return callMcp(endpoint, request);
    }

    /**
     * Call a specific MCP tool.
     *
     * @param toolName      MCP tool name
     * @param argumentsJson JSON object string, e.g. {"query":"latest campaign"}
     */
    public String mcpCallTool(String toolName, String argumentsJson) {
        if (!config.isAgentScopeMcpEnabled()) {
            return "MCP is disabled. Set agentscope.mcp.enabled=true";
        }
        if (toolName == null || toolName.isBlank()) {
            return "toolName is required";
        }
        String endpoint = config.getAgentScopeMcpServerUrl();
        if (endpoint == null || endpoint.isBlank()) {
            return "MCP server url is empty. Set agentscope.mcp.server.url";
        }

        JsonObject argsObj = new JsonObject();
        if (argumentsJson != null && !argumentsJson.isBlank()) {
            try {
                JsonElement parsed = JsonParser.parseString(argumentsJson);
                if (!parsed.isJsonObject()) {
                    return "argumentsJson must be a JSON object string";
                }
                argsObj = parsed.getAsJsonObject();
            } catch (Exception e) {
                return "Invalid argumentsJson: " + e.getMessage();
            }
        }

        JsonObject params = new JsonObject();
        params.addProperty("name", toolName.trim());
        params.add("arguments", argsObj);

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", UUID.randomUUID().toString());
        request.addProperty("method", "tools/call");
        request.add("params", params);

        return callMcp(endpoint, request);
    }

    private String callMcp(String endpoint, JsonObject payload) {
        int timeoutMs = Math.max(3_000, config.getAgentScopeMcpTimeoutMs());
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));

            String apiKey = config.getAgentScopeMcpApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return "MCP HTTP error: " + response.statusCode() + " body=" + response.body();
            }
            return prettyJson(response.body());
        } catch (Exception e) {
            return "MCP call failed: " + e.getMessage();
        }
    }

    private String prettyJson(String value) {
        try {
            JsonElement parsed = JsonParser.parseString(value);
            return GSON.toJson(parsed);
        } catch (Exception e) {
            return value;
        }
    }
}

