package com.yeahmobi.everything.cli;

import com.google.gson.JsonObject;
import com.yeahmobi.everything.common.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Lightweight client for local CLI gateway.
 */
public class LocalCliGatewayClient {

    private final Config config;
    private final HttpClient client;

    public LocalCliGatewayClient() {
        this(Config.getInstance());
    }

    public LocalCliGatewayClient(Config config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String detectOs() {
        return get("/api/local-cli/os");
    }

    public String exec(String command, String cwd, int timeoutSec, boolean dryRun, boolean userConfirmed) {
        return exec(command, cwd, timeoutSec, dryRun, userConfirmed, null);
    }

    public String exec(String command, String cwd, int timeoutSec, boolean dryRun, boolean userConfirmed, String confirmTicket) {
        JsonObject body = new JsonObject();
        body.addProperty("command", command == null ? "" : command);
        body.addProperty("workingDirectory", cwd == null ? "" : cwd);
        body.addProperty("timeoutSeconds", timeoutSec);
        body.addProperty("dryRun", dryRun);
        body.addProperty("userConfirmed", userConfirmed);
        body.addProperty("confirmTicket", confirmTicket == null ? "" : confirmTicket);
        return post("/api/local-cli/exec", body.toString());
    }

    public String issueConfirmTicket(String command, String cwd) {
        JsonObject body = new JsonObject();
        body.addProperty("command", command == null ? "" : command);
        body.addProperty("workingDirectory", cwd == null ? "" : cwd);
        return post("/api/local-cli/confirm-ticket", body.toString());
    }

    public String scheduleCreate(String name, String command, String triggerSpec) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name == null ? "" : name);
        body.addProperty("command", command == null ? "" : command);
        body.addProperty("triggerSpec", triggerSpec == null ? "" : triggerSpec);
        return post("/api/local-cli/schedule/create", body.toString());
    }

    public String scheduleList() {
        return get("/api/local-cli/schedule/list");
    }

    public String schedulePause(String id, String backend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id == null ? "" : id);
        body.addProperty("backend", backend == null ? "" : backend);
        return post("/api/local-cli/schedule/pause", body.toString());
    }

    public String scheduleDelete(String id, String backend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id == null ? "" : id);
        body.addProperty("backend", backend == null ? "" : backend);
        return post("/api/local-cli/schedule/delete", body.toString());
    }

    public String scheduleRunNow(String id, String backend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id == null ? "" : id);
        body.addProperty("backend", backend == null ? "" : backend);
        return post("/api/local-cli/schedule/run-now", body.toString());
    }

    private String get(String path) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            attachToken(builder);
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.body();
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + safeMsg(e.getMessage()) + "\"}";
        }
    }

    private String post(String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8));
            attachToken(builder);
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.body();
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + safeMsg(e.getMessage()) + "\"}";
        }
    }

    private String baseUrl() {
        String base = config.getLocalCliGatewayUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:19199";
        }
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private void attachToken(HttpRequest.Builder builder) {
        String token = config.getLocalCliGatewayToken();
        if (token != null && !token.isBlank()) {
            builder.header("X-Local-Token", token);
        }
    }

    private String safeMsg(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.replace("\"", "'");
    }
}
