package io.github.yixing233.hyperduo;

/**
 * Desktop-only stand-in for the real {@code TrioState}, which pulls in Xposed
 * and ROM reflection. The preview harness only needs the fields the renderer
 * reads.
 */
final class TrioState {

    static final int SIM_SLOTS = 2;

    /**
     * The clock string the capsule draws. On the device {@code ClockTicker}
     * publishes this on the minute boundary; the harness sets it directly so a
     * render is reproducible without waiting for one.
     */
    static String sClockText = "19:41";

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
