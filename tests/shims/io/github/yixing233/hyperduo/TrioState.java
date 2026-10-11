package io.github.yixing233.hyperduo;

/**
 * Desktop-only stand-in for the real {@code TrioState}, which pulls in Xposed
 * and ROM reflection. The preview harness only needs the fields the renderer
 * reads.
 */
final class TrioState {

    static final int SIM_SLOTS = 2;

    int level = 72;
    boolean charging;
    boolean quickCharging;
    boolean powerSave;
    boolean low;
    boolean wifiPresent = true;
    int wifiLevel = 3;
    int mobileLevel = 4;
    int[] slotLevels = { -1, -1 };
    int sims;
    String mobileType = "";

    int foreground() {
        return 0xFF000000;
    }
}
