package io.github.yixing233.hyperduo;

/**
 * Geometry of the "trio" glyph in a 120x120 design space. All units are design
 * units.
 */
final class TrioGeometry {

    private TrioGeometry() {
    }

    /** The SVG viewBox. Everything is drawn inside 0..120 on both axes. */
    static final float BOX = 120f;

    // ---------------------------------------------------------------- battery
    /** Arc centre: the two endpoints (15.5,88.25) and (103.5,88.25) with r=51.5. */
    static final float B_CX = 59.5f;
    static final float B_CY = 61.48715261785473f;
    static final float B_R = 51.5f;
    /** Default ring stroke; the user's {@code ring_stroke} setting overrides it. */
    static final float B_STROKE = 8f;
    /** Start angle (deg) from centre towards (15.5, 88.25). */
    static final float B_START = 148.69008689281117f;
    /** Clockwise sweep (deg) to (103.5, 88.25). The missing part is at the BOTTOM. */
    static final float B_SWEEP = 242.6198262143777f;

    /**
     * The whole open arc is further broken at 12 o'clock by stroke-dasharray over
     * pathLength=100: "35.3 29.4 35.3 4" -> gap covers [0.353, 0.647] of the path.
     *
     * <p>The reference figures are widened here: 29.4% of the path leaves only
     * ~60 design units of chord, which forced the percentage to shrink and left
     * the charging bolt looking pinched. Opening the mouth gives the top
     * indicator the room the ring interior already has.
     */
    static final float GAP_START_IDLE = 0.325f;
    static final float GAP_END_IDLE = 0.675f;
    /** Charging variant: "38.5 22.9 38.6 4" -> gap covers [0.385, 0.614]. */
    static final float GAP_START_CHARGE = 0.345f;
    static final float GAP_END_CHARGE = 0.655f;

    /**
     * The 12 o'clock gap used by the dual-SIM reading: exactly as wide as the
     * opening the arc already leaves bare at the bottom, so the two rows of dots
     * sit in matching mouths.
     *
     * <p>{@link #GAP_START_IDLE} is sized for a bolt or two digits. Measured
     * against the dots it is too narrow: the top row is {@link #DOTS} mirrored
     * about {@link #B_CY}, so its outer dots reach the arc's own radius, and the
     * rounded stroke ends are then only ~10.4 units from the outer dot centres
     * while a cap (stroke/2) plus a dot ({@link #DUAL_DOT_R}) is 14. The cap and
     * the dot merge into one blob and the top row reads as if the ring had cut it
     * off. The bottom row never had this problem because it sits in the arc's
     * natural opening, which is 32.5 degrees wider.
     *
     * <p>Opening the notch to that natural width is what "the rows are symmetric"
     * means geometrically, and it hands the top row the same clearance the bottom
     * row has (~10 units instead of an overlap of ~3.6).
     *
     * <p>Both bounds are derived from {@link #B_SWEEP} rather than typed in: the
     * bare part of the circle is {@code 360 - B_SWEEP}, so its half is
     * {@code 180 / B_SWEEP - 0.5} of the path - measured either side of t=0.5,
     * which is 12 o'clock.
     */
    static final float GAP_START_DUAL = 1f - 180f / B_SWEEP;
    static final float GAP_END_DUAL = 180f / B_SWEEP;

    /**
     * Gap used when nothing is drawn in the 12 o'clock slot.
     *
     * <p>A zero-width gap at position 0 makes the ring draw as one unbroken arc:
     * with {@code gapStart == gapEnd == 0} the two halves of
     * {@code batteryRing}'s split collapse into a single 0..to sweep. An empty
     * mouth is not a feature, so the ring simply closes.
     */
    static final float GAP_NONE = 0f;

    /** Path progress t (0..1) -> angle in degrees. */
    static float bAngle(float t) {
        return B_START + t * B_SWEEP;
    }

    // ----------------------------------------------------------------- charging
    /**
     * The charging bolt, scaled up from the reference path.
     *
     * <p>The reference path only spans x 51.3..67.6 and y 2.2..21.6 — a pinched
     * sliver next to a 103-unit ring. {@link #BOLT_SCALE} grows it about
     * {@link #BOLT_CX} and pins its top edge to {@link #BOLT_TOP}, so it fills
     * the opened 12 o'clock gap without leaving the 120x120 box.
     *
     * <p>The renderer draws the path with
     * {@code translate(boltOffsetX(), boltOffsetY())} then
     * {@code scale(BOLT_SCALE)}: a reference point {@code (x, y)} lands on
     * {@code (x * BOLT_SCALE + boltOffsetX(), y * BOLT_SCALE + boltOffsetY())}.
     */
    static final float BOLT_CX = 59.45f;
    static final float BOLT_TOP = 1.5f;
    /** Top edge and horizontal centre of the reference path, before scaling. */
    static final float BOLT_TOP_REF = 2.2f;
    static final float BOLT_CX_REF = 59.45f;
    /**
     * Chosen against the default ring stroke: the reference bolt is sized for a
     * stroke of 8 design units, and the shipping default is 14, so a much larger
     * scale is needed for the two to read as one icon.
     */
    static final float BOLT_SCALE = 1.6f;

    static float boltOffsetX() {
        return BOLT_CX - BOLT_CX_REF * BOLT_SCALE;
    }

    static float boltOffsetY() {
        return BOLT_TOP - BOLT_TOP_REF * BOLT_SCALE;
    }

    // ------------------------------------------------------------------- value
    static final float VALUE_X = 59.5f;
    static final float VALUE_BASELINE = 24f;
    static final float VALUE_SIZE = 32f;
    /** SVG letter-spacing="-0.04em". */
    static final float VALUE_LETTER_SPACING = -0.04f;

    /**
     * The character the reference draws smaller than the rest of the network
     * type: the "A" of "5GA".
     *
     * <p>Kept here rather than in either drawing path so the ring canvas and the
     * out-of-ring label cannot end up disagreeing about which glyph is the
     * suffix. MIUI reports the type as one string ("5G", "5GA"), so the only
     * signal available is the trailing character.
     */
    static final char TYPE_SUFFIX = 'A';

    /**
     * Whether {@code type} ends in a suffix that should be drawn smaller.
     *
     * <p>Both conditions matter: {@code scalePercent} of 100 is "no shrink", and
     * a bare "A" is the whole label rather than a suffix of one - shrinking it
     * would just draw the label smaller, which is what the size slider is for.
     */
    static boolean hasShrunkSuffix(String type, int scalePercent) {
        return scalePercent < 100
                && type != null
                && type.length() > 1
                && type.charAt(type.length() - 1) == TYPE_SUFFIX;
    }

    /** {@code type} without its trailing {@link #TYPE_SUFFIX}. */
    static String typeBase(String type) {
        return type.substring(0, type.length() - 1);
    }

    /**
     * Vertical centre of the ring interior. The level is drawn here once the
     * Wi-Fi area is empty, so the hole left by a missing Wi-Fi glyph carries the
     * battery percentage instead of staying blank.
     */
    static final float CENTER_CY = 64f;
    /** Cap height of the digits, in em. Roboto's is ~0.71em. */
    static final float CAP_HEIGHT_RATIO = 0.71f;
    /**
     * Height of a digit's visual centre above its baseline, in em. Digits fill
     * the cap, so their centre sits half of the cap height above the baseline.
     */
    static final float CAP_CENTRE_RATIO = CAP_HEIGHT_RATIO / 2f;

    /**
     * Smallest y a value's cap may start at when it is drawn in the top gap.
     * Anything above this leaves the 120x120 box and gets clipped by the host.
     *
     * <p>Not 0: the cap sits on a curve, and font metrics differ by a hair
     * between Roboto and the measurement used here, so a cap exactly on the ink
     * edge loses its top row to antialiasing. Two and a half units of headroom
     * costs nothing visually and keeps every digit whole.
     */
    static final float GAP_TOP = 2.5f;

    /**
     * Baseline for a value drawn in the 12 o'clock gap. {@link #VALUE_BASELINE}
     * is the design baseline, but a larger font would push the cap above
     * {@link #GAP_TOP}, so the line is dropped just enough to keep it inside.
     */
    static float gapBaseline(float size) {
        return Math.max(VALUE_BASELINE, GAP_TOP + size * CAP_HEIGHT_RATIO);
    }

    /**
     * The centre of the ring gives the value far more room than the narrow top
     * gap it normally lives in, so a centred value is drawn this much larger.
     *
     * <p>1.5 crowded the ring: the digits ran into the stroke on both sides once
     * the ring was thickened. 1.3 still reads as "the big number" while keeping
     * a visible margin against the inner edge.
     */
    static final float CENTRE_SIZE_RATIO = 1.4f;
    /** Upper bound for {@link #CENTRE_SIZE_RATIO}: "100" must stay inside the ring. */
    static final float CENTRE_MAX_SIZE = 52f;

    /** Font size actually used for a centred value of configured {@code size}. */
    static float centreSize(float size) {
        return Math.min(size * CENTRE_SIZE_RATIO, CENTRE_MAX_SIZE);
    }

    /** Baseline that centres digits of {@code size} on {@link #CENTER_CY}. */
    static float centerBaseline(float size) {
        return CENTER_CY + size * CAP_CENTRE_RATIO;
    }

    /**
     * Clear horizontal room for text drawn inside the ring. The ring's inner
     * edge is one stroke width inside its centre line; the text's cap corners
     * must stay within that circle, so the room narrows as it gets taller.
     *
     * <p>Both centre slots go through here: the percentage when Wi-Fi is off,
     * and the mobile network type ({@code Prefs.KEY_TYPE_SIZE}) when it replaces
     * the percentage. Sizing them independently is safe because neither can
     * overrun the ring.
     */
    static float centreClearWidth(float size, float stroke) {
        final float inner = B_R - stroke * 0.5f - 1f;
        final float dy = size * CAP_HEIGHT_RATIO * 0.5f;
        final float dx = (float) Math.sqrt(Math.max(0f, inner * inner - dy * dy));
        return dx * 2f;
    }

    /**
     * Clear horizontal room for a value drawn in the 12 o'clock gap: the chord
     * between the two rounded arc ends, less one stroke width for those caps.
     */
    static float gapClearWidth(float stroke) {
        final double half = Math.toRadians((GAP_END_IDLE - GAP_START_IDLE) * B_SWEEP * 0.5f);
        return (float) (2.0 * B_R * Math.sin(half)) - stroke;
    }

    // ------------------------------------------------------------------- wi-fi

    static final float W_CX = 59.5f;
    static final float W_STROKE = 7f;
    /** Outer arc: chord (38.5,55.5)-(80.5,55.5), r=31 -> centre below the chord. */
    static final float W1_CY = 78.3035085f;
    static final float W1_R = 31f;
    static final float W1_START = 227.3423f;
    static final float W1_SWEEP = 85.3154f;
    /** Middle arc: chord (47,65.25)-(72,65.25), r=18.5. */
    static final float W2_CY = 78.8881817f;
    static final float W2_R = 18.5f;
    static final float W2_START = 227.5048f;
    static final float W2_SWEEP = 84.9904f;

    /** Topmost ink of the outer arc, at its apex. */
    static final float W_TOP = W1_CY - W1_R;
    /**
     * Lowest ink of the group: the underside of the dot beneath the middle arc.
     * Read off {@code TrioRenderer.buildWifiDot}, whose path bottoms out at
     * y=80.95.
     */
    static final float W_BOTTOM = 80.95f;
    /**
     * Scale of the Wi-Fi group when the percentage is centred
     * ({@code Prefs.KEY_VALUE_CENTRED}) and the group is moved into the 12
     * o'clock notch. The notch is 69.6 units of chord at the mouth and the group
     * is 49 wide at full size, so it has to shrink to read as a notch ornament
     * rather than spill over the arc ends.
     */
    static final float GAP_WIFI_SCALE = 0.8f;

    /** Keeps the group horizontally centred on the icon axis. */
    static float gapWifiOffsetX() {
        return W_CX * (1f - GAP_WIFI_SCALE);
    }

    /**
     * Puts the topmost ink - the outer arc's apex, half a stroke above its
     * centre line - at {@link #GAP_TOP}, the same clearance the digits get when
     * they sit in the notch.
     */
    static float gapWifiOffsetY(float stroke) {
        return GAP_TOP - (W_TOP - stroke * 0.5f) * GAP_WIFI_SCALE;
    }

    // ------------------------------------------------------- four level dots
    static final float DOT_R = 5.5f;
    static final float[][] DOTS = {
            {33f, 104.2f},
            {50.5f, 111.2f},
            {68.5f, 111.7f},
            {86f, 105.8f},
    };

    /**
     * Radius of a level dot in the dual-SIM reading.
     *
     * <p>Larger than {@link #DOT_R}: with two rows of four the dots are the
     * glyph's dominant feature and the reference draws them at 50px across a
     * 451px-wide image, i.e. 14.56 design units - a 7.28 radius. Rounded down to
     * 7.0 so the top row keeps a hair of clearance inside the ink box at the
     * largest reachable stroke.
     *
     * <p>Only used for the two rows of the dual reading; the single-row layout
     * keeps {@link #DOT_R} untouched, because that is the shipped look.
     */
    static final float DUAL_DOT_R = 7.0f;

    /**
     * The dual-SIM dot rows, eight entries: indices 0..3 are SIM 1 (slot 0) in
     * the top half of the ring, indices 4..7 are SIM 2 (slot 1) at
     * {@link #DOTS}.
     *
     * <p>The top row is {@link #DOTS} mirrored about {@link #B_CY}, which is what
     * makes the two rows sit symmetrically inside the ring - the bottom row
     * follows the ring's lower curve, and the mirror puts the top row on the
     * matching upper curve. The mirror line is the ring centre rather than the
     * middle of the design box: the ring itself is centred at {@code B_CY}, and
     * mirroring about anything else would leave the rows visibly off-centre
     * inside it.
     *
     * <p>Both rows also fall inside the ring's two mouths - the bottom row in the
     * arc's natural opening, the top row in the 12 o'clock notch - which is why
     * the dual reading forces a gap even though it draws no bolt.
     */
    static final float[][] DOTS_DUAL = new float[8][2];

    static {
        for (int i = 0; i < DOTS.length; i++) {
            DOTS_DUAL[i][0] = DOTS[i][0];
            DOTS_DUAL[i][1] = 2f * B_CY - DOTS[i][1];
            DOTS_DUAL[i + DOTS.length][0] = DOTS[i][0];
            DOTS_DUAL[i + DOTS.length][1] = DOTS[i][1];
        }
    }

    /** Opacity of the battery track / inactive level dots. */
    static final int TRACK_ALPHA = 56; // 0.22 * 255

    /** Below this battery level the ring turns the "critical" colour. */
    static final int CRITICAL_LEVEL = 20;

    // ------------------------------------------- stacked out-of-ring signal
    //
    // The out-of-ring reading stands in the status bar, not in the glyph, so
    // none of the ink box above applies to it. Its numbers are quoted in the
    // 350x350 reference image and kept in that image's own units; the view
    // measures itself against them and scales to whatever height the status bar
    // row gives it. Working in the reference's units rather than in dp is what
    // keeps the two rows, the bar steps and the round caps in the proportions
    // the reference draws, at every density.

    /** Columns in one reading: one capsule bar, and one dot beneath it. */
    static final int STACK_COLUMNS = 4;

    /** Bar width in reference pixels. Also the dot row's diameter. */
    static final float STACK_BAR_W = 50f;

    /** Column pitch: {@link #STACK_BAR_W} plus the 17px gap the reference leaves. */
    static final float STACK_PITCH = 67f;

    /**
     * Bar heights, ascending left to right, in reference pixels.
     *
     * <p>An even 25px step - 75, 100, 125, 150 - on a shared baseline, which is
     * what makes the row read as a level at a glance. The baseline is the
     * <em>bottom</em>: the bars grow upwards, so index {@code 3} is both the
     * tallest bar and the height of the whole row.
     */
    static final float[] STACK_BAR_H = {75f, 100f, 125f, 150f};

    /** Dot diameter in reference pixels. Equal to the bar width, as drawn. */
    static final float STACK_DOT_D = 50f;

    /**
     * Gap between the bars' baseline and the top of the dot row, both measured
     * as edges.
     *
     * <p>The reference has no antialiasing, so its edges land on whole pixels
     * and the gap is exactly determinate: the bars' last ink row is 237, which
     * puts their bottom edge at 238, and the dots' first ink row is 247, so the
     * blank band is {@code 238..246} - nine units. Measuring the gap by
     * subtracting those two ink-row indices instead gives ten and a reading one
     * unit too tall, which is exactly the error the reference comparison in
     * {@code work/outringcheck/compare.py} exists to catch.
     */
    static final float STACK_DOT_GAP = 9f;

    /** Width of the two-row reading: {@code 59..309} in the reference. */
    static final float STACK_INK_W =
            STACK_PITCH * (STACK_COLUMNS - 1) + STACK_BAR_W;

    /** Height of the two-row reading: {@code 88..297} in the reference. */
    static final float STACK_INK_H =
            STACK_BAR_H[STACK_COLUMNS - 1] + STACK_DOT_GAP + STACK_DOT_D;

    // ------------------------------------------------- rectangular arrangement
    //
    // Everything below lays the same pieces out the other way: two columns of
    // four dots carry the mobile signal, the top slot between them carries the
    // Wi-Fi arcs, the network type or the charging bolt, the percentage sits
    // enlarged below that, and a full-width bar along the bottom is the battery.
    // There is no ring at all.
    //
    // The coordinates still live in the same 120x120 design space, so the two
    // arrangements differ only in where the pieces go and in the ink box used to
    // fit them into the host view.

    /** Radius of a level dot, and the centre x of each of the two columns. */
    static final float RECT_DOT_R = 8.6f;
    static final float RECT_DOT_LX = 9.6f;
    static final float RECT_DOT_RX = 110.4f;
    /** Centre y of the first dot; each following dot drops by {@link #RECT_DOT_STEP}. */
    static final float RECT_DOT_TOP = 15.6f;
    static final float RECT_DOT_STEP = 22.5f;
    /** Four dots per column, the number of steps a phone's signal meter has. */
    static final int RECT_DOT_ROWS = 4;

    /**
     * The battery bar: a capsule spanning the full width of the columns' outer
     * edges, so its ends line up with the dots above it.
     *
     * <p>Its thickness is {@code TrioSettings#ringStroke} - the ring the slider
     * would otherwise draw is absent here, and the bar is the one heavy line the
     * arrangement keeps, so the slider stays meaningful.
     */
    static final float RECT_BAR_LEFT = 1.0f;
    static final float RECT_BAR_RIGHT = 119.0f;
    static final float RECT_BAR_CY = 107.3f;

    /**
     * Horizontal room for text between the two dot columns. Unlike the ring's
     * clear width this does not depend on the font size: the columns are
     * vertical, so a taller glyph eats into the same gap.
     */
    static final float RECT_TEXT_CLEAR =
            (RECT_DOT_RX - RECT_DOT_R) - (RECT_DOT_LX + RECT_DOT_R);

    /** Baseline and scale of the network type in the top slot. */
    static final float RECT_TYPE_BASELINE = 42.3f;
    static final float RECT_TYPE_SCALE = 1.26f;

    /**
     * Baseline, x and scale of the percentage. The reference draws the digits
     * noticeably larger than the ring arrangement does, which is the point of
     * the arrangement: the columns leave the middle free to be read at a glance.
     */
    static final float RECT_VALUE_X = 60f;
    static final float RECT_VALUE_BASELINE = 92.7f;
    static final float RECT_VALUE_SCALE = 1.38f;

    /**
     * Scale of the Wi-Fi group in the top slot and the y of its topmost ink.
     *
     * <p>Only a translation and a uniform scale: the arcs themselves are the
     * same paths the ring arrangement uses, so both arrangements stay visually
     * identical in shape.
     *
     * <p>{@code 0.95} is measured against the reference: its outer arc's ink is
     * 3.05 dot diameters wide, and the untouched group at scale 1 is 3.20, so
     * the arcs are very nearly full size in this arrangement. It is not exactly
     * 1: the reference's own arcs are a little flatter than this module's
     * (wider ink, thinner stroke). That difference is deliberately not copied -
     * the arcs here are the existing icon, only repositioned.
     */
    static final float RECT_WIFI_SCALE = 0.95f;
    /**
     * The outer arc's apex, half a stroke above its centre line, sits here in
     * design units. The reference puts that ink two of its own pixels below the
     * top of the dot columns, which is about 1.5 design units, so this is a
     * little lower than the dots' ink top at {@link #RECT_DOT_TOP} - {@link
     * #RECT_DOT_R}.
     */
    static final float RECT_WIFI_TOP = 8.5f;

    /** Keeps the Wi-Fi group horizontally centred on the icon axis. */
    static float rectWifiOffsetX() {
        return 60f - W_CX * RECT_WIFI_SCALE;
    }

    /** Puts the outer arc's apex, half a stroke above its centre line, at {@link #RECT_WIFI_TOP}. */
    static float rectWifiOffsetY(float stroke) {
        return RECT_WIFI_TOP - (W_TOP - stroke * 0.5f) * RECT_WIFI_SCALE;
    }

    /**
     * The charging bolt sits in the same top slot. The ring arrangement puts its
     * ink centre at {@link #BOLT_CX} / y=17.26; the arrangement's axis is 60 and
     * its top slot centre slightly lower, so both are nudged by a hair.
     */
    static final float RECT_BOLT_DX = 0.55f;
    static final float RECT_BOLT_DY = 0.24f;

    static float rectBoltOffsetX() {
        return boltOffsetX() + RECT_BOLT_DX;
    }

    static float rectBoltOffsetY() {
        return boltOffsetY() + RECT_BOLT_DY;
    }

    // ------------------------------------------------------------------- frame
    /**
     * Tight ink box of the whole glyph (battery arc + stroke, Wi-Fi arcs + dot,
     * level dots, top indicator) in design units. Used to fit 120x120 artwork
     * into the host view without non-uniform scaling.
     */
    static final float INK_X = 1.0f;
    static final float INK_Y = 0.5f;
    static final float INK_W = 117.0f;
    static final float INK_H = 117.5f;

    /**
     * The same box for the rectangular arrangement.
     *
     * <p>Wider and slightly shorter, and - unlike {@link #INK_W}/{@link #INK_H} -
     * sized against the <em>extremes</em> every slider can reach rather than
     * against the default look: the bolt's tip at {@code showValue} with the
     * thickest arc strokes, and the bar's underside at
     * {@code Prefs#MAX_RING_STROKE}. Fitting the extremes means no setting can
     * push ink outside the host view, where it would be clipped; the cost is
     * that the default glyph ends up a few percent smaller than it could be.
     */
    static final float RECT_INK_X = 1.0f;
    static final float RECT_INK_Y = 1.0f;
    static final float RECT_INK_W = 118.0f;
    static final float RECT_INK_H = 114.4f;

    // ------------------------------------------------------- capsule arrangement
    //
    // The outline itself is the battery gauge: its length is the full track and
    // the role-coloured part of it is the level, so the shape fills up instead of
    // containing a gauge. Two four-dot rows interrupt the stroke at 12 and 6
    // o'clock, and the time and the right-hand reading share the interior.
    //
    // The shape is quoted against the reference the design was drawn from: a
    // capsule 2.34 times as wide as it is tall, a stroke 12.6% of that height,
    // dots as thick as the stroke with a pitch of 18.5%, and text 26.5% of the
    // height. All four of those are kept, and the reason they can be is the host
    // width - see CAP_RIGHT.

    /** Outer bounds of the capsule outline, in design units. */
    static final float CAP_LEFT = 1.0f;
    /**
     * Right edge of the outline.
     *
     * <p>Set so the shape is the reference's own 2.34:1 - the width the design was
     * drawn at. It is a free parameter rather than a derived one because the host
     * is no longer a fixed box: {@code MiuiBatteryMeterIconView.onMeasure} used to
     * pin the glyph's width to MIUI's 28dp battery, which left the fit decided by
     * the width and forced the shape down to 2:1 (84x46px in a 60px-tall bar, with
     * a third of the bar's height unused). The module now answers that measurement
     * with the width this aspect ratio asks for, so the proportions are the
     * designer's again rather than the stock icon's.
     *
     * <p>The height is untouched by that: {@link #CAP_BOTTOM} is what the bar
     * offers, and only the width grows to meet it.
     */
    static final float CAP_RIGHT = 139.0f;
    static final float CAP_TOP = 1.0f;
    /**
     * Bottom edge of the outline.
     *
     * <p>Quoted against the height the status bar offers its icons -
     * {@code status_bar_icon_height}, 20dp, 60px at density 3 - less the two
     * units of the design space that keep the ink off the host's edge. The
     * reference's 26.5% text rule then lands at 15.6 units of cap height, which
     * is what the stock battery percentage is drawn at, so the readings are the
     * size they would be anywhere else on the bar.
     *
     * <p>Deliberately the number that does <em>not</em> move when the shape gets
     * wider: the bar's height is fixed, and the capsule grows sideways into it
     * rather than up. See {@link #CAP_RIGHT}.
     */
    static final float CAP_BOTTOM = 60.0f;

    /**
     * Corner radius: half the height, which is what makes the outline a stadium
     * rather than a rounded rectangle.
     */
    static final float CAP_RADIUS = (CAP_BOTTOM - CAP_TOP) * 0.5f;

    /**
     * The outline's thickness, as a fraction of {@code ringStroke}.
     *
     * <p>The reference draws it at 12.6% of the capsule's height, which is 7.4
     * units here; {@code ringStroke} defaults to 14, so this is the factor that
     * lands there. Kept as a ratio rather than a constant so the stroke slider
     * still means something in this arrangement - it is the one row that adjusts
     * the glyph's weight in every style.
     */
    static final float CAP_STROKE_RATIO = 0.53f;

    /**
     * Radius of one signal dot, and the pitch between their centres.
     *
     * <p>From the reference: a dot is as thick as the stroke (12.6% of the
     * height) and the pitch is 18.5% of it. Dots thinner than the line they
     * interrupt read as beads dropped on top of it, which is the one thing the
     * reference is careful not to look like.
     */
    static final float CAP_DOT_R = 3.7f;
    static final float CAP_DOT_PITCH = 10.9f;

    /** Centre x of the four-dot row: the capsule's own centre. */
    static final float CAP_DOT_CX = (CAP_LEFT + CAP_RIGHT) * 0.5f;

    /**
     * The clear space the stroke leaves on each side of a dot row, as a fraction
     * of the capsule's height.
     *
     * <p>Read off the reference: it leaves about 3.3% of the height between the
     * outer dot and the end of the stroke. Without it the row and the line run
     * together - which is exactly what the first cut did, because its
     * {@link #capDotReach} was written as "the dots' own span" and nothing else,
     * so the gap was exactly as wide as the dots and the outer two merged into the
     * stroke ends. The row then reads as a line with two lumps on it rather than as
     * four dots interrupting a line.
     *
     * <p>A ratio rather than a constant so it holds at every stroke width: the
     * stroke is a slider, and a clearance in design units would vanish as the user
     * thickened the line.
     */
    static final float CAP_DOT_CLEARANCE_RATIO = 0.033f;

    /** The clear space on one side of a dot row, in design units. */
    static float capDotClearance() {
        return CAP_DOT_CLEARANCE_RATIO * (CAP_BOTTOM - CAP_TOP);
    }

    /**
     * Half the horizontal span of one dot row: the dots themselves plus the
     * clearance the stroke keeps from them.
     *
     * <p>This is how far the outline is cut away at 12 and 6 o'clock, so it has to
     * cover both - covering only the dots leaves them touching the line, and
     * covering more than this opens a gap wide enough to read as a break in the
     * outline rather than as a row sitting in it.
     */
    static float capDotReach() {
        return CAP_DOT_PITCH * 1.5f + CAP_DOT_R + capDotClearance();
    }

    /**
     * Centre y of the top and bottom dot rows.
     *
     * <p>Exactly the outline's path coordinates, and deliberately not
     * {@code CAP_TOP + stroke / 2}: the stroke straddles the path, so the path's
     * own y is the line's centre. Putting the dots on the stroke's inner edge
     * instead - which the first cut of this did - hangs them below the line they
     * are supposed to be set into.
     */
    static float capDotTopY() {
        return CAP_TOP;
    }

    static float capDotBottomY() {
        return CAP_BOTTOM;
    }

    /** Where the two end caps stop and the flat runs begin. */
    static float capFlatLeft() {
        return CAP_LEFT + CAP_RADIUS;
    }

    static float capFlatRight() {
        return CAP_RIGHT - CAP_RADIUS;
    }

    /**
     * The interior, and the two reading slots inside it.
     *
     * <p>Not the flat run between the caps: the caps curve in only near the top
     * and bottom edges, so at the height the readings are drawn the interior runs
     * almost the full width. The reference relies on exactly that - its text
     * spans well past the point where the flat run ends - so insetting by the
     * radius would shrink the type for no reason.
     *
     * <p>Everything is derived from the stroke rather than fixed, because the
     * stroke is a slider: a heavier line eats into the room the readings have, and
     * they have to give way rather than overrun.
     */
    static float capInteriorLeft(float stroke) {
        return CAP_LEFT + stroke * 0.5f;
    }

    static float capInteriorRight(float stroke) {
        return CAP_RIGHT - stroke * 0.5f;
    }

    /**
     * The interior's vertical extent - and this is the bound that matters.
     *
     * <p>The two dot rows ride the outline's own centreline, so the top row's ink
     * reaches {@code CAP_TOP + stroke / 2 + CAP_DOT_R} and the bottom row's
     * mirrors it. Anything drawn inside the capsule has to live between them;
     * measuring against the outline's outer bounds instead - which is what the
     * first cut of this did - lets the Wi-Fi arcs and the digits run straight
     * through the dot rows, which is exactly what the offline render showed.
     */
    static float capInteriorTop(float stroke) {
        return CAP_TOP + stroke * 0.5f + CAP_DOT_R;
    }

    static float capInteriorBottom(float stroke) {
        return CAP_BOTTOM - stroke * 0.5f - CAP_DOT_R;
    }

    static float capInteriorHeight(float stroke) {
        return capInteriorBottom(stroke) - capInteriorTop(stroke);
    }

    static float capInteriorCy(float stroke) {
        return (capInteriorTop(stroke) + capInteriorBottom(stroke)) * 0.5f;
    }

    /** The gap the two readings keep between each other. */
    static final float CAP_TEXT_GAP = 6f;

    /**
     * The margin a reading keeps from the outline's inner edge, as a fraction of
     * the capsule's height.
     *
     * <p>Read off the reference at about 8.6% of the height. It matters most for
     * the time, which sits in the left slot right where the end cap curves back in
     * underneath it: with the slot starting at the interior's own edge the digits
     * begin immediately at the stroke's inner face, and at the reference device's
     * text size the "1" of "19:41" ends up touching the line.
     *
     * <p>A ratio rather than a constant for the same reason as the dot clearance -
     * the stroke is a slider, and the margin has to grow with it.
     */
    static final float CAP_TEXT_INSET_RATIO = 0.086f;

    /** The margin on one side of the readings, in design units. */
    static float capTextInset() {
        return CAP_TEXT_INSET_RATIO * (CAP_BOTTOM - CAP_TOP);
    }

    /** Where the readings' usable width starts, inside the outline's inner face. */
    static float capTextLeft(float stroke) {
        return capInteriorLeft(stroke) + capTextInset();
    }

    static float capTextRight(float stroke) {
        return capInteriorRight(stroke) - capTextInset();
    }

    /** Width of one of the two reading slots. */
    static float capSlotWidth(float stroke) {
        return (capTextRight(stroke) - capTextLeft(stroke) - CAP_TEXT_GAP) * 0.5f;
    }

    /** Centre of the left-hand slot: the time's own place. */
    static float capLeftCx(float stroke) {
        return capTextLeft(stroke) + capSlotWidth(stroke) * 0.5f;
    }

    /** Centre of the right-hand slot: the percentage, the arcs or the bolt. */
    static float capRightCx(float stroke) {
        return capTextRight(stroke) - capSlotWidth(stroke) * 0.5f;
    }

    /**
     * Where a line of digits sits, so its cap height is centred in the interior:
     * half the cap above and half below the interior's own middle.
     */
    static float capTextBaseline(float stroke, float size) {
        return capInteriorCy(stroke) + size * CAP_HEIGHT_RATIO * 0.5f;
    }

    /**
     * How tall a reading may be, as a fraction of the interior.
     *
     * <p>Not a fraction of the capsule's outer height: the dot rows are inside
     * that, and a reading has to clear them. The other two arrangements never
     * needed this - they are 117 and 114 units tall, so a size that fits their
     * width always fits their height too.
     *
     * <p><b>A backstop, not the binding bound.</b> Measured rather than assumed:
     * with the size slider at its own ceiling the width fit is what decides the
     * type, and the height only takes over for a single narrow glyph at a
     * {@code valueSize} the slider cannot reach. A negative control that made
     * this limit a hundred times larger still passed every pixel assertion in
     * {@code tests/capsule.ps1}. It is kept because the two bounds are computed
     * from different things and the height is the one that would fail silently if
     * a future size range grew past it - but it is not what keeps the digits
     * inside the outline today, and saying otherwise would be a comment that
     * lies about which line is doing the work.
     */
    static final float CAP_TEXT_HEIGHT_RATIO = 0.82f;

    /**
     * The requested reading size, as a multiple of {@code valueSize}.
     *
     * <p>Two conversions, and leaving either out is what makes the type come out
     * the wrong size: the reference draws text whose <em>cap height</em> is 26.5%
     * of the capsule's height, and a font's em size is that cap height divided by
     * {@link #CAP_HEIGHT_RATIO}. Quoted against {@code valueSize} so the existing
     * size slider keeps control of it exactly as it does in the other two
     * arrangements.
     */
    static final float CAP_TEXT_SCALE = 0.265f * CAP_BOTTOM / (CAP_HEIGHT_RATIO * 36f);

    /**
     * Largest size the capsule's interior allows.
     *
     * <p>A backstop for a size the slider cannot currently reach - see
     * {@link #CAP_TEXT_HEIGHT_RATIO} for why it is stated as one.
     */
    static float capTextMaxSize(float stroke) {
        return capInteriorHeight(stroke) * CAP_TEXT_HEIGHT_RATIO / CAP_HEIGHT_RATIO;
    }

    /**
     * The Wi-Fi group's ink extents, in design units.
     *
     * <p>Measured off the geometry rather than read off a constant, because the
     * obvious constants are the wrong ones: {@code W1_R} is the arc's centre-line
     * radius, and the ink reaches half a stroke beyond it, while the arc's
     * <em>endpoints</em> are what set the width - not its apex. Taking the chord
     * for the width and the centre-line span for the height - which the first cut
     * of this did - understates both, and the group then overruns the slot it was
     * fitted to.
     *
     * <p>Quoted at {@code DEF_ARC_STROKE}, the default the arcs are drawn at; the
     * fit below is against this height, and the offset compensates for the stroke
     * actually in force the way the ring's own Wi-Fi offsets do.
     */
    static final float W_INK_W = 54.0f;
    static final float W_INK_H = 39.65f;
    /**
     * The group's ink centre. Centring on this - rather than aligning its top
     * edge - is what keeps the arcs optically centred in a slot whose height
     * comes from the dot rows rather than from the group itself.
     */
    static final float W_INK_CY = 61.13f;

    /** The bolt's ink extremes, from {@code buildBolt}'s own coordinates. */
    static final float BOLT_INK_H = 19.9f;
    static final float BOLT_INK_CY = 12.05f;

    /**
     * The height every reading and icon in the capsule is drawn at, in design
     * units: the reference's 26.5% of the capsule's height, as a cap height.
     *
     * <p>The same number {@link #CAP_TEXT_SCALE} produces, restated as the height
     * the icons are matched against so the two cannot drift apart. Measured off
     * the reference: its arcs and its digits are both 41px in a 151px capsule -
     * the same height to the pixel - and its percentage matches them.
     */
    static final float CAP_ICON_H = 0.265f * CAP_BOTTOM;

    /**
     * The arcs' and the bolt's scales, each sized so its own ink is
     * {@link #CAP_ICON_H} tall.
     *
     * <p>Two scales, not one shared, because the two icons have different ink
     * heights - the group is 39.65 units tall and the bolt 19.9 - so a single
     * factor would draw the bolt at half the height of the arcs. The ring
     * arrangement keeps them separate for the same reason.
     *
     * <p>Each is also capped by the interior, so an extreme stroke setting makes
     * the icons give way rather than cross the dot rows.
     */
    static float capWifiScale(float stroke) {
        return Math.min(CAP_ICON_H / W_INK_H, capInteriorHeight(stroke) / W_INK_H);
    }

    static float capBoltScale(float stroke) {
        return Math.min(CAP_ICON_H / BOLT_INK_H, capInteriorHeight(stroke) / BOLT_INK_H);
    }

    /** Places the arcs' ink centre on the right-hand slot's own centre. */
    static float capWifiOffsetX(float stroke) {
        return capRightCx(stroke) - W_CX * capWifiScale(stroke);
    }

    static float capWifiOffsetY(float stroke) {
        return capInteriorCy(stroke) - W_INK_CY * capWifiScale(stroke);
    }

    /**
     * The bolt's offset. Written against {@code BOLT_CX_REF} and
     * {@link #BOLT_INK_CY} - which are the bolt path's own ink centre - and never
     * against {@code BOLT_CX}/{@code BOLT_TOP}: those two are the ring's
     * <em>targets</em>, so treating them as path coordinates a second time puts
     * the bolt down and to the right of where it belongs.
     */
    static float capBoltOffsetX(float stroke) {
        return capRightCx(stroke) - BOLT_CX_REF * capBoltScale(stroke);
    }

    static float capBoltOffsetY(float stroke) {
        return capInteriorCy(stroke) - BOLT_INK_CY * capBoltScale(stroke);
    }

    /**
     * The capsule's ink box: the outline plus its stroke, which is what has to
     * fit inside the host view.
     *
     * <p>Sized against the largest stroke the slider offers rather than the
     * default, the way {@link #RECT_INK_W} is: fitting the extremes means no
     * setting can push ink outside the host, where the platform clips it.
     */
    static final float CAP_INK_X = CAP_LEFT - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f;
    static final float CAP_INK_Y = CAP_TOP - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f;
    static final float CAP_INK_W =
            (CAP_RIGHT + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_X;
    static final float CAP_INK_H =
            (CAP_BOTTOM + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_Y;

    /**
     * The width the capsule's host view has to ask the status bar for, so that the
     * shape can be as tall as the bar allows.
     *
     * <p><b>Why this method exists at all.</b> The host is not a square like the
     * other two arrangements' - it is MIUI's battery icon, and
     * {@code MiuiBatteryMeterIconView.onMeasure} pins its width to
     * {@code R.dimen.battery_meter_width}, which is 28dp. That number is a
     * property of the <em>stock</em> battery glyph, which is a narrow vertical
     * battery; the capsule is a wide shape, so being handed a tall narrow box
     * means the fit is decided by the width and the shape ends up short. On the
     * reference device that is 84x46px in a 60px-tall bar - fourteen pixels of
     * the bar's height go unused, and the glyph looks like a squashed pill.
     *
     * <p>The fix is to answer {@code onMeasure} with the width this aspect ratio
     * actually needs for the height being offered, rather than the stock width.
     * Nothing has to be told about it: {@code MiuiBatteryMeterView.onMeasure} does
     * not measure its icon child at all, it just adds up
     * {@code mBatteryDigitalView.getMeasuredWidth()}, and
     * {@code MiuiStatusBatteryContainer.onMeasure} adds up the meter's - so
     * widening this one view widens the battery slot and the status bar makes room
     * for it on its own.
     *
     * <p>Deliberately <em>not</em> scaled by {@code glyphFit}: that multiplier is
     * already applied to the drawing scale, and folding it in here as well would
     * apply it twice - the ink would shrink by the square of the setting. The
     * consequence is that the host keeps its full width when the user shrinks the
     * glyph, which is what the ring arrangement already does with its square host.
     *
     * @param heightPx the height the bar is offering, in pixels
     * @return the width to measure at, or 0 when there is no height to work from
     */
    static int capHostWidthPx(int heightPx) {
        if (heightPx <= 0) {
            return 0;
        }
        return Math.round(heightPx * (CAP_INK_W / CAP_INK_H));
    }

    // ------------------------------------------------------------------ colours
    static final int CRITICAL = 0xFFFF3B30;
    static final int CHARGING_LIGHT = 0xFF34C759;
    static final int CHARGING_DARK = 0xFF1F8F3D;
    static final int LOW_POWER_LIGHT = 0xFFF2B900;
    static final int LOW_POWER_DARK = 0xFFC99700;

    /**
     * Amber used for the charging bolt while the battery reports quick charge.
     * At the real 60px status-bar size colour is the only channel that stays
     * instantly legible, so quick charge is told apart by hue rather than by
     * extra glyphs (two bolts merge into an unreadable blob at that size).
     */
    static final int QUICK_CHARGE = 0xFFFFBA28;
}
