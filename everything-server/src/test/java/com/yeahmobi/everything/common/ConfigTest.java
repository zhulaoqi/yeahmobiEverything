package com.yeahmobi.everything.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @AfterEach
    void tearDown() {
        Config.resetInstance();
    }

    @Test
    void testGetReturnsPropertyValue() {
        Properties props = new Properties();
        props.setProperty("test.key", "test-value");
        Config config = Config.fromProperties(props);

        assertEquals("test-value", config.get("test.key"));
    }

    @Test
    void testGetReturnsNullForMissingKey() {
        Config config = Config.fromProperties(new Properties());
        assertNull(config.get("nonexistent.key"));
    }

    @Test
    void testGetWithDefaultValue() {
        Config config = Config.fromProperties(new Properties());
        assertEquals("default", config.get("missing.key", "default"));
    }

    @Test
    void testGetIntParsesInteger() {
        Properties props = new Properties();
        props.setProperty("port", "8080");
        Config config = Config.fromProperties(props);

        assertEquals(8080, config.getInt("port", 0));
    }

    @Test
    void testGetIntReturnsDefaultForInvalidValue() {
        Properties props = new Properties();
        props.setProperty("port", "not-a-number");
        Config config = Config.fromProperties(props);

        assertEquals(3000, config.getInt("port", 3000));
    }

    @Test
    void testGetIntReturnsDefaultForMissingKey() {
        Config config = Config.fromProperties(new Properties());
        assertEquals(6379, config.getInt("redis.port", 6379));
    }

    @Test
    void testSystemPropertyResolution() {
        String userHome = System.getProperty("user.home");
        Properties props = new Properties();
        props.setProperty("path", "${user.home}/data.db");
        Config config = Config.fromProperties(props);

        assertEquals(userHome + "/data.db", config.get("path"));
    }

    @Test
    void testUnresolvedPlaceholderKeptAsIs() {
        Properties props = new Properties();
        props.setProperty("path", "${nonexistent.prop}/data.db");
        Config config = Config.fromProperties(props);

        assertEquals("${nonexistent.prop}/data.db", config.get("path"));
    }

    @Test
    void testSingletonInstance() {
        Config instance1 = Config.getInstance();
        Config instance2 = Config.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void testSingletonLoadsApplicationProperties() {
        Config config = Config.getInstance();
        // application.properties is on the classpath with these values
        assertEquals("Yeahmobi Everything", config.getAppName());
        assertEquals("1.0.0", config.getAppVersion());
        assertEquals("light", config.getAppTheme());
    }

    @Test
    void testConvenienceGettersFromProperties() {
        Properties props = new Properties();
        props.setProperty("llm.api.url", "https://api.example.com/v1/chat");
        props.setProperty("llm.api.key", "test-key");
        props.setProperty("llm.api.timeout", "15000");
        props.setProperty("feishu.webhook.url", "https://feishu.example.com/hook");
        props.setProperty("feishu.oauth.app_id", "app123");
        props.setProperty("feishu.oauth.app_secret", "secret456");
        props.setProperty("feishu.oauth.redirect_uri", "http://localhost/callback");
        props.setProperty("backend.api.url", "https://api.yeahmobi.com");
        props.setProperty("mysql.url", "jdbc:mysql://localhost:3306/test");
        props.setProperty("mysql.username", "root");
        props.setProperty("mysql.password", "pass");
        props.setProperty("redis.host", "redis-host");
        props.setProperty("redis.port", "6380");
        props.setProperty("redis.password", "redis-pass");
        props.setProperty("redis.database", "2");
        props.setProperty("db.local.path", "/tmp/data.db");
        props.setProperty("app.name", "Test App");
        props.setProperty("app.version", "2.0.0");
        props.setProperty("app.theme", "dark");

        Config config = Config.fromProperties(props);

        assertEquals("https://api.example.com/v1/chat", config.getLlmApiUrl());
        assertEquals("test-key", config.getLlmApiKey());
        assertEquals(15000, config.getLlmApiTimeout());
        assertEquals("https://feishu.example.com/hook", config.getFeishuWebhookUrl());
        assertEquals("app123", config.getFeishuOAuthAppId());
        assertEquals("secret456", config.getFeishuOAuthAppSecret());
        assertEquals("http://localhost/callback", config.getFeishuOAuthRedirectUri());
        assertEquals("https://api.yeahmobi.com", config.getBackendApiUrl());
        assertEquals("jdbc:mysql://localhost:3306/test", config.getMysqlUrl());
        assertEquals("root", config.getMysqlUsername());
        assertEquals("pass", config.getMysqlPassword());
        assertEquals("redis-host", config.getRedisHost());
        assertEquals(6380, config.getRedisPort());
        assertEquals("redis-pass", config.getRedisPassword());
        assertEquals(2, config.getRedisDatabase());
        assertEquals("/tmp/data.db", config.getLocalDbPath());
        assertEquals("Test App", config.getAppName());
        assertEquals("2.0.0", config.getAppVersion());
        assertEquals("dark", config.getAppTheme());
    }

    @Test
    void testDefaultValuesForRedis() {
        Config config = Config.fromProperties(new Properties());
        assertEquals("localhost", config.getRedisHost());
        assertEquals(6379, config.getRedisPort());
        assertEquals(0, config.getRedisDatabase());
    }

    @Test
    void testDefaultLlmTimeout() {
        Config config = Config.fromProperties(new Properties());
        assertEquals(30000, config.getLlmApiTimeout());
    }
}
