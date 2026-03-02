package io.github.sibmaks;

import java.time.Instant;

public final class Log {
    public static volatile boolean ENABLED = true;

    public static void info(String who, String msg) {
        if (!ENABLED) return;
        System.out.printf("%s [%s] %s%n", Instant.now(), who, msg);
    }

    public static void error(String who, String msg, Throwable t) {
        if (!ENABLED) return;
        System.out.printf("%s [%s] ERROR: %s%n", Instant.now(), who, msg);
        if (t != null) t.printStackTrace(System.out);
    }

    private Log() {}
}