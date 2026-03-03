package com.yeahmobi.everything.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientUtilTest {

    @Test
    void testConstructorWithDefaultTimeout() {
        HttpClientUtil util = new HttpClientUtil();
        assertNotNull(util);
    }

    @Test
    void testConstructorWithCustomTimeout() {
        HttpClientUtil util = new HttpClientUtil(Duration.ofSeconds(10));
        assertNotNull(util);
    }

    @Test
    void testGetWithInvalidUrlThrowsNetworkException() {
        HttpClientUtil util = new HttpClientUtil(Duration.ofSeconds(5));
        assertThrows(NetworkException.class, () ->
                util.get("not-a-valid-url", Map.of())
        );
    }

    @Test
    void testPostWithInvalidUrlThrowsNetworkException() {
        HttpClientUtil util = new HttpClientUtil(Duration.ofSeconds(5));
        assertThrows(NetworkException.class, () ->
                util.post("not-a-valid-url", "{}", Map.of())
        );
    }

    @Test
    void testGetWithUnreachableHostThrowsNetworkException() {
        HttpClientUtil util = new HttpClientUtil(Duration.ofSeconds(2));
        assertThrows(NetworkException.class, () ->
                util.get("http://192.0.2.1:1/unreachable", Map.of())
        );
    }

    @Test
    void testPostWithUnreachableHostThrowsNetworkException() {
        HttpClientUtil util = new HttpClientUtil(Duration.ofSeconds(2));
        assertThrows(NetworkException.class, () ->
                util.post("http://192.0.2.1:1/unreachable", "{\"key\":\"value\"}", Map.of())
        );
    }

    @Test
    void testGetWithNullHeaders() {
        HttpClientUtil util = new HttpClientUtil(Duration.ofSeconds(2));
        // Should not throw NullPointerException, but will throw NetworkException for unreachable host
        assertThrows(NetworkException.class, () ->
                util.get("http://192.0.2.1:1/test", null)
        );
    }

    @Test
    void testPostWithNullHeaders() {
        HttpClientUtil util = new HttpClientUtil(Duration.ofSeconds(2));
        assertThrows(NetworkException.class, () ->
                util.post("http://192.0.2.1:1/test", "{}", null)
        );
    }
}
