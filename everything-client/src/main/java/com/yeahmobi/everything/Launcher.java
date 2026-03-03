package com.yeahmobi.everything;

import javafx.application.Application;

/**
 * Application launcher entry point.
 * <p>
 * JavaFX requires the main class to NOT extend {@link Application} when
 * running from a Fat JAR (shaded JAR). This is because the Java module
 * system performs a check on the main class: if it extends Application,
 * it expects JavaFX modules to be loaded via the module path, which is
 * not the case in a Fat JAR.
 * </p>
 * <p>
 * This plain launcher class bypasses that check by delegating to
 * {@link Application#launch(Class, String...)}.
 * </p>
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
