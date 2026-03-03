package com.yeahmobi.everything.cli;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.machineops.CliCommandRequest;
import com.yeahmobi.everything.machineops.CliCommandResult;
import com.yeahmobi.everything.machineops.CliScheduleResult;
import com.yeahmobi.everything.machineops.MachineOpsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Local loopback gateway that exposes machine operation APIs.
 */
public class LocalCliGatewayServer {

    private static final Gson GSON = new Gson();

    private final int port;
    private final String token;
    private final MachineOpsService machineOpsService;
    private HttpServer server;

    public LocalCliGatewayServer(int port, String token) {
        this.port = port;
        this.token = token == null ? "" : token.trim();
        this.machineOpsService = new MachineOpsService();
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", ex -> respondJson(ex, 200, "{\"ok\":true}"));
        server.createContext("/api/local-cli/os", this::handleDetectOs);
        server.createContext("/api/local-cli/confirm-ticket", this::handleConfirmTicket);
        server.createContext("/api/local-cli/exec", this::handleExec);
        server.createContext("/api/local-cli/schedule/create", this::handleScheduleCreate);
        server.createContext("/api/local-cli/schedule/list", this::handleScheduleList);
        server.createContext("/api/local-cli/schedule/pause", this::handleSchedulePause);
        server.createContext("/api/local-cli/schedule/delete", this::handleScheduleDelete);
        server.createContext("/api/local-cli/schedule/run-now", this::handleScheduleRunNow);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        machineOpsService.shutdown();
    }

    private void handleDetectOs(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        out.addProperty("osType", machineOpsService.detectOs());
        respondJson(exchange, 200, GSON.toJson(out));
    }

    private void handleExec(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }
        JsonObject req = parseBody(exchange);
        String command = getString(req, "command");
        String cwd = getString(req, "workingDirectory");
        int timeout = getInt(req, "timeoutSeconds", 30);
        boolean dryRun = getBoolean(req, "dryRun", true);
        boolean confirmed = getBoolean(req, "userConfirmed", false);
        String confirmTicket = getString(req, "confirmTicket");
        CliCommandResult result = machineOpsService.exec(new CliCommandRequest(
                command, cwd, timeout, dryRun, confirmed, confirmTicket));
        respondJson(exchange, 200, GSON.toJson(result));
    }

    private void handleConfirmTicket(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }
        JsonObject req = parseBody(exchange);
        String command = getString(req, "command");
        String cwd = getString(req, "workingDirectory");
        String ticket = machineOpsService.issueConfirmTicket(command, cwd);
        JsonObject out = new JsonObject();
        out.addProperty("success", ticket != null && !ticket.isBlank());
        out.addProperty("ticket", ticket == null ? "" : ticket);
        out.addProperty("expiresInSeconds", 30);
        out.addProperty("message", (ticket == null || ticket.isBlank())
                ? "确认票据生成失败，请先检查命令是否可执行"
                : "确认票据已生成，30秒内有效");
        respondJson(exchange, 200, GSON.toJson(out));
    }

    private void handleScheduleCreate(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        JsonObject req = parseBody(exchange);
        CliScheduleResult result = machineOpsService.createSchedule(
                getString(req, "name"),
                getString(req, "command"),
                getString(req, "triggerSpec")
        );
        respondJson(exchange, 200, GSON.toJson(result));
    }

    private void handleScheduleList(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        out.add("items", GSON.toJsonTree(machineOpsService.listSchedules()));
        respondJson(exchange, 200, GSON.toJson(out));
    }

    private void handleSchedulePause(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        JsonObject req = parseBody(exchange);
        CliScheduleResult result = machineOpsService.pauseSchedule(
                getString(req, "id"), getString(req, "backend"));
        respondJson(exchange, 200, GSON.toJson(result));
    }

    private void handleScheduleDelete(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        JsonObject req = parseBody(exchange);
        CliScheduleResult result = machineOpsService.deleteSchedule(
                getString(req, "id"), getString(req, "backend"));
        respondJson(exchange, 200, GSON.toJson(result));
    }

    private void handleScheduleRunNow(HttpExchange exchange) throws IOException {
        if (!authorize(exchange)) {
            return;
        }
        JsonObject req = parseBody(exchange);
        CliScheduleResult result = machineOpsService.runNow(
                getString(req, "id"), getString(req, "backend"));
        respondJson(exchange, 200, GSON.toJson(result));
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        if (token.isBlank()) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("X-Local-Token");
        if (header != null && header.equals(token)) {
            return true;
        }
        respondJson(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
        return false;
    }

    private JsonObject parseBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
