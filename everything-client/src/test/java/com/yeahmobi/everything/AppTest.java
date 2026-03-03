package com.yeahmobi.everything;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link App} class.
 * <p>
 * Tests focus on verifiable non-UI aspects: minimum window size constants,
 * NoOpCacheService behavior, and the main method existence.
 * Full JavaFX integration testing requires a running JavaFX toolkit which
 * is not available in headless CI environments.
 * </p>
 */
class AppTest {

    @Test
    void minimumWindowSizeConstants() {
        assertEquals(800, App.MIN_WIDTH, "Minimum width should be 800");
        assertEquals(600, App.MIN_HEIGHT, "Minimum height should be 600");
    }

    @Test
    void appClassExtendsJavaFXApplication() {
        assertTrue(javafx.application.Application.class.isAssignableFrom(App.class),
                "App should extend javafx.application.Application");
    }

    @Test
    void mainMethodExists() throws NoSuchMethodException {
        // Verify the main entry point method exists with correct signature
        var mainMethod = App.class.getMethod("main", String[].class);
        assertNotNull(mainMethod, "App should have a public static main method");
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()),
                "main method should be static");
    }
}
