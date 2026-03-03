package com.yeahmobi.everything.skill;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.common.NetworkException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Best-effort localization generator for Skill display metadata.
 *
 * <p>Uses the configured OpenAI-compatible LLM endpoint (DashScope compatible-mode)
 * to generate a Chinese "how to use" package for non-technical users. The output
 * is stored in {@code skill.i18n_json} and can be rendered by the client.</p>
 */
public class SkillLocalizationService {

    private static final Logger LOGGER = Logger.getLogger(SkillLocalizationService.class.getName());
    private static final Gson GSON = new Gson();

    private final Config config;
    private final HttpClientUtil http;

    public SkillLocalizationService(Config config, HttpClientUtil httpClientUtil) {
        this.config = config;
        this.http = httpClientUtil;
    }

    /**
     * Generates i18n JSON (zh-CN) and suggested examples for a Skill.
     *
     * @param skill the skill to localize
     * @return localization payload, or empty if disabled/failed
     */
    public Optional<SkillLocalizationPayload> localizeToZhCn(SkillAdmin skill) {
        if (skill == null) {
            return Optional.empty();
        }
        if (!config.isSkillLocalizationEnabled()) {
            return Optional.empty();
        }
        // If already localized, skip (idempotent)
        if (skill.i18nJson() != null && !skill.i18nJson().isBlank()) {
            return Optional.empty();
        }

        String apiKey = config.getLlmApiKey();
        String baseUrl = config.getLlmApiUrl();
        String model = config.getLlmApiModel();
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            return Optional.empty();
        }

        String url = baseUrl.endsWith("/") ? (baseUrl + "chat/completions") : (baseUrl + "/chat/completions");
        String system = buildSystemPrompt();

        try {
            return localizeWithRetry(skill, url, apiKey, model, system);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Skill localization failed for: " + skill.id(), ex);
            return Optional.empty();
        }
    }

    private Optional<SkillLocalizationPayload> localizeWithRetry(
            SkillAdmin skill, String url, String apiKey, String model, String system
    ) {
        int configuredMaxChars = Math.max(600, config.getSkillLocalizationMaxChars());
        int[] userPromptCaps = new int[] {
                configuredMaxChars,
                Math.min(3000, configuredMaxChars),
                Math.min(1500, configuredMaxChars)
        };
        int[] maxTokens = new int[] {900, 700, 500};
        Exception lastError = null;

        for (int i = 0; i < userPromptCaps.length; i++) {
            try {
                String user = buildUserPrompt(skill, userPromptCaps[i]);
                JsonObject request = buildRequest(model, system, user, maxTokens[i]);
                String response = http.post(
                        url,
                        GSON.toJson(request),
                        Map.of("Authorization", "Bearer " + apiKey)
                );
                Optional<SkillLocalizationPayload> payload = parsePayload(response);
                if (payload.isPresent()) {
                    if (i > 0) {
                        LOGGER.info("Skill localization recovered after retry for: " + skill.id());
                    }
                    return payload;
                }
            } catch (NetworkException ex) {
                lastError = ex;
            } catch (Exception ex) {
                lastError = ex;
                break;
            }

            if (i < userPromptCaps.length - 1) {
                sleepQuietly(800L * (i + 1));
            }
        }

        if (lastError != null) {
            throw new RuntimeException(lastError);
        }
        return Optional.empty();
    }

    private JsonObject buildRequest(String model, String system, String user, int maxTokens) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("temperature", 0.2);
        request.addProperty("top_p", 0.8);
        request.addProperty("max_tokens", Math.max(300, maxTokens));

        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));
        request.add("messages", messages);
        return request;
    }

    private Optional<SkillLocalizationPayload> parsePayload(String response) {
        String content = extractAssistantContent(response);
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String json = extractJsonObjectString(content);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("zh-CN") || !root.get("zh-CN").isJsonObject()) {
            return Optional.empty();
        }
        JsonObject zh = root.getAsJsonObject("zh-CN");
        List<String> exampleInputs = extractExampleInputs(zh);
        String localizedOneLine = optString(zh, "oneLine");
        String localizedUsageGuide = buildLocalizedUsageGuide(zh);
        return Optional.of(new SkillLocalizationPayload(
                GSON.toJson(root),
                exampleInputs,
                localizedOneLine,
                localizedUsageGuide
        ));
    }

    private static JsonObject message(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content != null ? content : "");
        return msg;
    }

    private String buildSystemPrompt() {
        return """
你是一个“技能包产品经理 + 文案编辑”，要把一个可能是英文的 Skill，转换成面向非技术用户的中文使用说明包。

严格要求：
1) 只输出一个 JSON 对象，禁止输出多余文字、代码块围栏、解释。
2) JSON 顶层必须只有一个键：\"zh-CN\"
3) zh-CN 对象必须包含这些字段（全部必填）：
   - displayName: string（中文名，日常办公表达）
   - oneLine: string（一句话用途，<= 24字）
   - scenarios: string[]（3条，具体场景）
   - inputChecklist: string[]（用户需要提供的信息清单，3-7条）
   - examples: {title:string,input:string,expectedOutput:string}[]（2-3条）
   - outputFormat: string（说明输出结构，如“Markdown：标题+要点+表格”）
4) examples.input 需要是用户可以直接粘贴到输入框的内容；expectedOutput 用一句话说明会得到什么。
5) 额外输出字段：
   - usageGuide: string（中文使用引导，建议用 Markdown，包含“适用场景/输入清单/输出说明”三段）

输出示例（仅供你理解，最终仍需按要求输出 JSON）：
{"zh-CN":{"displayName":"会议纪要助手","oneLine":"把会议记录整理成纪要与待办","scenarios":["..."],"inputChecklist":["..."],"examples":[{"title":"...","input":"...","expectedOutput":"..."}],"outputFormat":"Markdown：标题+要点+待办表格","usageGuide":"## 适用场景\\n- ...\\n## 输入清单\\n- ...\\n## 输出说明\\n..."}}
""";
    }

    private String buildUserPrompt(SkillAdmin skill, int maxChars) {
        String usage = truncate(skill.usageGuide(), maxChars);
        String prompt = truncate(skill.promptTemplate(), maxChars);

        StringBuilder sb = new StringBuilder();
        sb.append("Skill 原始信息如下（可能为英文）：\n");
        sb.append("id: ").append(nullToEmpty(skill.id())).append("\n");
        sb.append("name: ").append(nullToEmpty(skill.name())).append("\n");
        sb.append("description: ").append(nullToEmpty(skill.description())).append("\n");
        sb.append("category: ").append(nullToEmpty(skill.category())).append("\n");
        sb.append("usage_guide(raw):\n").append(usage).append("\n\n");
        sb.append("prompt_template(raw):\n").append(prompt).append("\n\n");

        if (skill.examples() != null && !skill.examples().isEmpty()) {
            sb.append("examples(existing):\n");
            for (String ex : skill.examples()) {
                sb.append("- ").append(ex).append("\n");
            }
        }
        return sb.toString();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extractAssistantContent(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!root.has("choices")) {
                return null;
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonObject first = choices.get(0).getAsJsonObject();
            if (!first.has("message")) {
                return null;
            }
            JsonObject msg = first.getAsJsonObject("message");
            if (!msg.has("content")) {
                return null;
            }
            return msg.get("content").isJsonNull() ? null : msg.get("content").getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String extractJsonObjectString(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim();
        // Remove accidental code fences
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            int endFence = cleaned.lastIndexOf("```");
            if (endFence != -1) {
                cleaned = cleaned.substring(0, endFence);
            }
            cleaned = cleaned.trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            return null;
        }
        return cleaned.substring(start, end + 1).trim();
    }

    private static List<String> extractExampleInputs(JsonObject zh) {
        if (zh == null || !zh.has("examples") || !zh.get("examples").isJsonArray()) {
            return List.of();
        }
        List<String> inputs = new ArrayList<>();
        JsonArray arr = zh.getAsJsonArray("examples");
        for (int i = 0; i < arr.size(); i++) {
            try {
                JsonObject ex = arr.get(i).getAsJsonObject();
                if (ex.has("input") && !ex.get("input").isJsonNull()) {
                    String input = ex.get("input").getAsString();
                    if (input != null && !input.isBlank()) {
                        inputs.add(input.trim());
                    }
                }
            } catch (Exception ignored) {
                // ignore malformed items
            }
        }
        return inputs;
    }

    private static String buildLocalizedUsageGuide(JsonObject zh) {
        if (zh == null) {
            return null;
        }
        String usageGuide = optString(zh, "usageGuide");
        if (usageGuide != null && !usageGuide.isBlank()) {
            return usageGuide.trim();
        }
        List<String> scenarios = optStringList(zh.get("scenarios"));
        List<String> inputChecklist = optStringList(zh.get("inputChecklist"));
        String outputFormat = optString(zh, "outputFormat");
        if (scenarios.isEmpty() && inputChecklist.isEmpty() && (outputFormat == null || outputFormat.isBlank())) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (!scenarios.isEmpty()) {
            sb.append("## 适用场景\n");
            for (String s : scenarios) {
                sb.append("- ").append(s).append("\n");
            }
            sb.append("\n");
        }
        if (!inputChecklist.isEmpty()) {
            sb.append("## 输入清单\n");
            for (String item : inputChecklist) {
                sb.append("- ").append(item).append("\n");
            }
            sb.append("\n");
        }
        if (outputFormat != null && !outputFormat.isBlank()) {
            sb.append("## 输出说明\n");
            sb.append(outputFormat).append("\n");
        }
        String value = sb.toString().trim();
        return value.isBlank() ? null : value;
    }

    private static String optString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static List<String> optStringList(com.google.gson.JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            try {
                String s = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
                if (s != null && !s.isBlank()) {
                    list.add(s.trim());
                }
            } catch (Exception ignored) {
                // ignore malformed items
            }
        }
        return list;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (maxChars <= 0) {
            return s;
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }
}

