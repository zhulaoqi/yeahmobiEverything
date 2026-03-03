package com.yeahmobi.everything.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration reader.
 * Reads configuration from application.properties on the classpath.
 */
public final class Config {

    private static final String CONFIG_FILE = "application.properties";
    private static Config instance;
    private final Properties properties;

    private Config() {
        properties = new Properties();
        loadProperties();
    }

    private Config(Properties properties) {
        this.properties = properties;
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            // Properties remain empty if loading fails
        }
    }

    /**
     * Returns the singleton Config instance.
     */
    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    /**
     * Creates a Config instance from the given Properties (for testing).
     */
    public static Config fromProperties(Properties properties) {
        return new Config(properties);
    }

    /**
     * Resets the singleton instance (useful for testing).
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    /**
     * Gets a configuration value by key.
     *
     * @param key the property key
     * @return the property value, or null if not found
     */
    public String get(String key) {
        return resolveSystemProperties(properties.getProperty(key));
    }

    /**
     * Gets a configuration value by key with a default value.
     *
     * @param key          the property key
     * @param defaultValue the default value if key is not found
     * @return the property value, or defaultValue if not found
     */
    public String get(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        return resolveSystemProperties(value);
    }

    /**
     * Gets a configuration value as an integer.
     *
     * @param key          the property key
     * @param defaultValue the default value if key is not found or not a valid integer
     * @return the integer value
     */
    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // --- LLM API ---
    public String getLlmApiUrl() {
        return get("llm.api.url");
    }

    public String getLlmApiKey() {
        return get("llm.api.key");
    }

    public String getLlmApiModel() {
        return get("llm.api.model");
    }

    public int getLlmApiTimeout() {
        return getInt("llm.api.timeout", 30000);
    }

    // --- AgentScope ---
    public boolean isAgentScopeEnabled() {
        return "true".equalsIgnoreCase(get("agentscope.enabled", "false"));
    }

    public String getAgentScopeApiUrl() {
        return get("agentscope.api.url");
    }

    public String getAgentScopeApiKey() {
        return get("agentscope.api.key");
    }

    public String getAgentScopeModel() {
        return get("agentscope.model");
    }

    public int getAgentScopeServerPort() {
        return getInt("agentscope.server.port", 8099);
    }

    public String getAgentScopeServerUrl() {
        return get("agentscope.server.url");
    }

    public int getAgentScopeRequestTimeoutMs() {
        return getInt("agentscope.request.timeout.ms", 120000);
    }

    public int getAgentScopeExecutionTimeoutSeconds() {
        return getInt("agentscope.execution.timeout.seconds", 120);
    }

    public boolean isAgentScopeMcpEnabled() {
        return "true".equalsIgnoreCase(get("agentscope.mcp.enabled", "false"));
    }

    public String getAgentScopeMcpServerUrl() {
        return get("agentscope.mcp.server.url");
    }

    public String getAgentScopeMcpApiKey() {
        return get("agentscope.mcp.api.key");
    }

    public int getAgentScopeMcpTimeoutMs() {
        return getInt("agentscope.mcp.timeout.ms", 30000);
    }

    // --- Local CLI Gateway ---
    public int getLocalCliGatewayPort() {
        return getInt("local.cli.gateway.port", 19199);
    }

    public String getLocalCliGatewayUrl() {
        return get("local.cli.gateway.url", "http://127.0.0.1:" + getLocalCliGatewayPort());
    }

    public String getLocalCliGatewayToken() {
        return get("local.cli.gateway.token", "");
    }

    public boolean isAnthropicSkillAutoImport() {
        return "true".equalsIgnoreCase(get("skills.anthropic.auto_import", "false"));
    }

    public String getAnthropicSkillPath() {
        return get("skills.anthropic.path");
    }

    // --- Skill Localization ---
    public boolean isSkillLocalizationEnabled() {
        return "true".equalsIgnoreCase(get("skills.localization.enabled", "true"));
    }

    public int getSkillLocalizationMaxChars() {
        return getInt("skills.localization.max_chars", 6000);
    }

    // --- Feishu ---
    public String getFeishuWebhookUrl() {
        return get("feishu.webhook.url");
    }

    public String getFeishuOAuthAppId() {
        return get("feishu.oauth.app_id");
    }

    public String getFeishuOAuthAppSecret() {
        return get("feishu.oauth.app_secret");
    }

    public String getFeishuOAuthRedirectUri() {
        return get("feishu.oauth.redirect_uri");
    }

    public String getFeishuAdminUserId() {
        return get("feishu.admin.user_id");
    }

    public String getFeishuAdminUserIdType() {
        return get("feishu.admin.user_id_type", "union_id");
    }

    // --- Backend API ---
    public String getBackendApiUrl() {
        return get("backend.api.url");
    }

    // --- MySQL ---
    public String getMysqlUrl() {
        return get("mysql.url");
    }

    public String getMysqlUsername() {
        return get("mysql.username");
    }

    public String getMysqlPassword() {
        return get("mysql.password");
    }

    // --- Redis ---
    public String getRedisHost() {
        return get("redis.host", "localhost");
    }

    public int getRedisPort() {
        return getInt("redis.port", 6379);
    }

    public String getRedisPassword() {
        return get("redis.password");
    }

    public int getRedisDatabase() {
        return getInt("redis.database", 0);
    }

    // --- Local SQLite ---
    public String getLocalDbPath() {
        return get("db.local.path");
    }

    // --- App ---
    public String getAppName() {
        return get("app.name", "Yeahmobi Everything");
    }

    public String getAppVersion() {
        return get("app.version", "1.0.0");
    }

    public String getAppTheme() {
        return get("app.theme", "light");
    }

    /**
     * Resolves ${system.property} placeholders in the value.
     */
    private String resolveSystemProperties(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            if (value.charAt(i) == '$' && i + 1 < value.length() && value.charAt(i + 1) == '{') {
                int end = value.indexOf('}', i + 2);
                if (end != -1) {
                    String propName = value.substring(i + 2, end);
                    String propValue = System.getProperty(propName);
                    result.append(propValue != null ? propValue : value.substring(i, end + 1));
                    i = end + 1;
                } else {
                    result.append(value.charAt(i));
                    i++;
                }
            } else {
                result.append(value.charAt(i));
                i++;
            }
        }
        return result.toString();
    }
}
