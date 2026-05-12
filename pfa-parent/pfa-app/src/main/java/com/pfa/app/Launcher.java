package com.pfa.app;

/**
 * Non-JavaFX launcher class for fat JAR distribution.
 * <p>
 * JavaFX requires that the main class in a fat JAR does NOT extend
 * {@link javafx.application.Application} when running from the classpath
 * (as opposed to the module path). This class serves as the entry point
 * for the shaded JAR and simply delegates to {@link Main#main(String[])}.
 * </p>
 * <p>
 * Satisfies Requirement 11.5: runnable JAR distribution.
 * </p>
 */
public class Launcher {

    public static void main(String[] args) {
        Main.main(args);
    }
}
