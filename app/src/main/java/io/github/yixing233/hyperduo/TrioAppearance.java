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

    /** {@link Prefs#STYLE_RING} or {@link Prefs#STYLE_RECT}. */
    public final int style;
    /** True when {@link #style} is the rectangular arrangement. */
    public final boolean rect;
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
    /** {@link Prefs#SIGNAL_IN_RING} or {@link Prefs#SIGNAL_OUT_RING}. */
    public final int signalMode;
    /** The signal is the glyph's own level dots. */
    public final boolean signalInRing;
    /**
     * The signal is drawn beside the battery instead of inside the glyph.
     *
     * <p>Alone this means "do not draw it, and do not claim it either" - the
     * native icon stays where MIUI put it. Whether the module draws a
     * replacement is {@link #stackedSignal}, and whether it takes the native
     * icon away is {@link #stackedOut}.
     */
    public final boolean signalOutOfRing;
    /** The user wants the bars-and-dots reading of the out-of-ring signal. */
    public final boolean stackedSignal;
    /** The user wants only the current data SIM in that reading. */
    public final boolean dataSimOnly;
    /** The battery percentage is drawn. */
    public final boolean value;
    /** The user wants the bolt while charging. Only ever drawn with {@link #value}. */
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
        glyph = c.enabled;
        wifi = c.showWifi;
        mobile = c.showMobile;
        dualSim = c.dualSim;
        signalMode = c.signalMode;
        signalInRing = c.signalMode == Prefs.SIGNAL_IN_RING;
        signalOutOfRing = c.signalMode == Prefs.SIGNAL_OUT_RING;
        stackedSignal = c.stackedSignal;
        dataSimOnly = c.dataSimOnly;
        value = c.showValue;
        boltWanted = c.showBolt;
        centreValue = c.valueCentred && !rect;
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
     * Whether the glyph draws the bolt itself: only while it also draws the
     * percentage the bolt replaces.
     *
     * <p>This one line is why the bolt has no effect when the percentage is off,
     * which is the behaviour the settings screen now explains instead of leaving
     * the user to discover.
     *
     * <p>Not gated on {@link #glyph}, because the preview keeps drawing the
     * chosen arrangement while the master switch is off.
     */
    public boolean drawsBolt() {
        return boltWanted && value;
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
        return wifi && wifiLevel >= 1;
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
     * Whether the two-SIM rows take the ring's 12 o'clock notch.
     *
     * <p>The notch is a single slot, so the things that can occupy it are ordered
     * rather than merely each testing for themselves. This is that order: the
     * rows outrank the Wi-Fi arcs and the type label, and only the bolt outranks
     * the rows.
     *
     * <p>What suppresses the rows here is the <em>bolt</em> - the glyph actually
     * drawing one - and deliberately not the charger. The two came apart when the
     * bolt became switchable: with "show the charging bolt" off the glyph hands
     * the bolt back to MIUI and its own notch is free again, so the rows can have
     * it. Keying this on charging, as {@link #dualSimRows} does, would keep the
     * notch empty on a charging frame that draws nothing in it.
     *
     * <p>{@link #dualSimRows} stays as it is for the rectangular arrangement,
     * which has no notch to arbitrate: there the second row is a column beside
     * the first, so the arcs and the charger really do rule it out.
     *
     * @param bolt whether this frame draws the charging bolt
     * @param sims how many SIM signal levels are known this frame
     */
    public boolean dualClaimsNotch(boolean bolt, int sims) {
        return dualSim && signalDots() && sims >= 2 && !bolt;
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
        return mobile && signalInRing;
    }

    /**
     * Whether the module takes the native signal icon away and draws its own
     * reading in its place.
     *
     * <p>This is the one condition that folds the mobile slots while the signal
     * is out of ring. With the switch off the slots are left alone, so MIUI
     * keeps showing its own icon exactly where it always was - which is the
     * whole difference between the two positions of the switch.
     *
     * <p>Also gated on {@link #mobile}: the meter switch means "no mobile
     * reading anywhere", and it already answers that way in ring through
     * {@link #signalDots()}. Leaving it out here would let the mobile meter be
     * off and the stacked reading drawn anyway - a signal the user turned off,
     * showing up because a different switch was the one they did not touch.
     */
    public boolean stackedOut() {
        return glyph && mobile && signalOutOfRing && stackedSignal;
    }

    /**
     * Whether MIUI's own mobile-signal slot has to be handed over, which is
     * exactly while this module draws that signal itself.
     *
     * <p>Stated here rather than inside {@code TrioHooks.foldedSlots()} so the
     * rule can be exercised without the Xposed API. The trap this encodes is the
     * out-of-ring position with the stacked switch off: nothing is drawn and the
     * native icon has to stay, so folding there would simply delete a signal
     * indicator from the status bar.
     */
    public boolean foldsMobile() {
        return signalDots() || stackedOut();
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

        private Ring(TrioAppearance a, int level, boolean charging, int wifiLevel,
                     String mobileType, int sims) {
            wifi = a.wifiInk(wifiLevel);
            bolt = charging && a.drawsBolt();
            final boolean type = a.typeInRing && mobileType != null && !mobileType.isEmpty();
            final boolean hasValue = a.value && level >= 0;

            // The plug, not the bolt: charging suppresses the second row whether
            // or not this frame happens to draw a bolt for it (the bolt is
            // conditional on the percentage being on, the plug never is).
            // The notch is arbitrated, not merely shared: the rows outrank the
            // arcs, and only the bolt outranks the rows. See dualClaimsNotch.
            dual = a.dualClaimsNotch(bolt, sims);
            // The dual reading's top row owns the 12 o'clock notch, so the
            // percentage cannot be drawn there: it moves to the ring centre,
            // which is where it already goes whenever the notch is spoken for.
            // The in-glyph type label then yields, exactly as it does to the
            // percentage everywhere else.
            valueInCentre = hasValue && (a.centreValue || dual || (!wifi && !type));
            valueInGap = hasValue && !valueInCentre && !bolt;
            // The rows outrank the arcs: both want the same slot, and the rows
            // carry a per-SIM reading the arcs cannot show anywhere else, while a
            // Wi-Fi level is also legible from the arcs' absence. The bolt above
            // already yielded to the rows via dualClaimsNotch.
            wifiInGap = wifi && a.centreValue && !bolt && !dual;
            typeInCentre = type && !wifi && !valueInCentre;
            // The second row sits in the notch the gap label would otherwise use,
            // so the label yields rather than overlapping SIM 1's dots.
            typeInGap = type && valueInCentre && !wifi && !bolt && !dual;
            // The rows live in the ring's two openings, so the ring has to open
            // even when nothing else claimed the notch: a closed ring would draw
            // its arc straight through the top row.
            gapUsed = bolt || wifiInGap || valueInGap || typeInGap || dual;
            // With the centre reserved and nothing to move into the notch, the
            // arcs are dropped outright rather than drawn behind the number.
            //
            // Keyed on where the percentage actually landed, not on the switch
            // that asked for it: the rows reserve the centre as well, so a frame
            // with the rows in the notch while the centring switch is off would
            // otherwise send the arcs to the centre and draw them across the
            // number. That combination is reachable since the rows outrank the
            // arcs; with the rows off, `valueInCentre` and `centreValue` agree
            // except when there is no ink to draw anyway.
            drawWifi = a.wifi && (!valueInCentre || wifiInGap);
            drawValue = valueInCentre || valueInGap;
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
}
