package io.github.yixing233.hyperduo;

/**
 * Every decision about <em>what</em> the trio glyph puts on screen, resolved once
 * from a single settings snapshot.
 *
 * <p>Before this existed the same rule was written out by hand in five places:
 * {@code TrioRenderer.drawRingLayout}, {@code TrioRenderer.drawRectLayout},
 * {@code TrioHooks.applyMeterText}, the two battery-style hooks - and, in a
 * different shape again, the settings screen's own row gates. Copies drift, and
 * two of them had already drifted away from what the documentation claimed: the
 * rectangular arrangement silently ignored {@code valueCentred}, and the
 * percentage took the centre whenever Wi-Fi was absent whether or not the user
 * had asked for it.
 *
 * <p>This class is now the one place that rule lives. The renderer asks it what
 * to draw, the hook side asks it what to suppress, and the settings screen asks
 * it which rows mean anything - so a row can no longer be greyed out for a
 * reason the drawing code does not share.
 *
 * <p>Deliberately free of Android types and of static state. The hooked SystemUI
 * process and the settings app both build it, and the settings app builds it
 * again on every recomposition; it is a handful of field reads and copies, cheap
 * enough to build per frame.
 *
 * <p>Immutable.
 */
public final class TrioAppearance {

    /** {@link Prefs#STYLE_RING}, {@link Prefs#STYLE_RECT} or {@link Prefs#STYLE_CAPSULE}. */
    public final int style;
    /** True when {@link #style} is the rectangular arrangement. */
    public final boolean rect;
    /**
     * True when {@link #style} is the capsule arrangement.
     *
     * <p>Its own flag rather than a third case at each use: the two existing
     * arrangements are near enough siblings that one boolean covered both, and
     * every place asking about one of them also means "not the other". The
     * capsule is a different shape with a different meaning - its outline is the
     * gauge - so only the tests that really care are written against it.
     */
    public final boolean capsule;
    /**
     * True when {@link #style} is the ring.
     *
     * <p>Stated explicitly rather than left as {@code !rect}: that expression was
     * correct only while there were two arrangements, and the capsule makes it
     * quietly wrong. Every row and rule that says "the ring" now has a name for
     * it.
     */
    public final boolean ring;
    /** The master switch. False hands every slot back to MIUI. */
    public final boolean glyph;
    /** The Wi-Fi arcs are wanted; ink also needs a signal - see {@link #wifiInk}. */
    public final boolean wifi;
    /** The mobile level dots are drawn. */
    public final boolean mobile;
    /**
     * The user wants a <em>second</em> row of level dots while the glyph has
     * neither Wi-Fi ink nor a charger to report - see {@link #dualReading}.
     */
    public final boolean dualSim;
    /**
     * SIM 1's own row of four dots is wanted, as a component of its own.
     *
     * <p>Separate from {@link #dualSim}: that switch asks the aggregate meter for
     * a second row, this pair places each card's row independently. Neither
     * implies the other - a user can place SIM 2 in the notch and SIM 1 at the
     * bottom, or read only one card without touching the dual switch.
     */
    public final boolean showSim1;
    /** SIM 2's own row of four dots is wanted; see {@link #showSim1}. */
    public final boolean showSim2;
    /** The user wants only the current data SIM in the out-of-ring reading. */
    public final boolean dataSimOnly;
    /** The battery percentage is drawn. */
    public final boolean value;
    /** The charging bolt toggle; the free ring gives it a slot independent of the percentage. */
    public final boolean boltWanted;
    /**
     * The percentage takes the ring's centre even when the arcs are drawn.
     *
     * <p>Always false for the rectangular arrangement, which has no notch to move
     * the arcs into. Saying so here - rather than leaving the renderer to ignore
     * the switch quietly - is what lets the settings screen gate the row on the
     * style without a second copy of the rule.
     */
    public final boolean centreValue;
    /** {@link Prefs#MOBILE_TYPE_OFF}, {@code IN_RING} or {@code OUT_RING}. */
    public final int typeMode;
    /** The network type is drawn inside the glyph. */
    public final boolean typeInRing;
    /** The network type is drawn as a status-bar view outside the glyph. */
    public final boolean typeOutOfRing;
    /**
     * The charging power is drawn in the out-of-ring strip.
     *
     * <p>Unlike every other flag here this one does not decide the whole view:
     * the power and the temperature share one strip, and either flag alone is
     * enough to bring it back. Use {@link #perfAnywhere} for that question and
     * this one only for the power segment.
     */
    public final boolean perfPower;
    /** The battery temperature is drawn in the same out-of-ring strip. */
    public final boolean perfTemp;
    /**
     * Overall size of the two readouts, in percent of the adaptive fit.
     *
     * <p>Read by the hooked side only, but kept here with the rest so that every
     * caller that sizes the reading asks one object rather than reaching back
     * into the settings for a number the appearance already resolved.
     */
    public final int perfScale;
    /**
     * How heavy the two readouts are drawn, on the platform's 100..900 scale.
     *
     * <p>A setting of its own rather than a second reader of {@link #typeWeight}:
     * the readout is status-bar text beside MIUI's own figures, and the type
     * weight also drives the label drawn inside the ring. Kept here with the rest
     * so the hooked side asks one object for both the size and the weight.
     */
    public final int perfWeight;
    /**
     * Overall size of the glyph, in percent of the fit the host box implies.
     *
     * <p>Shrink-only, so 100 is the largest value this can hold and the shipped
     * look. The renderer multiplies its own scale by this; see
     * {@code TrioRenderer.drawInto}.
     */
    public final int glyphScale;

    public final boolean roleColors;
    public final int criticalOnDark;
    public final int criticalOnLight;
    public final int chargingOnDark;
    public final int chargingOnLight;
    public final int lowOnDark;
    public final int lowOnLight;
    public final int lowThreshold;

    public final int ringStroke;
    public final int arcStroke;
    public final int trackAlpha;
    public final int valueSize;
    public final int valueWeight;
    /** Independent size and weight of the capsule or free-ring clock. */
    public final int clockSize;
    public final int clockWeight;
    public final int typeSize;
    public final int outTypeSize;
    public final int outSignalSize;
    /**
     * The gap the out-of-ring label keeps on each side, in dp: {@code left} is
     * the side facing away from the battery in LTR and {@code right} the side
     * facing it. Both are physical sides of the label, not reading-order starts
     * and ends - the RTL branch swaps which gap each one supplies.
     */
    public final int outTypeMarginLeft;
    public final int outTypeMarginRight;
    /**
     * Gap between the out-of-ring reading and the battery, in dp. A reserved
     * margin: it is folded into the strip the native icon row gives up, so the
     * reading can never be moved on top of its neighbours.
     */
    public final int outSignalMargin;
    /** Scale of a trailing "A" in the network type, as a percentage. */
    public final int typeSuffixScale;
    public final int typeWeight;

    private TrioAppearance(TrioSettings c) {
        style = c.trioStyle;
        rect = c.trioStyle == Prefs.STYLE_RECT;
        capsule = c.trioStyle == Prefs.STYLE_CAPSULE;
        ring = c.trioStyle == Prefs.STYLE_RING;
        ringZones = TrioSettings.copyZones(c.ringZones);

        glyph = c.enabled;
        wifi = c.showWifi;
        mobile = c.showMobile;
        dualSim = c.dualSim;
        showSim1 = c.showSim1;
        showSim2 = c.showSim2;
        dataSimOnly = c.dataSimOnly;
        value = c.showValue;
        boltWanted = c.showBolt;
        centreValue = c.valueCentred && ring;
        typeMode = c.mobileTypeMode;
        typeInRing = c.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING;
        typeOutOfRing = c.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING;
        perfPower = c.perfPower;
        perfTemp = c.perfTemp;
        perfScale = c.perfScale;
        perfWeight = c.perfWeight;
        glyphScale = c.glyphScale;

        roleColors = c.roleColors;
        criticalOnDark = c.criticalOnDark;
        criticalOnLight = c.criticalOnLight;
        chargingOnDark = c.chargingOnDark;
        chargingOnLight = c.chargingOnLight;
        lowOnDark = c.lowOnDark;
        lowOnLight = c.lowOnLight;
        lowThreshold = c.lowThreshold;

        ringStroke = c.ringStroke;
        arcStroke = c.arcStroke;
        trackAlpha = c.trackAlpha;
        valueSize = c.valueSize;
        valueWeight = c.valueWeight;
        clockSize = c.clockSize;
        clockWeight = c.clockWeight;
        typeSize = c.typeSize;
        outTypeSize = c.outTypeSize;
        outSignalSize = c.outSignalSize;
        outTypeMarginLeft = c.outTypeMarginLeft;
        outTypeMarginRight = c.outTypeMarginRight;
        outSignalMargin = c.outSignalMargin;
        typeSuffixScale = c.typeSuffixScale;
        typeWeight = c.typeWeight;
    }

    /** The appearance of {@code cfg}, or of the shipped defaults when it is null. */
    public static TrioAppearance of(TrioSettings cfg) {
        return new TrioAppearance(cfg == null ? TrioSettings.defaults() : cfg);
    }

    /**
     * Whether the glyph owns the bolt.
     *
     * <p>The two arrangements answer differently, and the difference is real rather
     * than leftover:
     *
     * <ul>
     *   <li>The <b>ring</b> places pieces in three slots, so the bolt appears exactly
     *       where a slot names it ({@code PART_BOLT}). It is no longer tied to the
     *       percentage: with slots, "show the bolt" and "show the number" are two
     *       independent placements, and tying them would make one switch silently
     *       govern the other's slot.
     *   <li>The <b>capsule</b> has no slots - its right-hand reading is arbitrated
     *       (bolt, then arcs, then percentage, then type) - so there the bolt keeps
     *       following the percentage switch, which is what "charging replaces the
     *       number" means in that arrangement.
     * </ul>
     *
     * <p>Not gated on {@link #glyph}, because the preview keeps drawing the chosen
     * arrangement while the master switch is off.
     */
    public boolean drawsBolt() {
        return boltWanted && (ring ? hasRingPart(Prefs.PART_BOLT) : value);
    }

    /**
     * The three ring slots, packed four bits each - the whole layout.
     *
     * <p>There is no second, switch-derived layout: every piece the ring draws is
     * whatever these three slots name.
     */
    public final int[][] ringZones;

    /**
     * Whether {@code part} appears anywhere in the layout, whatever its priority.
     *
     * <p>This is the question the native-icon handoff needs: if a piece is placed at all,
     * the module owns that reading and MIUI's own icon must stay folded - even on a frame
     * where a higher-priority candidate is the one actually drawing that zone. Asking
     * "is it drawn this frame" instead would make the native icon flicker in and out as
     * the fallback chain moved.
     */
    public boolean hasRingPart(int part) {
        for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
            final int[] ids = ringZones[z];
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == part) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the module draws the arcs itself, which is also what decides whether MIUI's
     * own Wi-Fi icon is folded away.
     *
     * <p>Only the ring is slot-based; the capsule and the rectangle arbitrate their single
     * reading from the switches, so there the switch alone answers. Asking slots outside
     * the ring would read a layout those arrangements never consult.
     */
    public boolean ownsWifi() {
        return wifi && (!ring || hasRingPart(Prefs.PART_WIFI));
    }

    public boolean ownsValue() {
        return value && (!ring || hasRingPart(Prefs.PART_VALUE));
    }

    public boolean ownsType() {
        return typeOutOfRing || (typeInRing && (!ring || hasRingPart(Prefs.PART_TYPE)));
    }

    /** Native percentage follows the user's system style when the free ring releases it. */
    public boolean handsBackPercentage() {
        return glyph && ring && !ownsValue();
    }

    /**
     * Whether MIUI's own charging bolt has to stay out of the way, which is
     * exactly while this module is on <em>and</em> draws one of its own.
     */
    public boolean hidesNativeBolt() {
        return glyph && drawsBolt();
    }

    /** Whether the Wi-Fi arcs carry ink at this signal level. */
    public boolean wifiInk(int wifiLevel) {
        return ownsWifi() && wifiLevel >= 1;
    }

    /**
     * Whether a row of level dots is drawn per SIM - SIM 1 on top, SIM 2 below -
     * rather than the usual single row for the current data SIM.
     *
     * <p>The user asks for this while neither Wi-Fi ink nor a charger is on
     * screen. Both of those own the glyph's slots: the arcs take the notch the
     * bottom row needs, and charging already reports itself through the colour
     * and the bolt. Hiding the second row there keeps one reading per state
     * instead of stacking two.
     *
     * <p>Also needs two SIMs to read: with one - or with the levels not yet
     * sampled - the single row falls back to the current data SIM, which is what
     * the switch degrades to rather than drawing a row of empty dots.
     *
     * @param wifiInk whether the arcs actually carry ink this frame
     * @param charging whether the device reports a charger
     * @param sims how many SIM signal levels are known this frame
     */
    public boolean dualSimRows(boolean wifiInk, boolean charging, int sims) {
        return dualSim && signalDots() && sims >= 2 && !wifiInk && !charging;
    }

    /**
     * Whether the glyph itself draws the mobile level dots.
     *
     * <p>Both arrangements go through here so the two can never disagree about
     * where the signal lives. Out of ring the answer is no in both: the reading
     * has moved to the status bar, and leaving the dots behind would show the
     * same signal twice.
     */
    public boolean signalDots() {
        // The ring is slot-based, so a card has to be placed there to be drawn. The capsule
        // and the rectangle derive what they draw from the switches alone - they have no
        // slots - so for them the switch IS the answer.
        return mobile && (!ring || hasRingPart(Prefs.PART_MOBILE));
    }

    /**
     * Whether the glyph draws SIM 1's own row this frame.
     *
     * <p>Placement is the only thing that can turn a per-card row on: the row
     * exists as a <em>slot occupant</em>, and only the free ring has slots. The
     * automatic layouts keep the aggregate meter they have always drawn, because
     * they have no place to put a second independent piece - so answering "yes"
     * there would claim the native mobile slot while drawing nothing at all.
     * The switch is still independent of its twin: it gates the row without the
     * slot (a reserved slot with the switch off draws nothing, as with Wi-Fi).
     *
     * <p>Deliberately not gated on the two-SIM condition {@link #dualSimRows}
     * needs: a row is one card's reading, so a single-SIM device can still place
     * SIM 1's row. SIM 2's row draws nothing there, which is what an absent
     * reading should look like.
     */
    public boolean drawsSim1() {
        return showSim1 && mobile && ring
                && hasRingPart(Prefs.PART_SIM_1);
    }

    /** Whether the glyph draws SIM 2's own row this frame; see {@link #drawsSim1()}. */
    public boolean drawsSim2() {
        return showSim2 && mobile && ring
                && hasRingPart(Prefs.PART_SIM_2);
    }

    /** Whether either per-card row is drawn, which is what the hooks must ask. */
    public boolean drawsAnySimRow() {
        return drawsSim1() || drawsSim2();
    }

    /**
     * Whether MIUI's own mobile-signal slot has to be handed over, which is
     * exactly while this module draws that signal itself.
     *
     * <p>Stated here rather than inside {@code TrioHooks.foldedSlots()} so the rule can be
     * exercised without the Xposed API. The trap it encodes: folding the slot while drawing
     * nothing in its place would delete a signal indicator from the status bar. That is why
     * this asks "am I drawing the reading" rather than "did the user ask for a reading" -
     * with the out-of-ring position gone, the only way the module draws it is by having a
     * card placed in a third, which is exactly what these three predicates test.
     */
    public boolean foldsMobile() {
        return signalDots() || drawsAnySimRow();
    }

    /** Whether the network type is drawn at all, in either position. */
    public boolean typeAnywhere() {
        return typeMode != Prefs.MOBILE_TYPE_OFF;
    }

    /**
     * Whether the performance strip is drawn at all.
     *
     * <p>The two readouts share one view and one reserved strip, so this is the
     * question every caller that mounts or measures that view has to ask - never
     * {@link #perfPower} or {@link #perfTemp} alone. Either switch brings the
     * view back; with both off it is taken away entirely.
     */
    public boolean perfAnywhere() {
        return perfPower || perfTemp;
    }

    /**
     * The fit multiplier the glyph is drawn at, from {@link #glyphScale}.
     *
     * <p>Stated here rather than at the call site so the drawing, the window that
     * shows the same glyph and the settings preview cannot disagree about what the
     * slider means. An out-of-range value is clamped rather than trusted: the
     * stored number is clamped on read, but the broadcast path builds an
     * appearance from extras, and this is the one place a bad value would reach
     * the canvas.
     */
    public float glyphFit() {
        return Prefs.clamp(glyphScale, Prefs.MIN_GLYPH_SCALE, Prefs.MAX_GLYPH_SCALE) / 100f;
    }

    /** The same, for the reading: {@code perfScale} as a multiplier. */
    public float perfFit() {
        return Prefs.clamp(perfScale, Prefs.MIN_PERF_SCALE, Prefs.MAX_PERF_SCALE) / 100f;
    }

    /**
     * The weight the two readouts are drawn at, from {@link #perfWeight}.
     *
     * <p>Stated here for the same reason {@link #glyphFit} is: the broadcast path
     * builds an appearance from extras, and this is the one place a bad weight
     * would reach the typeface. Not a second reader of {@link #typeWeight} - the
     * readout is status-bar text and the type weight also feeds the in-ring label.
     */
    public int perfTypeWeight() {
        return Prefs.clamp(perfWeight, Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT);
    }

    /** The ring arrangement's slot assignment for one frame. */
    public Ring ring(int level, boolean charging, int wifiLevel, String mobileType, int sims) {
        return new Ring(this, level, charging, wifiLevel, mobileType, sims);
    }

    /** The rectangular arrangement's slot assignment for one frame. */
    public Rect rect(int level, boolean charging, int wifiLevel, String mobileType, int sims) {
        return new Rect(this, level, charging, wifiLevel, mobileType, sims);
    }

    /** The capsule arrangement's slot assignment for one frame. */
    public Capsule capsule(int level, boolean charging, int wifiLevel, String mobileType,
                           int sims) {
        return new Capsule(this, level, charging, wifiLevel, mobileType, sims);
    }

    /**
     * Where each piece goes in the ring arrangement.
     *
     * <p>The rule worth reading twice is {@code valueInCentre}: the percentage
     * takes the middle when the user asked for it, and <em>also</em> whenever
     * neither the arcs nor an in-glyph type label is competing for that space.
     * With Wi-Fi off nothing competes, so the switch is not needed to get the
     * centre - which is why its row is gated on Wi-Fi. Stated once here, the
     * renderer, the settings screen and the documentation cannot disagree about
     * it again.
     *
     * <p>Immutable.
     */
    public static final class Ring {
        /** Actual occupants this frame; null selects the original automatic layout. */
        public final int[] parts;
        /** The arcs leave ink this frame. */
        public final boolean wifi;
        /** The arcs are drawn at all. */
        public final boolean drawWifi;
        /** The arcs are shrunk into the notch to make room for the centre. */
        public final boolean wifiInGap;
        /** The bolt takes the notch. */
        public final boolean bolt;
        /** The percentage is drawn at all. */
        public final boolean drawValue;
        public final boolean valueInCentre;
        public final boolean valueInGap;
        /** The in-glyph network type is drawn, and where. */
        public final boolean typeInCentre;
        public final boolean typeInGap;
        /** Something occupies the notch, so the ring has to open for it. */
        public final boolean gapUsed;
        /**
         * The level dots are drawn as two rows, one per SIM, instead of one row
         * for the current data SIM.
         */
        public final boolean dual;

        /**
         * The renderer and editor use the same opening around the top component.
         *
         * <p>Only the <em>dual reading</em> widens the mouth: its top row is
         * {@code DOTS} mirrored about the ring centre, so the outer dots sit almost on
         * the arc's own radius and a narrow mouth runs the rounded stroke end into
         * them.
         *
         * <p>The ring's <b>bottom</b> opening is the reference for symmetry, not this
         * mouth - see {@code TrioGeometry.B_SWEEP}, where the arc's own bottom opening
         * was brought down to the top gap's width so the two halves mirror each other.
         */
        public float gapStart() {
            return !gapUsed ? TrioGeometry.GAP_NONE
                    : dual ? TrioGeometry.GAP_START_DUAL
                    : bolt ? TrioGeometry.GAP_START_CHARGE : TrioGeometry.GAP_START_IDLE;
        }

        public float gapEnd() {
            return !gapUsed ? TrioGeometry.GAP_NONE
                    : dual ? TrioGeometry.GAP_END_DUAL
                    : bolt ? TrioGeometry.GAP_END_CHARGE : TrioGeometry.GAP_END_IDLE;
        }

        /**
         * The first entry of {@code order} that has ink on this frame, or {@link
         * Prefs#PART_NONE}.
         *
         * <p>Skipping rather than stopping is the whole behaviour: a candidate whose own
         * switch is off, or which has no reading to show, is simply passed over and the next
         * one is tried. That is why a zone does not go blank when the top of its list happens
         * to be a piece with nothing to draw.
         *
         * <p>Each condition is the same one that piece would test on its own - a percentage
         * needs a level, the arcs need a signal, the bolt only exists while charging, and a
         * card answers to its own switch - so a place's list cannot resurrect something the
         * user switched off.
         */
        private static int firstDrawable(TrioAppearance a, int[] order, int level,
                                         boolean charging, int wifiLevel, String mobileType) {
            if (order == null) {
                return Prefs.PART_NONE;
            }
            for (int i = 0; i < order.length; i++) {
                if (draws(a, order[i], level, charging, wifiLevel, mobileType)) {
                    return order[i];
                }
            }
            return Prefs.PART_NONE;
        }

        /**
         * Whether one piece would draw ink on this frame, judging only its SWITCH and the
         * frame's readings - never the layout.
         *
         * <p>The layout is what is being decided here, so consulting it would be circular:
         * {@link #drawsBolt()} asks whether the bolt is placed, and asking that while working
         * out what to place would make the answer depend on itself. The switch alone is the
         * right question, because "is this piece wanted" and "is this the zone it goes in" are
         * separate, and this only answers the first.
         */
        private static boolean draws(TrioAppearance a, int part, int level, boolean charging,
                                     int wifiLevel, String mobileType) {
            switch (part) {
                case Prefs.PART_WIFI:
                    return a.wifi && a.wifiInk(wifiLevel);
                case Prefs.PART_VALUE:
                    return a.value && level >= 0;
                case Prefs.PART_BOLT:
                    return a.boltWanted && charging;
                case Prefs.PART_TYPE:
                    return a.typeInRing && mobileType != null && !mobileType.isEmpty();
                case Prefs.PART_MOBILE:
                    return a.mobile && a.ring;
                case Prefs.PART_SIM_1:
                    return a.mobile && a.ring && a.showSim1;
                case Prefs.PART_SIM_2:
                    return a.mobile && a.ring && a.showSim2;
                case Prefs.PART_CLOCK:
                    // The clock always has something to say; whether the arrangement shows it
                    // is the renderer's business.
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Resolves one frame from the three zones.
         *
         * <p>There is no automatic alternative any more: the slots are the layout, so
         * this reads them and decides only whether each piece has anything to draw on
         * this frame (a percentage needs a level, the arcs need a signal, and so on).
         * The old second path - which derived every position from switches and had a
         * priority order for the shared notch - is gone, and with it the case where the
         * same settings produced two different drawings depending on whether the user
         * had ever opened the editor.
         */
        private Ring(TrioAppearance a, int level, boolean charging, int wifiLevel,
                     String mobileType, int sims) {
            parts = new int[3];
            for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
                // The zone draws the FIRST candidate that has something to show this frame.
                //
                // That is what makes the list a fallback chain rather than a stack: with
                // [percentage, arcs] the percentage takes the zone while it has a reading,
                // and the arcs step in on a frame where it does not - so a zone never goes
                // blank while any of its pieces could draw. Order is therefore the only
                // arbitration, which is exactly what dragging the list edits.
                parts[z] = firstDrawable(a, a.ringZones[z], level, charging, wifiLevel,
                        mobileType);
            }
            wifi = drawWifi = a.wifiInk(wifiLevel);
            bolt = charging && a.drawsBolt();
            drawValue = a.ownsValue() && level >= 0;
            valueInCentre = parts[1] == Prefs.PART_VALUE;
            valueInGap = parts[0] == Prefs.PART_VALUE;
            wifiInGap = parts[0] == Prefs.PART_WIFI;
            typeInCentre = parts[1] == Prefs.PART_TYPE;
            typeInGap = parts[0] == Prefs.PART_TYPE;
            gapUsed = parts[0] != Prefs.PART_NONE;
            // The dual switch does not change how a zone is drawn. It used to mean "add a
            // second row to the meter, and widen the notch to hold it" - but a zone draws one
            // piece, so a second row has nowhere to go. Two cards are expressed by placing
            // PART_SIM_1 and PART_SIM_2 in two different zones. Kept as a field (always false
            // here) so the renderer's parameters do not change shape for the rectangular
            // arrangement, which does still have two columns.
            dual = false;
        }
    }

    /**
     * Where each piece goes in the rectangular arrangement.
     *
     * <p>Note what is <em>not</em> decided here: the centring switch, the notch,
     * and any second thickness setting. The bar takes its thickness from
     * {@code ringStroke} and the arcs keep {@code arcStroke}; the settings screen
     * relabels that one row for this style rather than offering a near-identical
     * twin the renderer would then have to keep in step.
     *
     * <p>The single top slot is filled by the bolt, the arcs or the type label, in
     * that order, so those three are mutually exclusive by construction.
     *
     * <p>Immutable.
     */
    public static final class Rect {
        /** The mobile level dots are drawn. */
        public final boolean dots;
        /** The bolt takes the top slot. */
        public final boolean bolt;
        /** The Wi-Fi arcs take the top slot. */
        public final boolean wifi;
        /** The in-glyph network type takes the top slot. */
        public final boolean type;
        /** The percentage is drawn below. */
        public final boolean value;
        /**
         * The two dot columns carry one SIM each - SIM 1 left, SIM 2 right -
         * instead of both repeating the current data SIM.
         */
        public final boolean dual;

        private Rect(TrioAppearance a, int level, boolean charging, int wifiLevel,
                     String mobileType, int sims) {
            dots = a.signalDots();
            bolt = charging && a.drawsBolt();
            wifi = !bolt && a.wifiInk(wifiLevel);
            type = !bolt && !wifi && a.typeInRing
                    && mobileType != null && !mobileType.isEmpty();
            value = a.value && level >= 0;
            // Same plug-not-bolt rule as the ring: a charger on screen means one
            // reading, and the bar's colour already carries it.
            dual = a.dualSimRows(wifi, charging, sims);
        }
    }

    /**
     * Whether the bar's own clock has to step aside for this appearance.
     *
     * <p>True for the capsule or an assigned free-ring clock while drawing:
     * those arrangements put the time inside the icon, so
     * leaving the bar's clock visible would show the time twice. Turning the
     * module off has to bring it back, which is why this follows the master
     * switch and not just the style.
     *
     * <p>Stated as a rule rather than checked at the two call sites, so the
     * renderer drawing the time and the hook hiding the bar's copy cannot
     * disagree - the failure being either a duplicated clock or none at all.
     */
    public boolean hidesStatusClock() {
        return glyph && (capsule || (ring && hasRingPart(Prefs.PART_CLOCK)));
    }

    /**
     * Where each piece goes in the capsule arrangement.
     *
     * <p>The capsule has two interior slots rather than one, and they do not
     * compete: the time always takes the left, and the right holds whichever of
     * the four readings wins the order below. That is why this arrangement needs
     * no settings of its own beyond being chosen - every question it asks is one
     * the other two already ask.
     *
     * <p>The order is <b>bolt, then arcs, then percentage, then type</b>. The only
     * place it differs from the ring is the middle pair: the ring lets the type
     * label take the centre from the percentage, because the ring's centre is the
     * one place either can go and the label is the more specific reading. Here the
     * percentage outranks it, because this arrangement exists to show the time
     * <em>and</em> a level side by side - that is the whole shape of the reference
     * - and a two-slot strip that showed "5G" where the level belongs would be
     * answering a different question. The type is still reachable: turning the
     * percentage switch off gives it the slot, so the setting does not become dead
     * in this style.
     *
     * <p>No {@code valueCentred}: there is no ring centre or notch here, so the
     * percentage has one place to be.
     *
     * <p>Immutable.
     */
    public static final class Capsule {
        /** The right-hand slot carries the charging bolt. */
        public final boolean bolt;
        /** The right-hand slot carries the Wi-Fi arcs. */
        public final boolean wifi;
        /** The right-hand slot carries the battery percentage. */
        public final boolean value;
        /** The right-hand slot carries the network type. */
        public final boolean type;
        /** The two dot rows are drawn (the mobile switch is on). */
        public final boolean dots;
        /** The rows carry one SIM each - SIM 1 on top, SIM 2 below. */
        public final boolean dual;

        private Capsule(TrioAppearance a, int level, boolean charging, int wifiLevel,
                        String mobileType, int sims) {
            dots = a.signalDots();
            bolt = charging && a.drawsBolt();
            // The bolt and the arcs share the right slot exactly as they share the
            // ring's notch: a charger on screen means one reading.
            wifi = !bolt && a.wifiInk(wifiLevel);
            // The percentage before the type, which inverts the ring's centre
            // order - see the class note above for why.
            final boolean valueWanted = a.value && level >= 0;
            value = !bolt && !wifi && valueWanted;
            type = !bolt && !wifi && !value
                    && a.typeInRing && mobileType != null && !mobileType.isEmpty();
            dual = a.dualSimRows(wifi, charging, sims);
        }
    }
}
