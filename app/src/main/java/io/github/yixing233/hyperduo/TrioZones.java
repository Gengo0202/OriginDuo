package io.github.yixing233.hyperduo;

/**
 * The three tappable thirds of the glyph, and which drawn piece sits in each.
 *
 * <p>This is the rule behind the settings screen's visual editor: the glyph is
 * shown large, the user taps its top, middle or bottom, and the switch for
 * whatever is drawn there flips. For that to be honest two things must hold, and
 * this class is where both are stated once:
 *
 * <ul>
 *   <li><b>Where the thirds are.</b> {@link #boxes} gives each in the same
 *       120x120 design units the rest of the renderer uses, and {@link #zoneAt}
 *       inverts that through the very transform {@code TrioRenderer
 *       .designTransform} draws with - so a hot spot lands on the pixels it
 *       names rather than near them.
 *   <li><b>What is in them.</b> {@link #occupant} reads the slot arbitration
 *       {@link TrioAppearance} has already performed and only <em>places</em>
 *       each decided piece in a third. It deliberately decides nothing itself:
 *       with the drawing rule and this table free to disagree, the editor would
 *       offer to switch off something the glyph was not drawing.
 * </ul>
 *
 * <h2>The three zones are each arrangement's own three slots</h2>
 *
 * <p>They are not "the top, middle and bottom of the square" - they are where
 * each arrangement actually puts its pieces, and the two arrangements differ:
 *
 * <table>
 *   <caption>Zone contents by arrangement</caption>
 *   <tr><th>Zone</th><th>Ring</th><th>Rectangular</th></tr>
 *   <tr><td>top</td><td>the 12 o'clock notch: percentage, bolt, type, dual row,
 *       or the arcs once centred</td>
 *       <td>the top slot: bolt, arcs or type</td></tr>
 *   <tr><td>centre</td><td>the ring interior: the arcs, the centred percentage,
 *       or the type</td><td>the enlarged percentage</td></tr>
 *   <tr><td>bottom</td><td>the level dots</td>
 *       <td>the two dot columns, and the battery bar between them</td></tr>
 * </table>
 *
 * <p>The ring's default look is the case worth reading twice: the percentage
 * normally sits in the <em>notch</em>, and the arcs normally sit in the
 * <em>middle</em>. That is why {@link #resident} is arrangement-aware instead of
 * a single table - an empty notch in the ring belongs to the percentage, an
 * empty top slot in the rectangle belongs to the arcs.
 *
 * <p>The out-of-ring pieces are not zones at all: the status bar lays them out
 * beside the icon, and the preview gives them their own cells.
 *
 * <p>Deliberately free of Android types and of static state, like
 * {@link TrioAppearance}: the hooked process and the settings app both build it,
 * and the offline harness compiles it against desktop shims.
 */
public final class TrioZones {

    private TrioZones() {
    }

    /** One of the glyph's three tappable thirds. */
    public enum Zone {
        /** The 12 o'clock notch of the ring, or the rectangle's top slot. */
        TOP,
        /** The ring interior, or the rectangle's enlarged percentage. */
        CENTRE,
        /** The level dots along the bottom, or the rectangle's dot columns and bar. */
        BOTTOM
    }

    /**
     * A piece the user can switch on and off, named the way the settings screen
     * names it.
     *
     * <p>Not every drawn piece is here: the battery ring and its bar are not
     * optional, and the two performance readouts are drawn outside the glyph.
     */
    public enum Part {
        /** The charging bolt. Only ever drawn while the percentage is, which it replaces. */
        BOLT,
        /** The Wi-Fi arcs. */
        WIFI,
        /** The mobile level dots. */
        MOBILE,
        /** The battery percentage. */
        VALUE,
        /** The in-glyph mobile network type, "5G" / "5GA". */
        TYPE,
        /** SIM 1's own row of four dots. */
        SIM_1,
        /** SIM 2's own row of four dots. */
        SIM_2,
        /** The capsule's fixed left clock, editable but not removable. */
        CLOCK
    }

    // ------------------------------------------------------------------ bounds
    //
    // The cut lines below are measured against the ink the pieces actually
    // produce, not chosen as round numbers, and each is named with the piece it
    // was measured against. Moving a piece in TrioGeometry is therefore a reason
    // to revisit a bound rather than a silent overlap - and tests/drawing-zones.ps1
    // renders each piece alone and fails if its ink leaves the zone it claims,
    // so the bounds cannot rot unnoticed.
    //
    // Ring, in design units:
    //   12 o'clock notch  y 2.5 .. 33.7   (gapBaseline(44) at the largest type
    //                                      size; the bolt's scaled tip at 32.1)
    //   arcs, not centred y 39.3 .. 89.0  (outer apex 47.3 minus half the
    //                                      thickest arc stroke, to the Wi-Fi
    //                                      dot's bottom at 81 plus that half)
    //   centred percentage y 46.1 .. 81.9 (centreSize/centerBaseline at size 44)
    //   level dots        y 97.2 .. 118.7 (DOTS, with the dual radius)
    // so 38 separates the notch from the interior and 92 the interior from the dots.
    //
    // Rectangular, in design units, measured the same way (see Ext2/Ext3 in the
    // work tree, and tests/drawing-zones.ps1 which re-checks them):
    //   top slot   ink bottom 46.28 at the default arc stroke, 48.06 at the
    //              thickest (the arcs' own stroke is what reaches lowest)
    //   percentage ink top    56.97 at the default size, 49.04 at the largest
    //   bar        ink top    105.3 at the thinnest stroke, 99.3 at the thickest
    //
    // So the two cuts are placed in the gaps those extremes leave, and both hold
    // at *every* slider setting rather than only the default:
    //   48.5  sits between 48.06 and 49.04 - a 0.44/0.54 clear margin, the
    //         narrowest window in the whole feature, which is why it is stated
    //         with its two bounds instead of as a round number
    //   96    sits between 95.4 and 99.3.
    //
    // The rectangular arrangement's own percentage has no descenders - it is
    // digits - so its ink bottom is its baseline, which is what "95.4" measures
    // after antialiasing.
    private static final float RECT_TOP_BOTTOM = 48.5f;
    private static final float RECT_CENTRE_BOTTOM = 96f;
    //
    // The horizontal bounds come from the geometry too. In the ring the bands run
    // the full width of the design box: the ring's own ink reaches the edges at
    // mid-height, and a tap there has to land somewhere rather than fall through
    // a gap between two inset columns. That also makes the ring's three bands a
    // complete partition of the glyph, which tests/drawing-zones.ps1 checks by
    // mapping every painted pixel back to a zone.
    //
    // In the rectangle the two dot columns are one component - the mobile meter -
    // so they are the bottom zone at every height, and only the strip between them
    // is cut into the three bands.
    //
    // That seam is not chosen here: it is exactly the box the renderer bounds the
    // centre text by, read off TrioGeometry. The two used to be separate constants
    // and that was a real defect: the renderer fits the percentage into
    // RECT_TEXT_LEFT..RIGHT, so a three-digit 100% at a large size reaches those
    // edges - and a seam even one design unit inside them put the right-hand stroke
    // of "100" in the mobile column, where a tap turns the signal meter off instead
    // of flipping the number. Measured at 960px, a seam 1 unit in misattributes 334
    // ink pixels and 3 units in misattributes 3324, every one of them a three-digit
    // reading. Nudging the seam "into the gap between the dots and the text" was the
    // wrong instinct for the same reason: there is no such gap, because the text's
    // allowance IS the gap between the dots.
    //
    // So there is no margin to tune. The seam is the boundary, and the property
    // that keeps it honest is that it is derived rather than restated - pinned by
    // tests/drawing-zones.ps1, which renders a 100% reading at the largest value
    // size and fails if any of its ink lands outside the centre column.

    private static final float RECT_SEAM_LEFT = TrioGeometry.RECT_TEXT_LEFT;
    private static final float RECT_SEAM_RIGHT = TrioGeometry.RECT_TEXT_RIGHT;

    // The ring's two cuts now come from TrioGeometry, because the renderer needs
    // them as well: a piece placed in a third is sized by the room that third
    // gives, so the band the editor hit-tests and the band the text is fitted into
    // have to be the same band. They used to be 38/92 here and a separate set of
    // placement centres there - the numbers agree only by luck, and they stopped
    // agreeing the moment a slot had to hold a full-size reading.
    private static final float RING_TOP_BOTTOM = TrioGeometry.ZONE_CUT_TOP;
    private static final float RING_CENTRE_BOTTOM = TrioGeometry.ZONE_CUT_BOTTOM;

    /**
     * The rectangles {@code zone} covers in design units, each
     * {@code {left, top, right, bottom}}.
     *
     * <p>A zone is usually one rectangle. The rectangle arrangement's bottom zone
     * is the exception and has three: the two dot columns, which run the full
     * height of the glyph on either side, plus the bar along the bottom. Those
     * columns are one component - the mobile meter - however tall they are, so
     * pressing anywhere on either must mean the same thing as pressing the bar.
     *
     * @param rect whether the rectangular arrangement is drawn, which moves the
     *             cut lines and adds the two dot columns
     */
    public static float[][] boxes(Zone zone, boolean rect) {
        if (rect) {
            switch (zone) {
                case TOP:
                    return new float[][] { {
                            RECT_SEAM_LEFT, 0f, RECT_SEAM_RIGHT, RECT_TOP_BOTTOM } };
                case CENTRE:
                    return new float[][] { {
                            RECT_SEAM_LEFT, RECT_TOP_BOTTOM,
                            RECT_SEAM_RIGHT, RECT_CENTRE_BOTTOM } };
                case BOTTOM:
                default:
                    // The two columns, then the strip the bar sits in.
                    return new float[][] {
                            { 0f, 0f, RECT_SEAM_LEFT, TrioGeometry.BOX },
                            { RECT_SEAM_RIGHT, 0f, TrioGeometry.BOX, TrioGeometry.BOX },
                            { RECT_SEAM_LEFT, RECT_CENTRE_BOTTOM,
                                    RECT_SEAM_RIGHT, TrioGeometry.BOX },
                    };
            }
        }
        switch (zone) {
            case TOP:
                return new float[][] { {
                        0f, 0f, TrioGeometry.BOX, RING_TOP_BOTTOM } };
            case CENTRE:
                return new float[][] { {
                        0f, RING_TOP_BOTTOM, TrioGeometry.BOX, RING_CENTRE_BOTTOM } };
            case BOTTOM:
            default:
                // Full width: the level dots sit along the arc's own opening,
                // which is the full width of the glyph at that height.
                return new float[][] { {
                        0f, RING_CENTRE_BOTTOM, TrioGeometry.BOX, TrioGeometry.BOX } };
        }
    }

    /** Layout-aware regions, including the capsule's two readings and dot bands. */
    public static float[][] boxes(Zone zone, TrioAppearance a) {
        if (!a.capsule) {
            return boxes(zone, a.rect);
        }
        final float stroke = a.ringStroke * TrioGeometry.CAP_STROKE_RATIO;
        final float top = TrioGeometry.capInteriorTop(stroke);
        final float bottom = TrioGeometry.capInteriorBottom(stroke);
        final float split = TrioGeometry.capLeftCx(stroke)
                + TrioGeometry.capTimeSlotWidth(stroke) / 2f + TrioGeometry.CAP_TEXT_GAP / 2f;
        // Include the antialiased fringe outside the mathematical stroke bounds;
        // this does not move the seams between the readings and signal rows.
        final float fringe = 1f;
        final float left = TrioGeometry.CAP_INK_X - fringe;
        final float right = TrioGeometry.CAP_INK_X + TrioGeometry.CAP_INK_W + fringe;
        if (zone == Zone.TOP) {
            return new float[][] { { left, top, split, bottom } };
        }
        if (zone == Zone.CENTRE) {
            return new float[][] { { split, top, right, bottom } };
        }
        return new float[][] {
                { left, TrioGeometry.CAP_INK_Y - fringe, right, top },
                { left, bottom, right, TrioGeometry.CAP_INK_Y + TrioGeometry.CAP_INK_H + fringe }
        };
    }

    /** The same shared inverse transform, with style-aware region boundaries. */
    public static Zone zoneAt(float[] transform, TrioAppearance a, float viewX, float viewY) {
        if (a == null || transform == null || transform.length < 3 || transform[0] <= 0f) {
            return null;
        }
        final float x = (viewX - transform[1]) / transform[0];
        final float y = (viewY - transform[2]) / transform[0];
        for (Zone zone : Zone.values()) {
            for (float[] box : boxes(zone, a)) {
                if (inside(box, x, y)) {
                    return zone;
                }
            }
        }
        return null;
    }

    /** Whether a point in design units falls inside {@code box}. */
    private static boolean inside(float[] box, float x, float y) {
        return x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3];
    }

    /** Whether a point in design units falls anywhere in {@code zone}. */
    public static boolean inZone(Zone zone, boolean rect, float designX, float designY) {
        for (float[] box : boxes(zone, rect)) {
            if (inside(box, designX, designY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The zone a tap at this canvas pixel falls in, or {@code null} when it
     * missed the glyph entirely.
     *
     * <p>{@code transform} is the {@code {scale, translateX, translateY}} triple
     * {@code TrioRenderer.designTransform} produced for this very canvas, so the
     * inversion below is the drawing's own arithmetic read backwards:
     * {@code design = (device - translate) / scale}. Passing a transform built
     * anywhere else would be exactly the drift this method exists to prevent.
     *
     * <p>The zones are disjoint - the rectangle's central column and its two side
     * columns do not overlap, and the ring's bands are cut upwards of each other -
     * so at most one contains the point. The search order only decides a point
     * sitting exactly on a shared seam, and top-to-bottom keeps such a tap on the
     * upper band.
     */
    public static Zone zoneAt(float[] transform, boolean rect, float viewX, float viewY) {
        if (transform == null || transform.length < 3) {
            return null;
        }
        final float scale = transform[0];
        if (scale <= 0f) {
            return null;
        }
        final float designX = (viewX - transform[1]) / scale;
        final float designY = (viewY - transform[2]) / scale;
        for (Zone zone : Zone.values()) {
            if (inZone(zone, rect, designX, designY)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Maps a design-space point onto the canvas, the direction
     * {@link #zoneAt} reverses: {@code device = design * scale + translate}.
     *
     * <p>Offered so the settings screen can outline a zone over the drawing
     * without re-deriving the transform. Returns false when there is no usable
     * transform, leaving {@code out} untouched.
     */
    public static boolean toView(float[] transform, float designX, float designY, float[] out) {
        if (transform == null || transform.length < 3 || out == null || out.length < 2
                || transform[0] <= 0f) {
            return false;
        }
        out[0] = designX * transform[0] + transform[1];
        out[1] = designY * transform[0] + transform[2];
        return true;
    }

    // ------------------------------------------------------------- the mapping

    /**
     * The largest inset {@link #viewTransform} will honour, matching the clamp in
     * {@code TrioPreviewView.setInset}. Beyond it the glyph is a speck and its
     * thirds are unpressable, so the ceiling is stated here rather than trusted to
     * arrive already clamped.
     */
    public static final float MAX_INSET = 0.4f;

    /**
     * The design-to-view transform for a glyph drawn in a box of this size, or
     * {@code null} when there is nothing to map onto.
     *
     * <p>This is the single definition of where the glyph lands, and both ends of
     * the feature go through it: the settings screen's editor asks for it to place
     * its hot zones, and {@code TrioPreviewView} asks for it to draw and to
     * hit-test. It is stated here rather than in the view because a view needs an
     * Android {@code Context} to exist at all, which would put this arithmetic out
     * of reach of the offline harness - and untested arithmetic about where the
     * tappable areas are is precisely what this feature cannot afford.
     *
     * <p>The drawing itself comes from {@link TrioRenderer#designTransform}, the
     * method {@code drawInto} paints with. All this adds is the inset: drawing
     * happens in a canvas already translated by {@code pad}, so the returned
     * translation is relative to that inner box, while a caller's point is in the
     * full box. Folding {@code pad} in here is what lets every caller work in its
     * own coordinates instead of each remembering to.
     *
     * @param inset fraction of the shorter edge left empty around the glyph, as
     *              {@code TrioPreviewView.setInset} takes it
     */
    public static float[] viewTransform(int width, int height, float inset,
                                        TrioAppearance appearance) {
        if (width <= 0 || height <= 0 || appearance == null) {
            return null;
        }
        final float safeInset = Math.max(0f, Math.min(MAX_INSET, inset));
        final float pad = Math.min(width, height) * safeInset;
        final int innerW = Math.round(width - 2f * pad);
        final int innerH = Math.round(height - 2f * pad);
        if (innerW <= 0 || innerH <= 0) {
            return null;
        }
        final float[] t = new float[3];
        if (!TrioRenderer.designTransform(innerW, innerH, appearance, t)) {
            return null;
        }
        t[1] += pad;
        t[2] += pad;
        return t;
    }

    // -------------------------------------------------------------- occupancy

    /**
     * The piece drawn in {@code zone} this frame, or {@code null} when the zone
     * is empty, for the ring arrangement.
     *
     * <p>Every condition here is a field of {@code r} or a predicate on {@code a}
     * - that is, a question {@link TrioAppearance} has already answered. Nothing
     * is re-derived, so this table cannot disagree with the renderer about what is
     * on screen; it only says which third each decided piece ended up in.
     *
     * <p>Within a zone the candidates are mutually exclusive by construction -
     * {@code TrioAppearance.Ring} arbitrates one occupant per slot - so the order
     * of the tests below is documentation rather than precedence. The ranking it
     * expresses is the real one: the bolt outranks the dual rows, which outrank
     * the arcs, which outrank the type label.
     */
    public static Part occupant(Zone zone, TrioAppearance a, TrioAppearance.Ring r) {
        if (a == null || r == null) {
            return null;
        }
        // One answer, because there is one layout: the slot says what is drawn, and
        // TrioAppearance has already blanked the slots whose piece has nothing to show
        // this frame. The switch-derived arbitration that used to follow is gone with the
        // automatic layout.
        return decodePart(r.parts[zone.ordinal()]);
    }

    /** The same, for the rectangular arrangement. */
    public static Part occupant(Zone zone, TrioAppearance a, TrioAppearance.Rect rc) {
        if (a == null || rc == null) {
            return null;
        }
        switch (zone) {
            case TOP:
                // One slot at the top and only one thing in it: the arrangement
                // is mutually exclusive by construction, so the first non-null
                // wins and the rest are unreachable.
                if (rc.bolt) {
                    return Part.BOLT;
                }
                if (rc.wifi) {
                    return Part.WIFI;
                }
                if (rc.type) {
                    return Part.TYPE;
                }
                return null;
            case CENTRE:
                return rc.value ? Part.VALUE : null;
            case BOTTOM:
            default:
                // The bar is the battery and is not optional; what the user can
                // switch down here is the dot columns framing it, and their
                // switch is the same mobile meter the ring draws along its
                // bottom.
                return rc.dots ? Part.MOBILE : null;
        }
    }

    /**
     * The occupant of {@code zone} for whichever arrangement {@code a} selects.
     *
     * @param sims how many SIM signal levels are known this frame, as
     *             {@code TrioRenderer.drawInto} counts them
     */
    public static Part occupant(Zone zone, TrioAppearance a, int level, boolean charging,
                                int wifiLevel, String mobileType, int sims) {
        if (a == null) {
            return null;
        }
        if (a.capsule) {
            final TrioAppearance.Capsule c = a.capsule(level, charging, wifiLevel, mobileType, sims);
            if (zone == Zone.TOP) { return Part.CLOCK; }
            if (zone == Zone.BOTTOM) { return c.dots ? Part.MOBILE : null; }
            if (c.bolt) { return Part.BOLT; }
            if (c.wifi) { return Part.WIFI; }
            if (c.value) { return Part.VALUE; }
            return c.type ? Part.TYPE : null;
        }
        return a.rect
                ? occupant(zone, a, a.rect(level, charging, wifiLevel, mobileType, sims))
                : occupant(zone, a, a.ring(level, charging, wifiLevel, mobileType, sims));
    }

    /** Hit only the battery arc, including its round caps, leaving component gaps editable. */
    public static boolean batteryRingAt(float[] transform, TrioAppearance a,
            TrioAppearance.Ring ring, float viewX, float viewY) {
        if (transform == null || transform.length < 3 || transform[0] <= 0f
                || a == null || !a.ring || ring == null) return false;
        double x = (viewX - transform[1]) / transform[0] - TrioGeometry.B_CX;
        double y = (viewY - transform[2]) / transform[0] - TrioGeometry.B_CY;
        double tolerance = Math.max(5f, a.ringStroke * .5f + 2f);
        if (Math.abs(Math.hypot(x, y) - TrioGeometry.B_R) > tolerance) return false;
        double cap = Math.toDegrees(Math.atan2(tolerance, TrioGeometry.B_R));
        double angle = (Math.toDegrees(Math.atan2(y, x)) - TrioGeometry.B_START + 360) % 360;
        if (angle > 360 - cap) angle -= 360;
        if (angle < -cap || angle > TrioGeometry.B_SWEEP + cap) return false;
        double progress = angle / TrioGeometry.B_SWEEP;
        double pad = cap / TrioGeometry.B_SWEEP;
        return ring.gapStart() == ring.gapEnd()
                || progress <= ring.gapStart() + pad || progress >= ring.gapEnd() - pad;
    }

    // ------------------------------------------------------------- assignment

    /** Stable storage IDs, deliberately independent of enum declaration order. */
    public static int encodePart(Part part) {
        if (part == null) return Prefs.PART_NONE;
        switch (part) {
            case WIFI: return Prefs.PART_WIFI;
            case MOBILE: return Prefs.PART_MOBILE;
            case SIM_1: return Prefs.PART_SIM_1;
            case SIM_2: return Prefs.PART_SIM_2;
            case VALUE: return Prefs.PART_VALUE;
            case BOLT: return Prefs.PART_BOLT;
            case TYPE: return Prefs.PART_TYPE;
            case CLOCK: return Prefs.PART_CLOCK;
            default: return Prefs.PART_NONE;
        }
    }

    public static Part decodePart(int id) {
        switch (id) {
            case Prefs.PART_WIFI: return Part.WIFI;
            case Prefs.PART_MOBILE: return Part.MOBILE;
            case Prefs.PART_VALUE: return Part.VALUE;
            case Prefs.PART_BOLT: return Part.BOLT;
            case Prefs.PART_TYPE: return Part.TYPE;
            case Prefs.PART_CLOCK: return Part.CLOCK;
            case Prefs.PART_SIM_1: return Part.SIM_1;
            case Prefs.PART_SIM_2: return Part.SIM_2;
            default: return null;
        }
    }

    /**
     * The piece a zone's list puts first, whether or not it can draw right now.
     *
     * <p>This is the saved priority's top entry, not the frame's render decision: the editor
     * uses it to show "what this zone is for" while the preview shows what actually appears.
     */
    public static Part configuredPart(TrioAppearance a, Zone zone) {
        final int[] order = a.ringZones[zone.ordinal()];
        return order.length == 0 ? null : decodePart(order[0]);
    }

    /**
     * The three slots as they currently stand.
     *
     * <p>No conversion is needed any more. This used to freeze the <em>automatic</em>
     * frame into explicit slots the first time the editor was opened, because the ring
     * had two ways of deciding positions and the editor only spoke slots. Slots are the
     * only answer now, so this is a read.
     */
    public static TrioSettings editableRingSettings(TrioSettings current, int level,
            boolean charging, int wifiLevel, String mobileType, int sims) {
        return current;
    }

    /**
     * Explicit placement: the settings that put {@code want} in {@code zone}.
     *
     * <p>Delegates to {@link #assign}, which is the one place that knows how a placement
     * is expressed. This used to be a second implementation of the same rules - a copy
     * that only handled the ring - and the two were free to disagree about which
     * switches a placement turns on.
     */
    public static Assignment place(Zone zone, Part want, TrioSettings current, int sims) {
        if (current == null || zone == null) return null;
        return assign(zone, current.trioStyle == Prefs.STYLE_RECT, want, current, sims);
    }

    /**
     * {@code current} with {@code want} at the front of {@code zone}'s priority list.
     *
     * <p>Three behaviours in one, because they are the same write seen from different
     * starting points:
     *
     * <ul>
     *   <li>a piece that was not placed anywhere is <b>added</b>;
     *   <li>a piece already in this zone moves to the front, so the user can promote an
     *       existing candidate without re-adding it;
     *   <li>a piece that was in another zone is <b>moved</b>, removed from there - the same
     *       piece in two zones would draw twice and make the priority lists disagree about
     *       which copy is the real one.
     * </ul>
     *
     * <p>Passing {@code null} clears the zone instead. To drop a single candidate see
     * {@link #withoutAt(TrioSettings, int, int)}: "empty this third" and "remove this one
     * entry" are different intentions, and one call cannot honestly mean both.
     */
    public static TrioSettings withPiece(TrioSettings current, int zone, Part want) {
        if (current == null || zone < 0 || zone >= Prefs.ZONE_COUNT) {
            return current;
        }
        final TrioSettings next = current.copy();
        final int id = encodePart(want);
        if (id == Prefs.PART_NONE) {
            final int[][] cleared = TrioSettings.copyZones(current.ringZones);
            cleared[zone] = new int[0];
            next.ringZones = cleared;
            return next;
        }
        final java.util.List<java.util.List<Integer>> lists = new java.util.ArrayList<>();
        for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
            final java.util.List<Integer> list = new java.util.ArrayList<>();
            for (int existing : current.ringZones[z]) {
                // Drop the id everywhere, then re-add it at the target's front: that single
                // rule gives add, promote and move without three separate paths.
                if (existing == id) {
                    continue;
                }
                list.add(existing);
            }
            lists.add(list);
        }
        lists.get(zone).add(0, id);
        final int[][] zones = new int[Prefs.ZONE_COUNT][];
        for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
            final java.util.List<Integer> list = lists.get(z);
            zones[z] = new int[list.size()];
            for (int i = 0; i < zones[z].length; i++) {
                zones[z][i] = list.get(i);
            }
        }
        next.ringZones = zones;
        // A placed piece needs its own switch on, or the zone would reserve space it then
        // draws nothing in.
        if (want == Part.WIFI) next.showWifi = true;
        if (want == Part.VALUE) next.showValue = true;
        if (want == Part.BOLT) next.showBolt = true;
        if (want == Part.MOBILE) next.showMobile = true;
        if (want == Part.SIM_1) { next.showMobile = true; next.showSim1 = true; }
        if (want == Part.SIM_2) { next.showMobile = true; next.showSim2 = true; }
        if (want == Part.TYPE) next.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
        return next;
    }

    /**
     * {@code current} with the entry at {@code index} dropped from {@code zone}'s list.
     *
     * <p>Separate from {@link #withPiece(TrioSettings, int, Part)} because "clear this zone"
     * and "drop this one candidate" are different intentions: when a zone's head is removed
     * the user expects the piece behind it to take over, not the whole chain to vanish.
     */
    public static TrioSettings withoutAt(TrioSettings current, int zone, int index) {
        if (current == null || zone < 0 || zone >= Prefs.ZONE_COUNT) {
            return current;
        }
        final int[] list = current.ringZones[zone];
        if (index < 0 || index >= list.length) {
            return current;
        }
        final TrioSettings next = current.copy();
        final int[][] zones = TrioSettings.copyZones(current.ringZones);
        final java.util.List<Integer> kept = new java.util.ArrayList<>();
        for (int i = 0; i < list.length; i++) {
            if (i != index) {
                kept.add(list[i]);
            }
        }
        zones[zone] = new int[kept.size()];
        for (int i = 0; i < zones[zone].length; i++) {
            zones[zone][i] = kept.get(i);
        }
        next.ringZones = zones;
        return next;
    }

    /**
     * {@code current} with the entries of {@code zone} swapped around {@code index}.
     *
     * @param delta -1 to promote one place, +1 to demote
     */
    public static TrioSettings moveWithin(TrioSettings current, int zone, int index, int delta) {
        if (current == null || zone < 0 || zone >= Prefs.ZONE_COUNT) {
            return current;
        }
        final TrioSettings next = current.copy();
        final int[][] zones = TrioSettings.copyZones(current.ringZones);
        final int[] list = zones[zone];
        final int to = index + delta;
        if (index < 0 || index >= list.length || to < 0 || to >= list.length) {
            // Out of range is a no-op rather than an error: the buttons at the ends of the
            // list stay present and simply do nothing, which is honest feedback.
            return current;
        }
        final int swapped = list[index];
        list[index] = list[to];
        list[to] = swapped;
        next.ringZones = zones;
        return next;
    }

    public static java.util.List<Part> placementChoices(Zone zone, TrioSettings current, int sims) {
        return current.trioStyle == Prefs.STYLE_RING
                ? java.util.Arrays.asList(Part.values())
                : choices(zone, current.trioStyle == Prefs.STYLE_RECT, current, sims);
    }

    /**
     * The switch settings that put {@code want} into {@code zone}, or null when no
     * combination can.
     *
     * <p>This is the whole reason the drawing page can offer a list per zone. The
     * renderer does not have "put X in the notch" as a concept - it <em>derives</em>
     * where each piece goes from the switches, with a fixed precedence inside each
     * slot (the bolt outranks the dual rows, which outrank the arcs, which outrank
     * the type label). So an assignment has to be solved for: find the switch
     * combination whose derivation lands {@code want} where the user asked.
     *
     * <p>It is solved by <b>enumerating the relevant switch combinations and asking
     * {@link TrioAppearance} where each one puts things</b>, rather than by a table
     * of intended assignments written out here. That distinction is the point: a
     * hand-written table of "to get X in Z, set these switches" is a second copy of
     * the arbitration rule, and the last feature in this file to keep two copies of
     * one boundary shipped a defect where tapping a battery percentage turned the
     * signal meter off. Enumeration cannot drift, because it consults the same rule
     * the renderer does.
     *
     * <p>Among all the combinations that work, the one returned is the least
     * disruptive, measured in this order:
     *
     * <ol>
     *   <li>fewest switches changed, so moving the top slot from the arcs to the
     *       percentage does not also silently move the signal meter;
     *   <li>then fewest pieces <em>hidden</em>. Moving a displaced piece is gentler
     *       than switching it off, so between two equally small edits the one that
     *       keeps more pieces enabled wins;
     *   <li>then the enumerated order, so the answer is deterministic rather than
     *       dependent on iteration luck.
     * </ol>
     *
     * @param current the settings this is solved against - the user's live snapshot
     * @param want the piece to place, or null to empty the zone
     * @param sims how many SIMs the device actually has a reading for. Passed in
     *   rather than read from {@code current.dualSim}: the number of rows the dual
     *   reading draws is a fact about the device, while {@code dualSim} is the switch
     *   that decides whether to use them. Deriving one from the other is circular -
     *   it makes the dual rows unreachable, because a frame built from
     *   {@code dualSim = false} has no second SIM to draw and so can never show what
     *   turning it on would do.
     * @return the combination to write, or null when the zone cannot hold it
     */
    public static Assignment assign(Zone zone, boolean rect, Part want, TrioSettings current,
                                    int sims) {
        if (current == null) {
            return null;
        }
        if (current.trioStyle == Prefs.STYLE_CAPSULE && zone == Zone.TOP) {
            return want == Part.CLOCK ? new Assignment(current.copy(), 0) : null;
        }
        // The ring keeps ordered lists, so "put X in Z" appends or moves X to the FRONT of
        // Z's list: the newly chosen piece is the one that wins the zone, and the ones
        // behind it stay as fallbacks rather than being thrown away.
        //
        // Moving rather than copying matters: a piece that was in another zone is removed
        // from there, because the same piece twice would draw in two places and make the
        // priority lists fight over it.
        if (current.trioStyle == Prefs.STYLE_RING) {
            return new Assignment(withPiece(current, zone.ordinal(), want),
                    distance(current, withPiece(current, zone.ordinal(), want)));
        }
        final int level = ASSIGN_LEVEL;
        final int wifiLevel = ASSIGN_WIFI_LEVEL;
        final int slots = Math.max(0, sims);

        Assignment best = null;
        for (TrioSettings candidate : candidates(current, rect, zone)) {
            if (!satisfies(candidate, zone, want, level, wifiLevel, slots)) {
                continue;
            }
            best = better(best, candidate, current);
        }
        return best;
    }

    /**
     * Whether this snapshot answers the request on every frame where the request is
     * meaningful.
     *
     * <p>Which frames those are is the whole subtlety, and getting it wrong is how the
     * first version of this solver reported the bolt and the dual rows as unreachable:
     *
     * <ul>
     *   <li><b>Place the bolt</b> - judged while charging, because that is the only
     *       frame it exists on. Asking an idle frame to show a bolt is asking for
     *       something that cannot appear.
     *   <li><b>Empty a zone</b> - judged with and without Wi-Fi, on both charging
     *       and idle frames. Otherwise a cleared slot could immediately fill with
     *       a fallback network type when Wi-Fi disconnects, or a bolt when plugged in.
     *   <li><b>Place anything else</b> - judged while idle. The percentage really is
     *       displaced by the bolt while charging, and that is existing behaviour the
     *       page has no business overriding: the user asked where the number goes,
     *       not that it survive a charge.
     * </ul>
     */
    private static boolean satisfies(TrioSettings candidate, Zone zone, Part want, int level,
                                     int wifiLevel, int slots) {
        if (want == Part.BOLT) {
            return occupantOf(candidate, zone, level, true, wifiLevel, slots) == Part.BOLT;
        }
        if (want == null) {
            for (int wifi : new int[] { -1, wifiLevel }) {
                if (occupantOf(candidate, zone, level, true, wifi, slots) != null
                        || occupantOf(candidate, zone, level, false, wifi, slots) != null) {
                    return false;
                }
            }
            return true;
        }
        return occupantOf(candidate, zone, level, false, wifiLevel, slots) == want;
    }

    /** Where a snapshot puts a zone, on one frame. */
    private static Part occupantOf(TrioSettings s, Zone zone, int level, boolean charging,
                                   int wifiLevel, int slots) {
        final TrioAppearance a = TrioAppearance.of(s);
        return occupant(zone, a, level, charging, wifiLevel, ASSIGN_TYPE, slots);
    }

    /**
     * The level the assignment solver reasons at: a two-digit reading, which is what
     * the glyph spends most of its life showing. It only matters for whether a piece
     * is present at all (a percentage needs a level, the arcs need a signal), and
     * those answers do not depend on which two-digit number it is.
     */
    private static final int ASSIGN_LEVEL = 79;

    /**
     * The Wi-Fi level the solver assumes. Any level that draws ink answers the same
     * way, so this is simply "there is a network".
     */
    private static final int ASSIGN_WIFI_LEVEL = 3;

    /** The network type the solver assumes; any non-empty type answers the same. */
    private static final String ASSIGN_TYPE = "5G";

    /**
     * One solved assignment: the settings to write and how many switches they move.
     *
     * <p>{@code changes} counts the switches that differ from the frame it was solved
     * against, which is what makes "the smallest edit that does what was asked" a
     * measurable property rather than an intention.
     */
    public static final class Assignment {
        /** The settings that achieve the requested placement. */
        public final TrioSettings settings;
        /** How many switches differ from the settings this was solved against. */
        public final int changes;

        Assignment(TrioSettings settings, int changes) {
            this.settings = settings;
            this.changes = changes;
        }
    }

    /** Picks the candidate that is least disruptive; see {@link #assign}. */
    private static Assignment better(Assignment best, TrioSettings candidate,
                                     TrioSettings current) {
        final int changes = distance(current, candidate);
        if (best == null) {
            return new Assignment(candidate, changes);
        }
        if (changes != best.changes) {
            return changes < best.changes ? new Assignment(candidate, changes) : best;
        }
        final int hidden = hidden(candidate);
        final int bestHidden = hidden(best.settings);
        if (hidden != bestHidden) {
            return hidden < bestHidden ? new Assignment(candidate, changes) : best;
        }
        // Equal on both counts: keep the incumbent, so the winner is decided by the
        // enumeration order rather than by which candidate happened to be compared
        // last. Determinism matters here because the page shows this as a menu.
        return best;
    }

    /**
     * How many pieces this snapshot switches off.
     *
     * <p>Used as the second tie-break, and it encodes a preference worth stating:
     * moving a displaced piece is gentler than hiding it. Emptying the ring's centre
     * can be done either by moving the arcs into the notch or by switching them off;
     * both cost one switch, and this picks the move.
     */
    private static int hidden(TrioSettings s) {
        int n = 0;
        if (!s.showWifi) { n++; }
        if (!s.showMobile) { n++; }
        if (!s.showValue) { n++; }
        if (s.mobileTypeMode == Prefs.MOBILE_TYPE_OFF) { n++; }
        // The two per-card switches hide a piece exactly as the others do, so
        // leaving them out would make "switch a card off" look free and let the
        // tie-break prefer it over moving a piece that is on screen.
        if (!s.showSim1) { n++; }
        if (!s.showSim2) { n++; }
        return n;
    }

    /** How many switches differ between two snapshots. */
    private static int distance(TrioSettings a, TrioSettings b) {
        int n = 0;
        // The layout itself is an edit. It used to be only the switches, because the
        // layout lived in them - but a placement IS a slot write now, so leaving this
        // out would report "moved a component to another slot" as zero changes and make
        // the smallest-edit tie-break meaningless.
        if (!java.util.Arrays.deepEquals(a.ringZones, b.ringZones)) { n++; }
        if (a.showWifi != b.showWifi) { n++; }
        if (a.showMobile != b.showMobile) { n++; }
        if (a.showValue != b.showValue) { n++; }
        if (a.showBolt != b.showBolt) { n++; }
        if (a.showSim1 != b.showSim1) { n++; }
        if (a.showSim2 != b.showSim2) { n++; }
        if (a.valueCentred != b.valueCentred) { n++; }
        if (a.mobileTypeMode != b.mobileTypeMode) { n++; }
        return n;
    }

    /**
     * Every switch combination worth testing for a request about this zone.
     *
     * <p>Not the full 2^5 x 3 space: a switch that cannot reach the zone being asked
     * about is held at the user's value, which both shrinks the search and avoids
     * proposing edits the request did not need. The switches that DO matter are the
     * pieces that can occupy the zone plus the switches that gate them.
     *
     * <p>The bolt is the one piece without a switch of its own: it is drawn instead
     * of the percentage while charging, so "put the bolt in the notch" is expressed
     * as the percentage plus the bolt, and the solver finds that because it varies
     * both.
     */
    private static java.util.List<TrioSettings> candidates(TrioSettings current, boolean rect,
                                                           Zone zone) {
        final java.util.List<TrioSettings> out = new java.util.ArrayList<>();
        final boolean[] bools = { false, true };
        final boolean capsule = current.trioStyle == Prefs.STYLE_CAPSULE;
        final boolean[] centres = capsule ? new boolean[] { current.valueCentred } : bools;
        // Keep an existing outside label when a request does not need to change it.
        // The editor places pieces inside the glyph, so it may preserve OUT_RING
        // but must not turn that separate label on as a side effect of clearing ink.
        final int[] typeModes = current.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING
                ? new int[] { Prefs.MOBILE_TYPE_OFF, Prefs.MOBILE_TYPE_IN_RING,
                              Prefs.MOBILE_TYPE_OUT_RING }
                : new int[] { Prefs.MOBILE_TYPE_OFF, Prefs.MOBILE_TYPE_IN_RING };
        // The signal meter's switch decides whether the bottom band and the
        // rectangle's columns hold anything, so it is varied there and held
        // everywhere else: this page places pieces, it is not the place to decide
        // whether the signal is drawn at all.
        final boolean[] mobiles = (zone == Zone.BOTTOM) ? bools
                : new boolean[] { current.showMobile };
        // The two per-card rows are placed by a slot and by their own switch, and
        // they can sit in any of the three zones - so unlike the aggregate meter
        // they are varied everywhere. Their switches are also what makes them
        // reachable at all: held at "off", a request to place one could never be
        // satisfied.
        final boolean[] sim1s = bools;
        final boolean[] sim2s = bools;

        for (boolean wifi : bools) {
            for (boolean value : bools) {
                for (boolean centred : centres) {
                    for (boolean bolt : bools) {
                        for (int typeMode : typeModes) {
                            for (boolean mobile : mobiles) {
                                for (boolean sim1 : sim1s) {
                                    for (boolean sim2 : sim2s) {
                                        final TrioSettings s = current.copy();
                                        s.showWifi = wifi;
                                        s.showValue = value;
                                        s.valueCentred = centred;
                                        s.showBolt = bolt;
                                        s.mobileTypeMode = typeMode;
                                        s.showMobile = mobile;
                                        s.showSim1 = sim1;
                                        s.showSim2 = sim2;
                                        out.add(s);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * The pieces a zone could hold, whether or not they are there now.
     *
     * <p>Derived by asking {@link #assign} whether each piece is reachable, so the
     * list the page offers and the solver that satisfies it cannot disagree - a piece
     * offered but unsolvable would be a menu entry that does nothing, and a piece
     * solvable but unlisted would be a capability with no way to reach it.
     *
     * <p>Returned in the order the page should show them, and a piece the zone cannot
     * hold is omitted rather than offered and then rejected. The caller decides
     * whether to show an omitted piece greyed out with a reason.
     */
    public static java.util.List<Part> choices(Zone zone, boolean rect, TrioSettings current,
                                               int sims) {
        final java.util.List<Part> out = new java.util.ArrayList<>();
        if (current == null) {
            return out;
        }
        for (Part p : Part.values()) {
            if (assign(zone, rect, p, current, sims) != null) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Whether {@code part} can be shown at all on this frame, whatever the zone.
     *
     * <p>Exists so the page can tell "this piece has nowhere to go" from "this piece
     * cannot be drawn right now" - the two need different explanations, and only the
     * second is about the current battery or signal state.
     */
    public static boolean reachable(Part part, boolean rect, TrioSettings current, int sims) {
        for (Zone zone : Zone.values()) {
            if (assign(zone, rect, part, current, sims) != null) {
                return true;
            }
        }
        return false;
    }
}

