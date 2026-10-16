package io.github.yixing233.hyperduo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Draws the "trio" glyph (battery ring + Wi-Fi arcs + signal dots + level indicator)
 * into a host view's canvas, in the 120x120 design space of {@link TrioGeometry}.
 */
final class TrioRenderer {

    private TrioRenderer() {
    }

    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path BOLT = buildBolt();
    private static final Path WIFI_DOT = buildWifiDot();
    private static final RectF ARC = new RectF();
    /** Scratch for the rectangular battery bar; rewritten every frame, never kept. */
    private static final Path RECT_BAR = new Path();
    /**
     * Scratch for the capsule's outline; rebuilt each pass because the gap the
     * dot rows cut changes with the stroke and with the mobile switch.
     */
    private static final Path CAPSULE = new Path();
    private static final RectF RECT_RECT = new RectF();
    /**
     * Corner radii of the lit bar segment: rounded on the left so it follows the
     * track's capsule, square on the right so the level ends on a clean vertical
     * edge. Order is the {@link Path#addRoundRect} order - TL, TR, BR, BL.
     */
    private static final float[] RECT_RADII = new float[8];

    /**
     * The weight currently installed on {@link #TEXT}; -1 until first applied.
     *
     * <p>Cached against the installed face rather than per text role: the
     * percentage and the network type can carry different weights and both may
     * be drawn in the same frame, so a role-keyed cache would make the second
     * role skip a swap it still needs. With the shared default (700) this is
     * still zero swaps per frame.
     */
    private static int sTextWeight = -1;

    /**
     * Base face the weight is applied to. {@code Typeface.create(String, int)}
     * only takes a style bitmask, so a numeric weight has to go through the
     * {@code (Typeface, int, boolean)} overload with this as its family.
     */
    private static final Typeface TEXT_BASE = Typeface.create("sans-serif", Typeface.NORMAL);

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
        FILL.setStyle(Paint.Style.FILL);
        TEXT.setStyle(Paint.Style.FILL);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setLetterSpacing(TrioGeometry.VALUE_LETTER_SPACING);
    }

    // ------------------------------------------------------------------ public

    static void draw(Canvas canvas, View host, TrioState s) {
        draw(canvas, host, s, TrioConfig.get());
    }

    /**
     * Draws the glyph with the user's current settings. The text size is applied
     * here rather than in the static initialiser so a live settings change is
     * picked up by the very next frame.
     */
    static void draw(Canvas canvas, View host, TrioState s, TrioSettings cfg) {
        drawState(canvas, host.getWidth(), host.getHeight(), s, cfg);
    }

    /**
     * The same frame for a caller that has a canvas of its own instead of a host
     * view to measure - {@code GlyphDrawable} hands the icon view's own bounds
     * through, and the slot it paints into can be asked for nothing else.
     */
    static void drawState(Canvas canvas, int w, int h, TrioState s, TrioSettings cfg) {
        if (w <= 0 || h <= 0 || s == null || cfg == null) {
            return;
        }
        drawInto(canvas, w, h, s.level, s.charging, s.quickCharging, s.powerSave, s.low,
                s.wifiPresent ? s.wifiLevel : -1, s.mobileLevel, s.slotLevels, s.mobileType,
                s.foreground(), cfg, true);
    }

    /**
     * Renders the glyph into any canvas of the given size. Kept free of
     * {@link View} so the settings app can preview the exact same drawing code
     * instead of a hand-maintained copy of it.
     */
    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean powerSave, boolean low, int wifiLevel, int mobileLevel,
                         int fg, TrioSettings cfg) {
        drawInto(canvas, w, h, level, charging, false, powerSave, low, wifiLevel, mobileLevel,
                null, null, fg, cfg, true);
    }

    /**
     * @param quickCharging {@code true} while the host reports a fast charger;
     *   only the bolt's colour changes, never its shape or size.
     * @param slotLevels the per-SIM signal levels by slot - SIM 1 first - or
     *   {@code null} when the caller has none, which keeps the single row.
     * @param clearWhenDone {@code true} when this glyph owns the whole canvas
     *   (the hooked view case); {@code false} when drawing onto a canvas that
     *   already holds the settings app's background.
     */
    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean quickCharging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel, int[] slotLevels, String mobileType,
                         int fg, TrioSettings cfg, boolean clearWhenDone) {
        if (w <= 0 || h <= 0 || cfg == null) {
            return;
        }
        // Resolve what is on screen once, here, from the single rule source. The
        // layout methods below read positions off it instead of re-deriving the
        // switches themselves, which is what used to let the two arrangements
        // drift apart - and let the documentation drift from both.
        final TrioAppearance appearance = TrioAppearance.of(cfg);
        // The three arrangements have different ink extents, so each is fitted to
        // the host view with its own box.
        final boolean rect = appearance.rect;
        final boolean capsule = appearance.capsule;
        // The overall-size setting is applied to the fit rather than to the design
        // units: the glyph's whole geometry is expressed in one 120x120 box, so
        // scaling the fit scales every stroke, arc and digit by the same factor
        // and the translate below keeps it centred. Doing it anywhere else - a
        // second canvas.scale around the layout calls, say - would scale the
        // stroke widths differently from the radii and the glyph would come apart.
        final float scale = inkScale(w, h, appearance.style) * appearance.glyphFit();
        if (scale <= 0f) {
            return;
        }

        if (clearWhenDone) {
            // The native onDraw only clears the canvas on its non-legacy path
            // (MiuiBatteryMeterIconView.onDraw L543-544). On the legacy path it just
            // calls super.onDraw and returns, so the framework's battery drawable
            // would show through underneath the glyph. Clear unconditionally: our
            // glyph is the sole content of this view either way.
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        }

        final int role = roleColor(appearance, level, charging, powerSave, low, fg);

        // A slot with no reading counts as absent, so a single-SIM device - or a
        // poll that has not answered yet - is not mistaken for a second SIM.
        final int[] slots = (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS)
                ? slotLevels : null;
        final int sims = (slots == null) ? 0
                : (slots[0] >= 0 ? 1 : 0) + (slots[1] >= 0 ? 1 : 0);

        final int save = canvas.save();
        if (capsule) {
            canvas.translate(
                    (w - TrioGeometry.CAP_INK_W * scale) / 2f - TrioGeometry.CAP_INK_X * scale,
                    (h - TrioGeometry.CAP_INK_H * scale) / 2f - TrioGeometry.CAP_INK_Y * scale);
        } else if (rect) {
            canvas.translate(
                    (w - TrioGeometry.RECT_INK_W * scale) / 2f - TrioGeometry.RECT_INK_X * scale,
                    (h - TrioGeometry.RECT_INK_H * scale) / 2f - TrioGeometry.RECT_INK_Y * scale);
        } else {
            canvas.translate(
                    (w - TrioGeometry.INK_W * scale) / 2f - TrioGeometry.INK_X * scale,
                    (h - TrioGeometry.INK_H * scale) / 2f - TrioGeometry.INK_Y * scale);
        }
        canvas.scale(scale, scale);

        if (capsule) {
            drawCapsuleLayout(canvas, level, charging, quickCharging, wifiLevel, mobileType,
                    slots, sims, role, fg, appearance);
        } else if (rect) {
            drawRectLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        } else {
            drawRingLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        }

        canvas.restoreToCount(save);
    }

    /**
     * The ring arrangement: the battery arc, then whichever pieces claim the ring
     * centre and the 12 o'clock notch.
     *
     * <p>The canvas is already scaled into the design space and positioned on the
     * ink box, so everything here is plain design-unit drawing.
     *
     * <p>Which piece goes where is not decided here - {@link TrioAppearance.Ring}
     * already resolved it from the user's switches. This method only draws.
     */
    private static void drawRingLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a) {
        final TrioAppearance.Ring r = a.ring(level, charging, wifiLevel, mobileType, sims);
        // The 12 o'clock mouth is picked by what fills it. The bolt wants the
        // narrower charging mouth; the digits and the arcs need all the room the
        // idle mouth gives them; the two dot rows want the wide mouth, because
        // the top row reaches the arc's radius and a narrow mouth would run the
        // rounded stroke end straight into the outer dot. An empty mouth just
        // breaks the ring, so it closes instead.
        final boolean dualGap = r.dual;
        final float gapStart = !r.gapUsed
                ? TrioGeometry.GAP_NONE
                : dualGap ? TrioGeometry.GAP_START_DUAL
                : r.bolt ? TrioGeometry.GAP_START_CHARGE
                : TrioGeometry.GAP_START_IDLE;
        final float gapEnd = !r.gapUsed
                ? TrioGeometry.GAP_NONE
                : dualGap ? TrioGeometry.GAP_END_DUAL
                : r.bolt ? TrioGeometry.GAP_END_CHARGE
                : TrioGeometry.GAP_END_IDLE;

        drawBattery(canvas, level, role, fg, a, gapStart, gapEnd);
        // When the percentage is centred the arcs only ever appear in the notch,
        // so a bolt that has taken the notch suppresses them outright rather than
        // letting them fall back onto the digits at the centre.
        if (r.drawWifi) {
            drawWifi(canvas, wifiLevel, fg, a, r.wifiInGap);
        }
        if (a.signalDots()) {
            drawLevelDots(canvas, mobileLevel, slotLevels, r.dual, fg, a);
        }
        if (r.typeInCentre) {
            drawCentreType(canvas, mobileType, fg, a);
        }
        if (r.typeInGap) {
            drawGapType(canvas, mobileType, fg, a);
        }
        if (r.bolt) {
            drawBolt(canvas, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        }
        if (r.drawValue) {
            drawValue(canvas, level, fg, a, r.valueInCentre);
        }
    }

    /**
     * The rectangular arrangement: two columns of four level dots, the top slot
     * between them, the enlarged percentage below that, and the battery bar
     * across the bottom.
     *
     * <p>Draw order is back to front and does not depend on which slot is used,
     * because none of the pieces overlap: the bar and the dots are the frame, and
     * the value never reaches either.
     *
     * <p>This arrangement has no notch and no ring centre, so the centring switch
     * means nothing here - and {@link TrioAppearance} already says so, which is
     * what lets the settings screen explain the switch's scope instead of
     * silently ignoring it.
     */
    private static void drawRectLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a) {
        final TrioAppearance.Rect r = a.rect(level, charging, wifiLevel, mobileType, sims);
        if (r.dots) {
            drawRectDots(canvas, mobileLevel, slotLevels, r.dual, fg, a);
        }
        drawRectBatteryBar(canvas, level, role, fg, a);

        // One slot at the top, and only one thing in it. The bolt outranks the
        // arcs exactly as it does in the ring arrangement, and the arcs outrank
        // the network type, which shares that slot rather than claiming a second
        // line of its own.
        if (r.bolt) {
            drawRectBolt(canvas, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        } else if (r.wifi) {
            drawRectWifi(canvas, wifiLevel, fg, a);
        } else if (r.type) {
            drawRectType(canvas, mobileType, fg, a);
        }

        if (r.value) {
            drawRectValue(canvas, level, fg, a);
        }
    }

    // ---------------------------------------------------------- capsule layout

    /**
     * The capsule arrangement: a stadium outline that <em>is</em> the battery
     * gauge, broken at 12 and 6 o'clock by two four-dot rows, with the time on
     * the left inside and the percentage, the arcs or the bolt on the right.
     *
     * <p>The order of the two outline passes is the whole design. The track is
     * drawn first, in full, in the track colour; then the charged part is drawn
     * over it in the role colour, covering the fraction of the outline's length
     * the level asks for. That is what reads as one line filling up rather than as
     * two lines meeting.
     *
     * <p>Both passes cut their gap at the dot rows, so the outline is drawn as the
     * two open runs it actually is and the two passes align exactly.
     *
     * <p>Nothing here competes for space the way the ring's notch does: the
     * interior is a fixed two-slot strip, so {@code valueCentred} has nothing to
     * move and the readings simply sit side by side.
     */
    private static void drawCapsuleLayout(Canvas canvas, int level, boolean charging,
                                          boolean quickCharging, int wifiLevel, String mobileType,
                                          int[] slotLevels, int sims, int role, int fg,
                                          TrioAppearance a) {
        final float stroke = a.ringStroke * TrioGeometry.CAP_STROKE_RATIO;
        final TrioAppearance.Capsule cap =
                a.capsule(level, charging, wifiLevel, mobileType, sims);

        // The track first, in full, then the charged part over it. `level < 0` is
        // "no reading yet" and fills nothing: painting a whole outline would claim
        // a full battery on a device that had simply not answered.
        STROKE.setStrokeWidth(stroke);
        STROKE.setStyle(Paint.Style.STROKE);
        buildCapsuleOutline(CAPSULE, cap.dots ? TrioGeometry.capDotReach() : 0f);
        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        canvas.drawPath(CAPSULE, STROKE);

        if (level > 0) {
            STROKE.setColor(role);
            drawCapsuleFill(canvas, cap.dots, Math.min(1f, level / 100f));
        }

        if (cap.dots) {
            drawCapsuleDots(canvas, level, slotLevels, cap.dual, stroke, fg, a);
        }

        drawCapsuleTime(canvas, stroke, fg, a);
        if (cap.bolt) {
            drawCapsuleBolt(canvas, stroke, quickCharging ? TrioGeometry.QUICK_CHARGE : fg, a);
        } else if (cap.wifi) {
            drawCapsuleWifi(canvas, stroke, wifiLevel, fg, a);
        } else if (cap.value) {
            drawCapsuleValue(canvas, stroke, level, fg, a);
        } else if (cap.type) {
            drawCapsuleType(canvas, stroke, mobileType, fg, a);
        }
    }

    /**
     * The stadium outline, with a gap cut at the top and bottom centre where the
     * dot rows cross it.
     *
     * <p>Two open subpaths, because that is what the shape is once the rows
     * interrupt it: top-centre-right → top-right run → right cap →
     * bottom-right run → bottom-centre-right, and its mirror. Each cap is two
     * quarter-circle cubics, using the usual circle-to-Bezier constant.
     *
     * <p>With {@code reach} at zero the two subpaths meet on the centre line and
     * the outline reads as continuous, which is the case when the rows are off.
     */
    private static void buildCapsuleOutline(Path p, float reach) {
        final float left = TrioGeometry.CAP_LEFT;
        final float right = TrioGeometry.CAP_RIGHT;
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        final float k = 0.5522847498307936f * r;

        p.reset();
        p.moveTo(cx + reach, top);
        p.lineTo(right - r, top);
        p.cubicTo(right - r + k, top, right, cy - k, right, cy);
        p.cubicTo(right, cy + k, right - r + k, bottom, right - r, bottom);
        p.lineTo(cx + reach, bottom);
        p.moveTo(cx - reach, bottom);
        p.lineTo(left + r, bottom);
        p.cubicTo(left + r - k, bottom, left, cy + k, left, cy);
        p.cubicTo(left, cy - k, left + r - k, top, left + r, top);
        p.lineTo(cx - reach, top);
    }

    /**
     * The charged part of the outline, walked segment by segment.
     *
     * <p>Drawn as its own geometry rather than by dashing the track's path: a
     * dash pattern would depend on the platform measuring the path for us, and
     * the fill's end would land wherever that measurement said. Walking the
     * outline explicitly means the fraction drawn is the fraction asked for, and
     * it is the same code path on the device and in the offline harness.
     *
     * <p><b>Where it fills from.</b> The two dot rows divide the outline into
     * exactly two arcs - a left one and a right one - and the level is read off
     * them as two bars: full is entirely lit, and the charge is spent from the
     * <em>bottom of the right bar</em>, travelling counterclockwise. So the grey
     * starts as a stub at the foot of the right bar and eats its way up the right
     * cap, across the top, down the left cap and along the bottom, and what is
     * left lit is the run from the bottom gap's left edge clockwise round to its
     * right edge.
     *
     * <p>That is why the walk below runs <em>clockwise from the bottom gap's left
     * edge</em>: it visits the lit arc in the order the level grows through it, so
     * simply drawing the first {@code fraction} of it puts the fill where the
     * reading is taken. The reverse walk - counterclockwise from the top gap -
     * fills from the other end and was the earlier, wrong, behaviour: it spent the
     * charge from the top left, so a half-full battery lit the top and left while
     * the user was reading the level off the right bar.
     */
    private static void drawCapsuleFill(Canvas c, boolean dots, float fraction) {
        final float left = TrioGeometry.CAP_LEFT;
        final float right = TrioGeometry.CAP_RIGHT;
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        final float reach = dots ? TrioGeometry.capDotReach() : 0f;

        // Four straight runs and two half-circle caps, in travel order. The gaps
        // are not segments: they carry no ink in either pass.
        final float topLeft = (cx - reach) - (left + r);
        final float bottomLeft = (cx - reach) - (left + r);
        final float bottomRight = (right - r) - (cx + reach);
        final float topRight = (right - r) - (cx + reach);
        final float capLen = (float) Math.PI * r;
        final float total = topLeft + capLen + bottomLeft + bottomRight + capLen + topRight;
        if (total <= 0f) {
            return;
        }
        float left0 = total * fraction;

        // The six pieces, clockwise from the bottom gap's left edge. Each starts
        // exactly where the previous one ended, so the fill has no seam; the two
        // gaps between the top and bottom runs are not pieces, because they carry
        // ink in neither pass and counting them would make the fraction wrong.
        //
        // Both caps sweep +180 here and not -180: the sweep has to follow the
        // direction of travel, or the fill would run the cap backwards and the two
        // ends would meet in the middle of it.
        left0 = run(c, left0, bottomLeft, cx - reach, bottom, left + r, bottom);
        left0 = cap(c, left0, capLen, left + r, cy, r, 90f, 180f);
        left0 = run(c, left0, topLeft, left + r, top, cx - reach, top);
        left0 = run(c, left0, topRight, cx + reach, top, right - r, top);
        left0 = cap(c, left0, capLen, right - r, cy, r, 270f, 180f);
        run(c, left0, bottomRight, right - r, bottom, cx + reach, bottom);
    }

    /**
     * Draws as much of one straight run as {@code remaining} allows, and answers
     * what is left.
     */
    private static float run(Canvas c, float remaining, float length,
                             float x1, float y1, float x2, float y2) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        if (remaining >= length) {
            c.drawLine(x1, y1, x2, y2, STROKE);
            return remaining - length;
        }
        final float t = remaining / length;
        c.drawLine(x1, y1, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, STROKE);
        return 0f;
    }

    /**
     * The same for one half-circle cap.
     *
     * <p>{@code startDeg} is the angle the cap is entered at and {@code sweepDeg}
     * is signed, so a partial cap grows from the end the walk arrives at rather
     * than from the middle - which is what keeps the fill's edge moving smoothly
     * across the cap instead of jumping when it crosses one.
     */
    private static float cap(Canvas c, float remaining, float length,
                             float cx, float cy, float r, float startDeg, float sweepDeg) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        if (remaining >= length) {
            c.drawArc(ARC, startDeg, sweepDeg, false, STROKE);
            return remaining - length;
        }
        c.drawArc(ARC, startDeg, sweepDeg * (remaining / length), false, STROKE);
        return 0f;
    }

    /**
     * The two four-dot rows, riding the outline's own centreline so a dot sits
     * exactly in the gap it interrupts.
     *
     * <p>Unlit steps are drawn too, at {@code trackAlpha}: the outline has a gap
     * there, and leaving it empty would read as a break in the line rather than
     * as an empty step.
     */
    private static void drawCapsuleDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                        float stroke, int fg, TrioAppearance a) {
        final int top = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int bottom = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int topActive = top < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, top);
        final int bottomActive = bottom < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, bottom);
        final float topY = TrioGeometry.capDotTopY();
        final float bottomY = TrioGeometry.capDotBottomY();
        final float first = TrioGeometry.CAP_DOT_CX - TrioGeometry.CAP_DOT_PITCH * 1.5f;
        FILL.setStyle(Paint.Style.FILL);

        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float x = first + i * TrioGeometry.CAP_DOT_PITCH;
            FILL.setColor(i < topActive ? fg : inactive);
            c.drawCircle(x, topY, TrioGeometry.CAP_DOT_R, FILL);
            FILL.setColor(i < bottomActive ? fg : inactive);
            c.drawCircle(x, bottomY, TrioGeometry.CAP_DOT_R, FILL);
        }
    }

    /** The time of day, in the capsule's left-hand slot. */
    private static void drawCapsuleTime(Canvas c, float stroke, int fg, TrioAppearance a) {
        final String text = TrioState.sClockText;
        if (text == null || text.isEmpty()) {
            return;
        }
        applyWeight(a.valueWeight);
        final float size = capsuleTextSize(text, a.valueSize, stroke, 100);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        TEXT.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, TrioGeometry.capLeftCx(stroke), TrioGeometry.capTextBaseline(stroke, size), TEXT);
    }

    /** The battery percentage, when the right-hand slot carries it. */
    private static void drawCapsuleValue(Canvas c, float stroke, int level, int fg,
                                         TrioAppearance a) {
        final String text = String.valueOf(level);
        applyWeight(a.valueWeight);
        final float size = capsuleTextSize(text, a.valueSize, stroke, 100);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        TEXT.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, TrioGeometry.capRightCx(stroke), TrioGeometry.capTextBaseline(stroke, size), TEXT);
    }

    /** The network type, when the right-hand slot carries it. */
    private static void drawCapsuleType(Canvas c, float stroke, String type, int fg,
                                        TrioAppearance a) {
        applyWeight(a.typeWeight);
        final float size = capsuleTextSize(type, a.typeSize, stroke, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.capRightCx(stroke), TrioGeometry.capTextBaseline(stroke, size),
                size, fg, a);
    }

    /**
     * The one place a capsule reading's size is decided, so the time and the
     * percentage cannot disagree about how big they are.
     *
     * <p>Three bounds, in the order they bind: the height the capsule leaves for
     * text, the width of the slot the reading sits in, and the user's own size
     * setting. The other two arrangements need only the second and third - they
     * are tall enough that the width always binds first - so this is the one
     * place the height has to be stated.
     */
    private static float capsuleTextSize(String text, int setting, float stroke,
                                         int suffixPercent) {
        final float requested = Math.min(setting * TrioGeometry.CAP_TEXT_SCALE,
                TrioGeometry.capTextMaxSize(stroke));
        return fitSize(text, requested, TrioGeometry.capSlotWidth(stroke), suffixPercent);
    }

    /** The Wi-Fi group moved and scaled into the capsule's right-hand slot. */
    private static void drawCapsuleWifi(Canvas c, float stroke, int level, int fg,
                                        TrioAppearance a) {
        final float scale = TrioGeometry.capWifiScale(stroke);
        final int save = c.save();
        c.translate(TrioGeometry.capWifiOffsetX(stroke),
                TrioGeometry.capWifiOffsetY(stroke));
        c.scale(scale, scale);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    /** The charging bolt, in the capsule's right-hand slot. */
    private static void drawCapsuleBolt(Canvas c, float stroke, int color, TrioAppearance a) {
        final float scale = TrioGeometry.capBoltScale(stroke);
        final int save = c.save();
        c.translate(TrioGeometry.capBoltOffsetX(stroke), TrioGeometry.capBoltOffsetY(stroke));
        c.scale(scale, scale);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    // ------------------------------------------------------ rectangular pieces

    /**
     * The two mirrored dot columns. Both carry the same level: MIUI's own meter
     * is one column, and drawing two of them is what makes the arrangement
     * symmetrical around the text rather than a list of items on one side.
     *
     * <p>In the dual-SIM reading that symmetry becomes the point: the columns are
     * already two, so the left one reports SIM 1 and the right one SIM 2 - each
     * column being one SIM's four steps, the same shape MIUI's own meter has.
     *
     * @param slotLevels per-SIM levels, or {@code null} to repeat {@code level}
     */
    private static void drawRectDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                     int fg, TrioAppearance a) {
        final int left = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int right = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int leftActive = left < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, left);
        final int rightActive = right < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, right);
        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float y = TrioGeometry.RECT_DOT_TOP + i * TrioGeometry.RECT_DOT_STEP;
            FILL.setColor(i < leftActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_LX, y, TrioGeometry.RECT_DOT_R, FILL);
            FILL.setColor(i < rightActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_RX, y, TrioGeometry.RECT_DOT_R, FILL);
        }
    }

    /**
     * The battery bar: the track as one capsule across the full width, then the
     * lit part on top of it.
     *
     * <p>The lit part is rounded on the left and square on the right, so its head
     * follows the track's cap while its tail stays a vertical edge wherever the
     * level happens to fall - a capsule at both ends would read as a pill that
     * shrinks, not as a bar that fills. The radii are clamped by the platform when
     * the segment is shorter than the corner, so a low level cannot draw a knot.
     *
     * <p>The thickness still comes from {@code ringStroke}: it is the one stroke
     * setting, and the settings screen relabels that row "bar thickness" for this
     * arrangement. A second key would only add a row that is inert in whichever
     * arrangement the user is not looking at.
     */
    private static void drawRectBatteryBar(Canvas c, int level, int role, int fg,
                                          TrioAppearance a) {
        final float left = TrioGeometry.RECT_BAR_LEFT;
        final float right = TrioGeometry.RECT_BAR_RIGHT;
        final float r = a.ringStroke * 0.5f;
        final float top = TrioGeometry.RECT_BAR_CY - r;
        final float bottom = TrioGeometry.RECT_BAR_CY + r;

        FILL.setColor(withAlpha(fg, a.trackAlpha));
        RECT_RECT.set(left, top, right, bottom);
        c.drawRoundRect(RECT_RECT, r, r, FILL);

        if (level <= 0) {
            return;
        }
        final float cut = left + (right - left) * clamp01(level / 100f);
        RECT_RECT.set(left, top, cut, bottom);
        RECT_RADII[0] = r;
        RECT_RADII[1] = r;
        RECT_RADII[2] = 0f;
        RECT_RADII[3] = 0f;
        RECT_RADII[4] = 0f;
        RECT_RADII[5] = 0f;
        RECT_RADII[6] = r;
        RECT_RADII[7] = r;
        RECT_BAR.reset();
        RECT_BAR.addRoundRect(RECT_RECT, RECT_RADII, Path.Direction.CW);
        FILL.setColor(role);
        c.drawPath(RECT_BAR, FILL);
    }

    /** The Wi-Fi group moved into the top slot; the arcs themselves are unchanged. */
    private static void drawRectWifi(Canvas c, int level, int fg, TrioAppearance a) {
        final int save = c.save();
        c.translate(TrioGeometry.rectWifiOffsetX(), TrioGeometry.rectWifiOffsetY(a.arcStroke));
        c.scale(TrioGeometry.RECT_WIFI_SCALE, TrioGeometry.RECT_WIFI_SCALE);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    private static void drawRectBolt(Canvas c, int color) {
        final int save = c.save();
        c.translate(TrioGeometry.rectBoltOffsetX(), TrioGeometry.rectBoltOffsetY());
        c.scale(TrioGeometry.BOLT_SCALE, TrioGeometry.BOLT_SCALE);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    /**
     * The network type in the top slot, between the dot columns.
     *
     * <p>Sized against {@link TrioGeometry#RECT_TYPE_SCALE} like the percentage
     * is against its own, so the two read as a pair, and shrunk by
     * {@link #fitSize} if the slot is too narrow for it.
     */
    private static void drawRectType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize * TrioGeometry.RECT_TYPE_SCALE;
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, TrioGeometry.RECT_TEXT_CLEAR, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_TYPE_BASELINE, size, fg, a);
    }

    /** The percentage, in the middle of the arrangement and at its own scale. */
    private static void drawRectValue(Canvas c, int level, int fg, TrioAppearance a) {
        final String text = String.valueOf(level);
        final float requested = a.valueSize * TrioGeometry.RECT_VALUE_SCALE;
        applyWeight(a.valueWeight);
        final float size = fitSize(text, requested, TrioGeometry.RECT_TEXT_CLEAR);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        c.drawText(text, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_VALUE_BASELINE, TEXT);
    }

    /**
     * The colour of the lit part of the ring and the value text.
     *
     * <p>Priority: critical battery, then power-save/low battery, then charging,
     * then the framework's own foreground. {@code isDarkBackground(fg)} tells
     * which variant of a role colour to use: a light icon colour means the status
     * bar is dark, so the light-background variant applies.
     */
    static int roleColor(TrioAppearance a, int level, boolean charging,
                         boolean powerSave, boolean low, int fg) {
        if (a == null || !a.roleColors) {
            return fg;
        }
        final boolean dark = isDarkBackground(fg);
        if (level >= 0 && level < TrioGeometry.CRITICAL_LEVEL) {
            return dark ? a.criticalOnDark : a.criticalOnLight;
        }
        if (powerSave || low || (level >= 0 && level <= a.lowThreshold)) {
            return dark ? a.lowOnDark : a.lowOnLight;
        }
        if (charging) {
            return dark ? a.chargingOnDark : a.chargingOnLight;
        }
        return fg;
    }

    // ----------------------------------------------------------------- battery

    private static void drawBattery(Canvas c, int level, int role, int fg,
                                    TrioAppearance a, float gapStart, float gapEnd) {
        STROKE.setStrokeWidth(a.ringStroke);

        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        batteryRing(c, 0f, 1f, gapStart, gapEnd);

        if (level > 0) {
            STROKE.setColor(role);
            batteryRing(c, 0f, Math.min(1f, level / 100f), gapStart, gapEnd);
        }
    }

    /**
     * Draws the open battery arc from progress {@code from} to {@code to},
     * skipping the segment hidden behind the top gap.
     *
     * <p>Both ends are progress of the arc that is <em>actually drawn</em>, not
     * of the full path: the two segments the mouth leaves behind divide the
     * level between them, so 50% ends exactly where the left segment does and
     * the mouth never consumes any of it. Measuring over the whole path instead
     * made the level stand still across the entire width of the mouth — the
     * right half did not start filling until the level had climbed past 67%.
     */
    private static void batteryRing(Canvas c, float from, float to, float gapStart, float gapEnd) {
        from = clamp01(from);
        to = clamp01(to);
        if (to <= from) {
            return;
        }
        final float mouthStart = clamp01(gapStart);
        final float mouthEnd = clamp01(gapEnd);
        // Path length that actually carries ink; the level is spread over it.
        final float drawn = mouthStart + (1f - mouthEnd);
        if (drawn <= 0f) {
            return;
        }
        final float visibleFrom = from * drawn;
        final float visibleTo = to * drawn;
        // First segment: visible progress [0, mouthStart] is path [0, mouthStart].
        final float firstEnd = Math.min(visibleTo, mouthStart);
        if (firstEnd > visibleFrom) {
            arcByProgress(c, visibleFrom, firstEnd, TrioGeometry.B_CX, TrioGeometry.B_CY,
                    TrioGeometry.B_R);
        }
        // Second segment: visible progress (mouthStart, drawn] is path (mouthEnd, 1].
        final float secondFrom = Math.max(visibleFrom, mouthStart);
        if (visibleTo > secondFrom) {
            arcByProgress(c, mouthEnd + secondFrom - mouthStart, mouthEnd + visibleTo - mouthStart,
                    TrioGeometry.B_CX, TrioGeometry.B_CY, TrioGeometry.B_R);
        }
    }

    private static void arcByProgress(Canvas c, float from, float to, float cx, float cy, float r) {
        arc(c, cx, cy, r,
                TrioGeometry.B_START + from * TrioGeometry.B_SWEEP,
                (to - from) * TrioGeometry.B_SWEEP);
    }

    private static void arc(Canvas c, float cx, float cy, float r, float startDeg, float sweepDeg) {
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(ARC, startDeg, sweepDeg, false, STROKE);
    }

    // -------------------------------------------------------------------- wifi

    /**
     * @param gap {@code true} when the centred-value setting has moved the arcs
     *   up into the 12 o'clock notch. The whole group is then translated and
     *   scaled on the canvas, which keeps one set of reference coordinates for
     *   both slots - the same trick the bolt uses.
     */
    private static void drawWifi(Canvas c, int level, int fg, TrioAppearance a, boolean gap) {
        final int save = gap ? c.save() : 0;
        if (gap) {
            c.translate(TrioGeometry.gapWifiOffsetX(), TrioGeometry.gapWifiOffsetY(a.arcStroke));
            c.scale(TrioGeometry.GAP_WIFI_SCALE, TrioGeometry.GAP_WIFI_SCALE);
        }
        drawWifiArcs(c, level, fg, a);
        if (gap) {
            c.restoreToCount(save);
        }
    }

    /**
     * The arcs and the dot at their reference coordinates, with no transform of
     * their own: each caller positions the group first. Both arrangements draw
     * the identical group, which is what keeps the Wi-Fi icon the same in both.
     */
    private static void drawWifiArcs(Canvas c, int level, int fg, TrioAppearance a) {
        STROKE.setStrokeWidth(a.arcStroke);
        STROKE.setColor(fg);
        if (level >= 3) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W1_CY, TrioGeometry.W1_R,
                    TrioGeometry.W1_START, TrioGeometry.W1_SWEEP);
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        } else if (level == 2) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        }
        if (level >= 1) {
            FILL.setColor(fg);
            c.drawPath(WIFI_DOT, FILL);
        }
    }

    // ------------------------------------------------------------- level dots

    /**
     * The signal meter: four steps, lit from the left.
     *
     * <p>Single row: one SIM's level, the current data SIM. The meter keeps MIUI's
     * own {@code DOT_R} here, because a lone row has the whole ring to itself.
     *
     * <p>Dual reading: one row per SIM, SIM 1 above and SIM 2 below, each filled
     * from its own level. Both rows use {@link TrioGeometry#DUAL_DOT_R}: they are
     * symmetric about the ring's centre and have to read as one paired meter, so a
     * row sized for a lone arc would look shrunken next to its twin. A slot with no
     * reading lights nothing, which is how an empty slot reads as empty rather
     * than as a second full-strength SIM.
     *
     * @param slotLevels per-SIM levels by slot, or {@code null} for one row
     * @param dual whether the two rows are drawn instead of one
     */
    private static void drawLevelDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                     int fg, TrioAppearance a) {
        final int inactive = withAlpha(fg, a.trackAlpha);
        if (!dual || slotLevels == null) {
            final int active = level < 0 ? 0 : Math.min(TrioGeometry.DOTS.length, level);
            for (int i = 0; i < TrioGeometry.DOTS.length; i++) {
                FILL.setColor(i < active ? fg : inactive);
                c.drawCircle(TrioGeometry.DOTS[i][0], TrioGeometry.DOTS[i][1],
                        TrioGeometry.DOT_R, FILL);
            }
            return;
        }
        final int row = TrioGeometry.DOTS.length;
        for (int i = 0; i < TrioGeometry.DOTS_DUAL.length; i++) {
            final int step = i % row;
            final int slotLevel = slotLevels[i / row];
            final int active = slotLevel < 0 ? 0 : Math.min(row, slotLevel);
            FILL.setColor(step < active ? fg : inactive);
            c.drawCircle(TrioGeometry.DOTS_DUAL[i][0], TrioGeometry.DOTS_DUAL[i][1],
                    TrioGeometry.DUAL_DOT_R, FILL);
        }
    }

    // ------------------------------------------------- out-of-ring signal

    /**
     * Scratch for one capsule bar; rewritten every draw, never kept.
     *
     * <p>The out-of-ring reading is painted on a view of its own rather than
     * inside the glyph's {@code onDraw}, but never reentrantly: it is one view
     * per battery container, and a container paints its children one at a time.
     */
    private static final RectF OUT_BAR = new RectF();

    /**
     * Resolves the two readings the out-of-ring glyph draws: the capsule row, and
     * the dot row beneath it.
     *
     * <p>Row one is the first SIM that answered and row two the second, which is
     * also why the dot row disappears when only one SIM answered. Four ascending
     * bars is what a lone reading looks like, so a single SIM must not be split
     * across two rows just to fill the space.
     *
     * <p>{@code dataSimOnly} collapses the pair the other way: the reading that
     * matters is the data SIM's, and the other one is dropped whether or not it
     * answered. The data SIM keeps the bars, because that is the shape a lone
     * reading has.
     *
     * <p>When telephony never answered, the aggregate reading the glyph itself
     * uses is the last resort, so a missing subscription list does not leave the
     * out-of-ring glyph blank while the in-ring one still reads a level.
     *
     * @param out receives {@code {barLevel, dotLevel}}; a dot level of {@code -1}
     *            means "no second row", and a bar level of {@code -1} means
     *            "nothing to read"
     */
    static void outSignalLevels(boolean dataSimOnly, int mobileLevel, int[] slotLevels,
                               int dataSlot, int[] out) {
        int slot0 = -1;
        int slot1 = -1;
        if (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS) {
            slot0 = slotLevels[0];
            slot1 = slotLevels[1];
        }
        final int firstAnswered = slot0 >= 0 ? slot0 : slot1;
        int bars;
        int dots = -1;
        if (dataSimOnly) {
            bars = firstAnswered;
            if (dataSlot == 0 && slot0 >= 0) {
                bars = slot0;
            } else if (dataSlot == 1 && slot1 >= 0) {
                bars = slot1;
            }
        } else if (slot0 >= 0 && slot1 >= 0) {
            bars = slot0;
            dots = slot1;
        } else {
            bars = firstAnswered;
        }
        if (bars < 0) {
            bars = mobileLevel;
        }
        out[0] = bars;
        out[1] = dots;
    }

    /**
     * How much of the reference box the drawing actually fills, with or without a
     * dot row. Used to centre the ink inside a box that is always
     * {@link TrioGeometry#STACK_INK_H} tall.
     *
     * <p>Deliberately no longer what the scale is divided by. It used to be, and
     * that made a reading without a dot row divide its pixels by a box 209/150
     * times shorter - so the very same four bars came out 1.39x taller beside no
     * dot row than beside one, and the single-SIM reading towered over the
     * dual-SIM one. The box is the reading's frame, not its content.
     */
    static float outSignalInkH(boolean dots) {
        return dots ? TrioGeometry.STACK_INK_H
                : TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1];
    }

    /**
     * The pixel height of the reading's box at the user's {@code sizeDp}, on a
     * display of the given {@code density}.
     *
     * <p>Deliberately independent of any view: the reading's size used to be a
     * share of the row it stood in, so MIUI laying that row out taller - which
     * is exactly what pulling the control centre down does, 88px to 134px on the
     * test device - made the reading grow with it. A dp is fixed.
     *
     * <p>One box for both shapes: a reading with a dot row and one without are
     * given the same box, so switching between them - a second SIM answering, or
     * the data-SIM-only switch - resizes nothing.
     */
    static int outSignalHeight(int sizeDp, float density) {
        if (sizeDp <= 0 || density <= 0f) {
            return 0;
        }
        return Math.max(1, Math.round(sizeDp * density));
    }

    /**
     * The pixel width that gives a box {@code height} pixels tall the reference
     * aspect, dot row or not.
     */
    static int outSignalWidth(int height) {
        if (height <= 0) {
            return 0;
        }
        return Math.max(1,
                Math.round(height * TrioGeometry.STACK_INK_W / TrioGeometry.STACK_INK_H));
    }

    /**
     * Paints the out-of-ring reading into a canvas of its own, in that canvas's
     * pixel space.
     *
     * <p>Laid out from the reference image's own units rather than from the
     * glyph's 120x120 design space: this reading is not inside the ring, and
     * scaling it by the ink box above would tie a status bar height to a number
     * that describes a battery. The reference units are uniform in both axes, so
     * one scale factor keeps the bar steps, the pitch and the round caps in the
     * proportions the reference draws.
     *
     * <p>A reading below four lights its remaining bars in the track colour
     * instead of drawing a partial row: the row length is the reading, and the
     * unlit part of it is what says how much is missing.
     *
     * @return whether anything was painted
     */
    static boolean drawOutSignal(Canvas c, int width, int height, int bars, int dots,
                                 int fg, TrioAppearance a) {
        if (bars < 0 || width <= 0 || height <= 0) {
            return false;
        }
        final boolean dotRow = dots >= 0;
        final float inkW = TrioGeometry.STACK_INK_W;
        // The scale is always taken from the full reference box, never from the
        // shorter ink a reading without a dot row actually fills: that shorter
        // box is only what centres the drawing here, and dividing the pixels by
        // it would make the same bars a third taller whenever the dot row is
        // absent.
        final float inkH = outSignalInkH(dotRow);
        final float scale = Math.min(width / inkW, height / TrioGeometry.STACK_INK_H);
        if (scale <= 0f) {
            return false;
        }
        final int columns = TrioGeometry.STACK_COLUMNS;
        final float x0 = (width - inkW * scale) / 2f;
        // One baseline for all four bars: the steps are in the tops, which is
        // what makes the row read as a level rather than as four separate bars.
        final float baseline = (height - inkH * scale) / 2f
                + TrioGeometry.STACK_BAR_H[columns - 1] * scale;
        final float barW = TrioGeometry.STACK_BAR_W * scale;
        final float radius = barW / 2f;
        final float dotR = TrioGeometry.STACK_DOT_D * scale / 2f;
        final float dotCy = baseline
                + (TrioGeometry.STACK_DOT_GAP + TrioGeometry.STACK_DOT_D / 2f) * scale;
        final int inactive = withAlpha(fg, a.trackAlpha);
        for (int i = 0; i < columns; i++) {
            final float left = x0 + i * TrioGeometry.STACK_PITCH * scale;
            FILL.setColor(i < bars ? fg : inactive);
            OUT_BAR.set(left, baseline - TrioGeometry.STACK_BAR_H[i] * scale,
                    left + barW, baseline);
            c.drawRoundRect(OUT_BAR, radius, radius, FILL);
            if (dotRow) {
                FILL.setColor(i < dots ? fg : inactive);
                c.drawCircle(left + radius, dotCy, dotR, FILL);
            }
        }
        return true;
    }

    // ------------------------------------------------------------ top gap fill

    /**
     * Installs {@code weight} on {@link #TEXT}, skipping the work when that
     * weight is already the installed one. Every draw runs inside onDraw, so the
     * cached comparison keeps a per-frame {@code Typeface} allocation off the hot
     * path. The percentage and the network type share this cache because they
     * share the paint; when they differ both pay one swap per frame, which
     * {@link Typeface#create(Typeface, int, boolean)} answers from its own cache.
     */
    private static void applyWeight(int weight) {
        if (weight == sTextWeight) {
            return;
        }
        TEXT.setTypeface(typefaceFor(weight));
        sTextWeight = weight;
    }

    /**
     * Draws the value in whichever slot it belongs to.
     *
     * @param centre {@code true} to put the percentage in the middle of the ring,
     *   {@code false} to keep it in the 12 o'clock gap next to the charging bolt.
     */
    private static void drawValue(Canvas c, int level, int fg, TrioAppearance a, boolean centre) {
        final String text = String.valueOf(level);
        final float requested = centre ? TrioGeometry.centreSize(a.valueSize) : a.valueSize;
        final float clear = centre ? TrioGeometry.centreClearWidth(requested, a.ringStroke)
                                   : TrioGeometry.gapClearWidth(a.ringStroke);
        // The typeface has to be current before measuring: a heavier weight is
        // wider, and fitSize decides whether the text still clears the gap.
        applyWeight(a.valueWeight);
        final float size = fitSize(text, requested, clear);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        c.drawText(text, TrioGeometry.VALUE_X,
                centre ? TrioGeometry.centerBaseline(size)
                       : TrioGeometry.gapBaseline(size),
                TEXT);
    }

    /**
     * Draws the mobile network type ("5G", "5GA", ...) in the middle of the ring.
     *
     * <p>Reached only when the percentage is not claiming the centre, so this
     * never competes with the value for the same space.
     */
    private static void drawCentreType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize;
        final float clear = TrioGeometry.centreClearWidth(requested, a.ringStroke);
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, clear, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.VALUE_X, TrioGeometry.centerBaseline(size), size, fg, a);
    }

    /**
     * Draws the mobile network type in the 12 o'clock gap. Used when the
     * percentage has taken the ring centre, which is what the centred-value
     * setting promises, and the notch is otherwise empty: the type is squeezed
     * down to whatever the narrower slot affords rather than being dropped.
     */
    private static void drawGapType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize;
        final float clear = TrioGeometry.gapClearWidth(a.ringStroke);
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, clear, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.VALUE_X, TrioGeometry.gapBaseline(size), size, fg, a);
    }

    /**
     * Draws the network type at {@code x} on {@code baseline}, shrinking the
     * trailing "A" when the user asked for it.
     *
     * <p>{@code Canvas.drawText(CharSequence, ...)} does <em>not</em> apply
     * spans - only a {@code TextPaint} through {@code StaticLayout} does - so
     * the two sizes are drawn as two runs. The {@link Paint} is left aligned for
     * the duration: {@link #TEXT} is centred, and centring each run separately
     * would stack them on the same spot instead of setting them side by side.
     *
     * <p>The suffix shares the main run's baseline, which is what puts its foot
     * on the same line as the "5G" rather than dropping it into the ring.
     *
     * @param x the centre of the whole label: the single-run path passes it
     *          straight to a centred {@link Paint}, and the two-run path uses it
     *          to centre the block it composes
     */
    private static void drawType(Canvas c, String type, float x, float baseline,
                                 float size, int fg, TrioAppearance a) {
        if (!TrioGeometry.hasShrunkSuffix(type, a.typeSuffixScale)) {
            TEXT.setTextAlign(Paint.Align.CENTER);
            TEXT.setTextSize(size);
            TEXT.setColor(fg);
            c.drawText(type, x, baseline, TEXT);
            return;
        }
        final String base = TrioGeometry.typeBase(type);
        final String suffix = String.valueOf(TrioGeometry.TYPE_SUFFIX);
        final float suffixSize = size * a.typeSuffixScale / 100f;
        // Measure both runs, then place them as one centred block: total width
        // is the layout, and the label's own centre is what stays put.
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setColor(fg);
        TEXT.setTextSize(size);
        final float baseWidth = TEXT.measureText(base);
        TEXT.setTextSize(suffixSize);
        final float suffixWidth = TEXT.measureText(suffix);
        float left = x - (baseWidth + suffixWidth) * 0.5f;
        TEXT.setTextSize(size);
        c.drawText(base, left, baseline, TEXT);
        left += baseWidth;
        TEXT.setTextSize(suffixSize);
        c.drawText(suffix, left, baseline, TEXT);
        // Restore the shared paint's centred default: every other text draw in
        // this renderer relies on it, and this method is not the last per frame.
        TEXT.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Draws the charging bolt scaled up from its reference path. The transform is
     * applied to the canvas rather than baked into the path so the reference
     * coordinates stay readable, and it is saved/restored so nothing else moves.
     *
     * <p>The bolt lives in the 12 o'clock gap at the size the reference path
     * implies. It is never enlarged into the ring centre: that slot belongs to
     * the percentage, which is the point of the centred-value setting.
     *
     * @param color bolt fill; amber while the battery reports quick charge.
     */
    private static void drawBolt(Canvas c, int color) {
        final int save = c.save();
        c.translate(TrioGeometry.boltOffsetX(), TrioGeometry.boltOffsetY());
        c.scale(TrioGeometry.BOLT_SCALE, TrioGeometry.BOLT_SCALE);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    /**
     * Largest font size at which {@code text} fits in {@code clearWidth} design
     * units, capped by {@code requested}. "100" is far wider than "9", so a
     * long value shrinks instead of overrunning the ring.
     */
    private static float fitSize(String text, float requested, float clearWidth) {
        return fitSize(text, requested, clearWidth, 100);
    }

    /**
     * The same fit, measuring the label the way {@link #drawType} will lay it
     * out: a shrunk trailing "A" carries only {@code scalePercent} of the main
     * size, so it takes less room and the main run is left correspondingly
     * larger. Measuring the whole string at {@code requested} would shrink a
     * label that in fact fits.
     */
    private static float fitSize(String text, float requested, float clearWidth,
                                 int scalePercent) {
        if (clearWidth <= 0f) {
            return requested;
        }
        TEXT.setTextSize(requested);
        final float width = measureType(text, requested, scalePercent);
        if (width <= clearWidth || width <= 0f) {
            return requested;
        }
        return requested * (clearWidth / width);
    }

    /**
     * Width of {@code text} at {@code size}, with the trailing suffix scaled.
     *
     * <p>Leaves {@link #TEXT} at {@code size}, which is what every caller wants
     * next: {@link #fitSize} returns and the draw path re-sets the size anyway.
     */
    private static float measureType(String text, float size, int scalePercent) {
        if (!TrioGeometry.hasShrunkSuffix(text, scalePercent)) {
            TEXT.setTextSize(size);
            return TEXT.measureText(text);
        }
        TEXT.setTextSize(size);
        final float base = TEXT.measureText(TrioGeometry.typeBase(text));
        TEXT.setTextSize(size * scalePercent / 100f);
        final float suffix = TEXT.measureText(String.valueOf(TrioGeometry.TYPE_SUFFIX));
        TEXT.setTextSize(size);
        return base + suffix;
    }

    // ----------------------------------------------------------------- helpers

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Pixels per design unit for a host of this size: the uniform scale that
     * fits the 120x120 design box into the view, i.e. the same factor
     * {@link #drawInto} applies to its canvas. Anything drawn outside the
     * canvas (the out-of-ring network type label) has to convert its design-unit
     * size with this, or it will not match the glyph next to it.
     */
    static float inkScale(int w, int h) {
        return inkScale(w, h, TrioGeometry.INK_W, TrioGeometry.INK_H);
    }

    /**
     * The fit for whichever arrangement is selected. The settings preview draws
     * its own label beside the glyph, so it has to know the same factor
     * {@link #drawInto} will use - and that depends on the style.
     */
    static float inkScale(int w, int h, boolean rect) {
        return rect
                ? inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H)
                : inkScale(w, h);
    }

    /**
     * The same fit, chosen by the stored style.
     *
     * <p>Distinct from the boolean overload above because the capsule is a third
     * ink box rather than a variant of one of the other two - and because the
     * out-of-ring label has to agree with whichever arrangement is in force.
     */
    static float inkScale(int w, int h, int style) {
        if (style == Prefs.STYLE_RECT) {
            return inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H);
        }
        if (style == Prefs.STYLE_CAPSULE) {
            return inkScale(w, h, TrioGeometry.CAP_INK_W, TrioGeometry.CAP_INK_H);
        }
        return inkScale(w, h);
    }

    /**
     * The same fit for an arbitrary ink box. The two arrangements have different
     * ink extents, so the scale that fits one does not fit the other.
     */
    static float inkScale(int w, int h, float inkW, float inkH) {
        if (w <= 0 || h <= 0) {
            return 0f;
        }
        return Math.min(w / inkW, h / inkH);
    }

    /**
     * The shared text face at a numeric weight. The settings app and the hooked
     * side have to agree on it, and so do the in-canvas glyph and the label view
     * drawn beside it.
     */
    static Typeface typefaceFor(int weight) {
        return Typeface.create(TEXT_BASE, weight, false);
    }

    // ----------------------------------------------------------- path builders

    /** The charging bolt outline, in 120x120 design units. */
    private static Path buildBolt() {
        final Path p = new Path();
        p.moveTo(62.1f, 2.2f);
        p.quadTo(62.8f, 2.5f, 62.6f, 3.3f);
        p.lineTo(61.2f, 7.8f);
        p.lineTo(65.9f, 7.8f);
        p.quadTo(66.9f, 7.8f, 67.3f, 8.6f);
        p.quadTo(67.6f, 9.3f, 67.0f, 10.0f);
        p.lineTo(57.0f, 21.3f);
        p.quadTo(56.4f, 22.0f, 55.6f, 21.6f);
        p.quadTo(55.0f, 21.3f, 55.3f, 20.5f);
        p.lineTo(57.4f, 14.1f);
        p.lineTo(52.9f, 14.1f);
        p.quadTo(52.0f, 14.1f, 51.6f, 13.3f);
        p.quadTo(51.3f, 12.6f, 51.9f, 12.0f);
        p.lineTo(61.1f, 2.7f);
        p.quadTo(61.6f, 2.1f, 62.1f, 2.2f);
        p.close();
        return p;
    }

    /** The innermost Wi-Fi dot, in 120x120 design units. */
    private static Path buildWifiDot() {
        final Path p = new Path();
        p.moveTo(59.5f, 69.9f);
        p.cubicTo(61.0f, 69.9f, 65.2f, 70.8f, 66.5f, 73.0f);
        p.cubicTo(66.7f, 73.8f, 66.7f, 74.3f, 66.5f, 75.0f);
        p.cubicTo(63.8f, 78.8f, 61.15f, 80.95f, 59.5f, 80.95f);
        p.cubicTo(57.85f, 80.95f, 55.2f, 78.8f, 52.5f, 75.0f);
        p.cubicTo(52.3f, 74.3f, 52.3f, 73.8f, 52.5f, 73.0f);
        p.cubicTo(53.8f, 70.8f, 58.0f, 69.9f, 59.5f, 69.9f);
        p.close();
        return p;
    }

    /** sRGB luminance test: a light icon colour implies a dark status bar. */
    static boolean isDarkBackground(int color) {
        final float lum = (0.2126f * Color.red(color)
                + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
        return lum > 0.5f;
    }
}
