package com.yeahmobi.everything.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkExceptionTest {

    @Test
    void testMessageConstructor() {
        NetworkException ex = new NetworkException("connection failed");
        assertEquals("connection failed", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testMessageAndCauseConstructor() {
        IOException cause = new IOException("timeout");
        NetworkException ex = new NetworkException("request failed", cause);
        assertEquals("request failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void testIsCheckedException() {
        assertTrue(Exception.class.isAssignableFrom(NetworkException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(NetworkException.class));
    }

    // Helper class to avoid importing java.io.IOException in the test
    private static class IOException extends Exception {
        IOException(String message) {
            super(message);
        }
    }
}
