package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses command-line style Skill integration commands into SkillAdmin objects.
 * <p>
 * Command format:
 * {@code --name "Skill名称" --desc "描述" --category "分类" --icon "图标" --type GENERAL|INTERNAL --kind PROMPT_ONLY|KNOWLEDGE_RAG --exec SINGLE|MULTI --prompt "Prompt模板"}
 * </p>
 * <p>
 * Required fields: name, desc, category. Optional fields default to:
 * icon="default.png", type=GENERAL, kind=PROMPT_ONLY, exec=SINGLE, prompt="".
 * </p>
 */
public class SkillCommandParser {

    /**
     * Parses a command string into a SkillAdmin record.
     *
     * @param command the command string to parse
     * @return the parsed SkillAdmin
     * @throws IllegalArgumentException if the command is invalid or missing required fields
     */
    public SkillAdmin parse(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        Map<String, String> params = parseParams(command);

        String name = requireParam(params, "name");
        String desc = requireParam(params, "desc");
        String category = requireParam(params, "category");
        String icon = params.getOrDefault("icon", "default.png");
        SkillType type = parseSkillType(params.getOrDefault("type", "GENERAL"));
        SkillKind kind = parseSkillKind(params.getOrDefault("kind", "PROMPT_ONLY"));
        SkillExecutionMode exec = parseExecutionMode(params.getOrDefault("exec", "SINGLE"));
        String prompt = params.getOrDefault("prompt", "");

        return new SkillAdmin(
                UUID.randomUUID().toString(),
                name, desc, icon, category,
                true,
                "",
                List.of(),
                null,       // i18nJson
                "admin",    // source
                "zh",       // sourceLang
                "basic",    // qualityTier
                type, kind, prompt, exec,
                System.currentTimeMillis()
        );
    }

    /**
     * Serializes a SkillAdmin back into a command string.
     *
     * @param skill the SkillAdmin to serialize
     * @return the command string representation
     */
    public String toCommand(SkillAdmin skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("--name \"").append(escapeQuotes(skill.name())).append("\"");
        sb.append(" --desc \"").append(escapeQuotes(skill.description())).append("\"");
        sb.append(" --category \"").append(escapeQuotes(skill.category())).append("\"");
        sb.append(" --icon \"").append(escapeQuotes(skill.icon())).append("\"");
        sb.append(" --type ").append(skill.type().name());
        sb.append(" --kind ").append(skill.kind().name());
        sb.append(" --exec ").append(skill.executionMode().name());
        sb.append(" --prompt \"").append(escapeQuotes(skill.promptTemplate())).append("\"");
        return sb.toString();
    }

    // ---- Internal helpers ----

    Map<String, String> parseParams(String command) {
        Map<String, String> params = new HashMap<>();
        int i = 0;
        char[] chars = command.toCharArray();
        int len = chars.length;

        while (i < len) {
            // Skip whitespace
            while (i < len && Character.isWhitespace(chars[i])) i++;
            if (i >= len) break;

            // Expect --key
            if (i + 1 < len && chars[i] == '-' && chars[i + 1] == '-') {
                i += 2;
                int keyStart = i;
                while (i < len && !Character.isWhitespace(chars[i])) i++;
                String key = command.substring(keyStart, i);

                // Skip whitespace between key and value
                while (i < len && Character.isWhitespace(chars[i])) i++;
                if (i >= len) {
                    params.put(key, "");
                    break;
                }

                // Parse value (quoted or unquoted)
                String value;
                if (chars[i] == '"') {
                    i++; // skip opening quote
                    StringBuilder sb = new StringBuilder();
                    while (i < len && chars[i] != '"') {
                        if (chars[i] == '\\' && i + 1 < len && chars[i + 1] == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            sb.append(chars[i]);
                            i++;
                        }
                    }
                    if (i < len) i++; // skip closing quote
                    value = sb.toString();
                } else {
                    int valStart = i;
                    while (i < len && !Character.isWhitespace(chars[i])) i++;
                    value = command.substring(valStart, i);
                }
                params.put(key, value);
            } else {
                // Skip unexpected characters
                i++;
            }
        }
        return params;
    }

    private String requireParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: --" + key);
        }
        return value;
    }

    private SkillType parseSkillType(String value) {
        try {
            return SkillType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid skill type: " + value + ". Must be GENERAL or INTERNAL");
        }
    }

    private SkillKind parseSkillKind(String value) {
        try {
            return SkillKind.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid skill kind: " + value + ". Must be PROMPT_ONLY or KNOWLEDGE_RAG");
        }
    }

    private SkillExecutionMode parseExecutionMode(String value) {
        try {
            return SkillExecutionMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid exec mode: " + value + ". Must be SINGLE or MULTI");
        }
    }

    private String escapeQuotes(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"");
    }
}
