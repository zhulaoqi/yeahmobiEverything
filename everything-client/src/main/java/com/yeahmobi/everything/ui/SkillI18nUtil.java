package com.yeahmobi.everything.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight parser for {@link Skill#i18nJson()}.
 *
 * <p>We store localized display metadata as JSON (e.g. {"zh-CN":{...}}).
 * This helper extracts the zh-CN payload for UI display.</p>
 */
final class SkillI18nUtil {

    private SkillI18nUtil() {}

    static SkillI18nZhCn zhCn(Skill skill) {
        if (skill == null || skill.i18nJson() == null || skill.i18nJson().isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(skill.i18nJson()).getAsJsonObject();
            if (!root.has("zh-CN") || !root.get("zh-CN").isJsonObject()) {
                return null;
            }
            JsonObject zh = root.getAsJsonObject("zh-CN");
            String displayName = optString(zh, "displayName");
            String oneLine = optString(zh, "oneLine");
            String outputFormat = optString(zh, "outputFormat");
            List<String> scenarios = optStringList(zh.get("scenarios"));
            List<String> inputChecklist = optStringList(zh.get("inputChecklist"));
            List<SkillI18nExample> examples = optExamples(zh.get("examples"));

            return new SkillI18nZhCn(displayName, oneLine, scenarios, inputChecklist, examples, outputFormat);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String optString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        String v = obj.get(key).getAsString();
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    private static List<String> optStringList(JsonElement el) {
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
                // ignore
            }
        }
        return list;
    }

    private static List<SkillI18nExample> optExamples(JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<SkillI18nExample> list = new ArrayList<>();
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            try {
                JsonObject ex = arr.get(i).getAsJsonObject();
                String title = optString(ex, "title");
                String input = optString(ex, "input");
                String expected = optString(ex, "expectedOutput");
                if (input != null && !input.isBlank()) {
                    list.add(new SkillI18nExample(title, input, expected));
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return list;
    }
}

record SkillI18nExample(String title, String input, String expectedOutput) {}

record SkillI18nZhCn(
        String displayName,
        String oneLine,
        List<String> scenarios,
        List<String> inputChecklist,
        List<SkillI18nExample> examples,
        String outputFormat
) {}

