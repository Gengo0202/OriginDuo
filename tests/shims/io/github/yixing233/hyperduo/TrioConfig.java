package io.github.yixing233.hyperduo;

/**
 * Desktop-only stand-in for the real {@code TrioConfig}, which needs the Xposed
 * API. The harness always renders with {@link TrioSettings#defaults()}.
 */
final class TrioConfig {

    private static final TrioSettings SNAPSHOT = TrioSettings.defaults();

    private TrioConfig() {
    }

    static TrioSettings get() {
        return SNAPSHOT;
    }

    static boolean debugLog() {
        return false;
    }
}
