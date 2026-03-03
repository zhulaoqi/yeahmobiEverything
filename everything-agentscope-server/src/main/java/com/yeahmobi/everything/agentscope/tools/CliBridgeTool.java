package com.yeahmobi.everything.agentscope.tools;

import com.google.gson.Gson;
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

/**
 * Bridge toolkit to local CLI gateway running in client process.
 */
public class CliBridgeTool extends Toolkit {

    private static final Gson GSON = new Gson();
    private final Config config;

    public CliBridgeTool() {
        this(Config.getInstance());
    }

    public CliBridgeTool(Config config) {
        this.config = config;
    }

    public String cliDetectOs() {
        return get("/api/local-cli/os");
    }

    public String cliExec(String command, String workingDirectory, Integer timeoutSeconds, Boolean dryRun, Boolean userConfirmed) {
        JsonObject body = new JsonObject();
        body.addProperty("command", command == null ? "" : command);
        body.addProperty("workingDirectory", workingDirectory == null ? "" : workingDirectory);
        body.addProperty("timeoutSeconds", timeoutSeconds == null ? 30 : timeoutSeconds);
        body.addProperty("dryRun", dryRun == null || dryRun);
        body.addProperty("userConfirmed", userConfirmed != null && userConfirmed);
        body.addProperty("confirmTicket", "");
        return post("/api/local-cli/exec", body);
    }

    public String cliIssueConfirmTicket(String command, String workingDirectory) {
        JsonObject body = new JsonObject();
        body.addProperty("command", command == null ? "" : command);
        body.addProperty("workingDirectory", workingDirectory == null ? "" : workingDirectory);
        return post("/api/local-cli/confirm-ticket", body);
    }

    public String cliExecWithTicket(String command, String workingDirectory, Integer timeoutSeconds,
                                    Boolean dryRun, Boolean userConfirmed, String confirmTicket) {
        JsonObject body = new JsonObject();
        body.addProperty("command", command == null ? "" : command);
        body.addProperty("workingDirectory", workingDirectory == null ? "" : workingDirectory);
        body.addProperty("timeoutSeconds", timeoutSeconds == null ? 30 : timeoutSeconds);
        body.addProperty("dryRun", dryRun == null || dryRun);
        body.addProperty("userConfirmed", userConfirmed != null && userConfirmed);
        body.addProperty("confirmTicket", confirmTicket == null ? "" : confirmTicket);
        return post("/api/local-cli/exec", body);
    }

    public String scheduleCreate(String name, String command, String triggerSpec) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name == null ? "" : name);
        body.addProperty("command", command == null ? "" : command);
        body.addProperty("triggerSpec", triggerSpec == null ? "" : triggerSpec);
        return post("/api/local-cli/schedule/create", body);
    }

    public String scheduleList() {
        return get("/api/local-cli/schedule/list");
    }

    public String schedulePause(String id, String backend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id == null ? "" : id);
        body.addProperty("backend", backend == null ? "" : backend);
        return post("/api/local-cli/schedule/pause", body);
    }

    public String scheduleDelete(String id, String backend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id == null ? "" : id);
        body.addProperty("backend", backend == null ? "" : backend);
        return post("/api/local-cli/schedule/delete", body);
    }

    public String scheduleRunNow(String id, String backend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id == null ? "" : id);
        body.addProperty("backend", backend == null ? "" : backend);
        return post("/api/local-cli/schedule/run-now", body);
    }

    private String get(String path) {
        String endpoint = endpoint(path);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs()))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .GET();
            attachToken(builder);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return "{\"success\":false,\"message\":\"CLI gateway HTTP error: " + response.statusCode() + "\"}";
            }
            return prettyJson(response.body());
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"CLI gateway call failed: " + safeMsg(e.getMessage()) + "\"}";
        }
    }

    private String post(String path, JsonObject body) {
        String endpoint = endpoint(path);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs()))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            attachToken(builder);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return "{\"success\":false,\"message\":\"CLI gateway HTTP error: " + response.statusCode() + "\"}";
            }
            return prettyJson(response.body());
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"CLI gateway call failed: " + safeMsg(e.getMessage()) + "\"}";
        }
    }

    private void attachToken(HttpRequest.Builder builder) {
        String token = config.getLocalCliGatewayToken();
        if (token != null && !token.isBlank()) {
            builder.header("X-Local-Token", token);
        }
    }

    private String endpoint(String path) {
        String base = config.getLocalCliGatewayUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:19199";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private int timeoutMs() {
        return Math.max(3000, config.getAgentScopeMcpTimeoutMs());
    }

    private String prettyJson(String value) {
        try {
            return GSON.toJson(JsonParser.parseString(value));
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private String safeMsg(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'");
    }
}
